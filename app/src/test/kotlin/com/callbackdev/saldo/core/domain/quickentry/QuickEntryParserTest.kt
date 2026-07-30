package com.callbackdev.saldo.core.domain.quickentry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class QuickEntryParserTest {

    // 2026-07-30 is a Thursday.
    private val today = LocalDate.of(2026, 7, 30)

    private val italian = QuickEntryVocabulary(
        groupingSeparator = '.',
        yesterdayWords = setOf("ieri"),
        todayWords = setOf("oggi"),
        tomorrowWords = setOf("domani"),
        weekdayWords = mapOf(
            "lun" to DayOfWeek.MONDAY,
            "lunedi" to DayOfWeek.MONDAY,
            "gio" to DayOfWeek.THURSDAY,
        ),
        dayBeforeMonth = true,
    )

    private val english = italian.copy(
        groupingSeparator = ',',
        yesterdayWords = setOf("yesterday"),
        todayWords = setOf("today"),
        tomorrowWords = setOf("tomorrow"),
        dayBeforeMonth = false,
    )

    private fun parse(
        text: String,
        fractionDigits: Int = 2,
        markers: Set<String> = setOf("€", "eur"),
        vocabulary: QuickEntryVocabulary = italian,
    ) = QuickEntryParser.parse(text, fractionDigits, markers, vocabulary, today)

    @Test
    fun `amount with comma decimals`() {
        val parsed = parse("12,50 pizza")
        assertEquals("12.50", parsed.amount)
        assertEquals("pizza", parsed.description)
    }

    @Test
    fun `amount with point decimals in a comma locale`() {
        assertEquals("12.50", parse("12.50 pizza").amount)
    }

    @Test
    fun `thousands grouped with the locale separator`() {
        assertEquals("1234", parse("1.234 affitto").amount)
        assertEquals("1234567", parse("1.234.567 casa").amount)
    }

    @Test
    fun `mixed separators read the last one as decimal`() {
        assertEquals("1234.56", parse("1.234,56 mobili").amount)
        assertEquals("1234.56", parse("1,234.56 rent", vocabulary = english).amount)
    }

    @Test
    fun `three digits after the locale decimal separator are ambiguous, not thousands`() {
        val parsed = parse("1.234 rent", vocabulary = english)
        assertNull(parsed.amount)
        assertEquals("1.234 rent", parsed.description)
    }

    @Test
    fun `currency symbol before or after, attached or not, is consumed`() {
        assertEquals("12.50", parse("€12,50 caffe").amount)
        assertEquals("12.50", parse("12,50€ caffe").amount)
        assertEquals("caffe", parse("€ 12,50 caffe").description)
        assertEquals("caffe", parse("12,50 eur caffe").description)
    }

    @Test
    fun `zero-decimal currency takes integers and refuses decimals`() {
        assertEquals("1200", parse("1200 ramen", fractionDigits = 0).amount)
        assertNull(parse("12,5 ramen", fractionDigits = 0).amount)
    }

    @Test
    fun `text without an amount and amount without text`() {
        val noAmount = parse("pizza con amici")
        assertNull(noAmount.amount)
        assertEquals("pizza con amici", noAmount.description)

        val noText = parse("12,50")
        assertEquals("12.50", noText.amount)
        assertEquals("", noText.description)
    }

    @Test
    fun `a trailing separator is someone mid-typing, not an ambiguity`() {
        assertEquals("12", parse("12,").amount)
    }

    @Test
    fun `a bare integer followed by another number with decimals is ambiguous`() {
        // "1 234,56" in locales that group thousands with a space.
        val parsed = parse("1 234,56 taxi")
        assertNull(parsed.amount)
    }

    @Test
    fun `only the first number is the amount, later ones stay in the description`() {
        val parsed = parse("12,50 pizza 2")
        assertEquals("12.50", parsed.amount)
        assertEquals("pizza 2", parsed.description)
    }

    @Test
    fun `relative day words come from the vocabulary, not from hardcoded lists`() {
        assertEquals(today.minusDays(1), parse("12 caffe ieri").date)
        assertEquals(today, parse("12 caffe oggi").date)
        assertEquals(today.plusDays(1), parse("12 caffe domani").date)
        assertEquals(today.minusDays(1), parse("12 coffee yesterday", vocabulary = english).date)
    }

    @Test
    fun `a weekday is the most recent one, today included`() {
        assertEquals(LocalDate.of(2026, 7, 27), parse("12 spesa lun").date)
        assertEquals(LocalDate.of(2026, 7, 27), parse("12 spesa Lunedi").date)
        // Today is Thursday: "gio" is today, not last week.
        assertEquals(today, parse("12 spesa gio").date)
    }

    @Test
    fun `short date follows the locale's day-month order`() {
        assertEquals(LocalDate.of(2026, 7, 3), parse("12 bolletta 3/7").date)
        assertEquals(LocalDate.of(2026, 3, 7), parse("12 bill 3/7", vocabulary = english).date)
    }

    @Test
    fun `an impossible short date is not a date and stays in the description`() {
        val parsed = parse("12 codice 13/13")
        assertNull(parsed.date)
        assertEquals("codice 13/13", parsed.description)
    }

    @Test
    fun `description keeps the user's own casing and order`() {
        val parsed = parse("12,50 Pizza da Mario ieri")
        assertEquals("Pizza da Mario", parsed.description)
    }

    @Test
    fun `search words are folded, deduplicated and filtered`() {
        val parsed = parse("12,50 Pizza da PIZZA 2 perché")
        assertEquals(listOf("pizza", "perche"), parsed.searchWords.map { it.folded })
        assertEquals("Pizza", parsed.searchWords.first().typed)
    }

    @Test
    fun `empty and blank input parse to nothing`() {
        val parsed = parse("   ")
        assertNull(parsed.amount)
        assertNull(parsed.date)
        assertEquals("", parsed.description)
        assertEquals(emptyList<SearchWord>(), parsed.searchWords)
    }
}
