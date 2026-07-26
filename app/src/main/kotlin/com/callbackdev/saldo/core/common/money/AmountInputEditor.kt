package com.callbackdev.saldo.core.common.money

/** A key of the in-app amount keypad. */
sealed interface KeypadKey {
    data class Digit(val value: Int) : KeypadKey

    data object DecimalSeparator : KeypadKey

    data object Backspace : KeypadKey

    /** Long-press on backspace: wipes the amount. */
    data object Clear : KeypadKey

    data object ToggleSign : KeypadKey
}

/**
 * Pure editing logic behind the in-app keypad: applies a [KeypadKey] to the raw
 * amount text the caller holds. The caret is always at the end (the keypad only
 * appends or trims), so a key is a plain string edit followed by
 * [MoneyInput.sanitize], which owns every rule about separators, decimals and
 * the integer-digit cap.
 */
object AmountInputEditor {

    /**
     * [decimalSeparator] is the one the keypad shows, so the raw text carries
     * the character the user actually pressed; [MoneyInput] accepts both `.`
     * and `,` and normalizes at parse time.
     */
    fun apply(
        current: String,
        key: KeypadKey,
        fractionDigits: Int,
        allowNegative: Boolean = false,
        decimalSeparator: Char = '.',
    ): String {
        val edited = when (key) {
            is KeypadKey.Digit -> current + key.value
            KeypadKey.DecimalSeparator -> current + decimalSeparator
            KeypadKey.Backspace -> current.dropLast(1)
            KeypadKey.Clear -> ""
            KeypadKey.ToggleSign -> when {
                !allowNegative -> current
                current.startsWith("-") -> current.drop(1)
                else -> "-$current"
            }
        }
        return MoneyInput.sanitize(edited, fractionDigits, allowNegative)
    }
}
