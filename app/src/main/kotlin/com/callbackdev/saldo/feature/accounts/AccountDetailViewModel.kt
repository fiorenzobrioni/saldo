package com.callbackdev.saldo.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.common.time.midnightTicker
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.DailyBalance
import com.callbackdev.saldo.core.domain.model.LoanProgress
import com.callbackdev.saldo.core.domain.model.SavingsGoalProgress
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.localDate
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.rates.ConversionState
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.core.domain.rates.RateTable
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.undo.UndoDeleteCoordinator
import com.callbackdev.saldo.core.domain.undo.UndoableDelete
import com.callbackdev.saldo.core.domain.usecase.AdjustBalanceUseCase
import com.callbackdev.saldo.core.domain.usecase.DueStatement
import com.callbackdev.saldo.core.domain.usecase.ObserveAccountBalanceHistoryUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveConversionStateUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveDueStatementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveLoanProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveSavingsGoalsProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.SettleCreditCardStatementUseCase
import com.callbackdev.saldo.core.domain.usecase.StatementSettlement
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import com.callbackdev.saldo.feature.transactions.buildDayGroups
import com.callbackdev.saldo.feature.transactions.countervalueIn
import com.callbackdev.saldo.feature.transactions.filteredTotals
import com.callbackdev.saldo.navigation.AccountDetailRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import javax.inject.Inject

