package com.callbackdev.saldo.core.domain.quickentry

import com.callbackdev.saldo.core.domain.search.SearchText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The locale-derived words and symbols the parser recognizes. Built outside the
 * parser (from platform locale data, never from word lists hardcoded for one
 * language, ADR 42) and injected, so the parser stays a pure JVM-testable
 * function and works in any locale the device speaks.
 *
 * All words are expected already normalized with [SearchText.normalize].
 */
data class QuickEntryVocabulary(
    /**
     * The locale's thousands separator: the one contested reading ("1.234")
     * is settled by whether the separator is the one this locale groups with.
     */
    val groupingSeparator: Char,
    /** Words meaning "yesterday" / "today" / "tomorrow" in the user's locale. */
    val yesterdayWords: Set<String>,
    val todayWords: Set<String>,
    val tomorrowWords: Set<String>,
    /** Short and full weekday names, mapped to their day. */
    val weekdayWords: Map<String, DayOfWeek>,
    /** Whether the locale writes the day before the month in short dates (3/7). */
    val dayBeforeMonth: Boolean,
)

/**
 * A description word in the two forms the suggestion needs: as typed (for the
 * byte-wise SQL prefilter, where "perché" only matches its own bytes) and
 * folded by [SearchText.normalize] (the form every comparison runs on).
 */
data class SearchWord(val typed: String, val folded: String)

/**
 * What the parser understood of one line of text. Every field is a proposal
 * for the editor to preload, never something saved on its own (ADR 42).
 */
data class QuickEntryParse(
    /**
     * Canonical amount ("1234.56"), ready for the shared money sanitization,
     * or null when the text carries no amount or an ambiguous one: the caller
     * leaves the keypad open on the amount instead of guessing.
     */
    val amount: String?,
    /** A date expressed in one of the simple forms, or null for "today". */
    val date: LocalDate?,
    /** What remains once amount and date are consumed, as the user typed it. */
    val description: String,
    /** Words of the description usable for the category suggestion. */
    val searchWords: List<SearchWord>,
)

/**
 * Splits "12,50 pizza ieri" into amount, date and description. Pure function:
 * locale and currency knowledge come in through [QuickEntryVocabulary] and the
 * parameters, nothing is read from the environment.
 *
 * Design rules (ADR 42): the first token that reads as an amount is the
 * amount; one date token at most; everything else is description, order and
 * casing untouched. When a number is ambiguous ("1.234" where the point is
 * the locale's decimal separator but the currency has two decimals) there is
 * NO amount: an honest empty field beats a wrong figure.
 */
object QuickEntryParser {

    /** Words shorter than this never drive a category suggestion. */
    const val MIN_WORD_LENGTH = 3

    private val WHITESPACE = Regex("\\s+")
    private val NUMBER_SHAPE = Regex("[0-9.,]+")
    private val SHORT_DATE = Regex("([0-9]{1,2})/([0-9]{1,2})")
    private const val GROUP_SIZE = 3

    fun parse(
        text: String,
        fractionDigits: Int,
        currencyMarkers: Set<String>,
        vocabulary: QuickEntryVocabulary,
        today: LocalDate,
    ): QuickEntryParse {
        val tokens = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val consumed = BooleanArray(tokens.size)

        val amount = consumeAmount(tokens, consumed, fractionDigits, currencyMarkers, vocabulary)
        val date = consumeDate(tokens, consumed, vocabulary, today)

        val rest = tokens.filterIndexed { index, _ -> !consumed[index] }
        val words = rest.asSequence()
            .map { it.trim(*PUNCTUATION) }
            .map { SearchWord(typed = it, folded = SearchText.normalize(it)) }
            .filter { word ->
                // A word must carry at least one letter: "2" or "13/13" are
                // not habits worth querying the ledger for.
                word.folded.length >= MIN_WORD_LENGTH && word.folded.any { it.isLetter() }
            }
            .distinctBy { it.folded }
            .toList()
        return QuickEntryParse(
            amount = amount,
            date = date,
            description = rest.joinToString(" "),
            searchWords = words,
        )
    }

    /**
     * Finds and consumes the first token that reads as an amount, together
     * with a currency marker standing alone right before or after it
     * ("€ 12,50", "12,50 EUR"). The first numeric token IS the amount: when
     * it is ambiguous there is no amount at all, a later number is never
     * promoted in its place.
     */
    private fun consumeAmount(
        tokens: List<String>,
        consumed: BooleanArray,
        fractionDigits: Int,
        currencyMarkers: Set<String>,
        vocabulary: QuickEntryVocabulary,
    ): String? {
        val index = tokens.indexOfFirst { token ->
            val bare = stripMarkers(token, currencyMarkers)
            bare.any { it.isDigit() } && NUMBER_SHAPE.matches(bare)
        }
        if (index < 0) return null
        val bare = stripMarkers(tokens[index], currencyMarkers)
        val amount = readNumber(bare, fractionDigits, vocabulary) ?: return null
        // "1 234,56" in locales that group thousands with a space arrives as
        // two numeric tokens: reading "1" as the amount would be a wrong
        // figure, so a bare integer followed by another number carrying a
        // separator is ambiguous, and ambiguous means no amount.
        val next = tokens.getOrNull(index + 1)?.let { stripMarkers(it, currencyMarkers) }
        val bareIsInteger = !bare.contains('.') && !bare.contains(',')
        if (bareIsInteger && next != null && NUMBER_SHAPE.matches(next) &&
            (next.contains('.') || next.contains(','))
        ) {
            return null
        }
        consumed[index] = true
        if (index > 0 && isMarker(tokens[index - 1], currencyMarkers)) consumed[index - 1] = true
        if (index < tokens.lastIndex && isMarker(tokens[index + 1], currencyMarkers)) {
            consumed[index + 1] = true
        }
        return amount
    }

