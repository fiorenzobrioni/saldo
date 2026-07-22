package com.callbackdev.saldo.feature.transactions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ImpliedExchangeRateTest {

    @Test
    fun `rate is received divided by sent at four decimals`() {
        assertEquals(BigDecimal("1.0840"), impliedExchangeRate("50", "54.2"))
    }

    @Test
    fun `rate rounds half up on the fifth decimal`() {
        // 1 / 3 = 0.33333... -> 0.3333; 2 / 3 = 0.66666... -> 0.6667.
        assertEquals(BigDecimal("0.3333"), impliedExchangeRate("3", "1"))
        assertEquals(BigDecimal("0.6667"), impliedExchangeRate("3", "2"))
    }

    @Test
    fun `comma decimals are accepted like the amount fields do`() {
        assertEquals(BigDecimal("1.0840"), impliedExchangeRate("50", "54,2"))
    }

    @Test
    fun `signs are ignored so an adjustment-style minus cannot flip the rate`() {
        assertEquals(BigDecimal("1.0840"), impliedExchangeRate("-50", "54.2"))
    }

    @Test
    fun `incomplete or zero inputs produce no rate`() {
        assertNull(impliedExchangeRate("", "54.2"))
        assertNull(impliedExchangeRate("50", ""))
        assertNull(impliedExchangeRate("50", "0"))
        assertNull(impliedExchangeRate("0", "54.2"))
        assertNull(impliedExchangeRate("50", ","))
    }

    @Test
    fun `zero-decimal currencies still produce a meaningful rate`() {
        // 10000 JPY -> 58.50 EUR.
        assertEquals(BigDecimal("0.0059"), impliedExchangeRate("10000", "58.50"))
    }
}
