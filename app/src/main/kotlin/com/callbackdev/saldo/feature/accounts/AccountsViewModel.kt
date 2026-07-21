package com.callbackdev.saldo.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.common.time.midnightTicker
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.AdjustBalanceUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveDueStatementsUseCase
import com.callbackdev.saldo.core.domain.usecase.SettleCreditCardStatementUseCase
import com.callbackdev.saldo.core.domain.usecase.StatementSettlement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions") // One handler per account action, including the statement settlement.
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val adjustBalance: AdjustBalanceUseCase,
    private val observeDueStatements: ObserveDueStatementsUseCase,
    private val settleStatement: SettleCreditCardStatementUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val dialog = MutableStateFlow<AccountsDialog?>(null)
    private val selectedAccountId = MutableStateFlow<Long?>(null)

    // Re-anchors "today" at local midnight so the per-account "as of today"
    // line stays correct while the screen is open.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val accountsWithBalance = midnightTicker(clock).flatMapLatest { today ->
        accountRepository.observeAccountsWithBalanceAsOfToday(today.plusDays(1).toEpochDay())
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        accountsWithBalance,
        dialog,
        selectedAccountId,
        observeDueStatements(),
    ) { accounts, currentDialog, selectedId, due ->
        AccountsUiState(
            isLoading = false,
            activeGroups = buildAccountTypeGroups(accounts.filter { !it.account.isArchived }),
            archived = accounts.filter { it.account.isArchived }.sortedByTypeThenName(),
            selected = accounts.firstOrNull { it.account.id == selectedId },
            dialog = currentDialog,
            // Oldest due statement per card: settlement always pays the oldest
            // cycle first, so the CTA must show that cycle's amount, not the
            // newest one's (they differ only after a multi-cycle catch-up).
            dueStatements = due.groupBy { it.accountId }
                .mapValues { (_, statements) -> statements.first() },
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
     * movements and not referenced by any recurring rule (an enforced
     * foreign key would reject the delete anyway); otherwise archiving is
     * proposed instead, with the actual reason spelled out.
     */
    fun requestDelete(account: Account) {
        selectedAccountId.value = null
        viewModelScope.launch {
            val movementCount = transactionRepository.countForAccount(account.id)
            val ruleCount = recurringRuleRepository.countForAccount(account.id)
            dialog.value = if (movementCount == 0 && ruleCount == 0) {
                AccountsDialog.ConfirmDelete(account)
            } else {
                AccountsDialog.ArchiveInstead(account, movementCount, ruleCount)
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

    /** Pays the oldest due statement of a credit card (confirm-mode action). */
    fun settleStatement(accountId: Long) {
        selectedAccountId.value = null
        viewModelScope.launch {
            suspendRunCatching { settleStatement.invoke(accountId) }
                .onSuccess { result ->
                    if (result is StatementSettlement.Settled && result.amount.signum() > 0) {
                        val account = accountRepository.getAccount(accountId) ?: return@onSuccess
                        _events.send(
                            AccountsEvent.StatementSettled(result.amount, account.currency),
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
