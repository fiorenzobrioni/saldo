package com.callbackdev.saldo.core.domain.recurrence

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class RecurrenceCalculatorTest {

    private val eur: Currency = Currency.getInstance("EUR")

    private fun rule(
        frequency: RecurrenceFrequency,
        startDate: LocalDate,
        amount: BigDecimal? = null,
        endDate: LocalDate? = null,
    ) = RecurringRule(
        name = "rule",
        type = TransactionType.EXPENSE,
        currency = eur,
        accountId = 1L,
        frequency = frequency,
        startDate = startDate,
        amount = amount,
        dayOfReference = startDate.dayOfMonth,
        endDate = endDate,
    )

    @Test
    fun `daily and weekly step by day and week`() {
        val daily = rule(RecurrenceFrequency.DAILY, LocalDate.of(2026, 1, 1))
        assertEquals(LocalDate.of(2026, 1, 11), RecurrenceCalculator.occurrence(daily, 10))

        val weekly = rule(RecurrenceFrequency.WEEKLY, LocalDate.of(2026, 1, 1))
        assertEquals(LocalDate.of(2026, 1, 22), RecurrenceCalculator.occurrence(weekly, 3))
    }

    @Test
    fun `monthly day 31 clamps to the last day of short months and returns to 31`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2026, 1, 31), RecurrenceCalculator.occurrence(r, 0))
        // February 2026 has 28 days.
        assertEquals(LocalDate.of(2026, 2, 28), RecurrenceCalculator.occurrence(r, 1))
        assertEquals(LocalDate.of(2026, 3, 31), RecurrenceCalculator.occurrence(r, 2))
        assertEquals(LocalDate.of(2026, 4, 30), RecurrenceCalculator.occurrence(r, 3))
        // The clamp is re-derived from day 31 each month, never carried forward.
        assertEquals(LocalDate.of(2026, 5, 31), RecurrenceCalculator.occurrence(r, 4))
    }

    @Test
    fun `annual Feb 29 lands on Feb 28 in common years and Feb 29 in leap years`() {
        val r = rule(RecurrenceFrequency.ANNUAL, LocalDate.of(2024, 2, 29))
        assertEquals(LocalDate.of(2024, 2, 29), RecurrenceCalculator.occurrence(r, 0))
        assertEquals(LocalDate.of(2025, 2, 28), RecurrenceCalculator.occurrence(r, 1))
        assertEquals(LocalDate.of(2026, 2, 28), RecurrenceCalculator.occurrence(r, 2))
        assertEquals(LocalDate.of(2027, 2, 28), RecurrenceCalculator.occurrence(r, 3))
        assertEquals(LocalDate.of(2028, 2, 29), RecurrenceCalculator.occurrence(r, 4))
    }

    @Test
    fun `quarterly steps by three months`() {
        val r = rule(RecurrenceFrequency.QUARTERLY, LocalDate.of(2026, 1, 15))
        assertEquals(LocalDate.of(2026, 4, 15), RecurrenceCalculator.occurrence(r, 1))
        assertEquals(LocalDate.of(2026, 10, 15), RecurrenceCalculator.occurrence(r, 3))
    }

    @Test
    fun `nextOccurrence returns the first occurrence on or after a date`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 15))
        assertEquals(LocalDate.of(2026, 3, 15), RecurrenceCalculator.nextOccurrence(r, LocalDate.of(2026, 3, 10)))
        // On-or-after includes the exact day.
        assertEquals(LocalDate.of(2026, 3, 15), RecurrenceCalculator.nextOccurrence(r, LocalDate.of(2026, 3, 15)))
        assertEquals(LocalDate.of(2026, 4, 15), RecurrenceCalculator.nextOccurrence(r, LocalDate.of(2026, 3, 16)))
    }

    @Test
    fun `nextOccurrence never precedes the start date`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 6, 10))
        assertEquals(LocalDate.of(2026, 6, 10), RecurrenceCalculator.nextOccurrence(r, LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `nextOccurrence past the end date is null`() {
        val r = rule(
            RecurrenceFrequency.MONTHLY,
            LocalDate.of(2026, 1, 15),
            endDate = LocalDate.of(2026, 3, 31),
        )
        assertEquals(LocalDate.of(2026, 3, 15), RecurrenceCalculator.nextOccurrence(r, LocalDate.of(2026, 3, 1)))
        assertNull(RecurrenceCalculator.nextOccurrence(r, LocalDate.of(2026, 4, 1)))
    }

    @Test
    fun `occurrencesInClosedRange returns every due date, catching up missed months`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 1))
        val dates = RecurrenceCalculator.occurrencesInClosedRange(
            r,
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 4, 15),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1),
            ),
            dates,
        )
    }

    @Test
    fun `occurrencesInClosedRange from the day after the last generated date resumes cleanly`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 1))
        val dates = RecurrenceCalculator.occurrencesInClosedRange(
            r,
            from = LocalDate.of(2026, 2, 2), // last generated = 1 Feb
            to = LocalDate.of(2026, 4, 15),
        )
        assertEquals(listOf(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)), dates)
    }

    @Test
    fun `occurrencesInClosedRange is empty once caught up (idempotency)`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 1))
        val dates = RecurrenceCalculator.occurrencesInClosedRange(
            r,
            from = LocalDate.of(2026, 4, 2), // last generated = 1 Apr, today = 15 Apr
            to = LocalDate.of(2026, 4, 15),
        )
        assertEquals(emptyList<LocalDate>(), dates)
    }

    @Test
    fun `occurrencesInClosedRange respects the end date`() {
        val r = rule(
            RecurrenceFrequency.MONTHLY,
            LocalDate.of(2026, 1, 10),
            endDate = LocalDate.of(2026, 3, 15),
        )
        val dates = RecurrenceCalculator.occurrencesInClosedRange(
            r,
            from = LocalDate.of(2026, 1, 10),
            to = LocalDate.of(2026, 12, 31),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 10)),
            dates,
        )
    }

    @Test
    fun `latestOccurrenceBefore finds the most recent past charge`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 5, 7))
        assertEquals(
            LocalDate.of(2026, 7, 7),
            RecurrenceCalculator.latestOccurrenceBefore(r, LocalDate.of(2026, 7, 9)),
        )
    }

    @Test
    fun `latestOccurrenceBefore is null when the rule starts on or after the date`() {
        val onStart = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 7, 9))
        assertNull(RecurrenceCalculator.latestOccurrenceBefore(onStart, LocalDate.of(2026, 7, 9)))

        val future = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 8, 7))
        assertNull(RecurrenceCalculator.latestOccurrenceBefore(future, LocalDate.of(2026, 7, 9)))
    }

    @Test
    fun `monthly equivalent normalizes non-monthly charges over the month`() {
        val semiannual = rule(RecurrenceFrequency.SEMIANNUAL, LocalDate.of(2026, 9, 15), amount = BigDecimal("96.00"))
        assertEquals(BigDecimal("16.00"), RecurrenceCalculator.monthlyEquivalent(semiannual))

        val monthly = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 7, 7), amount = BigDecimal("12.99"))
        assertEquals(BigDecimal("12.99"), RecurrenceCalculator.monthlyEquivalent(monthly))

        val annual = rule(RecurrenceFrequency.ANNUAL, LocalDate.of(2026, 1, 1), amount = BigDecimal("120.00"))
        assertEquals(BigDecimal("10.00"), RecurrenceCalculator.monthlyEquivalent(annual))

        val quarterly = rule(RecurrenceFrequency.QUARTERLY, LocalDate.of(2026, 1, 1), amount = BigDecimal("30.00"))
        assertEquals(BigDecimal("10.00"), RecurrenceCalculator.monthlyEquivalent(quarterly))
    }

    @Test
    fun `monthly equivalent is null for variable amount rules`() {
        val variable = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 1), amount = null)
        assertNull(RecurrenceCalculator.monthlyEquivalent(variable))
    }
}