/**
 * State holder of the account detail (Fase 39, F1). Everything is observed
 * from the database and only while the screen is open: the balance walk, the
 * type-specific figures and the account's movements, cut to the selected
 * month in memory (a personal account holds a few thousand rows at most, and
 * the ledger tab already loads them in full).
 *
 * The account actions (adjust balance, archive, delete with its guard,
 * statement settlement) moved here from the accounts list, which now only
 * lists and reorders: the detail is where an account is looked at, so it is
 * where it is acted on.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per figure family; one handler per action.
@HiltViewModel(assistedFactory = AccountDetailViewModel.Factory::class)
class AccountDetailViewModel @AssistedInject constructor(
    @Assisted private val route: AccountDetailRoute,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val tagRepository: TagRepository,
    categoryRepository: CategoryRepository,
    private val adjustBalance: AdjustBalanceUseCase,
    private val settleStatement: SettleCreditCardStatementUseCase,
    observeDueStatements: ObserveDueStatementsUseCase,
    observeLoanProgress: ObserveLoanProgressUseCase,
    observeSavingsGoalsProgress: ObserveSavingsGoalsProgressUseCase,
    observeAccountBalanceHistory: ObserveAccountBalanceHistoryUseCase,
    userPreferences: UserPreferencesRepository,
    observeConversionState: ObserveConversionStateUseCase,
    private val undoCoordinator: UndoDeleteCoordinator,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: AccountDetailRoute): AccountDetailViewModel
    }

    private val accountId: Long = route.accountId
    private val month = MutableStateFlow(YearMonth.now(clock))
    private val dialog = MutableStateFlow<AccountsDialog?>(null)

    /** Every account (for transfer counterparts) plus the day they were read on. */
    private data class AccountsToday(val today: LocalDate, val accounts: List<AccountWithBalance>)

    /** The sources that need no per-account re-subscription. */
    private data class Core(
        val accountsToday: AccountsToday,
        val categories: List<Category>,
        val transactions: List<Transaction>,
        val currencyOverride: Currency?,
        val conversion: ConversionState,
    )

    /** The type-specific figures, one per account type that has any. */
    private data class Extras(
        val dueStatements: List<DueStatement>,
        val loans: Map<Long, LoanProgress>,
        val goals: List<SavingsGoalProgress>,
    )

    // Re-anchors "today" at local midnight so the "as of today" line and the
    // sparkline window stay correct while the screen is open.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val accountsToday: Flow<AccountsToday> = midnightTicker(clock).flatMapLatest { today ->
        accountRepository.observeAccountsWithBalanceAsOfToday(today.plusDays(1).toEpochDay())
            .map { AccountsToday(today, it) }
    }

    private val core: Flow<Core> = combine(
        accountsToday,
        categoryRepository.observeCategories(),
        transactionRepository.observeTransactionsForAccount(accountId),
        userPreferences.primaryCurrencyOverride,
        observeConversionState(),
        ::Core,
    )

    // The walk re-subscribes only when the account itself or the day changes,
    // not on every balance emission: the history query reacts to the ledger by
    // itself.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val history: Flow<List<DailyBalance>> = accountsToday
        .map { (today, accounts) -> today to accounts.firstOrNull { it.account.id == accountId }?.account }
        .distinctUntilChanged()
        .flatMapLatest { (today, account) ->
            if (account == null) {
                flowOf(emptyList())
            } else {
                val days = List(HISTORY_DAYS) { today.minusDays(HISTORY_DAYS - 1L - it) }
                observeAccountBalanceHistory(account, days)
            }
        }

    private val extras: Flow<Extras> = combine(
        observeDueStatements(),
        observeLoanProgress(),
        observeSavingsGoalsProgress(),
        ::Extras,
    )

    val uiState: StateFlow<AccountDetailUiState> = combine(
        core,
        history,
        extras,
        month,
        dialog,
        ::buildState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AccountDetailUiState(),
    )

    private val _events = Channel<AccountsEvent>(Channel.BUFFERED)
    val events: Flow<AccountsEvent> = _events.receiveAsFlow()

    private fun buildState(
        core: Core,
        history: List<DailyBalance>,
        extras: Extras,
        month: YearMonth,
        dialog: AccountsDialog?,
    ): AccountDetailUiState {
        val accounts = core.accountsToday.accounts
        val item = accounts.firstOrNull { it.account.id == accountId }
            ?: return AccountDetailUiState(isLoading = false, isMissing = true)
        val primary = primaryCurrency(accounts, core.currencyOverride)
        val rates = if (core.conversion.active) core.conversion.rates else RateTable.EMPTY
        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = core.categories.associateBy { it.id }
        val items = core.transactions.map { transaction ->
            TransactionListItem(
                transaction = transaction,
                account = accountById[transaction.accountId],
                toAccount = transaction.transferAccountId?.let { accountById[it] },
                category = transaction.categoryId?.let { categoryById[it] },
                countervalue = transaction.countervalueIn(primary, rates),
                countervalueCurrency = primary,
            )
        }
        val months = items.map { YearMonth.from(it.transaction.localDate) }
        val currentMonth = YearMonth.from(core.accountsToday.today)
        val earliest = months.minOrNull()?.let { minOf(it, currentMonth) } ?: currentMonth
        val latest = months.maxOrNull()?.let { maxOf(it, currentMonth) } ?: currentMonth
        val monthItems = items.filter { YearMonth.from(it.transaction.localDate) == month }
        val countervalue = if (item.account.currency != primary) {
            CurrencyConverter.convertAtLatest(item.balance, item.account.currency, primary, rates)
        } else {
            null
        }
        return AccountDetailUiState(
            isLoading = false,
            item = item,
            countervalue = countervalue,
            primaryCurrency = primary,
            history = history,
            dueStatement = extras.dueStatements.firstOrNull { it.accountId == accountId },
            loanProgress = extras.loans[accountId],
            savingsGoal = extras.goals.firstOrNull { it.goal.accountId == accountId },
            today = core.accountsToday.today,
            month = month,
            canGoToPreviousMonth = month > earliest,
            canGoToNextMonth = month < latest,
            days = buildDayGroups(monthItems),
            monthTotals = filteredTotals(monthItems),
            monthMovementCount = monthItems.size,
            dialog = dialog,
        )
    }

    fun previousMonth() {
        if (uiState.value.canGoToPreviousMonth) month.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        if (uiState.value.canGoToNextMonth) month.update { it.plusMonths(1) }
    }

    fun archive(account: Account) {
        dialog.value = null
        viewModelScope.launch {
            suspendRunCatching { accountRepository.upsert(account.copy(isArchived = true)) }
                .onSuccess { _events.send(AccountsEvent.AccountArchived(account)) }
                .onFailure { _events.send(AccountsEvent.WriteFailed) }
        }
    }

    fun unarchive(account: Account) {
        dialog.value = null
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

    fun openAdjustBalance() {
        val item = uiState.value.item ?: return
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

    /** Pays the oldest due statement of the credit card (confirm-mode action). */
    fun settleStatement() {
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

    /**
     * Deletes a movement right away, the ledger's swipe semantics: the app
     * shell shows the undo snackbar, which restores the movement with its tags.
     */
    fun deleteMovement(item: TransactionListItem) {
        viewModelScope.launch {
            suspendRunCatching {
                val tagIds = tagRepository.observeTagsForTransaction(item.id).first().map { it.id }
                transactionRepository.delete(item.transaction)
                tagIds
            }
                .onSuccess { tagIds ->
                    undoCoordinator.publish(UndoableDelete.Movement(item.transaction, tagIds))
                }
                .onFailure { _events.send(AccountsEvent.WriteFailed) }
        }
    }

    fun dismissDialog() {
        dialog.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Window of the account sparkline, today included: the dashboard's. */
        const val HISTORY_DAYS = 30
    }
}
