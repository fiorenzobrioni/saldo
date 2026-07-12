package com.callbackdev.saldo.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.AdjustBalanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val adjustBalance: AdjustBalanceUseCase,
) : ViewModel() {

    private val dialog = MutableStateFlow<AccountsDialog?>(null)
    private val selectedAccountId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.observeAccountsWithBalance(),
        dialog,
        selectedAccountId,
    ) { accounts, currentDialog, selectedId ->
        AccountsUiState(
            isLoading = false,
            active = accounts.filter { !it.account.isArchived },
            archived = accounts.filter { it.account.isArchived },
            selected = accounts.firstOrNull { it.account.id == selectedId },
            dialog = currentDialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AccountsUiState(),
    )

    private val _events = Channel<AccountsEvent>(Channel.BUFFERED)
    val events: Flow<AccountsEvent> = _events.receiveAsFlow()

    /** Opens (or closes, with null) the quick-actions sheet for an account. */
    fun onAccountSelected(accountId: Long?) {
        selectedAccountId.value = accountId
    }

    fun archive(account: Account) {
        closeModals()
        viewModelScope.launch {
            suspendRunCatching { accountRepository.upsert(account.copy(isArchived = true)) }
                .onSuccess { _events.send(AccountsEvent.AccountArchived(account)) }
                .onFailure { _events.send(AccountsEvent.WriteFailed) }
        }
    }

    fun unarchive(account: Account) {
        closeModals()
        viewModelScope.launch {
            suspendRunCatching { accountRepository.upsert(account.copy(isArchived = false)) }
                .onFailure { _events.send(AccountsEvent.WriteFailed) }
        }
    }

    /**
     * Deletion guard (domain rule): allowed only for accounts without
     * movements, otherwise archiving is proposed instead.
     */
    fun requestDelete(account: Account) {
        selectedAccountId.value = null
        viewModelScope.launch {
            val count = transactionRepository.countForAccount(account.id)
            dialog.value = if (count == 0) {
                AccountsDialog.ConfirmDelete(account)
            } else {
                AccountsDialog.ArchiveInstead(account, count)
            }
        }
    }

    fun confirmDelete() {
        val current = dialog.value as? AccountsDialog.ConfirmDelete ?: return
        dialog.value = null
        viewModelScope.launch {
            suspendRunCatching { accountRepository.delete(current.account) }
                .onSuccess { _events.send(AccountsEvent.AccountDeleted) }
                .onFailure { _events.send(AccountsEvent.WriteFailed) }
        }
    }

    fun openAdjustBalance(item: AccountWithBalance) {
        selectedAccountId.value = null
        dialog.value = AccountsDialog.AdjustBalance(
            account = item.account,
            currentBalance = item.balance,
        )
    }

    fun onAdjustInputChanged(raw: String) {
        val current = dialog.value as? AccountsDialog.AdjustBalance ?: return
        val digits = MoneyMapper.fractionDigits(current.account.currency)
        val input = MoneyInput.sanitize(raw, digits)
        val delta = MoneyInput.parse(input)
            ?.setScale(digits, RoundingMode.HALF_UP)
            ?.subtract(current.currentBalance)
        dialog.value = current.copy(input = input, delta = delta)
    }

    fun confirmAdjustBalance() {
        val current = dialog.value as? AccountsDialog.AdjustBalance ?: return
        val realBalance = MoneyInput.parse(current.input) ?: return
        dialog.value = null
        viewModelScope.launch {
            suspendRunCatching { adjustBalance(current.account.id, realBalance) }
                .onSuccess { result ->
                    if (result is AdjustBalanceUseCase.Result.Adjusted) {
                        _events.send(
                            AccountsEvent.BalanceAdjusted(result.delta, current.account.currency),
                        )
                    }
                }
                .onFailure { _events.send(AccountsEvent.WriteFailed) }
        }
    }

    fun dismissDialog() {
        dialog.value = null
    }

    private fun closeModals() {
        selectedAccountId.value = null
        dialog.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
