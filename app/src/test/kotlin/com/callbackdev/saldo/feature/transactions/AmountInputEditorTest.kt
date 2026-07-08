package com.callbackdev.saldo.feature.transactions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AmountInputEditorTest {

    private fun apply(
        current: String,
        key: KeypadKey,
        fractionDigits: Int = 2,
        allowNegative: Boolean = false,
    ): String = AmountInputEditor.apply(current, key, fractionDigits, allowNegative)

    @Test
    fun `digits append`() {
        assertEquals("1", apply("", KeypadKey.Digit(1)))
        assertEquals("12", apply("1", KeypadKey.Digit(2)))
    }

    @Test
    fun `leading zero is replaced by the next digit`() {
        assertEquals("5", apply("0", KeypadKey.Digit(5)))
    }

    @Test
    fun `zero before a separator is kept`() {
        assertEquals("0.", apply("0", KeypadKey.DecimalSeparator))
        assertEquals("0.5", apply("0.", KeypadKey.Digit(5)))
    }

    @Test
    fun `separator typed first gains a leading zero`() {
        assertEquals("0.", apply("", KeypadKey.DecimalSeparator))
    }

    @Test
    fun `second separator is ignored`() {
        assertEquals("1.2", apply("1.2", KeypadKey.DecimalSeparator))
    }

    @Test
    fun `separator is ignored for zero-decimal currencies`() {
        assertEquals("12", apply("12", KeypadKey.DecimalSeparator, fractionDigits = 0))
    }

    @Test
    fun `decimals beyond the currency scale are dropped`() {
        assertEquals("1.25", apply("1.25", KeypadKey.Digit(9)))
    }

    @Test
    fun `double zero appends two digits and collapses on empty input`() {
        assertEquals("100", apply("1", KeypadKey.DoubleZero))
        assertEquals("0", apply("", KeypadKey.DoubleZero))
    }

    @Test
    fun `backspace drops the last character and clear empties`() {
        assertEquals("1.", apply("1.5", KeypadKey.Backspace))
        assertEquals("", apply("1", KeypadKey.Backspace))
        assertEquals("", apply("", KeypadKey.Backspace))
        assertEquals("", apply("123.45", KeypadKey.Clear))
    }

    @Test
    fun `sign toggle only applies when negatives are allowed`() {
        assertEquals("12", apply("12", KeypadKey.ToggleSign))
        assertEquals("-12", apply("12", KeypadKey.ToggleSign, allowNegative = true))
        assertEquals("12", apply("-12", KeypadKey.ToggleSign, allowNegative = true))
    }

    @Test
    fun `integer part is capped so minor units always fit in a Long`() {
        val nineDigits = "123456789"
        assertEquals(nineDigits, apply(nineDigits, KeypadKey.Digit(9)))
        assertEquals("$nineDigits.99", apply("$nineDigits.9", KeypadKey.Digit(9)))
    }
}
