package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.SpendDayTotal
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.localDate
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.rates.ConversionState
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.core.domain.rates.RateTable
import com.callbackdev.saldo.core.domain.recurrence.UpcomingChargesCalculator
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    /**
     * True when any leg includes amounts converted from other currencies
     * (ADR 40): the figure is an estimate and the UI declares it.
     */
    val includesEstimates: Boolean = false,
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
 * Accounts excluded from the budget (`isIncludedInBudget = 0`) are left out of
 * every leg: their spend is already dropped by [observeStatsSpendTotal], and
 * here their pending expenses and upcoming recurring charges are filtered out
 * too, so an excluded account never affects the figure.
 *
 * [SafeToSpend.perDay] divides the remainder over the days left (today
 * included), floored to the currency scale so it never suggests more than is
 * actually there.
 */
class ObserveSafeToSpendUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val accountRepository: AccountRepository,
    private val clock: Clock,
) {

    operator fun invoke(
        currency: Currency,
        conversion: ConversionState = ConversionState.INACTIVE,
    ): Flow<SafeToSpend?> {
        val today = LocalDate.now(clock)
        val windows = DashboardWindows.around(today, clock.zone)
        val rates = if (conversion.active) conversion.rates else RateTable.EMPTY
        // The spend leg mirrors the budget card: single-currency total when
        // conversion is off, per-(currency, day) rows converted at the rate
        // of their own day when it is on (ADR 40).
        val spendLeg: Flow<Pair<BigDecimal, Boolean>> = if (conversion.active) {
            transactionRepository.observeSpendByCurrencyDay(windows.monthStart, windows.monthEnd)
                .map { rows -> spendInto(currency, rows, rates) }
        } else {
            transactionRepository
                .observeStatsSpendTotal(windows.monthStart, windows.monthEnd, currency)
                .map { it to false }
        }
        return combine(
            budgetRepository.observeBudgets(),
            spendLeg,
            transactionRepository.observePendingTransactions(),
            recurringRuleRepository.observeRules(),
            accountRepository.observeAccountsWithBalance(),
        ) { budgets, (totalSpend, spendConverted), pending, rules, accounts ->
            val overall = budgets.firstOrNull { it.isOverall && it.currency == currency }
                ?: return@combine null
            val budgetExcludedAccountIds = accounts
                .filterNot { it.account.isIncludedInBudget }
                .map { it.account.id }
                .toSet()
            val spent = totalSpend.negate().max(BigDecimal.ZERO)
            val (pendingCommitted, pendingConverted) =
                pendingCommitted(pending, windows, currency, budgetExcludedAccountIds, rates)
            val budgetedRules = rules.filterNot { it.accountId in budgetExcludedAccountIds }
            val foreignRules = budgetedRules.any { it.currency != currency }
            val upcoming = UpcomingChargesCalculator
                .remainingExpenseChargesInMonth(budgetedRules, today, currency, rates)
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
                includesEstimates = spendConverted || pendingConverted ||
                    (conversion.active && foreignRules),
            )
        }
    }

    /** Same fold the budget progress uses: exact for [currency], estimated for the rest. */
    private fun spendInto(
        currency: Currency,
        rows: List<SpendDayTotal>,
        rates: RateTable,
    ): Pair<BigDecimal, Boolean> {
        var total = BigDecimal.ZERO
        var converted = false
        rows.forEach { row ->
            if (row.currency == currency) {
                total = total.add(row.total)
            } else {
                CurrencyConverter.convertOn(row.total, row.currency, currency, row.day, rates)
                    ?.let {
                        total = total.add(it.amount)
                        converted = true
                    }
            }
        }
        return total to converted
    }

    /**
     * The month's pending expenses as a positive magnitude, excluding those
     * charged to accounts left out of the budget. Foreign ones enter at the
     * rate of their own day (ADR 40) or stay out without rates.
     */
    private fun pendingCommitted(
        pending: List<Transaction>,
        windows: DashboardWindows,
        currency: Currency,
        budgetExcludedAccountIds: Set<Long>,
        rates: RateTable,
    ): Pair<BigDecimal, Boolean> {
        var converted = false
        val committed = pending
            .filter { transaction ->
                transaction.type == TransactionType.EXPENSE &&
                    transaction.accountId !in budgetExcludedAccountIds &&
                    transaction.timestamp >= windows.monthStart &&
                    transaction.timestamp < windows.monthEnd
            }
            // Signed convention: pending expenses are negative, like confirmed ones.
            .fold(BigDecimal.ZERO) { acc, transaction ->
                if (transaction.currency == currency) {
                    acc.subtract(transaction.amount)
                } else {
                    val estimate = CurrencyConverter
                        .convertOn(transaction.amount, transaction.currency, currency, transaction.localDate, rates)
                        ?: return@fold acc
                    converted = true
                    acc.subtract(estimate.amount)
                }
            }
            .max(BigDecimal.ZERO)
        return committed to converted
    }

    private fun perDay(remaining: BigDecimal, daysLeft: Int, currency: Currency): BigDecimal? {
        if (remaining.signum() <= 0 || daysLeft <= 0) return null
        return remaining.divide(
            BigDecimal(daysLeft),
            MoneyMapper.fractionDigits(currency),
            RoundingMode.FLOOR,
        )
    }
}
