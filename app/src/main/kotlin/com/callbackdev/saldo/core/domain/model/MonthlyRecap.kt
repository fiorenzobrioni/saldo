package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

/**
 * Everything the monthly recap ("Wrapped") pages show about one completed
 * month, computed under the exact statistics semantics of the Stats screen
 * (ADR 8: transfers/adjustments never count; refunds net the spend;
 * excluded-from-stats and pending movements are skipped), so every recap
 * figure matches the Statistics screen for the same month.
 */
data class MonthlyRecap(
    val month: YearMonth,
    val currency: Currency,
    /** Positive magnitude of the month's spend, refunds netted; zero when refunds win. */
    val expenseTotal: BigDecimal,
    /** Refund-free income total, >= 0. */
    val incomeTotal: BigDecimal,
    /**
     * Positive spend magnitude of the previous month, or null when that month
     * has no statistics movements at all (no baseline to compare against).
     */
    val previousExpenseTotal: BigDecimal?,
    /** Categories by net spend, biggest first, capped at [TOP_CATEGORIES]. */
    val topCategories: List<RecapCategoryShare>,
    val biggestExpense: RecapBiggestExpense?,
    val busiestDay: RecapBusiestDay?,
    /** Positive magnitude of the month's rule-generated expenses; zero when none. */
    val recurringSpend: BigDecimal,
    /** Number of statistics movements in the month. */
    val movementCount: Int,
    /**
     * Share of the income kept as net savings, floor-rounded percent; null
     * unless both income and net are positive.
     */
    val savingsRatePercent: Int?,
) {
    /** Net result of the month: income minus spend. */
    val net: BigDecimal get() = incomeTotal.subtract(expenseTotal)

    /** Whether the month has anything worth recapping. */
    val hasData: Boolean get() = movementCount > 0

    companion object {
        const val TOP_CATEGORIES = 5
    }
}

/**
 * One category's share of the recap month's spend. [categoryId] is null for
 * the uncategorized bucket, like the statistics ring; name/color/icon are
 * resolved by the ViewModel. [fraction] is a display proportion of the total
 * spend, never money math.
 */
data class RecapCategoryShare(
    val categoryId: Long?,
    /** Positive net spend of the category. */
    val amount: BigDecimal,
    val fraction: Float,
    val percent: Int,
    val count: Int,
)

/** The single biggest expense of the recap month. */
data class RecapBiggestExpense(
    /** Positive magnitude. */
    val amount: BigDecimal,
    val description: String?,
    val categoryId: Long?,
    /** The movement's own local day (ADR 7). */
    val date: LocalDate,
)

/** The day with the most statistics movements in the recap month. */
data class RecapBusiestDay(
    val date: LocalDate,
    val count: Int,
    /** Positive spend magnitude of that day; zero when incomes prevailed. */
    val spend: BigDecimal,
)
