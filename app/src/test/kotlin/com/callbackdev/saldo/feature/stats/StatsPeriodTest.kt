package com.callbackdev.saldo.feature.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsPeriodTest {

    private val today = LocalDate.of(2026, 7, 10)

    @Test
    fun `month covers its whole calendar month`() {
        val range = StatsPeriod.Month(YearMonth.of(2026, 2)).dateRange(today)
        assertEquals(LocalDate.of(2026, 2, 1), range.start)
        assertEquals(LocalDate.of(2026, 2, 28), range.endInclusive)
    }

    @Test
    fun `year covers january 1st through december 31st`() {
        val range = StatsPeriod.Year(2026).dateRange(today)
        assertEquals(LocalDate.of(2026, 1, 1), range.start)
        assertEquals(LocalDate.of(2026, 12, 31), range.endInclusive)
    }

    @Test
    fun `closed custom range keeps both bounds`() {
        val range = StatsPeriod.Custom(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 4, 10)).dateRange(today)
        assertEquals(LocalDate.of(2026, 3, 5), range.start)
        assertEquals(LocalDate.of(2026, 4, 10), range.endInclusive)
    }

    @Test
    fun `open-ended from resolves the end to today`() {
        val range = StatsPeriod.Custom(LocalDate.of(2026, 3, 5), null).dateRange(today)
        assertEquals(LocalDate.of(2026, 3, 5), range.start)
        assertEquals(today, range.endInclusive)
    }

    @Test
    fun `open-ended until floors the start at the earliest ledger date`() {
        val range = StatsPeriod.Custom(null, LocalDate.of(2026, 3, 5)).dateRange(today)
        assertEquals(EARLIEST_LEDGER_DATE, range.start)
        assertEquals(LocalDate.of(2026, 3, 5), range.endInclusive)
    }

    @Test
    fun `earliest ledger date survives conversion to epoch millis`() {
        // Guards the floor against LocalDate.MIN, whose epoch millis overflow a Long.
        assertEquals(
            EARLIEST_LEDGER_DATE,
            java.time.Instant.ofEpochMilli(
                EARLIEST_LEDGER_DATE.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            ).atZone(java.time.ZoneOffset.UTC).toLocalDate(),
        )
    }

    @Test
    fun `shifting months crosses year boundaries`() {
        val january = StatsPeriod.Month(YearMonth.of(2026, 1))
        assertEquals(StatsPeriod.Month(YearMonth.of(2025, 12)), january.shifted(-1))
        assertEquals(StatsPeriod.Month(YearMonth.of(2026, 2)), january.shifted(+1))
        assertEquals(StatsPeriod.Year(2025), StatsPeriod.Year(2026).shifted(-1))
    }

    @Test
    fun `custom ranges have no neighbours`() {
        val custom = StatsPeriod.Custom(today.minusDays(7), today)
        assertNull(custom.shifted(-1))
        assertNull(custom.shifted(+1))
    }

    @Test
    fun `isAtPresent stops forward stepping at the current month and year`() {
        assertTrue(StatsPeriod.Month(YearMonth.of(2026, 7)).isAtPresent(today))
        assertFalse(StatsPeriod.Month(YearMonth.of(2026, 6)).isAtPresent(today))
        assertTrue(StatsPeriod.Year(2026).isAtPresent(today))
        assertFalse(StatsPeriod.Year(2025).isAtPresent(today))
    }
}
