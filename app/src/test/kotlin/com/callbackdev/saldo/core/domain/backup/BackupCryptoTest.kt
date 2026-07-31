package com.callbackdev.saldo.core.domain.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * The Fase 22 contract: what goes into the container comes back out, and every
 * way of not getting it back is a distinct, honest failure.
 *
 * Sealing uses a reduced (but still accepted) work factor so the suite stays
 * fast; the shipped default is asserted on its own.
 */
class BackupCryptoTest {

    private val passphrase = "correct horse battery staple"
    private val payload = """{"format": "saldo-backup", "version": 1, "data": {}}"""

    @Test
    fun `seal then open returns the exact same document`() {
        val envelope = seal(payload)

        assertEquals(payload, BackupCrypto.open(envelope, passphrase.toCharArray()))
    }

    @Test
    fun `non-ascii content survives the round trip`() {
        val accented = """{"note": "caffè, però: 日本 - ünïcödé"}"""

        val envelope = seal(accented)

        assertEquals(accented, BackupCrypto.open(envelope, passphrase.toCharArray()))
    }

    @Test
    fun `a wrong passphrase is reported as such, not as a damaged file`() {
        val envelope = seal(payload)

        assertThrows(BackupCryptoException.WrongPassphrase::class.java) {
            BackupCrypto.open(envelope, "correct horse battery stapl".toCharArray())
        }
    }

    @Test
    fun `an empty passphrase does not open a container`() {
        val envelope = seal(payload)

        assertThrows(BackupCryptoException.WrongPassphrase::class.java) {
            BackupCrypto.open(envelope, CharArray(0))
        }
    }

    @Test
    fun `the iteration count travels with the file and is used to open it`() {
        val envelope = seal(payload, iterations = 150_000)

        assertEquals(150_000, envelope.iterations)
        assertEquals(payload, BackupCrypto.open(envelope, passphrase.toCharArray()))
    }

    @Test
    fun `the shipped work factor is the one recorded in a fresh container`() {
        val envelope = BackupCrypto.seal(payload, passphrase.toCharArray())

        assertEquals(BackupCrypto.DEFAULT_ITERATIONS, envelope.iterations)
    }

    @Test
    fun `editing the header breaks the file, because the header is authenticated`() {
        val envelope = seal(payload)

        // A plausible edit: raise the work factor by hand, still inside the
        // accepted range. The key would change, and so would the tag.
        assertThrows(BackupCryptoException.WrongPassphrase::class.java) {
            BackupCrypto.open(envelope.copy(iterations = 200_000), passphrase.toCharArray())
        }
    }

    @Test
    fun `a tampered payload does not open`() {
        val envelope = seal(payload)
        val flipped = envelope.payloadBase64.let { it.replaceRange(0, 1, if (it[0] == 'A') "B" else "A") }

        assertThrows(BackupCryptoException::class.java) {
            BackupCrypto.open(envelope.copy(payloadBase64 = flipped), passphrase.toCharArray())
        }
    }

    @Test
    fun `a truncated payload is a damaged file`() {
        val envelope = seal(payload)

        assertThrows(BackupCryptoException.Corrupted::class.java) {
            BackupCrypto.open(envelope.copy(payloadBase64 = ""), passphrase.toCharArray())
        }
    }

    @Test
    fun `garbage where base64 is expected is a damaged file`() {
        val envelope = seal(payload)

        assertThrows(BackupCryptoException.Corrupted::class.java) {
            BackupCrypto.open(envelope.copy(saltBase64 = "not base 64 at all!!"), passphrase.toCharArray())
        }
    }

    @Test
    fun `an iv of the wrong size is a damaged file`() {
        val envelope = seal(payload)

        assertThrows(BackupCryptoException.Corrupted::class.java) {
            BackupCrypto.open(envelope.copy(ivBase64 = "AAAA"), passphrase.toCharArray())
        }
    }

    @Test
    fun `an implausible work factor is refused instead of being derived`() {
        val envelope = seal(payload)

        // Below the floor: a downgraded header must not pass as protected.
        assertThrows(BackupCryptoException.Corrupted::class.java) {
            BackupCrypto.open(envelope.copy(iterations = 1_000), passphrase.toCharArray())
        }
        // Above the ceiling: this is the case that would otherwise hang.
        assertThrows(BackupCryptoException.Corrupted::class.java) {
            BackupCrypto.open(envelope.copy(iterations = Int.MAX_VALUE), passphrase.toCharArray())
        }
    }

    @Test
    fun `a newer container is refused with the version it declares`() {
        val envelope = seal(payload)

        val error = assertThrows(BackupCryptoException.UnsupportedContainer::class.java) {
            BackupCrypto.open(envelope.copy(container = 99), passphrase.toCharArray())
        }

        assertEquals(99, error.version)
    }

    @Test
    fun `two exports of the same data never produce the same bytes`() {
        val first = seal(payload)
        val second = seal(payload)

        assertNotEquals(first.saltBase64, second.saltBase64)
        assertNotEquals(first.ivBase64, second.ivBase64)
        assertNotEquals(first.payloadBase64, second.payloadBase64)
    }

    @Test
    fun `the container declares what it is, so a text editor shows a file and not a blob`() {
        val envelope = seal(payload)

        assertEquals(EncryptedBackup.FORMAT, envelope.format)
        assertEquals(EncryptedBackup.CONTAINER_VERSION, envelope.container)
        assertEquals(EncryptedBackup.KDF, envelope.kdf)
        assertEquals(EncryptedBackup.CIPHER, envelope.cipher)
    }

    private fun seal(json: String, iterations: Int = TEST_ITERATIONS): EncryptedBackup =
        BackupCrypto.seal(json, passphrase.toCharArray(), iterations)

    private companion object {
        /** The lowest accepted work factor: fast, and still a valid container. */
        const val TEST_ITERATIONS = 100_000
    }
}
