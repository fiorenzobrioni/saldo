package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.model.BudgetLevel
import com.callbackdev.saldo.core.domain.model.BudgetProgress
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.money.MoneyMapper
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
 * Joins every budget in [currency] with the current month's statistics spend
 * (refunds netted, transfers/adjustments/excluded/pending never counted, like
 * the statistics screens; the dashboard's month card is a cash figure and can
 * legitimately differ). Pending recurring movements are money already
 * committed but not yet confirmed: they stay out of budget progress on
 * purpose and are accounted for by the safe-to-spend figure instead.
 *
 * The overall budget comes first, then category budgets by descending
 * fraction (the closest to its cap on top). The month window is resolved from
 * [clock] when the flow is created, matching the dashboard's lifetime.
 */
class ObserveBudgetProgressUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) {

    operator fun invoke(currency: Currency): Flow<List<BudgetProgress>> {
        val windows = DashboardWindows.around(LocalDate.now(clock), clock.zone)
        return combine(
            budgetRepository.observeBudgets(),
            transactionRepository.observeStatsSpendTotal(windows.monthStart, windows.monthEnd, currency),
            transactionRepository.observeCategorySpendTotals(windows.monthStart, windows.monthEnd, currency),
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
            progressOf(budget, category, signedSpend)
        }
        return progresses.filter { it.budget.isOverall } +
            progresses.filterNot { it.budget.isOverall }.sortedByDescending { it.fraction }
    }

    private fun progressOf(budget: Budget, category: Category?, signedSpend: BigDecimal): BudgetProgress {
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
        )
    }
}
