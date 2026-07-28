package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.LoanProgress
import com.callbackdev.saldo.core.domain.usecase.DueStatement
import java.math.BigDecimal
import java.util.Currency

/**
 * One account-type section of the accounts list: the [type] and its accounts,
 * already ordered (manual position, then name). [subtotal] sums the group's
 * balances for the section header, in [currency]; both are null when the group
 * mixes currencies (a single figure would be meaningless), so the header then
 * shows the type label alone. [subtotalAsOfToday] is the same sum counting each
 * account's balance as of today, surfaced under the subtotal only when it
 * diverges (future-dated movements in the group), mirroring the per-account line.
 */
data class AccountTypeGroup(
    val type: AccountType,
    val accounts: List<AccountWithBalance>,
    val subtotal: BigDecimal? = null,
    val currency: Currency? = null,
    val subtotalAsOfToday: BigDecimal? = null,
)

/** Immutable UI state for the accounts list screen. */
data class AccountsUiState(
    val isLoading: Boolean = true,
    /** Active accounts grouped by type (CHECKING first) and sorted by name within each type. */
    val activeGroups: List<AccountTypeGroup> = emptyList(),
    /** Archived accounts, sorted by type then name, shown in a single collapsed card. */
    val archived: List<AccountWithBalance> = emptyList(),
    /** Account whose quick-actions sheet is open, or null. */
    val selected: AccountWithBalance? = null,
    val dialog: AccountsDialog? = null,
    /** Credit card statements waiting to be paid, keyed by account id. */
    val dueStatements: Map<Long, DueStatement> = emptyMap(),
    /** Repayment state of the active loan accounts, keyed by account id. */
    val loanProgressById: Map<Long, LoanProgress> = emptyMap(),
) {
    val isEmpty: Boolean get() = !isLoading && activeGroups.isEmpty() && archived.isEmpty()

    /** The statement due for [accountId], or null. */
    fun dueStatement(accountId: Long): DueStatement? = dueStatements[accountId]

    /** The loan repayment state for [accountId], or null for non-loans. */
    fun loanProgress(accountId: Long): LoanProgress? = loanProgressById[accountId]
}

/** Modal flows on top of the accounts list. */
sealed interface AccountsDialog {

    /**
     * Balance adjustment: the user types the real balance, [delta] is the
     * resulting adjustment (null while the input does not parse).
     */
    data class AdjustBalance(
        val account: Account,
        val currentBalance: BigDecimal,
        val input: String = "",
        val delta: BigDecimal? = null,
    ) : AccountsDialog

    /** The account has no movements: deletion is allowed after confirmation. */
    data class ConfirmDelete(val account: Account) : AccountsDialog

    /**
     * The account has movements or recurring rules: deletion is refused,
     * archiving is proposed.
     */
    data class ArchiveInstead(
        val account: Account,
        val movementCount: Int,
        val ruleCount: Int = 0,
    ) : AccountsDialog
}

/** One-shot events consumed by the screen (snackbars). */
sealed interface AccountsEvent {

    /** Shown with an undo action that restores the account. */
    data class AccountArchived(val account: Account) : AccountsEvent

    data class BalanceAdjusted(val delta: BigDecimal, val currency: Currency) : AccountsEvent

    /** A credit card statement was paid: the transfer covered [amount]. */
    data class StatementSettled(val amount: BigDecimal, val currency: Currency) : AccountsEvent

    data object AccountDeleted : AccountsEvent

    /** A write failed: nothing changed, let the user retry. */
    data object WriteFailed : AccountsEvent
}
