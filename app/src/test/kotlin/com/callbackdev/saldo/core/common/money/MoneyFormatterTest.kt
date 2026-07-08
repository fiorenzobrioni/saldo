package com.callbackdev.saldo.core.common.money

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

class MoneyFormatterTest {

    private val eur = Currency.getInstance("EUR")
    private val jpy = Currency.getInstance("JPY")

    @Test
    fun `formats with the locale grouping and decimal separators`() {
        val italian = MoneyFormatter.format(BigDecimal("1234.56"), eur, Locale.ITALY)
        assertTrue(italian.contains("1.234,56"), italian)
        assertTrue(italian.contains("€"), italian)

        val us = MoneyFormatter.format(BigDecimal("1234.56"), eur, Locale.US)
        assertTrue(us.contains("1,234.56"), us)
    }

    @Test
    fun `uses the currency fraction digits`() {
        val yen = MoneyFormatter.format(BigDecimal("1250"), jpy, Locale.US)
        assertTrue(yen.contains("1,250"), yen)
        assertFalse(yen.contains("1,250."), yen)
    }

    @Test
    fun `signed format adds an explicit plus only for positive amounts`() {
        val positive = MoneyFormatter.formatSigned(BigDecimal("5.50"), eur, Locale.ITALY)
        assertTrue(positive.startsWith("+"), positive)

        val negative = MoneyFormatter.formatSigned(BigDecimal("-5.50"), eur, Locale.ITALY)
        assertFalse(negative.startsWith("+"), negative)
        assertTrue(negative.contains("5,50"), negative)

        val zero = MoneyFormatter.formatSigned(BigDecimal.ZERO, eur, Locale.ITALY)
        assertFalse(zero.startsWith("+"), zero)
    }
}
