package com.callbackdev.saldo.core.common.applock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PinHasherTest {

    private val hasher = PinHasher()

    @Test
    fun `a created pin verifies against itself`() {
        val stored = hasher.create("123456")

        assertTrue(hasher.matches("123456", stored))
    }

    @Test
    fun `a wrong pin is rejected`() {
        val stored = hasher.create("123456")

        assertFalse(hasher.matches("123457", stored))
        assertFalse(hasher.matches("654321", stored))
        assertFalse(hasher.matches("", stored))
    }

    @Test
    fun `the same pin hashed twice gets different salts and hashes`() {
        val first = hasher.create("123456")
        val second = hasher.create("123456")

        assertNotEquals(first.saltBase64, second.saltBase64)
        assertNotEquals(first.hashBase64, second.hashBase64)
        // Both still verify: the salt travels with each entry.
        assertTrue(hasher.matches("123456", first))
        assertTrue(hasher.matches("123456", second))
    }

    @Test
    fun `the stored entry records the current work factor`() {
        val stored = hasher.create("123456")

        assertEquals(150_000, stored.iterations)
    }

    @Test
    fun `verification honours the iterations stored with the entry, not the current default`() {
        // An entry created at a lower work factor (an older install) must keep
        // verifying after the default is raised.
        val stored = hasher.create("123456")
        val legacy = StoredPin(
            saltBase64 = stored.saltBase64,
            hashBase64 = stored.hashBase64,
            iterations = stored.iterations,
        )

        assertTrue(hasher.matches("123456", legacy))
        assertFalse(hasher.matches("123456", legacy.copy(iterations = legacy.iterations - 1)))
    }

    @Test
    fun `a corrupted entry is a mismatch, never an exception`() {
        val stored = hasher.create("123456")

        assertFalse(hasher.matches("123456", stored.copy(saltBase64 = "not base64!!!")))
        assertFalse(hasher.matches("123456", stored.copy(hashBase64 = "@@@")))
    }
}
