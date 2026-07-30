package com.callbackdev.saldo.core.domain.rates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class CurrencyConverterTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")
    private val gbp: Currency = Currency.getInstance("GBP")
    private val jpy: Currency = Currency.getInstance("JPY")

    private val friday = LocalDate.of(2026, 7, 24)
    private val monday = LocalDate.of(2026, 7, 27)

    private fun rates(vararg rows: ExchangeRate) = RateTable.of(rows.toList())

    @Test
    fun `converts a foreign flow into euro at the rate of its day, scaled to the target currency`() {
        val table = rates(ExchangeRate("USD", friday, BigDecimal("1.1377")))

        val estimate = CurrencyConverter.convertOn(BigDecimal("125.00"), usd, eur, friday, table)

        // 125.00 / 1.1377 = 109.870356... -> 109.87 at EUR scale (2 digits).
        assertEquals(BigDecimal("109.87"), estimate?.amount)
        assertEquals(friday, estimate?.rateDay)
    }

    @Test
    fun `converts out of the euro by multiplying, not dividing`() {
        val table = rates(ExchangeRate("USD", friday, BigDecimal("1.1377")))

        val estimate = CurrencyConverter.convertOn(BigDecimal("100.00"), eur, usd, friday, table)

        assertEquals(BigDecimal("113.77"), estimate?.amount)
    }

    @Test
    fun `zero-decimal currencies round to their own scale`() {
        val table = rates(ExchangeRate("JPY", friday, BigDecimal("186.27")))

        val estimate = CurrencyConverter.convertOn(BigDecimal("10.00"), eur, jpy, friday, table)

        // 10 x 186.27 = 1862.7 -> 1863 at JPY scale (0 digits, half-up).
        assertEquals(BigDecimal("1863"), estimate?.amount)
        assertEquals(0, estimate?.amount?.scale())
    }

    @Test
    fun `cross rates between two quoted currencies go through the euro`() {
        val table = rates(
            ExchangeRate("USD", friday, BigDecimal("1.1377")),
            ExchangeRate("GBP", friday, BigDecimal("0.85388")),
        )

        val estimate = CurrencyConverter.convertOn(BigDecimal("100.00"), usd, gbp, friday, table)

        // 100 / 1.1377 x 0.85388 = 75.05229... -> 75.05.
        assertEquals(BigDecimal("75.05"), estimate?.amount)
    }

    @Test
    fun `a weekend day resolves to the most recent published rate, whose day is declared`() {
        val saturday = friday.plusDays(1)
        val table = rates(
            ExchangeRate("USD", friday, BigDecimal("1.1377")),
            ExchangeRate("USD", monday, BigDecimal("1.1389")),
        )

        val estimate = CurrencyConverter.convertOn(BigDecimal("113.77"), usd, eur, saturday, table)

        assertEquals(BigDecimal("100.00"), estimate?.amount)
        assertEquals(friday, estimate?.rateDay)
    }

    @Test
    fun `a day before the whole cache resolves to the oldest known rate, declared as such`() {
        val table = rates(ExchangeRate("USD", friday, BigDecimal("1.1377")))
        val longBefore = friday.minusYears(1)

        val estimate = CurrencyConverter.convertOn(BigDecimal("113.77"), usd, eur, longBefore, table)

        assertEquals(BigDecimal("100.00"), estimate?.amount)
        // The sample's own (later) day is the honest label: ADR 40.
        assertEquals(friday, estimate?.rateDay)
    }

    @Test
    fun `a stock converts at the latest known rate`() {
        val table = rates(
            ExchangeRate("USD", friday, BigDecimal("1.1377")),
            ExchangeRate("USD", monday, BigDecimal("1.1389")),
        )

        val estimate = CurrencyConverter.convertAtLatest(BigDecimal("113.89"), usd, eur, table)

        assertEquals(BigDecimal("100.00"), estimate?.amount)
        assertEquals(monday, estimate?.rateDay)
    }

    @Test
    fun `a currency without rates converts to nothing, never to a made-up figure`() {
        val table = rates(ExchangeRate("USD", friday, BigDecimal("1.1377")))

        assertNull(CurrencyConverter.convertOn(BigDecimal.TEN, gbp, eur, friday, table))
        assertNull(CurrencyConverter.convertAtLatest(BigDecimal.TEN, gbp, eur, table))
    }

    @Test
    fun `an empty table converts nothing`() {
        assertNull(CurrencyConverter.convertOn(BigDecimal.TEN, usd, eur, friday, RateTable.EMPTY))
    }

    @Test
    fun `same-currency amounts pass through exactly, with no estimate marker`() {
        val estimate =
            CurrencyConverter.convertOn(BigDecimal("12.34"), eur, eur, friday, RateTable.EMPTY)

        assertEquals(BigDecimal("12.34"), estimate?.amount)
        assertNull(estimate?.rateDay)
    }
}
