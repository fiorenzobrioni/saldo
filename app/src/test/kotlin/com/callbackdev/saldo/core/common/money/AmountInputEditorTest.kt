package com.callbackdev.saldo.core.common.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AmountInputEditorTest {

    private fun apply(
        current: String,
        key: KeypadKey,
        fractionDigits: Int = 2,
        allowNegative: Boolean = false,
        separator: Char = ',',
    ): String = AmountInputEditor.apply(
        current = current,
        key = key,
        fractionDigits = fractionDigits,
        allowNegative = allowNegative,
        decimalSeparator = separator,
    )

    @Test
    fun `digits append one after another`() {
        var text = ""
        listOf(1, 2, 5).forEach { digit -> text = apply(text, KeypadKey.Digit(digit)) }
        assertEquals("125", text)
    }

    @Test
    fun `the separator key prints the locale separator, once`() {
        val withSeparator = apply("12", KeypadKey.DecimalSeparator)
        assertEquals("12,", withSeparator)
        assertEquals("12,", apply(withSeparator, KeypadKey.DecimalSeparator))
        assertEquals("12.", apply("12", KeypadKey.DecimalSeparator, separator = '.'))
    }

    @Test
    fun `a leading separator gains its zero`() {
        assertEquals("0,", apply("", KeypadKey.DecimalSeparator))
    }

    @Test
    fun `decimals stop at the currency scale`() {
        assertEquals("1,25", apply("1,25", KeypadKey.Digit(9)))
        assertEquals("1,2", apply("1,2", KeypadKey.Digit(5), fractionDigits = 1))
    }

    @Test
    fun `a zero-decimal currency never takes a separator`() {
        assertEquals("1250", apply("1250", KeypadKey.DecimalSeparator, fractionDigits = 0))
    }

    @Test
    fun `backspace trims one character and stops at empty`() {
        assertEquals("12,5", apply("12,50", KeypadKey.Backspace))
        assertEquals("12", apply("12,", KeypadKey.Backspace))
        assertEquals("", apply("", KeypadKey.Backspace))
    }

    @Test
    fun `clear wipes the amount`() {
        assertEquals("", apply("1234,56", KeypadKey.Clear))
    }

    @Test
    fun `the sign toggles only where negatives are allowed`() {
        assertEquals("-12", apply("12", KeypadKey.ToggleSign, allowNegative = true))
        assertEquals("12", apply("-12", KeypadKey.ToggleSign, allowNegative = true))
        assertEquals("12", apply("12", KeypadKey.ToggleSign, allowNegative = false))
    }

    @Test
    fun `a digit past the integer cap changes nothing`() {
        val twelve = "123456789012"
        assertEquals(twelve, apply(twelve, KeypadKey.Digit(3)))
    }

    @Test
    fun `leading zeros collapse as digits arrive`() {
        assertEquals("5", apply("0", KeypadKey.Digit(5)))
        assertEquals("0", apply("", KeypadKey.Digit(0)))
    }
}
