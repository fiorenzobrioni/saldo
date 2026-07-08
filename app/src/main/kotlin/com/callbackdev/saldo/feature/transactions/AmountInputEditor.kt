package com.callbackdev.saldo.feature.transactions

import com.callbackdev.saldo.core.common.money.MoneyInput

/** A key of the in-app amount keypad. */
sealed interface KeypadKey {
    data class Digit(val value: Int) : KeypadKey
    data object DoubleZero : KeypadKey
    data object DecimalSeparator : KeypadKey
    data object Backspace : KeypadKey
    data object Clear : KeypadKey
    data object ToggleSign : KeypadKey
}

/**
 * Pure editing logic behind the in-app keypad: applies a [KeypadKey] to the
 * current raw amount text. Sanitization (single separator, decimals capped to
 * the currency's fraction digits) is delegated to [MoneyInput]; on top of that
 * leading zeros are normalized and the integer part is capped so the amount
 * always fits comfortably in `Long` minor units.
 */
object AmountInputEditor {

    private const val MAX_INTEGER_DIGITS = 9

    fun apply(
        current: String,
        key: KeypadKey,
        fractionDigits: Int,
        allowNegative: Boolean = false,
    ): String {
        val edited = when (key) {
            is KeypadKey.Digit -> current + key.value
            KeypadKey.DoubleZero -> current + "00"
            KeypadKey.DecimalSeparator -> current + '.'
            KeypadKey.Backspace -> current.dropLast(1)
            KeypadKey.Clear -> ""
            KeypadKey.ToggleSign -> when {
                !allowNegative -> current
                current.startsWith("-") -> current.drop(1)
                else -> "-$current"
            }
        }
        val sanitized = normalize(MoneyInput.sanitize(edited, fractionDigits, allowNegative))
        return if (integerDigits(sanitized) > MAX_INTEGER_DIGITS) current else sanitized
    }

    /** Normalizes leading zeros: `05` becomes `5`, a leading separator gains a `0`. */
    private fun normalize(text: String): String {
        val negative = text.startsWith("-")
        var body = if (negative) text.drop(1) else text
        while (body.length > 1 && body[0] == '0' && body[1].isDigit()) {
            body = body.drop(1)
        }
        if (body.firstOrNull() == '.' || body.firstOrNull() == ',') {
            body = "0$body"
        }
        return if (negative) "-$body" else body
    }

    private fun integerDigits(text: String): Int =
        text.substringBefore('.').substringBefore(',').count { it.isDigit() }
}
