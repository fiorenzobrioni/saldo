package com.callbackdev.saldo.core.common.applock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A PIN at rest: random salt, PBKDF2 hash and the iteration count that
 * produced it, all ready for the preferences store. The iterations travel
 * with the hash so the work factor can be raised later without migrating
 * existing PINs: old entries keep verifying with their own count.
 */
data class StoredPin(
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
)

/**
 * Derives and verifies the app-lock PIN with PBKDF2-HMAC-SHA256 (ADR 39).
 *
 * The PIN is never persisted in clear text, but this is honest key
 * stretching, not forensics-grade protection: a 6-digit space is small
 * enough that no iteration count survives an offline brute force. The
 * threat model is casual access on an unlocked phone; the database itself
 * is not encrypted with the PIN. Pure `javax.crypto`, so the round trip is
 * fully unit-testable on the JVM.
 *
 * Derivation takes ~100-200ms by design; callers run it off the main thread.
 */
@Singleton
class PinHasher @Inject constructor() {

    /** Hashes [pin] with a fresh random salt at the current work factor. */
    fun create(pin: String): StoredPin {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt, DEFAULT_ITERATIONS)
        return StoredPin(
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(hash),
            iterations = DEFAULT_ITERATIONS,
        )
    }

    /**
     * Whether [pin] matches [stored]. Constant-time comparison via
     * [MessageDigest.isEqual]; any decode failure counts as a mismatch
     * rather than an exception, so a corrupted entry can never crash the
     * lock screen.
     */
    fun matches(pin: String, stored: StoredPin): Boolean = runCatching {
        val salt = Base64.getDecoder().decode(stored.saltBase64)
        val expected = Base64.getDecoder().decode(stored.hashBase64)
        MessageDigest.isEqual(derive(pin, salt, stored.iterations), expected)
    }.getOrDefault(false)

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val SALT_BYTES = 16
        const val KEY_BITS = 256

        /**
         * ~100-200ms on a mid-range device: the point where the unlock still
         * feels instant while the derivation is not free (ADR 39).
         */
        const val DEFAULT_ITERATIONS = 150_000
    }
}
