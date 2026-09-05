package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.DailyBalance
import com.callbackdev.saldo.core.domain.model.LoanProgress
import com.callbackdev.saldo.core.domain.model.SavingsGoalProgress
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.core.domain.usecase.DueStatement
import com.callbackdev.saldo.feature.transactions.FilteredTotal
import com.callbackdev.saldo.feature.transactions.TransactionDayGroup
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

/**
 * Immutable UI state of the account detail (Fase 39, F1): the account with its
 * balance, its own 30-day history, the type-specific state and one month of
 * its movements.
 */
data class AccountDetailUiState(
    val isLoading: Boolean = true,
    /** True when the account no longer exists: the screen leaves. */
    val isMissing: Boolean = false,
    /** The account with its balance and, when it diverges, the balance as of today. */
    val item: AccountWithBalance? = null,
    /** Estimated countervalue in [primaryCurrency] for a foreign account (ADR 40). */
    val countervalue: CurrencyConverter.Estimate? = null,
    val primaryCurrency: Currency? = null,
    /** End-of-day balances of the last 30 days, today included, oldest first. */
    val history: List<DailyBalance> = emptyList(),
    /** The oldest statement due on a confirm-mode credit card, or null. */
    val dueStatement: DueStatement? = null,
    /** Repayment state of a loan account, or null. */
    val loanProgress: LoanProgress? = null,
    /** The goal laid over a savings account, or null. */
    val savingsGoal: SavingsGoalProgress? = null,
    val today: LocalDate = LocalDate.ofEpochDay(0),
    /** The month whose movements are listed. */
    val month: YearMonth = YearMonth.of(EPOCH_YEAR, 1),
    /** Whether an earlier month holds movements to step back to. */
    val canGoToPreviousMonth: Boolean = false,
    /** Whether a later month (the current one, or a future-dated movement) exists. */
    val canGoToNextMonth: Boolean = false,
    /** The month's movements grouped by day, newest first. */
    val days: List<TransactionDayGroup> = emptyList(),
    /** Per-currency expense and income totals of the month. */
    val monthTotals: List<FilteredTotal> = emptyList(),
    val monthMovementCount: Int = 0,
    val dialog: AccountsDialog? = null,
) {
    /** True when the type-specific block has something to show. */
    val hasTypeExtras: Boolean
        get() {
            val account = item?.account ?: return false
            if (account.isArchived) return false
            val card = account.creditCard
            val cardExtras = card != null &&
                ((card.creditLimit?.signum() ?: 0) > 0 || dueStatement != null)
            val loanExtras = loanProgress?.let { it.isPaidOff || it.hasLinkedRule } ?: false
            return cardExtras || loanExtras || savingsGoal != null
        }

    private companion object {
        const val EPOCH_YEAR = 1970
    }
}
