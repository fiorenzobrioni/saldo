package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.model.BudgetLevel
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * A budget threshold newly crossed this month, to be notified. [categoryName]
 * is null for the overall budget; [percent] is the rounded share of the limit
 * already spent (can exceed 100).
 */
data class BudgetAlert(
    val budget: Budget,
    val categoryName: String?,
    /** [BudgetLevel.WARNING] or [BudgetLevel.OVER], never UNDER. */
    val level: BudgetLevel,
    val spent: BigDecimal,
    val percent: Int,
)

/**
 * One-shot check of every budget against the current month's statistics
 * spend, deduplicated by the per-budget watermarks: each threshold fires at
 * most once per month (crossing 100% directly advances both, so a jump
 * straight past the limit produces a single OVER alert). The watermark
 * advances even if the caller ends up not posting (e.g. notification
 * permission denied), mirroring the pre-renewal reminders; and it never
 * rolls back when spending drops below a threshold again.
 *
 * Idempotent and cheap, so it is safe to invoke from both the daily worker
 * and the reactive watcher; each budget is evaluated in its own currency.
 */
class CheckBudgetThresholdsUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(): List<BudgetAlert> {
        val budgets = budgetRepository.getBudgets()
        if (budgets.isEmpty()) return emptyList()
        val today = LocalDate.now(clock)
        val month = YearMonth.from(today)
        val windows = DashboardWindows.around(today, clock.zone)
        return budgets.groupBy { it.currency }.flatMap { (currency, group) ->
            val totalSpend = if (group.any { it.isOverall }) {
                transactionRepository.getStatsSpendTotal(windows.monthStart, windows.monthEnd, currency)
            } else {
                BigDecimal.ZERO
            }
            val spendByCategory = if (group.any { !it.isOverall }) {
                transactionRepository
                    .getCategorySpendTotals(windows.monthStart, windows.monthEnd, currency)
                    .associate { it.categoryId to it.total }
            } else {
                emptyMap()
            }
            group.mapNotNull { budget ->
                val signedSpend =
                    if (budget.isOverall) totalSpend else spendByCategory[budget.categoryId] ?: BigDecimal.ZERO
                alertFor(budget, signedSpend, month)
            }
        }
    }

    private suspend fun alertFor(budget: Budget, signedSpend: BigDecimal, month: YearMonth): BudgetAlert? {
        val spent = signedSpend.negate().max(BigDecimal.ZERO)
        val spentMinor = MoneyMapper.toMinorUnits(spent, budget.currency)
        val limitMinor = MoneyMapper.toMinorUnits(budget.amount, budget.currency)
        val level = BudgetLevel.of(spentMinor, limitMinor)
        val alerted: Boolean = when {
            level == BudgetLevel.OVER && budget.lastNotified100Month.isBefore(month) -> {
                budgetRepository.markNotified100(budget.id, month)
                true
            }

            level == BudgetLevel.WARNING && budget.lastNotified80Month.isBefore(month) -> {
                budgetRepository.markNotified80(budget.id, month)
                true
            }

            else -> false
        }
        if (!alerted) return null
        return BudgetAlert(
            budget = budget,
            categoryName = budget.categoryId?.let { categoryRepository.getCategory(it)?.name },
            level = level,
            spent = spent,
            percent = if (limitMinor > 0) {
                (spentMinor * PERCENT / limitMinor).toInt()
            } else {
                PERCENT.toInt()
            },
        )
    }

    private fun YearMonth?.isBefore(month: YearMonth): Boolean = this == null || this < month

    private companion object {
        const val PERCENT = 100L
    }
}
