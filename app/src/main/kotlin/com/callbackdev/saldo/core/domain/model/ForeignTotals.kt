package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/*
 * Per-day residues of the movements the single-currency aggregates leave out
 * (ADR 40). Every aggregate keeps its primary-currency query untouched (the
 * single-currency case stays correct by construction) and gains a "foreign
 * residue" twin grouped by (currency, local day): the day granularity is what
 * lets the domain convert each bucket at the rate of the movement's own date,
 * the second condition of the ADR. Amounts are already scaled to their own
 * currency.
 */

/** One (currency, day) bucket of the dashboard flows outside the primary currency. */
data class ForeignDashboardDayFlows(
    val currency: Currency,
    val day: LocalDate,
    val today: PeriodTotals,
    val month: PeriodTotals,
    /** Positive magnitude, like [DashboardTotals.monthToDateSpend]. */
    val monthToDateSpend: BigDecimal,
    val monthToDateNonRecurringSpend: BigDecimal,
    val previousToDateSpend: BigDecimal,
)

/** One (category, currency, day) bucket of statistics totals outside the primary currency. */
data class ForeignCategoryDayTotal(
    val categoryId: Long?,
    val currency: Currency,
    val day: LocalDate,
    /** Signed, like [CategoryTotal.total]. */
    val total: BigDecimal,
    val count: Int,
)

/** One (account, currency, day) bucket of spend totals outside the primary currency. */
data class ForeignAccountDayTotal(
    val accountId: Long,
    val currency: Currency,
    val day: LocalDate,
    /** Signed, like [AccountTotal.total]. */
    val total: BigDecimal,
    val count: Int,
)

/** One (currency, day) bucket of the statistics trend outside the primary currency. */
data class ForeignMonthlyDayTotal(
    val currency: Currency,
    val day: LocalDate,
    /** Signed, refunds netted in, like [MonthlyTotal.expense]. */
    val expense: BigDecimal,
    val income: BigDecimal,
)

/** How many statistics movements a period holds per non-primary currency. */
data class CurrencyMovementCount(
    val currencyCode: String,
    val count: Int,
)

/** One (currency, day) bucket of budget-relevant spend, primary currency included. */
data class SpendDayTotal(
    val currency: Currency,
    val day: LocalDate,
    /** Signed spend (refunds netted), like the budget spend queries. */
    val total: BigDecimal,
)

/** [SpendDayTotal] broken down per category, for category budgets. */
data class CategorySpendDayTotal(
    val categoryId: Long,
    val currency: Currency,
    val day: LocalDate,
    val total: BigDecimal,
)
