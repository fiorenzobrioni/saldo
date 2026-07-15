package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.CurrencyCatalog
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.navigation.AccountEditorRoute
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.util.Currency

/** Immutable UI state of the account editor form. */
data class AccountEditorUiState(
    val isLoading: Boolean,
    val isNew: Boolean = true,
    val name: String = "",
    val type: AccountType = AccountType.CHECKING,
    val currency: Currency,
    /** True when the account already has movements: its currency cannot change. */
    val isCurrencyLocked: Boolean = false,
    val initialBalanceInput: String = "",
    val color: Int,
    val icon: String,
    val isIncludedInTotal: Boolean = true,
    val isIncludedInBudget: Boolean = true,
    /** Set on a failed save attempt to surface field errors. */
    val showValidation: Boolean = false,
) {
    val isNameValid: Boolean get() = name.isNotBlank()
}

/** One-shot events consumed by the editor screen. */
sealed interface AccountEditorEvent {
    data object Saved : AccountEditorEvent

    /** The account to edit no longer exists: leave the screen. */
    data object AccountMissing : AccountEditorEvent

    /** A write failed: stay on the screen and let the user retry. */
    data object WriteFailed : AccountEditorEvent
}

@HiltViewModel(assistedFactory = AccountEditorViewModel.Factory::class)
class AccountEditorViewModel @AssistedInject constructor(
    @Assisted private val route: AccountEditorRoute,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: AccountEditorRoute): AccountEditorViewModel
    }

    /** Currencies offered in the picker: locale currency first, then common ones. */
    val currencies: List<Currency> = CurrencyCatalog.supportedCurrencies

    private val _uiState = MutableStateFlow(
        AccountEditorUiState(
            isLoading = route.accountId != null,
            isNew = route.accountId == null,
            currency = fallbackCurrency,
            color = AccountVisuals.colors.first(),
            icon = AccountVisuals.defaultIconFor(AccountType.CHECKING),
        ),
    )
    val uiState: StateFlow<AccountEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<AccountEditorEvent>(Channel.BUFFERED)
    val events: Flow<AccountEditorEvent> = _events.receiveAsFlow()

    /** Snapshot of the editable fields captured when the form became ready. */
    private val baseline = MutableStateFlow<FormSnapshot?>(null)

    /** True once the user changed a field away from its initial value. */
    val hasUnsavedChanges: StateFlow<Boolean> = combine(_uiState, baseline) { state, base ->
        base != null && base != state.snapshot()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    /** The persisted account being edited; null in create mode. */
    private var existing: Account? = null

    /** Guards against a double-tap on save creating two accounts; reset on failure. */
    private var isSaving = false

    /** True once the user picks an icon: type changes stop updating it. */
    private var userPickedIcon = false

    init {
        val accountId = route.accountId
        if (accountId == null) captureBaseline() else loadAccount(accountId)
    }

    private fun loadAccount(accountId: Long) {
        viewModelScope.launch {
            val account = accountRepository.getAccount(accountId)
            if (account == null) {
                _events.send(AccountEditorEvent.AccountMissing)
                return@launch
            }
            existing = account
            userPickedIcon = true
            val movementCount = transactionRepository.countForAccount(accountId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isNew = false,
                    name = account.name,
                    type = account.type,
                    currency = account.currency,
                    isCurrencyLocked = movementCount > 0,
                    initialBalanceInput = account.initialBalance
                        .stripTrailingZeros()
                        .toPlainString(),
                    color = account.color ?: AccountVisuals.colors.first(),
                    icon = account.icon ?: AccountVisuals.defaultIconFor(account.type),
                    isIncludedInTotal = account.isIncludedInTotal,
                    isIncludedInBudget = account.isIncludedInBudget,
                )
            }
            captureBaseline()
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onTypeChanged(type: AccountType) {
        _uiState.update {
            it.copy(
                type = type,
                icon = if (userPickedIcon) it.icon else AccountVisuals.defaultIconFor(type),
            )
        }
    }

    fun onCurrencyChanged(currency: Currency) {
        if (_uiState.value.isCurrencyLocked) return
        val digits = MoneyMapper.fractionDigits(currency)
        _uiState.update { state ->
            // The new currency may allow fewer decimals (e.g. JPY): rescale the
            // typed amount instead of stripping the separator, which would
            // silently multiply the value.
            val parsed = MoneyInput.parse(state.initialBalanceInput)
            val input = if (parsed != null && parsed.scale() > digits) {
                parsed.setScale(digits, RoundingMode.HALF_UP).toPlainString()
            } else {
                state.initialBalanceInput
            }
            state.copy(currency = currency, initialBalanceInput = input)
        }
    }

    fun onInitialBalanceChanged(raw: String) {
        _uiState.update {
            it.copy(
                initialBalanceInput = MoneyInput.sanitize(
                    raw,
                    MoneyMapper.fractionDigits(it.currency),
                ),
            )
        }
    }

    fun onColorSelected(color: Int) {
        _uiState.update { it.copy(color = color) }
    }

    fun onIconSelected(icon: String) {
        userPickedIcon = true
        _uiState.update { it.copy(icon = icon) }
    }

    fun onIncludedInTotalChanged(included: Boolean) {
        _uiState.update { it.copy(isIncludedInTotal = included) }
    }

    fun onIncludedInBudgetChanged(included: Boolean) {
        _uiState.update { it.copy(isIncludedInBudget = included) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || isSaving) return
        if (!state.isNameValid) {
            _uiState.update { it.copy(showValidation = true) }
            return
        }
        val initialBalance = MoneyInput.parse(state.initialBalanceInput) ?: BigDecimal.ZERO
        val base = existing
        val account = Account(
            id = base?.id ?: 0L,
            name = state.name.trim(),
            type = state.type,
            currency = state.currency,
            initialBalance = initialBalance,
            color = state.color,
            icon = state.icon,
            isIncludedInTotal = state.isIncludedInTotal,
            isIncludedInBudget = state.isIncludedInBudget,
            isArchived = base?.isArchived ?: false,
            sortOrder = base?.sortOrder ?: 0,
            createdAt = base?.createdAt ?: clock.instant(),
        )
        isSaving = true
        viewModelScope.launch {
            val result = suspendRunCatching { accountRepository.upsert(account) }
            isSaving = false
            _events.send(
                if (result.isSuccess) AccountEditorEvent.Saved else AccountEditorEvent.WriteFailed,
            )
        }
    }

    /** Records the current form as the baseline to detect later edits against. */
    private fun captureBaseline() {
        baseline.value = _uiState.value.snapshot()
    }

    /** The user-editable fields whose change counts as an unsaved edit. */
    private data class FormSnapshot(
        val name: String,
        val type: AccountType,
        val currency: Currency,
        val initialBalanceInput: String,
        val color: Int,
        val icon: String,
        val isIncludedInTotal: Boolean,
        val isIncludedInBudget: Boolean,
    )

    private fun AccountEditorUiState.snapshot() = FormSnapshot(
        name = name,
        type = type,
        currency = currency,
        initialBalanceInput = initialBalanceInput,
        color = color,
        icon = icon,
        isIncludedInTotal = isIncludedInTotal,
        isIncludedInBudget = isIncludedInBudget,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
