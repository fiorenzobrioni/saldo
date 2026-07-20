package com.callbackdev.saldo.feature.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.SavingsGoal
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.SavingsGoalRepository
import com.callbackdev.saldo.navigation.SavingsGoalEditorRoute
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
import java.time.LocalDate
import java.util.Currency

/** Immutable UI state of the savings goal editor form. */
data class SavingsGoalEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** Savings accounts the goal can link to: the free ones on create, the linked one on edit. */
    val availableAccounts: List<Account> = emptyList(),
    val accountId: Long? = null,
    val currency: Currency = fallbackCurrency,
    /** Current balance of the linked account, already counted toward the goal. */
    val savedBalance: BigDecimal = BigDecimal.ZERO,
    val name: String = "",
    val targetInput: String = "",
    val targetDate: LocalDate? = null,
    val color: Int = CategoryVisuals.colors.first(),
    val icon: String = DEFAULT_ICON,
    /** True in create mode when no savings account can be linked (none free, or none at all). */
    val noAvailableAccounts: Boolean = false,
    /**
     * True when at least one (non-archived) savings account exists, regardless of whether it
     * is free. Distinguishes "you have no savings account" from "all of them already have a
     * goal", which need different empty-state copy.
     */
    val hasSavingsAccounts: Boolean = false,
    val showValidation: Boolean = false,
    val showDeleteDialog: Boolean = false,
) {
    val selectedAccount: Account? get() = availableAccounts.firstOrNull { it.id == accountId }
    val isNameValid: Boolean get() = name.isNotBlank()
    val isTargetValid: Boolean get() = MoneyInput.parse(targetInput)?.let { it.signum() > 0 } == true
    val isAccountValid: Boolean get() = accountId != null

    companion object {
        const val DEFAULT_ICON = "savings"
    }
}

/** One-shot events consumed by the editor screen. */
sealed interface SavingsGoalEditorEvent {
    data object Saved : SavingsGoalEditorEvent
    data object Deleted : SavingsGoalEditorEvent

    /** The goal to edit no longer exists: leave the screen. */
    data object GoalMissing : SavingsGoalEditorEvent

    /** A write failed: stay on the screen and let the user retry. */
    data object WriteFailed : SavingsGoalEditorEvent
}

/**
 * Plain CRUD on [SavingsGoalRepository] (ADR 12, no pass-through use case). A
 * goal links exactly one savings account (the pot/vault model): in create mode
 * the picker offers the savings accounts that do not have a goal yet, plus the
 * shortcut to create a new one; in edit mode the linked account is fixed (only
 * the target, date, name and avatar change), which preserves the goal's
 * identity. The account list is observed, so a savings account created through
 * the shortcut appears without reopening the editor.
 */
