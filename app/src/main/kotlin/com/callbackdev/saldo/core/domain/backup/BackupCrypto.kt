package com.callbackdev.saldo.core.domain.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seals and opens the [EncryptedBackup] container (Fase 22, ADR 44).
 *
 * AES-256-GCM over the JSON document, key derived from the passphrase with
 * PBKDF2-HMAC-SHA256 - the same primitive as the app-lock PIN (ADR 39), with a
 * far higher work factor because a passphrase is typed once per backup and not
 * at every unlock. Only `javax.crypto` of the platform: no new dependency.
 *
 * Two properties worth stating, because both are user-visible promises:
 * - the header is authenticated, not just carried: container version, KDF,
 *   cipher and iteration count are the GCM associated data, so editing them in
 *   a text editor makes the file fail to open instead of silently changing how
 *   it is read;
 * - a wrong passphrase is *detected* (the GCM tag does not verify) rather than
 *   producing garbage, which is what lets the restore say "wrong passphrase"
 *   instead of "damaged file".
 *
 * There is no way back from a lost passphrase, by construction: the key exists
 * only while it is being derived, and the app is offline with nowhere to escrow
 * anything. The UI says so before the user turns encryption on.
 *
 * Derivation is deliberately slow (hundreds of milliseconds): callers run it off
 * the main thread.
 */
object BackupCrypto {

    /**
     * Wraps [json] into a container that only [passphrase] can open. A fresh
     * random salt and IV per call: exporting the same data twice never produces
     * the same bytes, and an IV is never reused across files.
     *
     * [passphrase] is not modified and not retained; the caller owns wiping it.
     */
    fun seal(json: String, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): EncryptedBackup {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = EncryptedBackup(
            iterations = iterations,
            saltBase64 = salt.encodeBase64(),
            ivBase64 = iv.encodeBase64(),
            payloadBase64 = "",
        )
        val cipher = Cipher.getInstance(EncryptedBackup.CIPHER).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(header.associatedData())
        }
        return header.copy(payloadBase64 = cipher.doFinal(json.toByteArray(Charsets.UTF_8)).encodeBase64())
    }

    /**
     * Returns the JSON document inside [envelope], or fails with the reason.
     *
     * @throws BackupCryptoException.UnsupportedContainer for a container written
     *   by a newer app: refusing it is honest, guessing its layout is not.
     * @throws BackupCryptoException.Corrupted when the header or the payload are
     *   malformed - including an iteration count outside the plausible range,
     *   which would otherwise let a crafted file hang the app on derivation.
     * @throws BackupCryptoException.WrongPassphrase when authentication fails.
     */
    @Suppress("ThrowsCount") // Typed failures are this function's contract.
    fun open(envelope: EncryptedBackup, passphrase: CharArray): String {
        if (envelope.container > EncryptedBackup.CONTAINER_VERSION) {
            throw BackupCryptoException.UnsupportedContainer(envelope.container)
        }
        val salt = envelope.saltBase64.decodeBase64OrCorrupted()
        val iv = envelope.ivBase64.decodeBase64OrCorrupted()
        val payload = envelope.payloadBase64.decodeBase64OrCorrupted()
        // A payload no longer than the tag cannot be something this app sealed:
        // that is a damaged file, and calling it a wrong passphrase would send
        // the user hunting for a passphrase that was never the problem.
        if (envelope.iterations !in SUPPORTED_ITERATIONS ||
            salt.isEmpty() ||
            iv.size != IV_BYTES ||
            payload.size <= TAG_BITS / Byte.SIZE_BITS
        ) {
            throw BackupCryptoException.Corrupted()
        }
        // No export can produce an empty passphrase (the UI asks for at least a
        // few characters), so one cannot be the right one - and some providers
        // reject it outright while deriving, which would be a crash and not an
        // answer.
        if (passphrase.isEmpty()) throw BackupCryptoException.WrongPassphrase()
        val plaintext = try {
            Cipher.getInstance(EncryptedBackup.CIPHER).run {
                init(
                    Cipher.DECRYPT_MODE,
                    deriveKey(passphrase, salt, envelope.iterations),
                    GCMParameterSpec(TAG_BITS, iv),
                )
                updateAAD(envelope.associatedData())
                doFinal(payload)
            }
        } catch (error: AEADBadTagException) {
            throw BackupCryptoException.WrongPassphrase(error)
        } catch (error: GeneralSecurityException) {
            // Wrong sizes, unusable payload: the bytes are not a container we
            // produced, which is a damaged file and not a wrong passphrase.
            throw BackupCryptoException.Corrupted(error)
        } catch (error: IllegalArgumentException) {
            // Whatever the crypto provider refuses to even attempt lands here.
            // The caller is only allowed the three typed failures, so an
            // unexpected one must never escape as a crash.
            throw BackupCryptoException.Corrupted(error)
        }
        return String(plaintext, Charsets.UTF_8)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(EncryptedBackup.KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * The header fields bound to the ciphertext. Everything that changes how the
     * payload is read is in here, so tampering with any of it breaks the tag.
     */
    private fun EncryptedBackup.associatedData(): ByteArray =
        "$format:$container:$kdf:$cipher:$iterations".toByteArray(Charsets.US_ASCII)

    private fun ByteArray.encodeBase64(): String = Base64.getEncoder().encodeToString(this)

    private fun String.decodeBase64OrCorrupted(): ByteArray =
        try {
            Base64.getDecoder().decode(this)
        } catch (error: IllegalArgumentException) {
            throw BackupCryptoException.Corrupted(error)
        }

    private const val SALT_BYTES = 16

    /** 96 bits, the size GCM is specified for. */
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /**
     * OWASP's recommended work factor for PBKDF2-HMAC-SHA256: roughly half a
     * second on a mid-range device, paid once per export or restore. The PIN
     * uses far less (ADR 39) because it is derived at every unlock, where the
     * same cost would be felt as lag.
     */
    const val DEFAULT_ITERATIONS = 600_000

    /**
     * Iteration counts this app will spend time on. The floor keeps a
     * downgraded file from passing as protected; the ceiling keeps a crafted
     * header from turning a restore into an endless derivation.
     */
    private val SUPPORTED_ITERATIONS = 100_000..2_000_000
}
