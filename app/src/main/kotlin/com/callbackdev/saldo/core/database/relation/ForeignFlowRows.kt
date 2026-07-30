package com.callbackdev.saldo.core.database.relation

/*
 * Projections of the "foreign residue" aggregate queries (ADR 40): the exact
 * filters of their single-currency twins with the currency test inverted and
 * a GROUP BY on (currency, local day), so the domain can convert every bucket
 * at the rate of the movement's own date.
 */

/** Row of the foreign twin of the dashboard totals query. */
data class ForeignDashboardFlowsRow(
    val currency: String,
    val epochDay: Long,
    val todaySpendMinor: Long?,
    val todayIncomeMinor: Long?,
    val monthSpendMinor: Long?,
    val monthIncomeMinor: Long?,
    val monthToDateSpendMinor: Long?,
    val monthToDateNonRecurringSpendMinor: Long?,
    val previousToDateSpendMinor: Long?,
)

/** Row of the foreign twin of the per-category statistics totals. */
data class ForeignCategoryDayRow(
    val categoryId: Long?,
    val currency: String,
    val epochDay: Long,
    val totalMinor: Long,
    val count: Int,
)

/** Row of the foreign twin of the per-account spend totals. */
data class ForeignAccountDayRow(
    val accountId: Long,
    val currency: String,
    val epochDay: Long,
    val totalMinor: Long,
    val count: Int,
)

/** Row of the foreign twin of the monthly trend totals. */
data class ForeignMonthlyDayRow(
    val currency: String,
    val epochDay: Long,
    val expenseMinor: Long?,
    val incomeMinor: Long?,
)

/** Per-currency count of the statistics movements outside the primary currency. */
data class CurrencyCountRow(
    val currency: String,
    val count: Int,
)

/** Row of the budget spend query grouped by currency and local day. */
data class SpendCurrencyDayRow(
    val currency: String,
    val epochDay: Long,
    val totalMinor: Long,
)

/** [SpendCurrencyDayRow] broken down per category. */
data class CategorySpendCurrencyDayRow(
    val categoryId: Long,
    val currency: String,
    val epochDay: Long,
    val totalMinor: Long,
)
