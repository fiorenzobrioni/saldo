package com.callbackdev.saldo.feature.stats

import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.YearMonth

/** The period driving the category ring and the per-account spend. */
sealed interface StatsPeriod {
    data class Month(val month: YearMonth) : StatsPeriod
    data class Year(val year: Int) : StatsPeriod

    /**
     * An explicit range, reusing the movements filter's open-ended period: at
     * least one bound is set, a null bound is open on that side. The open end
     * resolves to today and the open start to [EARLIEST_LEDGER_DATE] when the
     * period becomes a query, so the ring and per-account windows cover
     * "from X onwards" / "up to Y".
     */
    data class Custom(val start: LocalDate?, val end: LocalDate?) : StatsPeriod
}

/**
 * A floor for open-start custom periods: earlier than any real ledger entry,
 * and (unlike `LocalDate.MIN`) safe to turn into epoch millis for the SQL
 * window. The query result is the same as "from the first movement", since
 * nothing predates it.
 */
val EARLIEST_LEDGER_DATE: LocalDate = LocalDate.of(1, 1, 1)

/**
 * Inclusive local-date range covered by the period. A custom period's open
 * bounds resolve against [today]: an open start floors at [EARLIEST_LEDGER_DATE],
 * an open end ceils at [today].
 *
 * The month and the year that contain [today] stop at [today] as well: a
 * movement dated in the future is not spending that happened yet (ADR 36), so
 * the current period reads "to date", exactly like the dashboard's month card
 * and the budgets. Past periods keep their full calendar span.
 */
fun StatsPeriod.dateRange(today: LocalDate): ClosedRange<LocalDate> = when (this) {
    is StatsPeriod.Month -> month.atDay(1)..minOf(month.atEndOfMonth(), today.coerceAtLeast(month.atDay(1)))
    is StatsPeriod.Year -> {
        val first = Year.of(year).atDay(1)
        first..minOf(Year.of(year).atMonth(Month.DECEMBER).atEndOfMonth(), today.coerceAtLeast(first))
    }
    is StatsPeriod.Custom -> (start ?: EARLIEST_LEDGER_DATE)..(end ?: today)
}

/**
 * The same period moved by [step] months/years, or null for custom ranges
 * (they have no natural previous/next).
 */
fun StatsPeriod.shifted(step: Int): StatsPeriod? = when (this) {
    is StatsPeriod.Month -> StatsPeriod.Month(month.plusMonths(step.toLong()))
    is StatsPeriod.Year -> StatsPeriod.Year(year + step)
    is StatsPeriod.Custom -> null
}

/** Whether stepping forward would move past [today]'s month/year. */
fun StatsPeriod.isAtPresent(today: LocalDate): Boolean = when (this) {
    is StatsPeriod.Month -> month >= YearMonth.from(today)
    is StatsPeriod.Year -> year >= today.year
    is StatsPeriod.Custom -> true
}
