package com.callbackdev.saldo.feature.transactions.importer

import java.math.BigDecimal
import java.text.Normalizer
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
     *
     * A single separator followed by exactly three digits (`"1,234"`) is
     * ambiguous on its own: this overload reads it as a decimal mark, the
     * historical behavior. The import resolves the convention once per file
     * with [inferDecimalMark] and calls the overload that takes it, so a
     * thousands separator without decimals is never read as a decimal.
     */
    fun parseAmount(raw: String): BigDecimal? = parseAmount(raw, decimalMark = null)

    /**
     * Parses [raw] with a known [decimalMark] (`.` or `,`): the other separator
     * is grouping and is dropped wherever it appears; a repeated decimal mark
     * makes the amount invalid. Null [decimalMark] falls back to the per-cell
     * heuristic of the one-argument overload.
     */
    fun parseAmount(raw: String, decimalMark: Char?): BigDecimal? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val negative = trimmed.startsWith('-') || (trimmed.startsWith('(') && trimmed.endsWith(')'))
        val digitsOnly = trimmed.filter { it.isDigit() || it == '.' || it == ',' }
        if (digitsOnly.none { it.isDigit() }) return null

        val normalized = if (decimalMark == null) {
            normalizeDecimalMarks(digitsOnly)
        } else {
            normalizeWithMark(digitsOnly, decimalMark) ?: return null
        }
        val magnitude = normalized.toBigDecimalOrNull() ?: return null
        return if (negative) magnitude.negate() else magnitude
    }

    /**
     * The decimal mark a whole column of raw amounts uses, or null when the
     * cells do not settle it. Evidence, strongest first: a cell carrying both
     * separators names the decimal (the last one); a cell with one separator
     * followed by anything but three digits names it too (`"12,5"`, `"3.1415"`);
     * a cell repeating one separator names the *grouping* (`"1.234.567"`). Cells
     * with one separator and exactly three trailing digits are the ambiguous
     * case and never decide. Conflicting evidence yields null: better an
     * undecided column resolved by the locale than a coin flip per cell.
     */
    fun inferDecimalMark(cells: Iterable<String>): Char? {
        val votes = mutableSetOf<Char>()
        for (raw in cells) {
            val digits = raw.filter { it.isDigit() || it == '.' || it == ',' }
            val dots = digits.count { it == '.' }
            val commas = digits.count { it == ',' }
            when {
                dots > 0 && commas > 0 ->
                    votes += if (digits.lastIndexOf('.') > digits.lastIndexOf(',')) '.' else ','
                dots == 1 && digits.substringAfterLast('.').length != GROUP_SIZE -> votes += '.'
                commas == 1 && digits.substringAfterLast(',').length != GROUP_SIZE -> votes += ','
                dots > 1 -> votes += ','
                commas > 1 -> votes += '.'
            }
        }
        return votes.singleOrNull()
    }

    /** With a known decimal mark: strip the grouping mark, refuse a repeated decimal mark. */
    private fun normalizeWithMark(digits: String, decimalMark: Char): String? {
        if (digits.count { it == decimalMark } > 1) return null
        val grouping = if (decimalMark == '.') ',' else '.'
        return digits.replace(grouping.toString(), "").replace(decimalMark, '.')
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

    private const val GROUP_SIZE = 3

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

    /**
     * Reads a flag column. The export writes the localized "yes" and leaves the
     * field empty for false; files from elsewhere spell the same thing in a
     * handful of ways, so a small set of tokens is accepted, accent- and
     * case-insensitively. Anything unrecognized (including a blank) reads as
     * false: an unexpected token must never turn a flag on by accident.
     */
    fun parseFlag(raw: String): Boolean = normalizeToken(raw) in TRUE_TOKENS

    private fun normalizeToken(raw: String): String =
        Normalizer.normalize(raw.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .filter { it.isLetterOrDigit() }

    private val DIACRITICS = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    /** Tokens read as "true" in a flag column, already normalized. */
    private val TRUE_TOKENS = setOf("si", "s", "yes", "y", "true", "vero", "x", "1")

    private const val ISO_CODE_LENGTH = 3
}
