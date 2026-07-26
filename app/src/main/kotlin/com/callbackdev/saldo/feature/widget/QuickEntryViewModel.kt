package com.callbackdev.saldo.feature.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.account.DefaultAccountResolver
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.transaction.QuickTransactionFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime

/** What the quick entry sheet renders. */
data class QuickEntryUiState(
    val isLoading: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val category: Category? = null,
    val categories: List<Category> = emptyList(),
    val account: AccountWithBalance? = null,
    val accounts: List<AccountWithBalance> = emptyList(),
    val isSaved: Boolean = false,
    /** The saved amount, formatted, shown by the confirmation state. */
    val savedAmount: String? = null,
) {
    val fractionDigits: Int
        get() = account?.let { MoneyMapper.fractionDigits(it.account.currency) } ?: DEFAULT_FRACTION_DIGITS

    val currencySymbol: String? get() = account?.account?.currency?.symbol

    val isAmountValid: Boolean
        get() = MoneyInput.parse(amountInput)?.let { it.signum() > 0 } == true

    /**
     * [isSaved] closes the door for good: the sheet holds its confirmation for
     * a beat before it dismisses, and a second tap in that window must not
     * write the movement twice.
     */
    val canSave: Boolean
        get() = !isLoading && !isSaved && isAmountValid && account != null && category != null

    private companion object {
        const val DEFAULT_FRACTION_DIGITS = 2
    }
}

sealed interface QuickEntryEvent {
    /** Saved: the sheet plays its confirmation and closes itself. */
    data object Saved : QuickEntryEvent
    data object WriteFailed : QuickEntryEvent
}

/**
 * The widget's amount step. Deliberately a separate, much smaller view model
 * than the full editor: it only ever writes an expense or an income on today's
 * date, and everything else (transfers, tags, notes, another date) is one tap
 * away in the real editor.
 *
 * The movement itself is built by [QuickTransactionFactory], the same shared
 * rules the full editor uses, so the sign convention and the zone offset cannot
 * drift between the two entry points.
 */
@HiltViewModel(assistedFactory = QuickEntryViewModel.Factory::class)
class QuickEntryViewModel @AssistedInject constructor(
    @Assisted private val route: QuickEntryRoute,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: QuickEntryRoute): QuickEntryViewModel
    }

    private data class Form(
        val amountInput: String = "",
        val categoryId: Long? = null,
        val accountId: Long? = null,
        val isSaved: Boolean = false,
        val savedAmount: String? = null,
    )

    private val form = MutableStateFlow(
        Form(categoryId = route.categoryId, accountId = route.accountId),
    )

    private val _events = Channel<QuickEntryEvent>(Channel.BUFFERED)
    val events: Flow<QuickEntryEvent> = _events.receiveAsFlow()

    private var isSaving = false

    private val categoryType = when (route.type) {
        TransactionType.INCOME -> CategoryType.INCOME
        else -> CategoryType.EXPENSE
    }

    val uiState: StateFlow<QuickEntryUiState> = combine(
        form,
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(categoryType),
    ) { current, accounts, categories ->
        val pickable = accounts.filter { !it.account.isArchived || it.account.id == current.accountId }
        val account = pickable.firstOrNull { it.account.id == current.accountId }
        QuickEntryUiState(
            isLoading = false,
            type = route.type,
            amountInput = current.amountInput,
            category = categories.firstOrNull { it.id == current.categoryId },
            categories = categories,
            account = account,
            accounts = pickable,
            isSaved = current.isSaved,
            savedAmount = current.savedAmount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = QuickEntryUiState(type = route.type),
    )

    init {
        // The widget passes the account it rendered with; if it was archived or
        // deleted since, fall back to the app's own default chain rather than
        // opening a sheet that cannot save.
        if (route.accountId == null) preselectAccount()
    }

    fun onAmountChanged(value: String) {
        form.update { it.copy(amountInput = value) }
    }

    fun onCategorySelected(categoryId: Long) {
        form.update { it.copy(categoryId = categoryId) }
    }

    fun onAccountSelected(accountId: Long) {
        form.update { it.copy(accountId = accountId) }
    }

    fun save() {
        if (isSaving) return
        val state = uiState.value
        if (!state.canSave) return
        // canSave already proved both of these; the guards keep the types honest.
        val account = state.account?.account ?: return
        val amount = MoneyInput.parse(state.amountInput) ?: return
        isSaving = true
        val transaction = QuickTransactionFactory.create(
            type = state.type,
            amount = amount,
            account = account,
            categoryId = state.category?.id,
            dateTime = LocalDateTime.now(clock),
            zone = clock.zone,
        )
        viewModelScope.launch {
            val result = suspendRunCatching {
                transactionRepository.upsert(transaction)
                userPreferences.setLastUsedAccountId(account.id)
            }
            isSaving = false
            if (result.isSuccess) {
                form.update {
                    it.copy(
                        isSaved = true,
                        savedAmount = MoneyFormatter.format(transaction.amount.abs(), account.currency),
                    )
                }
                _events.send(QuickEntryEvent.Saved)
            } else {
                _events.send(QuickEntryEvent.WriteFailed)
            }
        }
    }

    private fun preselectAccount() {
        viewModelScope.launch {
            val active = accountRepository.observeAccountsWithBalance().first()
                .map { it.account }
                .filter { !it.isArchived }
            val default = DefaultAccountResolver.resolve(
                accounts = active,
                defaultAccountId = userPreferences.defaultAccountId.first(),
                lastUsedAccountId = userPreferences.lastUsedAccountId.first(),
            )
            if (default != null) {
                form.update { if (it.accountId == null) it.copy(accountId = default.id) else it }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
