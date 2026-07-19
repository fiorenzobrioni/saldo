package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Net expense/income of a time window. */
data class PeriodTotals(
    /** Sum of expenses (<= 0). */
    val spend: BigDecimal = BigDecimal.ZERO,
    /** Sum of incomes (>= 0). */
    val income: BigDecimal = BigDecimal.ZERO,
) {
    val net: BigDecimal get() = spend.add(income)
}

/**
 * The dashboard's aggregate figures, computed by the database in a single
 * query (never by loading the ledger in memory).
 */
data class DashboardTotals(
    val today: PeriodTotals = PeriodTotals(),
    val month: PeriodTotals = PeriodTotals(),
    /** Positive magnitude spent from the 1st of the month through today. */
    val monthToDateSpend: BigDecimal = BigDecimal.ZERO,
    /**
     * Positive magnitude spent from the 1st of the month through today by
     * non-recurring (manual) movements only: the base of the forecast's daily
     * spend average, so recurring charges already booked this month do not
     * inflate it and get double-counted against their explicit future dates.
     */
    val monthToDateNonRecurringSpend: BigDecimal = BigDecimal.ZERO,
    /** Positive magnitude spent by this same day last month. */
    val previousMonthToDateSpend: BigDecimal = BigDecimal.ZERO,
)

/**
 * The instant windows behind [DashboardTotals], resolved once from the device
 * zone. Movements recorded in another timezone fall in the window of their
 * instant, which can differ by a day from their own local date; an acceptable
 * approximation for at-a-glance figures (the ledger keeps per-movement offsets).
 */
data class DashboardWindows(
    val todayStart: Instant,
    val todayEnd: Instant,
    val monthStart: Instant,
    val monthEnd: Instant,
    val previousStart: Instant,
    val previousToDateEnd: Instant,
) {
    companion object {
        fun around(today: LocalDate, zone: ZoneId): DashboardWindows {
            val monthStart = today.withDayOfMonth(1)
            // minusMonths clamps short months (Jul 31 -> Jun 30), matching the
            // "by this same day last month" reading.
            val previousToDate = today.minusMonths(1)
            return DashboardWindows(
                todayStart = today.atStartOfDay(zone).toInstant(),
                todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant(),
                monthStart = monthStart.atStartOfDay(zone).toInstant(),
                monthEnd = monthStart.plusMonths(1).atStartOfDay(zone).toInstant(),
                previousStart = previousToDate.withDayOfMonth(1).atStartOfDay(zone).toInstant(),
                previousToDateEnd = previousToDate.plusDays(1).atStartOfDay(zone).toInstant(),
            )
        }
    }
}
