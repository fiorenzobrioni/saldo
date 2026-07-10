package com.callbackdev.saldo.core.domain.recurrence

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pure date/amount math for recurring rules. No Android, no I/O: fully unit
 * testable (short months, leap years, catch-up, idempotency).
 *
 * ### Occurrence model
 * The start date is occurrence 0. Daily/weekly rules step by 1 day / 1 week.
 * Monthly-and-longer rules step by their month period and land on
 * [RecurringRule.dayOfReference] (defaulting to the start day), **clamped to the
 * month length** so a "day 31" rule falls on the 30th/28th in short months and
 * returns to the 31st afterwards - the clamp is re-derived from the reference day
 * each period, never carried forward from a previous clamp.
 */
object RecurrenceCalculator {

    private const val MONTHS_PER_YEAR = 12

    /** Months added per period for monthly-family frequencies; null for daily/weekly. */
    @Suppress("MagicNumber")
    private fun stepMonths(frequency: RecurrenceFrequency): Int? = when (frequency) {
        RecurrenceFrequency.MONTHLY -> 1
        RecurrenceFrequency.BIMONTHLY -> 2
        RecurrenceFrequency.QUARTERLY -> 3
        RecurrenceFrequency.SEMIANNUAL -> 6
        RecurrenceFrequency.ANNUAL -> MONTHS_PER_YEAR
        RecurrenceFrequency.DAILY, RecurrenceFrequency.WEEKLY -> null
    }

    private fun referenceDay(rule: RecurringRule): Int =
        rule.dayOfReference ?: rule.startDate.dayOfMonth

    /**
     * The date of the [index]-th occurrence (index >= 0), counting the start date
     * as index 0. Monthly-family days are clamped to the month length.
     */
    fun occurrence(rule: RecurringRule, index: Int): LocalDate {
        require(index >= 0) { "occurrence index must be >= 0, was $index" }
        val step = stepMonths(rule.frequency)
        return if (step == null) {
            when (rule.frequency) {
                RecurrenceFrequency.DAILY -> rule.startDate.plusDays(index.toLong())
                RecurrenceFrequency.WEEKLY -> rule.startDate.plusWeeks(index.toLong())
                else -> error("frequency ${rule.frequency} has no month step and is not daily/weekly")
            }
        } else {
            val month = YearMonth.from(rule.startDate).plusMonths(index.toLong() * step)
            month.atDay(minOf(referenceDay(rule), month.lengthOfMonth()))
        }
    }

    /**
     * The first occurrence on or after [date], within the rule's active span
     * ([RecurringRule.startDate]..[RecurringRule.endDate]); null when the rule has
     * already ended before such a date.
     */
    fun nextOccurrence(rule: RecurringRule, date: LocalDate): LocalDate? {
        val result = occurrence(rule, firstIndexOnOrAfter(rule, date))
        return result.takeUnless { rule.endDate != null && it > rule.endDate }
    }

    /**
     * The latest occurrence strictly before [date], within the rule's active span;
     * null when none exists. Used to seed `lastGeneratedDate` so a freshly created
     * rule does not back-fill past movements.
     */
    fun latestOccurrenceBefore(rule: RecurringRule, date: LocalDate): LocalDate? {
        val ceilingExclusive = rule.endDate
            ?.let { end -> if (end < date) end.plusDays(1) else date }
            ?: date
        if (ceilingExclusive <= rule.startDate) return null
        val previous = firstIndexOnOrAfter(rule, ceilingExclusive) - 1
        return if (previous < 0) null else occurrence(rule, previous)
    }

    /**
     * All occurrences in the closed range [from]..[to], clamped to the rule's span.
     * The engine's core: [from] is typically `lastGeneratedDate + 1` and [to] is
     * today, so this returns exactly the movements still owed.
     */
    fun occurrencesInClosedRange(
        rule: RecurringRule,
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDate> {
        val upperBound = rule.endDate?.let { minOf(it, to) } ?: to
        if (upperBound < rule.startDate) return emptyList()
        val result = mutableListOf<LocalDate>()
        var index = firstIndexOnOrAfter(rule, from)
        var date = occurrence(rule, index)
        while (date <= upperBound) {
            result.add(date)
            index++
            date = occurrence(rule, index)
        }
        return result
    }

    /**
     * The equivalent monthly cost of [rule], normalizing non-monthly charges over
     * the month (a 96,00 semi-annual charge reads as 16,00/month). Null for
     * variable-amount rules (no fixed amount to spread). Rounded to the currency's
     * fraction digits.
     */
    fun monthlyEquivalent(rule: RecurringRule): BigDecimal? {
        val amount = rule.amount ?: return null
        val perYear = occurrencesPerYear(rule.frequency)
        return amount
            .multiply(perYear)
            .divide(BigDecimal(MONTHS_PER_YEAR), MoneyMapper.fractionDigits(rule.currency), RoundingMode.HALF_UP)
    }

    /** Charges per year for a frequency, used to normalize to a monthly figure. */
    @Suppress("MagicNumber")
    private fun occurrencesPerYear(frequency: RecurrenceFrequency): BigDecimal = when (frequency) {
        RecurrenceFrequency.DAILY -> BigDecimal(365)
        RecurrenceFrequency.WEEKLY -> BigDecimal(52)
        RecurrenceFrequency.MONTHLY -> BigDecimal(12)
        RecurrenceFrequency.BIMONTHLY -> BigDecimal(6)
        RecurrenceFrequency.QUARTERLY -> BigDecimal(4)
        RecurrenceFrequency.SEMIANNUAL -> BigDecimal(2)
        RecurrenceFrequency.ANNUAL -> BigDecimal.ONE
    }

    /**
     * Index of the first occurrence on or after [date], never below 0 and never
     * before the start date. Estimates the index analytically, then adjusts by a
     * few steps so the clamp on short months lands exactly.
     */
    private fun firstIndexOnOrAfter(rule: RecurringRule, date: LocalDate): Int {
        val floor = maxOf(date, rule.startDate)
        var index = estimateIndex(rule, floor).coerceAtLeast(0)
        while (index > 0 && occurrence(rule, index - 1) >= floor) index--
        while (occurrence(rule, index) < floor) index++
        return index
    }

    private fun estimateIndex(rule: RecurringRule, date: LocalDate): Int {
        if (date <= rule.startDate) return 0
        val step = stepMonths(rule.frequency)
        return if (step == null) {
            val days = ChronoUnit.DAYS.between(rule.startDate, date)
            when (rule.frequency) {
                RecurrenceFrequency.DAILY -> days.toInt()
                RecurrenceFrequency.WEEKLY -> (days / DAYS_PER_WEEK).toInt()
                else -> 0
            }
        } else {
            val months = ChronoUnit.MONTHS.between(YearMonth.from(rule.startDate), YearMonth.from(date))
            (months / step).toInt()
        }
    }

    private const val DAYS_PER_WEEK = 7
}