    private fun isMarker(token: String, currencyMarkers: Set<String>): Boolean =
        SearchText.normalize(token) in currencyMarkers

    private fun stripMarkers(token: String, currencyMarkers: Set<String>): String {
        var value = token
        var changed = true
        while (changed) {
            changed = false
            for (marker in currencyMarkers) {
                val normalized = SearchText.normalize(value)
                when {
                    normalized.startsWith(marker) -> {
                        value = value.drop(marker.length)
                        changed = true
                    }
                    normalized.endsWith(marker) -> {
                        value = value.dropLast(marker.length)
                        changed = true
                    }
                }
            }
        }
        return value
    }

    /**
     * Interprets one numeric token, or null when it is ambiguous. Both `.` and
     * `,` are accepted as decimal separator (the app's money fields do the
     * same, ADR 31); the locale decides only the contested cases, and a case
     * the rules cannot settle honestly is no amount at all.
     */
    private fun readNumber(
        token: String,
        fractionDigits: Int,
        vocabulary: QuickEntryVocabulary,
    ): String? {
        // A trailing separator is someone mid-typing ("12,"): read the digits
        // typed so far instead of blanking the amount for a keystroke.
        val value = if (token.last() == '.' || token.last() == ',') token.dropLast(1) else token
        if (value.isEmpty() || value.first() == '.' || value.first() == ',') return null
        if (value.last() == '.' || value.last() == ',') return null
        val dots = value.count { it == '.' }
        val commas = value.count { it == ',' }
        return when {
            dots == 0 && commas == 0 -> value
            dots > 0 && commas > 0 -> mixedSeparators(value)
            else -> singleSeparator(value, fractionDigits, vocabulary)
        }
    }

    /** Both separators present: the last one is the decimal, the other groups. */
    private fun mixedSeparators(token: String): String? {
        val lastDot = token.lastIndexOf('.')
        val lastComma = token.lastIndexOf(',')
        val decimal = if (lastDot > lastComma) '.' else ','
        val grouping = if (decimal == '.') ',' else '.'
        if (token.count { it == decimal } > 1) return null
        val parts = token.split(decimal)
        val integer = parts[0]
        if (!validGrouping(integer, grouping)) return null
        return integer.replace(grouping.toString(), "") + "." + parts[1]
    }

    private fun singleSeparator(
        token: String,
        fractionDigits: Int,
        vocabulary: QuickEntryVocabulary,
    ): String? {
        val separator = if (token.contains('.')) '.' else ','
        val occurrences = token.count { it == separator }
        if (occurrences > 1) {
            return if (validGrouping(token, separator)) token.replace(separator.toString(), "") else null
        }
        val parts = token.split(separator)
        val decimals = parts[1]
        val looksGrouped = decimals.length == GROUP_SIZE
        val fitsDecimals = fractionDigits > 0 && decimals.length <= fractionDigits
        return when {
            // "12,50" with two decimals, or "1.2" with one: a plain decimal,
            // whatever the locale calls that separator.
            fitsDecimals && !looksGrouped -> parts[0] + "." + decimals
            // Three digits after the separator can be thousands or (rarely)
            // too many decimals: the locale settles it. "1.234" is thousands
            // in Italian and ambiguous in English, where the point means
            // decimals the currency cannot hold.
            looksGrouped && separator == vocabulary.groupingSeparator -> parts[0] + decimals
            looksGrouped && fitsDecimals -> parts[0] + "." + decimals
            else -> null
        }
    }

    private fun validGrouping(integerWithSeparators: String, separator: Char): Boolean {
        val groups = integerWithSeparators.split(separator)
        if (groups.any { it.isEmpty() }) return false
        if (groups[0].length > GROUP_SIZE) return false
        return groups.drop(1).all { it.length == GROUP_SIZE }
    }

    /** Consumes at most one date token: "ieri"-like words, a weekday, or d/M. */
    private fun consumeDate(
        tokens: List<String>,
        consumed: BooleanArray,
        vocabulary: QuickEntryVocabulary,
        today: LocalDate,
    ): LocalDate? {
        val hit = tokens.withIndex().firstNotNullOfOrNull { (index, token) ->
            if (consumed[index]) null else readDate(token, vocabulary, today)?.let { index to it }
        } ?: return null
        consumed[hit.first] = true
        return hit.second
    }

    private fun readDate(token: String, vocabulary: QuickEntryVocabulary, today: LocalDate): LocalDate? {
        val word = SearchText.normalize(token.trim(*PUNCTUATION))
        val weekday = vocabulary.weekdayWords[word]
        return when {
            word in vocabulary.yesterdayWords -> today.minusDays(1)
            word in vocabulary.todayWords -> today
            word in vocabulary.tomorrowWords -> today.plusDays(1)
            // The most recent such day, today included: an expense written
            // down with a weekday is one that already happened.
            weekday != null -> today.with(TemporalAdjusters.previousOrSame(weekday))
            else -> shortDate(token, vocabulary, today)
        }
    }

    /** "3/7" in the locale's day/month order, current year; never a guess. */
    private fun shortDate(token: String, vocabulary: QuickEntryVocabulary, today: LocalDate): LocalDate? {
        val match = SHORT_DATE.matchEntire(token) ?: return null
        val first = match.groupValues[1].toInt()
        val second = match.groupValues[2].toInt()
        val day = if (vocabulary.dayBeforeMonth) first else second
        val month = if (vocabulary.dayBeforeMonth) second else first
        return runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
    }

    private val PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', '(', ')', '"', '\'')
}
