package com.callbackdev.saldo.feature.transactions.importer

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/**
 * Field-level parsers shared by the analyzer. Each is tolerant on input and
 * returns null on anything it cannot make sense of, so the caller decides
 * whether a null is a hard error (amount, date) or a soft fallback (currency).
 */
object CsvFieldParsers {

    /**
     * Parses a monetary amount without assuming a decimal convention. Both the
     * dot and the comma can be either the decimal mark or a thousands grouping;
     * the last one that appears is taken as the decimal mark and the other is
     * dropped as grouping. Currency symbols, spaces and a `+` sign are ignored;
     * a leading `-` or parentheses denote a negative amount.
     *
     * `"1.234,56"`, `"1,234.56"`, `"1234,56"`, `"1234.56"` and `"€ 1 234,56"`
     * all parse to `1234.56`; `"(50)"` parses to `-50`.
     */
    fun parseAmount(raw: String): BigDecimal? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val negative = trimmed.startsWith('-') || (trimmed.startsWith('(') && trimmed.endsWith(')'))
        val digitsOnly = trimmed.filter { it.isDigit() || it == '.' || it == ',' }
        if (digitsOnly.none { it.isDigit() }) return null

        val normalized = normalizeDecimalMarks(digitsOnly)
        val magnitude = normalized.toBigDecimalOrNull() ?: return null
        return if (negative) magnitude.negate() else magnitude
    }

    /** Collapses mixed dot/comma grouping into a plain `#.#` decimal string. */
    private fun normalizeDecimalMarks(digits: String): String {
        val lastDot = digits.lastIndexOf('.')
        val lastComma = digits.lastIndexOf(',')
        val decimalMark = when {
            lastDot < 0 && lastComma < 0 -> return digits
            lastDot > lastComma -> '.'
            else -> ','
        }
        // A real decimal mark appears at most once; a repeated mark is grouping
        // (e.g. "1.234.567" is 1234567, not a malformed decimal).
        if (digits.count { it == decimalMark } > 1) return digits.filter { it.isDigit() }
        val grouping = if (decimalMark == '.') ',' else '.'
        return digits.replace(grouping.toString(), "").replace(decimalMark, '.')
    }

    /** Date formats accepted on import, tried in order; ISO first (the export format). */
    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd",
        "dd/MM/yyyy",
        "d/M/yyyy",
        "dd-MM-yyyy",
        "MM/dd/yyyy",
        "dd.MM.yyyy",
        "yyyy/MM/dd",
    )

    private val DATE_FORMATTERS: List<DateTimeFormatter> = DATE_PATTERNS.map {
        DateTimeFormatter.ofPattern(it, Locale.ROOT)
    }

    /**
     * Parses a date, tolerating a timestamp suffix ("2026-07-08 08:00" keeps the
     * date part) and the common European and ISO layouts. Ambiguous numeric
     * dates are read day-first except for the explicit `MM/dd/yyyy` fallback.
     */
    fun parseDate(raw: String): LocalDate? {
        val candidate = raw.trim().substringBefore(' ').substringBefore('T')
        if (candidate.isEmpty()) return null
        return DATE_FORMATTERS.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDate.parse(candidate, formatter) }.getOrNull()
        }
    }

    /**
     * Resolves an ISO 4217 currency code (case-insensitive). Returns null for an
     * unknown code, so the caller can fall back to the account's own currency.
     */
    fun parseCurrency(raw: String): Currency? {
        val code = raw.trim().uppercase(Locale.ROOT)
        if (code.length != ISO_CODE_LENGTH) return null
        return runCatching { Currency.getInstance(code) }.getOrNull()
    }

    private const val ISO_CODE_LENGTH = 3
}
