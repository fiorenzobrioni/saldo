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
     * Strips [raw] down to a valid partial amount: digits, at most one decimal
     * separator (`.` or `,`, only when [fractionDigits] > 0) with at most
     * [fractionDigits] decimals, and an optional leading `-` when
     * [allowNegative] is true.
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
        return if (separatorIndex >= 0 && text.length > maxLength) {
            text.substring(0, maxLength)
        } else {
            text
        }
    }

    /**
     * Parses a sanitized amount into a [BigDecimal], or null when [text] does
     * not (yet) form a complete number (empty, `-`, `.` or `,`).
     */
    fun parse(text: String): BigDecimal? {
        val normalized = text.replace(',', '.')
        return normalized.toBigDecimalOrNull()
    }
}
