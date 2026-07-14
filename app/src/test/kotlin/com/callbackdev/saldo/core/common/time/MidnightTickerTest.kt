package com.callbackdev.saldo.core.common.time

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MidnightTickerTest {

    @Test
    fun `emits the current local date immediately`() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-07-14T10:00:00Z"), ZoneId.of("Europe/Rome"))

        assertEquals(LocalDate.of(2026, 7, 14), midnightTicker(clock).first())
    }

    @Test
    fun `the local date respects the clock zone across UTC midnight`() = runTest {
        // 23:30 UTC is already the 15th in Rome (UTC+2 in July).
        val clock = Clock.fixed(Instant.parse("2026-07-14T23:30:00Z"), ZoneId.of("Europe/Rome"))

        assertEquals(LocalDate.of(2026, 7, 15), midnightTicker(clock).first())
    }
}
