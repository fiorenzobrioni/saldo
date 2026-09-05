package com.callbackdev.saldo.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.common.time.midnightTicker
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.rates.ConversionState
import com.callbackdev.saldo.core.domain.rates.ConvertedAggregates
import com.callbackdev.saldo.core.domain.rates.RateTable
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.usecase.ObserveConversionStateUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveDueStatementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveLoanProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.SettleCreditCardStatementUseCase
import com.callbackdev.saldo.core.domain.usecase.StatementSettlement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/**
 * The accounts list: grouped, reorderable, with the statement call to action
 * of the confirm-mode cards. Every other account action (adjust balance,
 * archive, delete) lives in the account detail (Fase 39, F1), which a tap on a
 * row opens.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val observeDueStatements: ObserveDueStatementsUseCase,
    private val observeLoanProgress: ObserveLoanProgressUseCase,
    private val settleStatement: SettleCreditCardStatementUseCase,
    userPreferences: UserPreferencesRepository,
    observeConversionState: ObserveConversionStateUseCase,
    private val clock: Clock,
) : ViewModel() {

    /** Accounts plus what the countervalues need: primary currency and rates. */
    private data class AccountsInputs(
        val accounts: List<AccountWithBalance>,
        val primary: Currency,
        val conversion: ConversionState,
    )

    // Re-anchors "today" at local midnight so the per-account "as of today"
    // line stays correct while the screen is open.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val accountsWithBalance = midnightTicker(clock).flatMapLatest { today ->
        accountRepository.observeAccountsWithBalanceAsOfToday(today.plusDays(1).toEpochDay())
    }

    // Collapsed upstream so the main combine stays within the typed arity.
    private val accountsInputs = combine(
        accountsWithBalance,
        userPreferences.primaryCurrencyOverride,
        observeConversionState(),
    ) { accounts, override, conversion ->
        AccountsInputs(accounts, primaryCurrency(accounts, override), conversion)
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        accountsInputs,
        observeDueStatements(),
        observeLoanProgress(),
    ) { inputs, due, loans ->
        val accounts = inputs.accounts
        val rates = if (inputs.conversion.active) inputs.conversion.rates else RateTable.EMPTY
        // Same fold as the dashboard hero (ADR 40): per-account countervalues
        // plus the day of the stalest rate, for the screen-level notice.
        val balance = ConvertedAggregates.convertTotalBalance(accounts, inputs.primary, rates)
        AccountsUiState(
            isLoading = false,
            activeGroups = buildAccountTypeGroups(
                items = accounts.filter { !it.account.isArchived },
                primary = inputs.primary,
                rates = rates,
            ),
            archived = accounts.filter { it.account.isArchived }.sortedByTypeThenName(),
            // Oldest due statement per card: settlement always pays the oldest
            // cycle first, so the CTA must show that cycle's amount, not the
            // newest one's (they differ only after a multi-cycle catch-up).
            dueStatements = due.groupBy { it.accountId }
                .mapValues { (_, statements) -> statements.first() },
            loanProgressById = loans,
            countervalues = balance.countervalues,
            primaryCurrency = inputs.primary,
            ratesDay = balance.countervalues.values.mapNotNull { it.rateDay }.minOrNull(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AccountsUiState(),
    )

    private val _events = Channel<AccountsEvent>(Channel.BUFFERED)
    val events: Flow<AccountsEvent> = _events.receiveAsFlow()

    /**
     * Persists a manual reorder of the active accounts. [orderedActiveIds] is the
     * full active list in its new display order (grouped by type); the repository
     * rewrites each account's position within its own type.
     */
    fun persistOrder(orderedActiveIds: List<Long>) {
        val byId = uiState.value.activeGroups
            .flatMap { it.accounts }
            .associateBy { it.account.id }
        val ordered = orderedActiveIds.mapNotNull { byId[it]?.account }
        if (ordered.isEmpty()) return
        viewModelScope.launch {
            suspendRunCatching { accountRepository.reorder(ordered) }
                .onFailure { _events.send(AccountsEvent.WriteFailed) }
        }
    }

    /** Pays the oldest due statement of a credit card (confirm-mode action). */
    fun settleStatement(accountId: Long) {
        viewModelScope.launch {
            suspendRunCatching { settleStatement.invoke(accountId, LocalDate.now(clock)) }
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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
