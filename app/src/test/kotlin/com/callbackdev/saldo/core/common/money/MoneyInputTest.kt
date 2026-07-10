package com.callbackdev.saldo.core.common.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyInputTest {

    @Test
    fun `sanitize keeps digits and a single separator`() {
        assertEquals("12,34", MoneyInput.sanitize("12,3x4", fractionDigits = 2))
        assertEquals("12.34", MoneyInput.sanitize("12.34.56", fractionDigits = 2))
        assertEquals("1234", MoneyInput.sanitize("1 2a3b4", fractionDigits = 2))
    }

    @Test
    fun `sanitize truncates decimals beyond the currency scale`() {
        assertEquals("1.99", MoneyInput.sanitize("1.999", fractionDigits = 2))
        assertEquals("0,5", MoneyInput.sanitize("0,5", fractionDigits = 2))
    }

    @Test
    fun `sanitize rejects separators for zero-decimal currencies`() {
        assertEquals("1250", MoneyInput.sanitize("1250,", fractionDigits = 0))
        assertEquals("1250", MoneyInput.sanitize("1.250", fractionDigits = 0).take(4))
    }

    @Test
    fun `sanitize allows a leading minus only when negative is allowed`() {
        assertEquals("-12", MoneyInput.sanitize("-12", fractionDigits = 2))
        assertEquals("12", MoneyInput.sanitize("1-2", fractionDigits = 2))
        assertEquals("12", MoneyInput.sanitize("-12", fractionDigits = 2, allowNegative = false))
    }

    @Test
    fun `sanitize normalizes leading zeros`() {
        assertEquals("5", MoneyInput.sanitize("05", fractionDigits = 2))
        assertEquals("0,5", MoneyInput.sanitize(",5", fractionDigits = 2))
        assertEquals("0", MoneyInput.sanitize("0", fractionDigits = 2))
        assertEquals("0,50", MoneyInput.sanitize("00,50", fractionDigits = 2))
    }

    @Test
    fun `sanitize caps the integer part so minor units fit in a Long`() {
        val twelve = "123456789012"
        assertEquals(twelve, MoneyInput.sanitize(twelve, fractionDigits = 2))
        assertEquals("$twelve.99", MoneyInput.sanitize("${twelve}345.99", fractionDigits = 2))
    }

    @Test
    fun `parse understands both decimal separators`() {
        assertEquals(BigDecimal("12.34"), MoneyInput.parse("12,34"))
        assertEquals(BigDecimal("12.34"), MoneyInput.parse("12.34"))
        assertEquals(BigDecimal("-5.5"), MoneyInput.parse("-5,5"))
        assertEquals(BigDecimal("1250"), MoneyInput.parse("1250"))
    }

    @Test
    fun `parse returns null for incomplete input`() {
        assertNull(MoneyInput.parse(""))
        assertNull(MoneyInput.parse("-"))
        assertNull(MoneyInput.parse(","))
    }
}
