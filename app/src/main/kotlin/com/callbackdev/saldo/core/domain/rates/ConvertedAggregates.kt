package com.callbackdev.saldo.core.domain.rates

import com.callbackdev.saldo.core.domain.model.AccountTotal
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.ForeignAccountDayTotal
import com.callbackdev.saldo.core.domain.model.ForeignCategoryDayTotal
import com.callbackdev.saldo.core.domain.model.ForeignDashboardDayFlows
import com.callbackdev.saldo.core.domain.model.ForeignMonthlyDayTotal
import com.callbackdev.saldo.core.domain.model.MonthlyTotal
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

/**
 * Folds the foreign residues into their primary-currency aggregates (ADR 40).
 * Every function converts each (currency, day) bucket at the rate of the
 * movement's own day through [CurrencyConverter] and reports what it could
 * not convert, so the screens can tell "estimated" apart from "left out".
 * Pure functions over immutable data: the unit tests of the mixed aggregates
 * live here.
 */
object ConvertedAggregates {

    /** A merged aggregate plus the honesty metadata every surface must show. */
    data class Merged<T>(
        val value: T,
        /** Foreign buckets actually converted; zero means the value is exact. */
        val convertedCount: Int,
        /** ISO codes whose buckets had no rate and stayed out, as before the feature. */
        val unconvertedCurrencies: Set<String>,
    ) {
        val includesEstimates: Boolean get() = convertedCount > 0
    }

    /** [DashboardTotals] plus the converted foreign flows of the same windows. */
    fun mergeDashboardTotals(
        base: DashboardTotals,
        foreign: List<ForeignDashboardDayFlows>,
        target: Currency,
        rates: RateTable,
    ): Merged<DashboardTotals> {
        var merged = base
        var converted = 0
        val unconverted = mutableSetOf<String>()
        for (bucket in foreign) {
            val convert: (BigDecimal) -> BigDecimal? = { amount ->
                CurrencyConverter.convertOn(amount, bucket.currency, target, bucket.day, rates)?.amount
            }
            // One probe decides for the whole bucket: either the currency has
            // rates (every field converts) or it has none.
            if (convert(BigDecimal.ONE) == null) {
                unconverted += bucket.currency.currencyCode
                continue
            }
            converted++
            merged = DashboardTotals(
                today = merged.today.plusConverted(bucket.today, convert),
                month = merged.month.plusConverted(bucket.month, convert),
                monthToDateSpend = merged.monthToDateSpend
                    .add(convert(bucket.monthToDateSpend) ?: BigDecimal.ZERO),
                monthToDateNonRecurringSpend = merged.monthToDateNonRecurringSpend
                    .add(convert(bucket.monthToDateNonRecurringSpend) ?: BigDecimal.ZERO),
                previousMonthToDateSpend = merged.previousMonthToDateSpend
                    .add(convert(bucket.previousToDateSpend) ?: BigDecimal.ZERO),
            )
        }
        return Merged(merged, converted, unconverted)
    }

    /** Per-category totals plus the converted foreign buckets, re-keyed by category. */
    fun mergeCategoryTotals(
        base: List<CategoryTotal>,
        foreign: List<ForeignCategoryDayTotal>,
        target: Currency,
        rates: RateTable,
    ): Merged<List<CategoryTotal>> {
        val byCategory = base.associateBy { it.categoryId }.toMutableMap()
        var converted = 0
        val unconverted = mutableSetOf<String>()
        for (bucket in foreign) {
            val estimate =
                CurrencyConverter.convertOn(bucket.total, bucket.currency, target, bucket.day, rates)
            if (estimate == null) {
                unconverted += bucket.currency.currencyCode
                continue
            }
            converted++
            val existing = byCategory[bucket.categoryId]
            byCategory[bucket.categoryId] = CategoryTotal(
                categoryId = bucket.categoryId,
                total = (existing?.total ?: BigDecimal.ZERO).add(estimate.amount),
                count = (existing?.count ?: 0) + bucket.count,
            )
        }
        return Merged(byCategory.values.toList(), converted, unconverted)
    }

