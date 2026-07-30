package com.callbackdev.saldo.core.domain.rates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class RateTableTest {

    private val monday = LocalDate.of(2026, 7, 20)
    private val wednesday = LocalDate.of(2026, 7, 22)
    private val friday = LocalDate.of(2026, 7, 24)

    private val table = RateTable.of(
        listOf(
            // Deliberately unsorted: the builder must sort per currency.
            ExchangeRate("USD", friday, BigDecimal("1.14")),
            ExchangeRate("USD", monday, BigDecimal("1.10")),
            ExchangeRate("USD", wednesday, BigDecimal("1.12")),
            ExchangeRate("GBP", wednesday, BigDecimal("0.85")),
        ),
    )

    @Test
    fun `resolves an exact day to its own rate`() {
        assertEquals(BigDecimal("1.12"), table.onOrBefore("USD", wednesday)?.perEuro)
        assertEquals(wednesday, table.onOrBefore("USD", wednesday)?.day)
    }

    @Test
    fun `resolves a gap day to the most recent earlier rate`() {
        val tuesday = monday.plusDays(1)
        val sample = table.onOrBefore("USD", tuesday)
        assertEquals(BigDecimal("1.10"), sample?.perEuro)
        assertEquals(monday, sample?.day)
    }

    @Test
    fun `resolves a day before the first sample to the oldest one`() {
        val sample = table.onOrBefore("USD", monday.minusDays(30))
        assertEquals(BigDecimal("1.10"), sample?.perEuro)
        assertEquals(monday, sample?.day)
    }

    @Test
    fun `latest returns the newest sample per currency`() {
        assertEquals(friday, table.latest("USD")?.day)
        assertEquals(wednesday, table.latest("GBP")?.day)
    }

    @Test
    fun `an unknown currency resolves to nothing`() {
        assertNull(table.onOrBefore("CHF", friday))
        assertNull(table.latest("CHF"))
        assertFalse(table.covers("CHF"))
        assertTrue(table.covers("USD"))
    }

    @Test
    fun `an empty table is empty`() {
        assertTrue(RateTable.EMPTY.isEmpty)
        assertTrue(RateTable.of(emptyList()).isEmpty)
        assertFalse(table.isEmpty)
    }
}
