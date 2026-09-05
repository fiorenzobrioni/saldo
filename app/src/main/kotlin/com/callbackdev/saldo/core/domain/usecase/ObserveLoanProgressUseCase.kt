package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.LoanProgress
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Joins every active loan account with its repayment state (PLANNING ADR 33),
 * keyed by account id for the accounts list. The residual debt is the
 * account's computed balance with the sign flipped; the installments are the
 * fixed-amount recurring transfers landing in the account in its currency,
 * found by destination because the rule-account link is not a column. Rules in
 * another currency are excluded, as for the savings goals: a cross-currency
 * transfer has its received amount only at confirmation, so it cannot be
 * projected. The remaining-installments estimate and the payoff date are
 * derived domain math (integer ceiling, monthly equivalents), which is why
 * this is a use case and not plain repository access (ADR 12). No interest,
 * no amortization plan: the numbers only read what the user recorded.
 */
class ObserveLoanProgressUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val clock: Clock,
) {

    operator fun invoke(): Flow<Map<Long, LoanProgress>> = combine(
        accountRepository.observeAccountsWithBalance(),
        recurringRuleRepository.observeRules(),
    ) { accounts, rules ->
        val today = LocalDate.now(clock)
        accounts
            .filter { it.account.type == AccountType.LOAN && !it.account.isArchived }
            .associate { it.account.id to progressOf(it, rules, today) }
    }

    private fun progressOf(
        item: AccountWithBalance,
        rules: List<RecurringRule>,
        today: LocalDate,
    ): LoanProgress {
        val account = item.account
        val currency = account.currency
        // Positive magnitudes in minor units: the debt still owed and the one
        // declared at creation. Both floor at zero - a repayment beyond the
        // debt (or a non-negative initial balance from old data) must read as
        // paid off, never as negative debt.
        val residualMinor = (-MoneyMapper.toMinorUnits(item.balance, currency)).coerceAtLeast(0)
        val initialMinor = (-MoneyMapper.toMinorUnits(account.initialBalance, currency)).coerceAtLeast(0)
        val isPaidOff = residualMinor == 0L

        val linked = rules.filter { RecurrenceCalculator.isPlannedTransferInto(it, account.id, currency, today) }
        val plannedMinor = RecurrenceCalculator.plannedMonthlyTransfersMinor(rules, account.id, currency, today)
        // The earliest upcoming charge among the linked rules; a rule whose
        // schedule is over contributes nothing.
        val next = linked
            .mapNotNull { rule ->
                RecurrenceCalculator.nextOccurrence(rule, today)?.let { date -> date to rule }
            }
            .minByOrNull { (date, _) -> date }
        val remaining = if (!isPaidOff && plannedMinor > 0) ceilDiv(residualMinor, plannedMinor) else null

        return LoanProgress(
            account = account,
            residual = MoneyMapper.toAmount(residualMinor, currency),
            fraction = repaidFraction(initialMinor, residualMinor, isPaidOff),
            isPaidOff = isPaidOff,
            plannedMonthly = if (linked.isEmpty()) null else MoneyMapper.toAmount(plannedMinor, currency),
            nextInstallmentAmount = next?.second?.amount,
            nextInstallmentDate = next?.first,
            remainingInstallments = remaining,
            // Anchored on the next installment, not on today: the last of the
            // remaining charges lands remaining - 1 months after the next one,
            // and counting from today could name a month too late.
            projectedPayoffDate = remaining?.let { (next?.first ?: today).plusMonths(it - 1) },
        )
    }

    /**
     * The repaid share of the initial debt, clamped to 0..1: an adjustment can
     * raise the debt above the declared one (fraction floors at zero) and a
     * degenerate initial balance of zero reads as fully repaid only when the
     * account actually is.
     */
    private fun repaidFraction(initialMinor: Long, residualMinor: Long, isPaidOff: Boolean): Float = when {
        isPaidOff -> 1f
        initialMinor <= 0L -> 0f
        else -> (1f - residualMinor.toFloat() / initialMinor.toFloat()).coerceIn(0f, 1f)
    }

    /** Integer ceiling of [value] / [divisor], both non-negative, [divisor] > 0. */
    private fun ceilDiv(value: Long, divisor: Long): Long = (value + divisor - 1) / divisor
}
