package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.recurrence.UpcomingChargesCalculator
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/**
 * What is still safely spendable this month. All magnitudes are positive;
 * [remaining] alone can go negative (the month's plan is already blown).
 */
data class SafeToSpend(
    /** budget - spent - pendingCommitted - upcomingRecurring; negative when over. */
    val remaining: BigDecimal,
    /** [remaining] spread over [daysLeft], floored; null when nothing remains. */
    val perDay: BigDecimal?,
    val budget: BigDecimal,
    val spent: BigDecimal,
    /** Pending recurring expenses of the month: committed but not confirmed yet. */
    val pendingCommitted: BigDecimal,
    /** Fixed-amount recurring expense charges still due before month end. */
    val upcomingRecurring: BigDecimal,
    /** Days of the month left, today included. */
    val daysLeft: Int,
)

/**
 * The proactive dashboard figure: overall monthly budget minus what is
 * already spent (statistics spend, coherent with the budget card), minus the
 * month's pending recurring expenses (generated but unconfirmed: excluded
 * from spend and already behind the generation floor, so this is the only
 * place they can be accounted), minus the fixed recurring expense charges
 * still due before month end. Null when no overall budget exists in
 * [currency]: the figure is meaningless without a plan.
 *
 * [SafeToSpend.perDay] divides the remainder over the days left (today
 * included), floored to the currency scale so it never suggests more than is
 * actually there.
 */
class ObserveSafeToSpendUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val clock: Clock,
) {

    operator fun invoke(currency: Currency): Flow<SafeToSpend?> {
        val today = LocalDate.now(clock)
        val windows = DashboardWindows.around(today, clock.zone)
        return combine(
            budgetRepository.observeBudgets(),
            transactionRepository.observeStatsSpendTotal(windows.monthStart, windows.monthEnd, currency),
            transactionRepository.observePendingTransactions(),
            recurringRuleRepository.observeRules(),
        ) { budgets, totalSpend, pending, rules ->
            val overall = budgets.firstOrNull { it.isOverall && it.currency == currency }
                ?: return@combine null
            val spent = totalSpend.negate().max(BigDecimal.ZERO)
            val pendingCommitted = pendingCommitted(pending, windows, currency)
            val upcoming = UpcomingChargesCalculator.remainingExpenseChargesInMonth(rules, today, currency)
            val remaining = overall.amount
                .subtract(spent)
                .subtract(pendingCommitted)
                .subtract(upcoming)
            val daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1
            SafeToSpend(
                remaining = remaining,
                perDay = perDay(remaining, daysLeft, currency),
                budget = overall.amount,
                spent = spent,
                pendingCommitted = pendingCommitted,
                upcomingRecurring = upcoming,
                daysLeft = daysLeft,
            )
        }
    }

    /** The month's pending expenses in [currency], as a positive magnitude. */
    private fun pendingCommitted(
        pending: List<Transaction>,
        windows: DashboardWindows,
        currency: Currency,
    ): BigDecimal = pending
        .filter { transaction ->
            transaction.type == TransactionType.EXPENSE &&
                transaction.currency == currency &&
                transaction.timestamp >= windows.monthStart &&
                transaction.timestamp < windows.monthEnd
        }
        // Signed convention: pending expenses are negative, like confirmed ones.
        .fold(BigDecimal.ZERO) { acc, transaction -> acc.subtract(transaction.amount) }
        .max(BigDecimal.ZERO)

    private fun perDay(remaining: BigDecimal, daysLeft: Int, currency: Currency): BigDecimal? {
        if (remaining.signum() <= 0 || daysLeft <= 0) return null
        return remaining.divide(
            BigDecimal(daysLeft),
            MoneyMapper.fractionDigits(currency),
            RoundingMode.FLOOR,
        )
    }
}
