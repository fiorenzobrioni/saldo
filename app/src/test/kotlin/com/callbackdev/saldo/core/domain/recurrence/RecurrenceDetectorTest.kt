package com.callbackdev.saldo.core.domain.recurrence

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class RecurrenceDetectorTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val today = LocalDate.of(2026, 7, 31)

    private fun group(
        vararg occurrences: Pair<LocalDate, Long>,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: Long? = 7L,
        displayName: String? = "Netflix",
    ) = RecurrenceCandidateGroup(
        stableKey = "test-key",
        type = type,
        accountId = 1L,
        categoryId = categoryId,
        currency = eur,
        displayName = displayName,
        occurrences = occurrences.map { (date, amount) ->
            CandidateOccurrence(date = date, amountMinor = amount, description = displayName)
        },
    )

    private fun date(month: Int, day: Int, year: Int = 2026): LocalDate = LocalDate.of(year, month, day)

    @Test
    fun `regular monthly series is detected with the next occurrence proposed`() {
        val suggestion = RecurrenceDetector.detect(
            group(date(4, 15) to -1299L, date(5, 15) to -1299L, date(6, 15) to -1299L, date(7, 15) to -1299L),
            today,
        )
        assertNotNull(suggestion)
        assertEquals(RecurrenceFrequency.MONTHLY, suggestion!!.frequency)
        assertEquals(1299L, suggestion.amountMinor)
        assertFalse(suggestion.isVariableAmount)
        assertEquals(date(8, 15), suggestion.nextOccurrence)
        assertEquals(date(7, 15), suggestion.lastOccurrence)
        assertEquals(4, suggestion.occurrenceCount)
    }

    @Test
    fun `monthly series with staggered days within tolerance is detected`() {
        val suggestion = RecurrenceDetector.detect(
            group(date(5, 3) to -4500L, date(6, 5) to -4500L, date(7, 2) to -4500L),
            today,
        )
        assertNotNull(suggestion)
        assertEquals(RecurrenceFrequency.MONTHLY, suggestion!!.frequency)
    }

    @Test
    fun `series on the 31st clamps through short months with zero drift`() {
        val suggestion = RecurrenceDetector.detect(
            group(date(1, 31) to -900L, date(2, 28) to -900L, date(3, 31) to -900L, date(4, 30) to -900L),
            LocalDate.of(2026, 5, 2),
        )
        assertNotNull(suggestion)
        assertEquals(RecurrenceFrequency.MONTHLY, suggestion!!.frequency)
        assertEquals(31, suggestion.dayOfReference)
        // The next occurrence lands back on the reference day, not on the clamped 30th.
        assertEquals(date(5, 31), suggestion.nextOccurrence)
    }

    @Test
    fun `weekly series is detected`() {
        val suggestion = RecurrenceDetector.detect(
            group(date(7, 6) to -1500L, date(7, 13) to -1500L, date(7, 20) to -1500L, date(7, 27) to -1500L),
            today,
        )
        assertNotNull(suggestion)
        assertEquals(RecurrenceFrequency.WEEKLY, suggestion!!.frequency)
        assertEquals(date(8, 3), suggestion.nextOccurrence)
    }

    @Test
    fun `annual series is detected`() {
        val suggestion = RecurrenceDetector.detect(
            group(date(7, 10, 2024) to -12000L, date(7, 12, 2025) to -12000L, date(7, 11, 2026) to -12000L),
            today,
        )
        assertNotNull(suggestion)
        assertEquals(RecurrenceFrequency.ANNUAL, suggestion!!.frequency)
        assertEquals(2027, suggestion.nextOccurrence.year)
    }

    @Test
    fun `variable amounts within tolerance produce a variable suggestion with the median`() {
        val suggestion = RecurrenceDetector.detect(
            group(date(5, 10) to -4200L, date(6, 10) to -4500L, date(7, 10) to -4700L, displayName = "Enel"),
            today,
        )
        assertNotNull(suggestion)
        assertTrue(suggestion!!.isVariableAmount)
        assertEquals(4500L, suggestion.amountMinor)
    }

    @Test
    fun `amounts diverging beyond tolerance produce no suggestion`() {
        assertNull(
            RecurrenceDetector.detect(
                group(date(5, 10) to -1000L, date(6, 10) to -4500L, date(7, 10) to -9000L),
                today,
            ),
        )
    }

    @Test
    fun `irregular frequent expenses are never proposed`() {
        assertNull(
            RecurrenceDetector.detect(
                group(date(7, 1) to -2000L, date(7, 4) to -2000L, date(7, 19) to -2000L, date(7, 26) to -2000L),
                today,
            ),
        )
    }

    @Test
    fun `fewer than three distinct days produce no suggestion`() {
        assertNull(
            RecurrenceDetector.detect(group(date(6, 15) to -1299L, date(7, 15) to -1299L), today),
        )
        // Two same-day charges are one occurrence: still under the minimum.
        assertNull(
            RecurrenceDetector.detect(
                group(date(6, 15) to -1299L, date(7, 15) to -1299L, date(7, 15) to -1299L),
                today,
            ),
        )
    }

    @Test
    fun `a stopped series is not proposed`() {
        // Last charge in March, today end of July: over one period overdue.
        assertNull(
            RecurrenceDetector.detect(
                group(date(1, 15) to -1299L, date(2, 15) to -1299L, date(3, 15) to -1299L),
                today,
            ),
        )
    }

    @Test
    fun `a series whose expected charge is a few days overdue is still alive`() {
        // Expected on the 28th, today the 31st: within monthly tolerance.
        val suggestion = RecurrenceDetector.detect(
            group(date(4, 28) to -999L, date(5, 28) to -999L, date(6, 28) to -999L),
            today,
        )
        assertNotNull(suggestion)
        // The proposed start is the first occurrence on or after today, never in the past.
        assertEquals(date(8, 28), suggestion!!.nextOccurrence)
    }

    @Test
    fun `zero amounts are never proposed`() {
        assertNull(
            RecurrenceDetector.detect(
                group(date(5, 15) to 0L, date(6, 15) to 0L, date(7, 15) to 0L),
                today,
            ),
        )
    }

    @Test
    fun `income series keeps its type and sign-independent amount`() {
        val suggestion = RecurrenceDetector.detect(
            group(
                date(5, 27) to 180000L,
                date(6, 27) to 180000L,
                date(7, 27) to 180000L,
                type = TransactionType.INCOME,
                displayName = "Stipendio",
            ),
            today,
        )
        assertNotNull(suggestion)
        assertEquals(TransactionType.INCOME, suggestion!!.type)
        assertEquals(180000L, suggestion.amountMinor)
    }

    private fun suggestion(
        amountMinor: Long = 1299L,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        categoryId: Long? = 7L,
    ) = RecurrenceSuggestion(
        key = "k",
        type = TransactionType.EXPENSE,
        name = "Netflix",
        amountMinor = amountMinor,
        isVariableAmount = false,
        currency = eur,
        frequency = frequency,
        accountId = 1L,
        categoryId = categoryId,
        occurrenceCount = 3,
        lastOccurrence = date(7, 15),
        nextOccurrence = date(8, 15),
        dayOfReference = 15,
    )

    private fun rule(
        amount: BigDecimal? = BigDecimal("12.99"),
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        categoryId: Long? = 7L,
        isVariableAmount: Boolean = false,
    ) = RecurringRule(
        name = "Netflix",
        type = TransactionType.EXPENSE,
        currency = eur,
        accountId = 1L,
        frequency = frequency,
        startDate = date(1, 15),
        amount = amount,
        categoryId = categoryId,
        mode = RecurrenceMode.AUTOMATIC,
        isVariableAmount = isVariableAmount,
    )

    @Test
    fun `a suggestion matching an existing rule is covered`() {
        assertTrue(RecurrenceDetector.isCoveredBy(suggestion(), listOf(rule())))
    }

    @Test
    fun `a variable-amount rule covers any figure in its slot`() {
        assertTrue(
            RecurrenceDetector.isCoveredBy(
                suggestion(amountMinor = 55000L),
                listOf(rule(amount = null, isVariableAmount = true)),
            ),
        )
    }

    @Test
    fun `a rule with a different frequency or amount does not cover the suggestion`() {
        assertFalse(
            RecurrenceDetector.isCoveredBy(
                suggestion(),
                listOf(rule(frequency = RecurrenceFrequency.ANNUAL)),
            ),
        )
        assertFalse(
            RecurrenceDetector.isCoveredBy(suggestion(), listOf(rule(amount = BigDecimal("55.00")))),
        )
    }

    @Test
    fun `duplicates recognizes the same series found by both paths`() {
        assertTrue(RecurrenceDetector.duplicates(suggestion(), suggestion(amountMinor = 1350L)))
        assertFalse(RecurrenceDetector.duplicates(suggestion(), suggestion(amountMinor = 9900L)))
        assertFalse(
            RecurrenceDetector.duplicates(
                suggestion(),
                suggestion(frequency = RecurrenceFrequency.WEEKLY),
            ),
        )
    }
}
