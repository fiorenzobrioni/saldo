package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.SavingsGoal
import com.callbackdev.saldo.core.domain.model.SavingsGoalProgress
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Joins every savings goal with the state of its linked account and the
 * recurring transfers feeding it. "Saved" is the account's computed balance
 * (PLANNING ADR 3), so a goal never holds money of its own: it reads a real
 * account. The suggested monthly contribution and the projected completion date
 * are pure domain math (short months, integer rounding), which is why this is a
 * use case and not plain repository access (ADR 12).
 *
 * "Planned" savings for a goal are the monthly-equivalent of the same-currency
 * recurring transfers landing in the account (the honest seed already surfaced
 * as "Risparmio pianificato" in the recurrences hub). Cross-currency transfers
 * are excluded: their received amount is entered at confirmation, so it cannot
 * be projected.
 *
 * Goals are returned sorted alphabetically by name (the aggregate saved/target
 * total shown above the list is order-independent).
 */
class ObserveSavingsGoalsProgressUseCase @Inject constructor(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val accountRepository: AccountRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val clock: Clock,
) {

    operator fun invoke(): Flow<List<SavingsGoalProgress>> = combine(
        savingsGoalRepository.observeGoals(),
        accountRepository.observeAccountsWithBalance(),
        recurringRuleRepository.observeRules(),
    ) { goals, accounts, rules ->
        val today = LocalDate.now(clock)
        val balanceByAccount = accounts.associate { it.account.id to it }
        goals
            .map { goal -> progressOf(goal, balanceByAccount[goal.accountId], rules, today) }
            .sortedWith(goalOrder)
    }

    private fun progressOf(
        goal: SavingsGoal,
        linked: AccountWithBalance?,
        rules: List<RecurringRule>,
        today: LocalDate,
    ): SavingsGoalProgress {
        val saved = linked?.balance ?: BigDecimal.ZERO
        val savedMinor = MoneyMapper.toMinorUnits(saved, goal.currency)
        val targetMinor = MoneyMapper.toMinorUnits(goal.targetAmount, goal.currency)
        val remainingMinor = (targetMinor - savedMinor).coerceAtLeast(0)
        val remaining = MoneyMapper.toAmount(remainingMinor, goal.currency)
        val isReached = savedMinor >= targetMinor

        val plannedMinor = plannedMonthlyMinor(goal, rules, today)
        val suggestedMinor = suggestedMonthlyMinor(goal, remainingMinor, isReached, today)

        return SavingsGoalProgress(
            goal = goal,
            account = linked?.account,
            saved = saved,
            remaining = remaining,
            fraction = if (targetMinor > 0) savedMinor.toFloat() / targetMinor.toFloat() else 0f,
            isReached = isReached,
            suggestedMonthly = suggestedMinor?.let { MoneyMapper.toAmount(it, goal.currency) },
            plannedMonthly = MoneyMapper.toAmount(plannedMinor, goal.currency),
            projectedDate = projectedDate(remainingMinor, plannedMinor, isReached, today),
            onTrack = if (suggestedMinor != null && plannedMinor > 0) plannedMinor >= suggestedMinor else null,
        )
    }

    /**
     * The monthly-equivalent of the active same-currency recurring transfers
     * landing in the goal's account, in minor units. Cross-currency and
     * variable-amount rules have no fixed received amount and are skipped.
     */
    private fun plannedMonthlyMinor(goal: SavingsGoal, rules: List<RecurringRule>, today: LocalDate): Long =
        rules
            .filter { rule ->
                rule.type == TransactionType.TRANSFER &&
                    rule.transferAccountId == goal.accountId &&
                    rule.amount != null &&
                    rule.currency == goal.currency &&
                    (rule.endDate == null || rule.endDate >= today)
            }
            .fold(0L) { acc, rule ->
                val monthly = RecurrenceCalculator.monthlyEquivalent(rule) ?: BigDecimal.ZERO
                acc + MoneyMapper.toMinorUnits(monthly, goal.currency)
            }

    /**
     * Minor units to set aside each month to reach the target by the goal date:
     * remaining divided over the whole calendar months still available, rounded
     * up (integer ceiling, no float). Null without a date, once reached, or when
     * the target month is the current one or already past.
     */
    private fun suggestedMonthlyMinor(
        goal: SavingsGoal,
        remainingMinor: Long,
        isReached: Boolean,
        today: LocalDate,
    ): Long? {
        val targetDate = goal.targetDate
        if (targetDate == null || isReached) return null
        val monthsRemaining = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(targetDate))
        return if (monthsRemaining < 1) null else ceilDiv(remainingMinor, monthsRemaining)
    }

    /**
     * When the goal is reached at the current planned rate: today plus the whole
     * months still needed. Null when nothing is planned or the goal is reached.
     */
    private fun projectedDate(
        remainingMinor: Long,
        plannedMinor: Long,
        isReached: Boolean,
        today: LocalDate,
    ): LocalDate? {
        if (isReached || plannedMinor <= 0 || remainingMinor <= 0) return null
        return today.plusMonths(ceilDiv(remainingMinor, plannedMinor))
    }

    /** Integer ceiling of [value] / [divisor], both non-negative, [divisor] > 0. */
    private fun ceilDiv(value: Long, divisor: Long): Long = (value + divisor - 1) / divisor

    private companion object {
        /** Goals listed alphabetically by name, case-insensitive, stable on id. */
        val goalOrder: Comparator<SavingsGoalProgress> =
            compareBy<SavingsGoalProgress, String>(String.CASE_INSENSITIVE_ORDER) { it.goal.name }
                .thenBy { it.goal.id }
    }
}
