package com.callbackdev.saldo.core.domain.recurrence

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Currency
import kotlin.math.abs

/**
 * One confirmed movement inside a candidate group: the local day it happened
 * (ADR 7), its signed amount in minor units and the raw description, kept for
 * the suggestion's display name.
 */
data class CandidateOccurrence(
    val date: LocalDate,
    val amountMinor: Long,
    val description: String?,
)

/**
 * A group of similar movements pre-selected by SQL (ADR 43): same type,
 * account, category and currency, grouped either by exact amount or by
 * normalized description. The detector only ever sees these few groups,
 * never the ledger.
 */
data class RecurrenceCandidateGroup(
    /** Stable identity of the group, used to persist a dismissal (ADR 43). */
    val stableKey: String,
    val type: TransactionType,
    val accountId: Long,
    val categoryId: Long?,
    val currency: Currency,
    /** Most frequent raw description of the group; null when none is written. */
    val displayName: String?,
    val occurrences: List<CandidateOccurrence>,
)

/**
 * A recurrence the detector believes is not registered as a rule yet. All the
 * fields the rule editor needs to open prefilled: the amount is a positive
 * magnitude in minor units like [RecurringRule.amount] is a positive
 * [java.math.BigDecimal].
 */
data class RecurrenceSuggestion(
    val key: String,
    val type: TransactionType,
    /** Display name from the movements' description; null falls back to the category name in UI. */
    val name: String?,
    /** Median of the observed amounts; exact when they are all equal. */
    val amountMinor: Long,
    /** True when the observed amounts differ within tolerance (a variable bill). */
    val isVariableAmount: Boolean,
    val currency: Currency,
    val frequency: RecurrenceFrequency,
    val accountId: Long,
    val categoryId: Long?,
    val occurrenceCount: Int,
    val lastOccurrence: LocalDate,
    /** First predicted occurrence on or after the scan day: the proposed rule start date. */
    val nextOccurrence: LocalDate,
    /** Day of month the series anchors to (the highest observed, so short-month clamps read back). */
    val dayOfReference: Int,
)

/**
 * Pure heuristic that decides whether a candidate group is a regular
 * recurrence worth proposing (Fase 19, ADR 43). No I/O and no clock: the
 * caller fetches the groups and passes the reference day, so every threshold
 * is JVM-testable.
 *
 * The phase's non-negotiable stance applies: a series that does not clearly
 * match a cadence produces NO suggestion. A missed suggestion costs nothing
 * (the row in the hub can always be tapped again); a false one proposes a
 * rule that would generate wrong movements.
 */
object RecurrenceDetector {

    /** A series with fewer distinct days than this is never proposed. */
    const val MIN_OCCURRENCES = 3

    /** Allowed drift, in days, between an expected occurrence and the observed one. */
    const val WEEKLY_TOLERANCE_DAYS = 2L
    const val MONTHLY_TOLERANCE_DAYS = 4L
    const val ANNUAL_TOLERANCE_DAYS = 10L

    /**
     * Max deviation of each amount from the group median, for variable bills.
     * Integer math on minor units, never float (domain rule in CLAUDE.md).
     */
    const val AMOUNT_TOLERANCE_PERCENT = 15L

    private const val PERCENT_DENOMINATOR = 100L
    private const val MONTHS_PER_YEAR = 12L

    /**
     * Judges [group] against the cadences of the phase (weekly, monthly,
     * annual) as of [today]. Returns null when the series is irregular, too
     * short, has diverging amounts, or looks stopped (its next expected
     * occurrence is already overdue beyond tolerance).
     */
    fun detect(group: RecurrenceCandidateGroup, today: LocalDate): RecurrenceSuggestion? {
        // Two same-day charges are one occurrence for cadence purposes.
        val occurrences = group.occurrences
            .sortedBy { it.date }
            .distinctBy { it.date }
        if (occurrences.size < MIN_OCCURRENCES) return null

        val amounts = occurrences.map { abs(it.amountMinor) }
        val median = median(amounts)
        if (!amountsAgree(amounts, median)) return null

        val dates = occurrences.map { it.date }
        val frequency = matchCadence(dates) ?: return null
        val referenceDay = dates.maxOf { it.dayOfMonth }
        val last = dates.last()
        if (!isAlive(frequency, last, today)) return null

        return RecurrenceSuggestion(
            key = group.stableKey,
            type = group.type,
            name = group.displayName,
            amountMinor = median,
            isVariableAmount = amounts.distinct().size > 1,
            currency = group.currency,
            frequency = frequency,
            accountId = group.accountId,
            categoryId = group.categoryId,
            occurrenceCount = occurrences.size,
            lastOccurrence = last,
            nextOccurrence = nextOnOrAfter(frequency, last, referenceDay, today),
            dayOfReference = referenceDay,
        )
    }