@HiltViewModel(assistedFactory = SavingsGoalEditorViewModel.Factory::class)
@Suppress("TooManyFunctions") // An editor naturally has one handler per field plus the delete flow.
class SavingsGoalEditorViewModel @AssistedInject constructor(
    @Assisted private val route: SavingsGoalEditorRoute,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: SavingsGoalEditorRoute): SavingsGoalEditorViewModel
    }

    private val _uiState = MutableStateFlow(SavingsGoalEditorUiState(isNew = route.goalId == null))
    val uiState: StateFlow<SavingsGoalEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<SavingsGoalEditorEvent>(Channel.BUFFERED)
    val events: Flow<SavingsGoalEditorEvent> = _events.receiveAsFlow()

    private val baseline = MutableStateFlow<FormSnapshot?>(null)

    /** True once the user changed a field away from its initial value. */
    val hasUnsavedChanges: StateFlow<Boolean> = combine(_uiState, baseline) { state, base ->
        base != null && base != state.snapshot()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    /** The persisted goal being edited; null in create mode. */
    private var existing: SavingsGoal? = null
    private var initialized = false
    private var balancesByAccount: Map<Long, BigDecimal> = emptyMap()

    /** Guards against a double-tap on save creating two goals; reset on failure. */
    private var isSaving = false

    init {
        viewModelScope.launch {
            combine(
                accountRepository.observeAccountsWithBalance(),
                savingsGoalRepository.observeGoals(),
            ) { accounts, goals -> accounts to goals }
                .collect { (accounts, goals) -> onData(accounts, goals) }
        }
    }

    private suspend fun onData(accounts: List<AccountWithBalance>, goals: List<SavingsGoal>) {
        balancesByAccount = accounts.associate { it.account.id to it.balance }
        val savingsAccounts = accounts
            .filter { it.account.type == AccountType.SAVINGS && !it.account.isArchived }
            .map { it.account }
        val freeSavings = savingsAccounts.filter { account -> goals.none { it.accountId == account.id } }
        val hasSavingsAccounts = savingsAccounts.isNotEmpty()

        if (!initialized) {
            initialized = true
            if (route.goalId == null) initCreate(freeSavings, hasSavingsAccounts) else initEdit(accounts, goals)
        } else if (_uiState.value.isNew) {
            refreshCreateOptions(freeSavings, hasSavingsAccounts)
        }
    }

    private fun initCreate(freeSavings: List<Account>, hasSavingsAccounts: Boolean) {
        val selected = freeSavings.firstOrNull()
        _uiState.update {
            it.copy(
                isLoading = false,
                isNew = true,
                availableAccounts = freeSavings,
                accountId = selected?.id,
                currency = selected?.currency ?: fallbackCurrency,
                savedBalance = selected?.let { acc -> balancesByAccount[acc.id] } ?: BigDecimal.ZERO,
                noAvailableAccounts = freeSavings.isEmpty(),
                hasSavingsAccounts = hasSavingsAccounts,
            )
        }
        captureBaseline()
    }

    private suspend fun initEdit(accounts: List<AccountWithBalance>, goals: List<SavingsGoal>) {
        val goal = goals.firstOrNull { it.id == route.goalId }
        if (goal == null) {
            _events.send(SavingsGoalEditorEvent.GoalMissing)
            return
        }
        existing = goal
        // Keep the linked account selectable even if archived, so the goal still resolves.
        val linked = accounts.firstOrNull { it.account.id == goal.accountId }?.account
        _uiState.update {
            it.copy(
                isLoading = false,
                isNew = false,
                availableAccounts = listOfNotNull(linked),
                accountId = goal.accountId,
                currency = goal.currency,
                savedBalance = balancesByAccount[goal.accountId] ?: BigDecimal.ZERO,
                name = goal.name,
                targetInput = goal.targetAmount.stripTrailingZeros().toPlainString(),
                targetDate = goal.targetDate,
                color = goal.color ?: CategoryVisuals.colors.first(),
                icon = goal.icon ?: SavingsGoalEditorUiState.DEFAULT_ICON,
            )
        }
        captureBaseline()
    }

    /**
     * Refreshes the create-mode picker when the account list changes (e.g. after
     * the "create a savings account" shortcut). Preselects the first option when
     * nothing valid is selected yet, so a freshly created account is picked up.
     */
    private fun refreshCreateOptions(freeSavings: List<Account>, hasSavingsAccounts: Boolean) {
        _uiState.update { state ->
            val stillValid = state.accountId != null && freeSavings.any { it.id == state.accountId }
            val accountId = if (stillValid) state.accountId else freeSavings.firstOrNull()?.id
            val account = freeSavings.firstOrNull { it.id == accountId }
            state.copy(
                availableAccounts = freeSavings,
                accountId = accountId,
                currency = account?.currency ?: state.currency,
                savedBalance = accountId?.let { balancesByAccount[it] } ?: BigDecimal.ZERO,
                noAvailableAccounts = freeSavings.isEmpty(),
                hasSavingsAccounts = hasSavingsAccounts,
            )
        }
    }

    fun onNameChanged(name: String) = _uiState.update { it.copy(name = name) }

    fun onTargetChanged(raw: String) = _uiState.update {
        it.copy(
            targetInput = MoneyInput.sanitize(
                raw,
                MoneyMapper.fractionDigits(it.currency),
                allowNegative = false,
            ),
        )
    }

    fun onAccountSelected(accountId: Long) {
        if (!_uiState.value.isNew) return
        _uiState.update { state ->
            val account = state.availableAccounts.firstOrNull { it.id == accountId }
            val currency = account?.currency ?: state.currency
            val digits = MoneyMapper.fractionDigits(currency)
            // Rescale the typed target to the new currency's precision (e.g. EUR->JPY).
            val parsed = MoneyInput.parse(state.targetInput)
            val input = if (parsed != null && parsed.scale() > digits) {
                parsed.setScale(digits, RoundingMode.HALF_UP).toPlainString()
            } else {
                state.targetInput
            }
            state.copy(
                accountId = accountId,
                currency = currency,
                savedBalance = balancesByAccount[accountId] ?: BigDecimal.ZERO,
                targetInput = input,
            )
        }
    }

    fun onTargetDateSelected(date: LocalDate) = _uiState.update { it.copy(targetDate = date) }

    fun onTargetDateCleared() = _uiState.update { it.copy(targetDate = null) }

    fun onColorSelected(color: Int) = _uiState.update { it.copy(color = color) }

    fun onIconSelected(icon: String) = _uiState.update { it.copy(icon = icon) }

    fun requestDelete() = _uiState.update { it.copy(showDeleteDialog = true) }

    fun dismissDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = false) }

    fun confirmDelete() {
        val goal = existing ?: return
        _uiState.update { it.copy(showDeleteDialog = false) }
        viewModelScope.launch {
            val result = suspendRunCatching { savingsGoalRepository.deleteGoal(goal.id) }
            _events.send(
                if (result.isSuccess) SavingsGoalEditorEvent.Deleted else SavingsGoalEditorEvent.WriteFailed,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || isSaving) return
        val target = MoneyInput.parse(state.targetInput)?.takeIf { it.signum() > 0 }
        val account = state.selectedAccount
        if (!state.isNameValid || target == null || account == null) {
            _uiState.update { it.copy(showValidation = true) }
            return
        }
        isSaving = true
        viewModelScope.launch {
            val goal = SavingsGoal(
                id = existing?.id ?: 0L,
                name = state.name.trim(),
                targetAmount = target,
                currency = account.currency,
                accountId = account.id,
                targetDate = state.targetDate,
                color = state.color,
                icon = state.icon,
                sortOrder = existing?.sortOrder ?: 0,
            )
            val result = suspendRunCatching { savingsGoalRepository.upsert(goal) }
            isSaving = false
            _events.send(
                if (result.isSuccess) SavingsGoalEditorEvent.Saved else SavingsGoalEditorEvent.WriteFailed,
            )
        }
    }

    /** Records the current form as the baseline to detect later edits against. */
    private fun captureBaseline() {
        baseline.value = _uiState.value.snapshot()
    }

    /** The user-editable fields whose change counts as an unsaved edit. */
    private data class FormSnapshot(
        val accountId: Long?,
        val name: String,
        val targetInput: String,
        val targetDate: LocalDate?,
        val color: Int,
        val icon: String,
    )

    private fun SavingsGoalEditorUiState.snapshot() = FormSnapshot(
        accountId = accountId,
        name = name,
        targetInput = targetInput,
        targetDate = targetDate,
        color = color,
        icon = icon,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
