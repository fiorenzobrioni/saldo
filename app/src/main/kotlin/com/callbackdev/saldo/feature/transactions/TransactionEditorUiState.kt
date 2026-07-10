package com.callbackdev.saldo.feature.transactions

import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.TransactionType
import java.time.LocalDate
import java.util.Currency

/** Immutable UI state of the transaction editor form. */
data class TransactionEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    /** True when editing a transfer or an adjustment: the type cannot change. */
    val isTypeLocked: Boolean = false,
    /** True when the type was chosen upfront (quick action): no in-form type selector. */
    val isTypePreset: Boolean = false,
    val amountInput: String = "",
    val toAmountInput: String = "",
    /** Accounts offered by the pickers: active ones plus any referenced archived one. */
    val accounts: List<AccountWithBalance> = emptyList(),
    val account: Account? = null,
    val toAccount: Account? = null,
    /** Categories usable for the current type (expense set when a refund). */
    val categories: List<Category> = emptyList(),
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.ofEpochDay(0),
    val description: String = "",
    val allTags: List<Tag> = emptyList(),
    val selectedTags: List<Tag> = emptyList(),
    val isExcludedFromStats: Boolean = false,
    val isRefund: Boolean = false,
    /** Set on a failed save attempt to surface field errors. */
    val showValidation: Boolean = false,
) {
    val currency: Currency? get() = account?.currency

    val isTransfer: Boolean get() = type == TransactionType.TRANSFER

    /** True when both transfer legs are chosen and their currencies differ. */
    val isCrossCurrency: Boolean
        get() = isTransfer && account != null && toAccount != null &&
            account.currency != toAccount.currency

    val hasCategorySection: Boolean
        get() = type == TransactionType.EXPENSE || type == TransactionType.INCOME

    val isAmountValid: Boolean
        get() = MoneyInput.parse(amountInput)?.let { amount ->
            if (type == TransactionType.ADJUSTMENT) amount.signum() != 0 else amount.signum() > 0
        } ?: false

    val isAccountValid: Boolean get() = account != null

    val isToAccountValid: Boolean
        get() = !isTransfer || (toAccount != null && toAccount.id != account?.id)

    val isToAmountValid: Boolean
        get() = !isCrossCurrency || (MoneyInput.parse(toAmountInput)?.signum() ?: 0) > 0

    val isCategoryValid: Boolean get() = !hasCategorySection || categoryId != null

    val isValid: Boolean
        get() = isAmountValid && isAccountValid && isToAccountValid &&
            isToAmountValid && isCategoryValid
}

/** One-shot events consumed by the editor screen. */
sealed interface TransactionEditorEvent {
    data object Saved : TransactionEditorEvent

    data object Deleted : TransactionEditorEvent

    /** The transaction to edit no longer exists: leave the screen. */
    data object TransactionMissing : TransactionEditorEvent

    /** A write failed: stay on the screen and let the user retry. */
    data object WriteFailed : TransactionEditorEvent
}
