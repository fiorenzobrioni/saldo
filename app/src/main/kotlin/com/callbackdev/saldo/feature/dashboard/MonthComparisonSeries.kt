package com.callbackdev.saldo.feature.dashboard

import com.callbackdev.saldo.core.domain.model.DailyBalance
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * The two normalized series the dashboard's month-comparison chart overlays:
 * the net change of the total balance since the start of the month, day by
 * day, for the previous month (complete) and the current one (up to today).
 *
 * The chart plots the change rather than the raw balances on purpose: two
 * months sit at different absolute balance levels, so raw lines would float
 * apart and say nothing, while both delta lines leave zero on day one and
 * answer the one question the card asks - how is this month tracking against
 * the last one.
 */
data class MonthComparisonSeries(
    /** Net change at the end of each day of the previous month, first to last day. */
    val previous: List<BigDecimal>,
    /** Net change at the end of each day of the current month, first day through today. */
    val current: List<BigDecimal>,
)

/**
 * Builds the comparison series from a contiguous daily-balance walk covering
 * the day before the previous month's start through today. Each month's
 * series is the balance at the end of each day minus the balance the month
 * started from. Null when the walk does not cover the expected window (no
 * accounts yet, or a partial emission while sources settle).
 */
internal fun buildMonthComparison(
    history: List<DailyBalance>,
    today: LocalDate,
): MonthComparisonSeries? {
    if (history.isEmpty()) return null
    val byDay = history.associate { it.date to it.balance }
    val currentMonth = YearMonth.from(today)
    val previousMonth = currentMonth.minusMonths(1)
    val previous = monthDeltas(byDay, previousMonth, previousMonth.lengthOfMonth()) ?: return null
    val current = monthDeltas(byDay, currentMonth, today.dayOfMonth) ?: return null
    return MonthComparisonSeries(previous = previous, current = current)
}

/** Per-day net change of [month] over its first [days] days; null when a day is missing. */
private fun monthDeltas(
    byDay: Map<LocalDate, BigDecimal>,
    month: YearMonth,
    days: Int,
): List<BigDecimal>? {
    val baseline = byDay[month.atDay(1).minusDays(1)] ?: return null
    return (1..days).map { day ->
        val balance = byDay[month.atDay(day)] ?: return null
        balance.subtract(baseline)
    }
}
