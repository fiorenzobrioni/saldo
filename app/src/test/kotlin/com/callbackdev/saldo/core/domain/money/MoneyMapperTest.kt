package com.callbackdev.saldo.core.domain.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency

class MoneyMapperTest {

    private val eur = Currency.getInstance("EUR")
    private val jpy = Currency.getInstance("JPY")

    @Test
    fun `EUR amount converts to cents`() {
        assertEquals(4500L, MoneyMapper.toMinorUnits(BigDecimal("45.00"), eur))
        assertEquals(1299L, MoneyMapper.toMinorUnits(BigDecimal("12.99"), eur))
        assertEquals(0L, MoneyMapper.toMinorUnits(BigDecimal.ZERO, eur))
    }

    @Test
    fun `negative amounts keep their sign`() {
        assertEquals(-4500L, MoneyMapper.toMinorUnits(BigDecimal("-45.00"), eur))
        assertEquals(BigDecimal("-45.00"), MoneyMapper.toAmount(-4500L, eur))
    }

    @Test
    fun `extra decimals are rounded half up`() {
        assertEquals(4501L, MoneyMapper.toMinorUnits(BigDecimal("45.005"), eur))
        assertEquals(4500L, MoneyMapper.toMinorUnits(BigDecimal("45.004"), eur))
        assertEquals(-4501L, MoneyMapper.toMinorUnits(BigDecimal("-45.005"), eur))
    }

    @Test
    fun `cents convert back with currency scale`() {
        val amount = MoneyMapper.toAmount(4500L, eur)
        assertEquals(BigDecimal("45.00"), amount)
        assertEquals(2, amount.scale())
    }

    @Test
    fun `zero-fraction currency uses no minor scaling`() {
        assertEquals(0, MoneyMapper.fractionDigits(jpy))
        assertEquals(1000L, MoneyMapper.toMinorUnits(BigDecimal("1000"), jpy))
        assertEquals(BigDecimal("1000"), MoneyMapper.toAmount(1000L, jpy))
    }

    @Test
    fun `pseudo currency with negative fraction digits is treated as zero`() {
        val xxx = Currency.getInstance("XXX")
        assertEquals(0, MoneyMapper.fractionDigits(xxx))
        assertEquals(5L, MoneyMapper.toMinorUnits(BigDecimal("5"), xxx))
    }

    @Test
    fun `round trip preserves the value for many amounts`() {
        listOf(0L, 1L, -1L, 99L, 100L, 250050L, -250050L, Long.MAX_VALUE, Long.MIN_VALUE)
            .forEach { minor ->
                val roundTripped = MoneyMapper.toMinorUnits(MoneyMapper.toAmount(minor, eur), eur)
                assertEquals(minor, roundTripped)
            }
    }
}