    /** Per-account spend totals plus the converted foreign buckets, re-keyed by account. */
    fun mergeAccountTotals(
        base: List<AccountTotal>,
        foreign: List<ForeignAccountDayTotal>,
        target: Currency,
        rates: RateTable,
    ): Merged<List<AccountTotal>> {
        val byAccount = base.associateBy { it.accountId }.toMutableMap()
        var converted = 0
        val unconverted = mutableSetOf<String>()
        for (bucket in foreign) {
            val estimate =
                CurrencyConverter.convertOn(bucket.total, bucket.currency, target, bucket.day, rates)
            if (estimate == null) {
                unconverted += bucket.currency.currencyCode
                continue
            }
            converted++
            val existing = byAccount[bucket.accountId]
            byAccount[bucket.accountId] = AccountTotal(
                accountId = bucket.accountId,
                total = (existing?.total ?: BigDecimal.ZERO).add(estimate.amount),
                count = (existing?.count ?: 0) + bucket.count,
            )
        }
        return Merged(byAccount.values.toList(), converted, unconverted)
    }

    /**
     * Monthly trend totals plus the converted foreign buckets, re-bucketed
     * into the month of each movement's own local day. Result sorted by month.
     */
    fun mergeMonthlyTotals(
        base: List<MonthlyTotal>,
        foreign: List<ForeignMonthlyDayTotal>,
        target: Currency,
        rates: RateTable,
    ): Merged<List<MonthlyTotal>> {
        val byMonth = base.associateBy { it.month }.toMutableMap()
        var converted = 0
        val unconverted = mutableSetOf<String>()
        for (bucket in foreign) {
            val expense =
                CurrencyConverter.convertOn(bucket.expense, bucket.currency, target, bucket.day, rates)
            val income =
                CurrencyConverter.convertOn(bucket.income, bucket.currency, target, bucket.day, rates)
            if (expense == null || income == null) {
                unconverted += bucket.currency.currencyCode
                continue
            }
            converted++
            val month = YearMonth.from(bucket.day)
            val existing = byMonth[month]
            byMonth[month] = MonthlyTotal(
                month = month,
                expense = (existing?.expense ?: BigDecimal.ZERO).add(expense.amount),
                income = (existing?.income ?: BigDecimal.ZERO).add(income.amount),
            )
        }
        return Merged(byMonth.values.sortedBy { it.month }, converted, unconverted)
    }

    /**
     * The total balance with foreign stocks converted at the latest known
     * rate, plus a per-account countervalue for the breakdown rows. The
     * inclusion rule matches the balance query: non-archived accounts flagged
     * into the total; countervalues are computed for every non-archived
     * foreign account, included or not, because the breakdown lists them all.
     */
    data class ConvertedBalance(
        val total: BigDecimal,
        /** Countervalue per foreign account id (non-archived), included in total or not. */
        val countervalues: Map<Long, CurrencyConverter.Estimate>,
        /** Foreign accounts whose balance entered [total]. */
        val convertedCount: Int,
        val unconvertedCurrencies: Set<String>,
        /** Publication day of the stalest rate [total] leans on; null when exact. */
        val rateDay: LocalDate?,
    )

    fun convertTotalBalance(
        accounts: List<AccountWithBalance>,
        target: Currency,
        rates: RateTable,
    ): ConvertedBalance {
        val active = accounts.filter { !it.account.isArchived }
        var total = active
            .filter { it.account.isIncludedInTotal && it.account.currency == target }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.balance) }
        val countervalues = mutableMapOf<Long, CurrencyConverter.Estimate>()
        var converted = 0
        val unconverted = mutableSetOf<String>()
        var rateDay: LocalDate? = null
        for (item in active.filter { it.account.currency != target }) {
            val estimate =
                CurrencyConverter.convertAtLatest(item.balance, item.account.currency, target, rates)
            if (estimate == null) {
                if (item.account.isIncludedInTotal) {
                    unconverted += item.account.currency.currencyCode
                }
                continue
            }
            countervalues[item.account.id] = estimate
            if (item.account.isIncludedInTotal) {
                total = total.add(estimate.amount)
                converted++
                rateDay = listOfNotNull(rateDay, estimate.rateDay).minOrNull()
            }
        }
        return ConvertedBalance(
            total = total,
            countervalues = countervalues,
            convertedCount = converted,
            unconvertedCurrencies = unconverted,
            rateDay = rateDay,
        )
    }

    private fun PeriodTotals.plusConverted(
        other: PeriodTotals,
        convert: (BigDecimal) -> BigDecimal?,
    ): PeriodTotals = PeriodTotals(
        spend = spend.add(convert(other.spend) ?: BigDecimal.ZERO),
        income = income.add(convert(other.income) ?: BigDecimal.ZERO),
    )
}
