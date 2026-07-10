package com.callbackdev.saldo.feature.stats

import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.YearMonth

/** The period driving the category ring and the per-account spend. */
sealed interface StatsPeriod {
    data class Month(val month: YearMonth) : StatsPeriod
    data class Year(val year: Int) : StatsPeriod
    data class Custom(val start: LocalDate, val end: LocalDate) : StatsPeriod
}

/** Inclusive local-date range covered by the period. */
fun StatsPeriod.dateRange(): ClosedRange<LocalDate> = when (this) {
    is StatsPeriod.Month -> month.atDay(1)..month.atEndOfMonth()
    is StatsPeriod.Year -> Year.of(year).atDay(1)..Year.of(year).atMonth(Month.DECEMBER).atEndOfMonth()
    is StatsPeriod.Custom -> start..end
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
