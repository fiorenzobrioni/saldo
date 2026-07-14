package com.callbackdev.saldo.feature.stats

import androidx.compose.ui.graphics.Color
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.MonthlyBalance
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

/**
 * One category's share of the period's spend. [amount] is the positive net
 * spend (refunds already netted by the query); categories whose refunds
 * exceed their expenses are not slices. [fraction] is the share of the total
 * (0..1) used for arcs and bars; a display proportion, never money math.
 * [category] is null for the uncategorized bucket (movements left without a
 * category after a deletion), so the ring always covers the whole spend.
 */
data class CategorySlice(
    val category: Category?,
    val amount: BigDecimal,
    val fraction: Float,
    val percent: Int,
    val count: Int,
)

/**
 * One account's positive net spend over the period. [fraction] is relative to
 * the biggest spender (0..1), for the proportional bars.
 */
data class AccountSpend(
    val account: Account,
    val amount: BigDecimal,
    val fraction: Float,
    val count: Int,
)

/**
 * One month of the trend charts. [expense] is a positive magnitude (a
 * refund-heavy month is clamped to zero); [income] is >= 0 by construction.
 */
data class MonthlyPoint(
    val month: YearMonth,
    val expense: BigDecimal,
    val income: BigDecimal,
)

/** One column series of the bar charts: minor-unit values (one per month) and its color. */
internal data class BarSeries(
    val valuesMinor: List<Long>,
    val color: Color,
)

/** Immutable UI state of the statistics screen. */
data class StatsUiState(
    val isLoading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.Month(YearMonth.of(EPOCH_YEAR, 1)),
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val currency: Currency = fallbackCurrency,
    /** Category shares of the selected period, biggest first. */
    val slices: List<CategorySlice> = emptyList(),
    /** Positive total spend of the selected period (the donut's center figure). */
    val periodSpendTotal: BigDecimal = BigDecimal.ZERO,
    /** Per-account spend of the selected period, biggest first. */
    val accountSpends: List<AccountSpend> = emptyList(),
    /** The last 12 months' expense/income, oldest first, months without data zero-filled. */
    val monthlyTotals: List<MonthlyPoint> = emptyList(),
    /** End-of-month total balance for the same 12 months. */
    val balanceHistory: List<MonthlyBalance> = emptyList(),
    /** False when the ledger holds nothing the statistics can chew on. */
    val hasData: Boolean = false,
) {
    /** The whole screen's first-run empty state. */
    val isEmpty: Boolean get() = !isLoading && !hasData

    /** True when the last 12 months hold nothing for the trend charts. */
    val isTrendEmpty: Boolean
        get() = monthlyTotals.all { it.expense.signum() == 0 && it.income.signum() == 0 }

    private companion object {
        const val EPOCH_YEAR = 1970
    }
}
