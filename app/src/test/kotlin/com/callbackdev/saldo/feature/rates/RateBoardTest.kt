package com.callbackdev.saldo.feature.rates

import com.callbackdev.saldo.core.domain.rates.ExchangeRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class RateBoardTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")

    private val monday = LocalDate.of(2026, 7, 20)
    private val tuesday = LocalDate.of(2026, 7, 21)

    private fun usdRates() = listOf(
        ExchangeRate("USD", monday, BigDecimal("1.10")),
        ExchangeRate("USD", tuesday, BigDecimal("1.12")),
    )

    @Test
    fun `quotes every currency against the euro base with its published history`() {
        val rows = RateBoard.build(usdRates(), base = eur, ledgerCurrencies = setOf("USD"))

        val usdRow = rows.single()
        assertEquals("USD", usdRow.currency.currencyCode)
        assertEquals(BigDecimal("1.1200"), usdRow.perBase)
        assertEquals(tuesday, usdRow.day)
        assertEquals(listOf(BigDecimal("1.1000"), BigDecimal("1.1200")), usdRow.history)
        assertTrue(usdRow.inUse)
    }

    @Test
    fun `computes the change against the previous publication`() {
        val row = RateBoard.build(usdRates(), base = eur, ledgerCurrencies = emptySet()).single()

        // (1.12 - 1.10) / 1.10 = 0.018182 (rounded half-up at scale 6).
        assertEquals(BigDecimal("0.018182"), row.changeFraction)
    }

    @Test
    fun `a single sample has no change to report`() {
        val row = RateBoard.build(
            listOf(ExchangeRate("USD", monday, BigDecimal("1.10"))),
            base = eur,
            ledgerCurrencies = emptySet(),
        ).single()

        assertNull(row.changeFraction)
        assertEquals(1, row.history.size)
    }

    @Test
    fun `against a non-euro base the euro becomes a row and rates cross through it`() {
        val rates = usdRates() + listOf(
            ExchangeRate("GBP", monday, BigDecimal("0.88")),
            ExchangeRate("GBP", tuesday, BigDecimal("0.84")),
        )

        val rows = RateBoard.build(rates, base = usd, ledgerCurrencies = setOf("USD", "EUR"))

        val byCode = rows.associateBy { it.currency.currencyCode }
        // 1 USD = 1 / 1.12 EUR = 0.8929.
        assertEquals(BigDecimal("0.8929"), byCode["EUR"]?.perBase)
        // 1 USD = 0.84 / 1.12 GBP = 0.7500.
        assertEquals(BigDecimal("0.7500"), byCode["GBP"]?.perBase)
        // In-use first (EUR), then the rest alphabetically.
        assertEquals(listOf("EUR", "GBP"), rows.map { it.currency.currencyCode })
    }

    @Test
    fun `a base outside the basket produces no board rather than a wrong one`() {
        val rows = RateBoard.build(
            usdRates(),
            base = Currency.getInstance("GBP"),
            ledgerCurrencies = emptySet(),
        )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `an empty cache produces no board`() {
        assertTrue(RateBoard.build(emptyList(), base = eur, ledgerCurrencies = emptySet()).isEmpty())
    }

    @Test
    fun `the history is capped to the newest samples`() {
        val rates = (0 until 10).map { offset ->
            ExchangeRate("USD", monday.plusDays(offset.toLong()), BigDecimal("1.10"))
        }

        val row = RateBoard.build(rates, base = eur, ledgerCurrencies = emptySet(), maxSamples = 7)
            .single()

        assertEquals(7, row.history.size)
        assertEquals(monday.plusDays(9), row.day)
    }
}
