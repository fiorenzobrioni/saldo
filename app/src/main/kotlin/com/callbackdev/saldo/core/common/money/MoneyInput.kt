package com.callbackdev.saldo.core.common.money

import java.math.BigDecimal

/**
 * Handling of free-form monetary text typed by the user.
 *
 * The input fields accept both `.` and `,` as decimal separator (users switch
 * keyboards and locales); parsing normalizes to [BigDecimal] without ever going
 * through Float/Double (domain rule).
 */
object MoneyInput {

    /**
     * The integer part is capped so the amount always fits comfortably in `Long`
     * minor units: 12 digits leave ample headroom over the ~18 a signed Long can
     * hold even at two decimals, well beyond any realistic personal figure.
     */
    private const val MAX_INTEGER_DIGITS = 12

    /**
     * Strips [raw] down to a valid partial amount: digits, at most one decimal
     * separator (`.` or `,`, only when [fractionDigits] > 0) with at most
     * [fractionDigits] decimals, and an optional leading `-` when
     * [allowNegative] is true. Leading zeros are normalized (`05` -> `5`, a
     * leading separator gains a `0`) and the integer part is capped so the value
     * cannot overflow `Long` minor units at save time.
     */
    fun sanitize(raw: String, fractionDigits: Int, allowNegative: Boolean = true): String {
        val builder = StringBuilder()
        var hasSeparator = false
        for (char in raw) {
            when {
                char == '-' && allowNegative && builder.isEmpty() -> builder.append(char)
                char.isDigit() -> builder.append(char)
                (char == '.' || char == ',') && !hasSeparator && fractionDigits > 0 -> {
                    hasSeparator = true
                    builder.append(char)
                }
            }
        }
        val text = builder.toString()
        val separatorIndex = text.indexOfFirst { it == '.' || it == ',' }
        val maxLength = separatorIndex + 1 + fractionDigits
        val capped = if (separatorIndex >= 0 && text.length > maxLength) {
            text.substring(0, maxLength)
        } else {
            text
        }
        return finalize(capped)
    }

    /** Normalizes leading zeros and caps the integer digits of a sanitized amount. */
    private fun finalize(text: String): String {
        val negative = text.startsWith("-")
        var body = if (negative) text.drop(1) else text
        while (body.length > 1 && body[0] == '0' && body[1].isDigit()) {
            body = body.drop(1)
        }
        if (body.firstOrNull() == '.' || body.firstOrNull() == ',') {
            body = "0$body"
        }
        val separatorIndex = body.indexOfFirst { it == '.' || it == ',' }
        val integerPart = if (separatorIndex >= 0) body.substring(0, separatorIndex) else body
        if (integerPart.length > MAX_INTEGER_DIGITS) {
            val decimals = if (separatorIndex >= 0) body.substring(separatorIndex) else ""
            body = integerPart.substring(0, MAX_INTEGER_DIGITS) + decimals
        }
        return if (negative) "-$body" else body
    }

    /**
     * Groups the integer part of a partial amount with [groupingSeparator], for
     * display only: `1234,5` reads `1.234,5` while the caller keeps the raw
     * text. Whatever the user typed after the decimal separator is left
     * untouched, trailing separator included, so the string never changes shape
     * under the caret while typing.
     */
    fun grouped(text: String, groupingSeparator: Char): String {
        val negative = text.startsWith("-")
        val body = if (negative) text.drop(1) else text
        val separatorIndex = body.indexOfFirst { it == '.' || it == ',' }
        val integerPart = if (separatorIndex >= 0) body.substring(0, separatorIndex) else body
        val decimalPart = if (separatorIndex >= 0) body.substring(separatorIndex) else ""
        val builder = StringBuilder()
        integerPart.forEachIndexed { index, char ->
            if (index > 0 && (integerPart.length - index) % GROUP_SIZE == 0) {
                builder.append(groupingSeparator)
            }
            builder.append(char)
        }
        return buildString {
            if (negative) append('-')
            append(builder)
            append(decimalPart)
        }
    }

    private const val GROUP_SIZE = 3

    /**
     * Parses a sanitized amount into a [BigDecimal], or null when [text] does
     * not (yet) form a complete number (empty, `-`, `.` or `,`).
     */
    fun parse(text: String): BigDecimal? {
        val normalized = text.replace(',', '.')
        return normalized.toBigDecimalOrNull()
    }
}
