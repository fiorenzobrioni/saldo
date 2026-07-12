package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import java.math.BigDecimal
import java.util.Currency

/** Immutable UI state for the accounts list screen. */
data class AccountsUiState(
    val isLoading: Boolean = true,
    val active: List<AccountWithBalance> = emptyList(),
    val archived: List<AccountWithBalance> = emptyList(),
    /** Account whose quick-actions sheet is open, or null. */
    val selected: AccountWithBalance? = null,
    val dialog: AccountsDialog? = null,
) {
    val isEmpty: Boolean get() = !isLoading && active.isEmpty() && archived.isEmpty()
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

    /** The account has movements: deletion is refused, archiving is proposed. */
    data class ArchiveInstead(val account: Account, val movementCount: Int) : AccountsDialog
}

/** One-shot events consumed by the screen (snackbars). */
sealed interface AccountsEvent {

    /** Shown with an undo action that restores the account. */
    data class AccountArchived(val account: Account) : AccountsEvent

    data class BalanceAdjusted(val delta: BigDecimal, val currency: Currency) : AccountsEvent

    data object AccountDeleted : AccountsEvent

    /** A write failed: nothing changed, let the user retry. */
    data object WriteFailed : AccountsEvent
}
