package com.callbackdev.saldo.feature.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.UpcomingLedger
import com.callbackdev.saldo.core.domain.model.UpcomingMovement
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.ObserveUpcomingMovementsUseCase
import com.callbackdev.saldo.navigation.UpcomingRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency

/** Which slice of the upcoming list is shown. */
enum class UpcomingFilter {
    /** Everything ahead, confirmed movements and occurrences to confirm alike. */
    ALL,

    /** Only what is waiting for an answer. */
    PENDING,
}

/**
 * One upcoming movement resolved against the account, category and rule it
 * refers to, so a row can be drawn without further lookups.
 */
data class UpcomingItem(
    val movement: UpcomingMovement,
    val account: Account?,
    val transferAccount: Account?,
    val category: Category?,
    val rule: RecurringRule?,
) {
    val id: Long get() = movement.id
    val date: LocalDate get() = movement.date
    val transaction: Transaction get() = movement.transaction
    val isPending: Boolean get() = movement.isPending

    val isTransfer: Boolean get() = transaction.type == TransactionType.TRANSFER

    /** The row's headline: the movement's own description, else the rule's name, else the category. */
    val title: String
        get() = transaction.description?.takeIf { it.isNotBlank() }
            ?: rule?.name?.takeIf { it.isNotBlank() }
            ?: category?.name.orEmpty()

    /**
     * A transfer whose legs hold different currencies: the received amount cannot
     * be fixed up front, so it is entered at confirmation (ADR 24).
     */
    val isCrossCurrencyTransfer: Boolean
        get() = isTransfer && transaction.transferCurrency != null &&
            transaction.transferCurrency != transaction.currency

    /** True while a variable amount still has to be typed before this can be recorded. */
    val needsAmountEntry: Boolean
        get() = isPending && (rule?.isVariableAmount == true || isCrossCurrencyTransfer)

    /** Currency the confirmation amount is entered in (the destination leg for a transfer). */
    val entryCurrency: Currency
        get() = if (isTransfer) transaction.transferCurrency ?: transaction.currency else transaction.currency

    /** Positive charge magnitude; zero for a variable-amount movement awaiting an amount. */
    val magnitude: BigDecimal get() = transaction.amount.abs()
}

/** Immutable UI state of the upcoming screen. */
data class UpcomingUiState(
    val isLoading: Boolean = true,
    val ledger: UpcomingLedger = UpcomingLedger(),
    val items: List<UpcomingItem> = emptyList(),
    val filter: UpcomingFilter = UpcomingFilter.ALL,
    val today: LocalDate = LocalDate.ofEpochDay(0),
) {
    /** Whether anything at all is ahead, regardless of the active filter. */
    val hasAnything: Boolean get() = !ledger.isEmpty

    /** True when the filter, not the absence of data, is what emptied the list. */
    val isFilteredEmpty: Boolean get() = !isLoading && items.isEmpty() && hasAnything

    val isEmpty: Boolean get() = !isLoading && !hasAnything

    /** The filter chips only earn their space once something can actually be confirmed. */
    val showFilters: Boolean get() = ledger.pendingCount > 0
}

/**
 * Drives the "Upcoming" screen: the single forward-looking list of confirmed
 * future movements and occurrences still to confirm (ADR 36), plus the two
 * writes a pending row offers - confirm with an amount, or skip it.
 *
 * Confirmation lives here rather than in a screen of its own because the two
 * lists were the same list: keeping them apart meant showing the same pending
 * movements in two places, reached from two cards of the same dashboard.
 */
@HiltViewModel(assistedFactory = UpcomingViewModel.Factory::class)
@Suppress("LongParameterList") // One repository per join the rows need.
class UpcomingViewModel @AssistedInject constructor(
    @Assisted route: UpcomingRoute,
    observeUpcomingMovements: ObserveUpcomingMovementsUseCase,
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    recurringRuleRepository: RecurringRuleRepository,
    clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: UpcomingRoute): UpcomingViewModel
    }

    private val today = LocalDate.now(clock)

    // The route only chooses where the screen opens; from then on the filter is
    // the user's, and it lives here so rotating does not snap it back.
    private val filterState = MutableStateFlow(
        if (route.pendingOnly) UpcomingFilter.PENDING else UpcomingFilter.ALL,
    )
    val filter: StateFlow<UpcomingFilter> = filterState.asStateFlow()

    val uiState: StateFlow<UpcomingUiState> = combine(
        observeUpcomingMovements(today),
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
        recurringRuleRepository.observeRules(),
        filterState,
    ) { ledger, accounts, categories, rules, filter ->
        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = categories.associateBy { it.id }
        val ruleById = rules.associateBy { it.id }
        UpcomingUiState(
            isLoading = false,
            ledger = ledger,
            items = ledger.items
                .filter { filter == UpcomingFilter.ALL || it.isPending }
                .map { movement ->
                    val transaction = movement.transaction
                    UpcomingItem(
                        movement = movement,
                        account = accountById[transaction.accountId],
                        transferAccount = transaction.transferAccountId?.let { accountById[it] },
                        category = transaction.categoryId?.let { categoryById[it] },
                        rule = transaction.recurringRuleId?.let { ruleById[it] },
                    )
                },
            filter = filter,
            today = today,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UpcomingUiState(today = today),
    )

    private val _events = Channel<UpcomingEvent>(Channel.BUFFERED)
    val events: Flow<UpcomingEvent> = _events.receiveAsFlow()

    fun onFilterSelected(filter: UpcomingFilter) {
        filterState.value = filter
    }

    /** Confirms [transaction] with [magnitude], applying the sign and clearing the pending flag. */
    fun confirm(transaction: Transaction, magnitude: BigDecimal) {
        val isCrossCurrencyTransfer = transaction.type == TransactionType.TRANSFER &&
            transaction.transferCurrency != null &&
            transaction.transferCurrency != transaction.currency
        val updated = when {
            transaction.type != TransactionType.TRANSFER -> {
                val signed =
                    if (transaction.type == TransactionType.EXPENSE) magnitude.negate() else magnitude
                transaction.copy(amount = signed, isPending = false)
            }
            // Cross-currency: the magnitude is the received (destination) amount;
            // the source leg was fixed at generation and stays as is.
            isCrossCurrencyTransfer ->
                transaction.copy(transferAmount = magnitude, isPending = false)
            // Same-currency: both legs move by the confirmed magnitude.
            else ->
                transaction.copy(amount = magnitude.negate(), transferAmount = magnitude, isPending = false)
        }
        viewModelScope.launch {
            suspendRunCatching { transactionRepository.upsert(updated) }
                .onFailure { _events.send(UpcomingEvent.WriteFailed) }
        }
    }

    /** Discards a pending movement the user does not want recorded. */
    fun skip(transaction: Transaction) {
        viewModelScope.launch {
            suspendRunCatching { transactionRepository.delete(transaction) }
                .onFailure { _events.send(UpcomingEvent.WriteFailed) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** One-shot outcomes surfaced as snackbars. */
sealed interface UpcomingEvent {
    /** A write failed: the movement is unchanged, let the user retry. */
    data object WriteFailed : UpcomingEvent
}