    /**
     * Whether [suggestion] is already represented by one of [rules]: same
     * type, account, category and frequency, with a matching amount (a
     * variable-amount rule matches any figure in its slot). A created rule
     * makes its suggestion disappear through this check, because a re-scan
     * would keep finding the manual history that produced it.
     */
    fun isCoveredBy(suggestion: RecurrenceSuggestion, rules: List<RecurringRule>): Boolean =
        rules.any { rule ->
            rule.type == suggestion.type &&
                rule.accountId == suggestion.accountId &&
                rule.categoryId == suggestion.categoryId &&
                rule.frequency == suggestion.frequency &&
                ruleAmountMatches(rule, suggestion)
        }

    /**
     * Whether two suggestions describe the same series, used to drop the
     * amount-grouped duplicate of a description-grouped hit (the same
     * subscription is found by both SQL paths when it has a description).
     */
    fun duplicates(first: RecurrenceSuggestion, second: RecurrenceSuggestion): Boolean =
        first.type == second.type &&
            first.accountId == second.accountId &&
            first.categoryId == second.categoryId &&
            first.frequency == second.frequency &&
            withinTolerance(first.amountMinor, second.amountMinor)

    private fun ruleAmountMatches(rule: RecurringRule, suggestion: RecurrenceSuggestion): Boolean {
        val amount = rule.amount ?: return true
        return withinTolerance(MoneyMapper.toMinorUnits(amount, rule.currency), suggestion.amountMinor)
    }

    private fun withinTolerance(amountMinor: Long, referenceMinor: Long): Boolean =
        abs(amountMinor - referenceMinor) * PERCENT_DENOMINATOR <=
            referenceMinor * AMOUNT_TOLERANCE_PERCENT

    private fun amountsAgree(amounts: List<Long>, median: Long): Boolean =
        median > 0 && amounts.all { withinTolerance(it, median) }

    /** Lower median: deterministic and never an average of two amounts. */
    private fun median(amounts: List<Long>): Long = amounts.sorted()[(amounts.size - 1) / 2]

    private fun matchCadence(dates: List<LocalDate>): RecurrenceFrequency? = when {
        matchesWeekly(dates) -> RecurrenceFrequency.WEEKLY
        matchesMonthStep(dates, 1L, MONTHLY_TOLERANCE_DAYS) -> RecurrenceFrequency.MONTHLY
        matchesMonthStep(dates, MONTHS_PER_YEAR, ANNUAL_TOLERANCE_DAYS) -> RecurrenceFrequency.ANNUAL
        else -> null
    }

    private fun matchesWeekly(dates: List<LocalDate>): Boolean =
        dates.zipWithNext().all { (previous, next) ->
            abs(ChronoUnit.DAYS.between(previous.plusWeeks(1), next)) <= WEEKLY_TOLERANCE_DAYS
        }

    /**
     * Monthly-family cadence anchored to the highest observed day of month,
     * clamped to short months like [RecurrenceCalculator.occurrence]: a series
     * on the 31st that reads 31 Jan, 28 Feb, 31 Mar matches with zero drift.
     */
    private fun matchesMonthStep(dates: List<LocalDate>, stepMonths: Long, toleranceDays: Long): Boolean {
        val referenceDay = dates.maxOf { it.dayOfMonth }
        return dates.zipWithNext().all { (previous, next) ->
            val expected = monthStep(previous, stepMonths, referenceDay)
            abs(ChronoUnit.DAYS.between(expected, next)) <= toleranceDays
        }
    }

    /**
     * A series is alive while its next expected occurrence is not overdue
     * beyond tolerance as of [today]. A subscription cancelled months ago
     * keeps matching the cadence checks; this is what keeps it out.
     */
    private fun isAlive(frequency: RecurrenceFrequency, last: LocalDate, today: LocalDate): Boolean {
        val horizon = when (frequency) {
            RecurrenceFrequency.WEEKLY -> last.plusWeeks(1).plusDays(WEEKLY_TOLERANCE_DAYS)
            RecurrenceFrequency.MONTHLY -> last.plusMonths(1).plusDays(MONTHLY_TOLERANCE_DAYS)
            RecurrenceFrequency.ANNUAL -> last.plusYears(1).plusDays(ANNUAL_TOLERANCE_DAYS)
            else -> return false
        }
        return today <= horizon
    }

    /** First predicted occurrence on or after [today]; bounded by [isAlive] to at most two steps. */
    private fun nextOnOrAfter(
        frequency: RecurrenceFrequency,
        last: LocalDate,
        referenceDay: Int,
        today: LocalDate,
    ): LocalDate {
        var next = step(frequency, last, referenceDay)
        while (next < today) next = step(frequency, next, referenceDay)
        return next
    }

    private fun step(frequency: RecurrenceFrequency, from: LocalDate, referenceDay: Int): LocalDate =
        when (frequency) {
            RecurrenceFrequency.WEEKLY -> from.plusWeeks(1)
            RecurrenceFrequency.MONTHLY -> monthStep(from, 1L, referenceDay)
            else -> monthStep(from, MONTHS_PER_YEAR, referenceDay)
        }

    private fun monthStep(from: LocalDate, months: Long, referenceDay: Int): LocalDate {
        val month = YearMonth.from(from).plusMonths(months)
        return month.atDay(minOf(referenceDay, month.lengthOfMonth()))
    }
}
