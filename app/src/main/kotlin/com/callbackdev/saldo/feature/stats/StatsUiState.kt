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
    /**
     * Movements of the selected period the charts leave out for the sole
     * reason of being in another currency: every foreign movement with
     * conversion off, only the ones without a usable rate with it on
     * (ADR 40). Zero in the ordinary single-currency case.
     */
    val otherCurrencyCount: Int = 0,
    /**
     * Foreign movements of the selected period converted into [currency] at
     * the rate of their own day and included in the charts (ADR 40). Feeds
     * the "estimated" notice; zero when conversion is off.
     */
    val convertedCurrencyCount: Int = 0,
    /** Whether conversion is on with at least one usable rate (ADR 40). */
    val conversionActive: Boolean = false,
) {
    /** The whole screen's first-run empty state. */
    val isEmpty: Boolean get() = !isLoading && !hasData

    /**
     * Whether to tell the user the charts are not the whole period. Also true
     * on an empty screen: a period holding only foreign movements would
     * otherwise read as "you recorded nothing", which is the most confusing
     * version of this.
     */
    val showsOtherCurrencyNotice: Boolean get() = !isLoading && otherCurrencyCount > 0

    /**
     * Whether to declare that some figures are estimates built on converted
     * foreign movements (ADR 40: a countervalue is always declared).
     */
    val showsConvertedNotice: Boolean get() = !isLoading && convertedCurrencyCount > 0

    /** True when the last 12 months hold nothing for the trend charts. */
    val isTrendEmpty: Boolean
        get() = monthlyTotals.all { it.expense.signum() == 0 && it.income.signum() == 0 }

    private companion object {
        const val EPOCH_YEAR = 1970
    }
}
