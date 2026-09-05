package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.model.BudgetLevel
import com.callbackdev.saldo.core.domain.model.BudgetProgress
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategorySpendDayTotal
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.SpendDayTotal
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.rates.ConversionState
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/**
 * Joins every budget with the current month's statistics spend (refunds
 * netted, transfers/adjustments/excluded/pending never counted, like the
 * statistics screens; the dashboard's month card is a cash figure and can
 * legitimately differ). Pending recurring movements are money already
 * committed but not yet confirmed: they stay out of budget progress on
 * purpose and are accounted for by the safe-to-spend figure instead.
 *
 * With conversion off, only the budgets in [Currency] are shown and only that
 * currency's spend counts, the pre-conversion behavior. With conversion on
 * (ADR 40) every budget is shown, whatever its currency (closing review
 * limit 2: a budget no longer vanishes when the primary currency changes),
 * and each budget's spend converts every movement into the budget's own
 * currency at the rate of the movement's day; what cannot be converted stays
 * out, as it always did.
 *
 * The overall budget comes first, then category budgets by descending
 * fraction (the closest to its cap on top), breaking ties by category name so
 * the list stays alphabetical when nothing has been spent yet. The month window is resolved from
 * [clock] when the flow is created, matching the dashboard's lifetime.
 */
class ObserveBudgetProgressUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) {

    operator fun invoke(
        currency: Currency,
        conversion: ConversionState = ConversionState.INACTIVE,
    ): Flow<List<BudgetProgress>> {
        val windows = DashboardWindows.around(LocalDate.now(clock), clock.zone)
        return if (conversion.active) {
            combine(
                budgetRepository.observeBudgets(),
                transactionRepository.observeSpendByCurrencyDay(windows.monthStart, windows.todayEnd),
                transactionRepository
                    .observeCategorySpendByCurrencyDay(windows.monthStart, windows.todayEnd),
                categoryRepository.observeCategories(),
            ) { budgets, spendRows, categoryRows, categories ->
                buildConvertedProgress(budgets, spendRows, categoryRows, categories, conversion)
            }
        } else {
            combine(
                budgetRepository.observeBudgets(),
                transactionRepository
                    .observeStatsSpendTotal(windows.monthStart, windows.todayEnd, currency),
                transactionRepository
                    .observeCategorySpendTotals(windows.monthStart, windows.todayEnd, currency),
                categoryRepository.observeCategories(),
            ) { budgets, totalSpend, categorySpends, categories ->
                buildProgress(
                    budgets = budgets.filter { it.currency == currency },
                    totalSpend = totalSpend,
                    categorySpends = categorySpends,
                    categories = categories,
                )
            }
        }
    }

    private fun buildProgress(
        budgets: List<Budget>,
        totalSpend: BigDecimal,
        categorySpends: List<CategoryTotal>,
        categories: List<Category>,
    ): List<BudgetProgress> {
        val categoryById = categories.associateBy { it.id }
        val spendByCategory = categorySpends.associate { it.categoryId to it.total }
        val progresses = budgets.mapNotNull { budget ->
            val category = budget.categoryId?.let { id ->
                // A vanishing category cascades its budget away; skip the
                // orphan if the two flows emit out of step for a moment.
                categoryById[id] ?: return@mapNotNull null
            }
            val signedSpend = if (budget.isOverall) {
                totalSpend
            } else {
                spendByCategory[budget.categoryId] ?: BigDecimal.ZERO
            }
            progressOf(budget, category, signedSpend, includesConverted = false)
        }
        return sorted(progresses)
    }

    /**
     * The conversion-aware join: one pass over the per-(currency, day) spend
     * rows per budget, each row entering in the budget's own currency.
     */
    private fun buildConvertedProgress(
        budgets: List<Budget>,
        spendRows: List<SpendDayTotal>,
        categoryRows: List<CategorySpendDayTotal>,
        categories: List<Category>,
        conversion: ConversionState,
    ): List<BudgetProgress> {
        val categoryById = categories.associateBy { it.id }
        val categoryRowsById = categoryRows.groupBy { it.categoryId }
        val progresses = budgets.mapNotNull { budget ->
            val category = budget.categoryId?.let { id ->
                categoryById[id] ?: return@mapNotNull null
            }
            val rows = if (budget.isOverall) {
                spendRows
            } else {
                categoryRowsById[budget.categoryId].orEmpty()
                    .map { SpendDayTotal(it.currency, it.day, it.total) }
            }
            val (signedSpend, converted) = spendInto(budget.currency, rows, conversion)
            progressOf(budget, category, signedSpend, includesConverted = converted)
        }
        return sorted(progresses)
    }

    /**
     * Folds the rows into [target]: same-currency rows enter exactly, the
     * others at the rate of their own day; rows with no usable rate stay out.
     * Returns the signed spend and whether any estimate entered it.
     */
    private fun spendInto(
        target: Currency,
        rows: List<SpendDayTotal>,
        conversion: ConversionState,
    ): Pair<BigDecimal, Boolean> {
        var total = BigDecimal.ZERO
        var converted = false
        rows.forEach { row ->
            if (row.currency == target) {
                total = total.add(row.total)
            } else {
                CurrencyConverter.convertOn(row.total, row.currency, target, row.day, conversion.rates)
                    ?.let {
                        total = total.add(it.amount)
                        converted = true
                    }
            }
        }
        return total to converted
    }

    /**
     * Category budgets: closest to the cap first. When fractions tie (every
     * budget sits at zero at the start of the month) fall back to the
     * category name, so the list reads alphabetically instead of by id.
     */
    private fun sorted(progresses: List<BudgetProgress>): List<BudgetProgress> {
        val categoryOrder = compareByDescending<BudgetProgress> { it.fraction }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.category?.name.orEmpty() }
            .thenBy { it.budget.id }
        return progresses.filter { it.budget.isOverall } +
            progresses.filterNot { it.budget.isOverall }.sortedWith(categoryOrder)
    }

    private fun progressOf(
        budget: Budget,
        category: Category?,
        signedSpend: BigDecimal,
        includesConverted: Boolean,
    ): BudgetProgress {
        // Signed convention: expenses negative, refunds positive. A month of
        // refunds only would go positive; budgets read that as zero spent.
        val spent = signedSpend.negate().max(BigDecimal.ZERO)
        val spentMinor = MoneyMapper.toMinorUnits(spent, budget.currency)
        val limitMinor = MoneyMapper.toMinorUnits(budget.amount, budget.currency)
        return BudgetProgress(
            budget = budget,
            category = category,
            spent = spent,
            fraction = if (limitMinor > 0) spentMinor.toFloat() / limitMinor.toFloat() else 0f,
            level = BudgetLevel.of(spentMinor, limitMinor),
            includesConvertedSpend = includesConverted,
        )
    }
}
