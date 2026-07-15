package com.callbackdev.saldo.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.di.ApplicationScope
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.usecase.GenerateRecurringMovementsUseCase
import com.callbackdev.saldo.navigation.RecurringRuleEditorRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/** Immutable UI state of the recurring-rule (subscription or income) editor. */
data class RecurringRuleEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    val name: String = "",
    val amountInput: String = "",
    val accountId: Long? = null,
    val accounts: List<Account> = emptyList(),
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val startDate: LocalDate = LocalDate.ofEpochDay(0),
    val endDate: LocalDate? = null,
    val mode: RecurrenceMode = RecurrenceMode.AUTOMATIC,
    val isVariableAmount: Boolean = false,
    val color: Int = CategoryVisuals.colors.first(),
    val icon: String = DEFAULT_ICON,
    val showValidation: Boolean = false,
    val showDeleteDialog: Boolean = false,
) {
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val currency: Currency? get() = account?.currency
    val category: Category? get() = categories.firstOrNull { it.id == categoryId }
    val isNameValid: Boolean get() = name.isNotBlank()
    val isAmountValid: Boolean get() = MoneyInput.parse(amountInput)?.let { it.signum() > 0 } == true
    val isAccountValid: Boolean get() = accountId != null

    companion object {
        const val DEFAULT_ICON = "subscriptions"
        const val DEFAULT_INCOME_ICON = "payments"

        /** The default avatar icon for a rule of [type]. */
        fun defaultIcon(type: TransactionType): String =
            if (type == TransactionType.INCOME) DEFAULT_INCOME_ICON else DEFAULT_ICON
    }
}

/** One-shot events consumed by the editor screen. */
sealed interface RecurringRuleEditorEvent {
    data object Saved : RecurringRuleEditorEvent
    data object Deleted : RecurringRuleEditorEvent

    /** The rule to edit no longer exists: leave the screen. */
    data object RuleMissing : RecurringRuleEditorEvent

    /** A write failed: stay on the screen and let the user retry. */
    data object WriteFailed : RecurringRuleEditorEvent
}

// One callback per form field is the natural shape of an editor; the constructor
// takes one Hilt-injected collaborator per concern (repos, generation, scope, clock).
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel(assistedFactory = RecurringRuleEditorViewModel.Factory::class)
class RecurringRuleEditorViewModel @AssistedInject constructor(
    @Assisted private val route: RecurringRuleEditorRoute,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val generateRecurringMovements: GenerateRecurringMovementsUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: RecurringRuleEditorRoute): RecurringRuleEditorViewModel
    }

    val frequencies: List<RecurrenceFrequency> = RecurrenceFrequency.entries

    /** The rule type on create, taken from the hub tab the editor was opened from. */
    private val initialType: TransactionType = route.initialTypeName
        ?.let { name -> TransactionType.entries.firstOrNull { it.name == name } }
        ?.takeIf { it == TransactionType.EXPENSE || it == TransactionType.INCOME }
        ?: TransactionType.EXPENSE

    private val _uiState = MutableStateFlow(
        RecurringRuleEditorUiState(
            isNew = route.ruleId == null,
            type = initialType,
            startDate = LocalDate.now(clock),
            icon = RecurringRuleEditorUiState.defaultIcon(initialType),
        ),
    )
    val uiState: StateFlow<RecurringRuleEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<RecurringRuleEditorEvent>(Channel.BUFFERED)
    val events: Flow<RecurringRuleEditorEvent> = _events.receiveAsFlow()

    /** Snapshot of the editable fields captured when the form became ready. */
    private val baseline = MutableStateFlow<FormSnapshot?>(null)

    /** True once the user changed a field away from its initial value. */
    val hasUnsavedChanges: StateFlow<Boolean> = combine(_uiState, baseline) { state, base ->
        base != null && base != state.snapshot()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    /** The persisted rule being edited; null in create mode. Preserves untouched fields. */
    private var existing: RecurringRule? = null
    private var userPickedIcon = false

    /** Guards against a double-tap on save creating two rules; reset on failure. */
    private var isSaving = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val allAccounts = accountRepository.observeAccountsWithBalance().first()
            .map { it.account }
        val accounts = allAccounts.filter { !it.isArchived }
        val allCategories = categoryRepository.observeCategories().first()

        val ruleId = route.ruleId
        if (ruleId == null) {
            val categories = allCategories.forRuleType(initialType)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    accounts = accounts,
                    accountId = accounts.firstOrNull()?.id,
                    categories = categories,
                    categoryId = defaultCategoryId(categories, initialType),
                )
            }
            captureBaseline()
            return
        }

        val rule = recurringRuleRepository.getRule(ruleId)
        if (rule == null) {
            _events.send(RecurringRuleEditorEvent.RuleMissing)
            return
        }
        existing = rule
        userPickedIcon = true
        // Keep the referenced account pickable even when archived, so editing
        // a rule tied to an archived account still resolves and saves (same
        // pattern as the movement editor).
        val pickableAccounts = if (accounts.any { it.id == rule.accountId }) {
            accounts
        } else {
            accounts + allAccounts.filter { it.id == rule.accountId }
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                isNew = false,
                type = rule.type,
                name = rule.name,
                amountInput = rule.amount?.stripTrailingZeros()?.toPlainString().orEmpty(),
                accounts = pickableAccounts,
                accountId = rule.accountId,
                categories = allCategories.forRuleType(rule.type),
                categoryId = rule.categoryId,
                frequency = rule.frequency,
                startDate = rule.startDate,
                endDate = rule.endDate,
                mode = rule.mode,
                isVariableAmount = rule.isVariableAmount,
                color = rule.color ?: CategoryVisuals.colors.first(),
                icon = rule.icon ?: RecurringRuleEditorUiState.defaultIcon(rule.type),
            )
        }
        captureBaseline()
    }

    /** Categories a rule of [type] can be filed under (its own type, plus "both"). */
    private fun List<Category>.forRuleType(type: TransactionType): List<Category> {
        val own = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        return filter { it.type == own || it.type == CategoryType.BOTH }
    }

    fun onNameChanged(name: String) = _uiState.update { it.copy(name = name) }

    fun onAmountChanged(raw: String) = _uiState.update {
        val digits = it.currency?.let(MoneyMapper::fractionDigits) ?: DEFAULT_FRACTION_DIGITS
        it.copy(amountInput = MoneyInput.sanitize(raw, digits, allowNegative = false))
    }

    fun onAccountSelected(accountId: Long) = _uiState.update { state ->
        val currency = state.accounts.firstOrNull { it.id == accountId }?.currency
        val digits = currency?.let(MoneyMapper::fractionDigits) ?: DEFAULT_FRACTION_DIGITS
        // Rescale the typed amount to the new currency's precision (e.g. EUR->JPY).
        val parsed = MoneyInput.parse(state.amountInput)
        val input = if (parsed != null && parsed.scale() > digits) {
            parsed.setScale(digits, RoundingMode.HALF_UP).toPlainString()
        } else {
            state.amountInput
        }
        state.copy(accountId = accountId, amountInput = input)
    }

    fun onCategorySelected(categoryId: Long?) = _uiState.update { it.copy(categoryId = categoryId) }

    fun onFrequencySelected(frequency: RecurrenceFrequency) =
        _uiState.update { it.copy(frequency = frequency) }

    fun onStartDateSelected(date: LocalDate) = _uiState.update {
        // Keep any end date after the first charge.
        val end = it.endDate?.takeIf { end -> end >= date }
        it.copy(startDate = date, endDate = end)
    }

    fun onEndDateSelected(date: LocalDate?) = _uiState.update { it.copy(endDate = date) }

    /** Toggles the presence of an end date; enabling seeds a sensible default. */
    fun onEndDateToggled(enabled: Boolean) = _uiState.update {
        it.copy(endDate = if (enabled) it.startDate.plusYears(1) else null)
    }

    fun onModeChanged(mode: RecurrenceMode) = _uiState.update { it.copy(mode = mode) }

    /** Variable amount implies confirm mode (the amount is asked at each charge). */
    fun onVariableAmountToggled(enabled: Boolean) = _uiState.update {
        it.copy(
            isVariableAmount = enabled,
            mode = if (enabled) RecurrenceMode.CONFIRM else it.mode,
        )
    }

    fun onColorSelected(color: Int) = _uiState.update { it.copy(color = color) }

    fun onIconSelected(icon: String) {
        userPickedIcon = true
        _uiState.update { it.copy(icon = icon) }
    }

    fun requestDelete() = _uiState.update { it.copy(showDeleteDialog = true) }
    fun dismissDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = false) }

    fun confirmDelete() {
        val rule = existing ?: return
        viewModelScope.launch {
            val result = suspendRunCatching { recurringRuleRepository.delete(rule) }
            _events.send(
                if (result.isSuccess) RecurringRuleEditorEvent.Deleted else RecurringRuleEditorEvent.WriteFailed,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || isSaving) return
        val rule = buildValidRule(state, existing, LocalDate.now(clock))
        if (rule == null) {
            _uiState.update { it.copy(showValidation = true) }
            return
        }
        isSaving = true
        viewModelScope.launch {
            val result = suspendRunCatching { recurringRuleRepository.upsert(rule) }
            isSaving = false
            if (result.isSuccess) {
                // Materialize any occurrence already owed by the rule (e.g. one
                // due today) right away, instead of waiting for the next app
                // launch or the daily worker. Runs in the application scope so
                // navigating back off this screen cannot cancel it mid-run; the
                // use case is idempotent and mutex-guarded, so overlapping with
                // the launch catch-up is harmless.
                applicationScope.launch { runCatching { generateRecurringMovements() } }
            }
            _events.send(
                if (result.isSuccess) RecurringRuleEditorEvent.Saved else RecurringRuleEditorEvent.WriteFailed,
            )
        }
    }

    /** Builds the rule from valid form state, or null when a required field is missing. */
    private fun buildValidRule(
        state: RecurringRuleEditorUiState,
        base: RecurringRule?,
        today: LocalDate,
    ): RecurringRule? {
        val amount = if (state.isVariableAmount) {
            null
        } else {
            MoneyInput.parse(state.amountInput)?.takeIf { it.signum() > 0 }
        }
        val account = state.account
        val amountMissing = !state.isVariableAmount && amount == null
        if (!state.isNameValid || account == null || amountMissing) return null
        val rule = RecurringRule(
            id = base?.id ?: 0L,
            name = state.name.trim(),
            type = state.type,
            currency = account.currency,
            accountId = account.id,
            frequency = state.frequency,
            startDate = state.startDate,
            amount = amount,
            categoryId = state.categoryId,
            dayOfReference = state.startDate.dayOfMonth,
            endDate = state.endDate,
            mode = if (state.isVariableAmount) RecurrenceMode.CONFIRM else state.mode,
            isVariableAmount = state.isVariableAmount,
            color = state.color,
            icon = state.icon,
            note = base?.note,
            // Watermarks survive an edit: losing the reminder one would
            // re-notify an occurrence that was already announced.
            lastReminderDate = base?.lastReminderDate,
        )
        // Preserve progress on edit; on create, skip past occurrences so an
        // existing subscription is not back-filled with history. When the
        // schedule itself changes, the old watermark no longer lies on the new
        // cadence: re-seed it so generation resumes aligned (and without
        // back-filling the new schedule's past occurrences).
        val scheduleChanged = base != null &&
            (
                base.frequency != rule.frequency ||
                    base.startDate != rule.startDate ||
                    base.dayOfReference != rule.dayOfReference
                )
        return rule.copy(
            lastGeneratedDate = if (base == null || scheduleChanged) {
                RecurrenceCalculator.latestOccurrenceBefore(rule, today)
            } else {
                base.lastGeneratedDate
            },
        )
    }

    /**
     * Preselects the natural category for a new rule: the seeded "Subscriptions"
     * category for expenses, "Salary" for incomes (both matched by icon, which
     * survives renames). Null when the user deleted it.
     */
    private fun defaultCategoryId(categories: List<Category>, type: TransactionType): Long? =
        categories.firstOrNull { it.icon == RecurringRuleEditorUiState.defaultIcon(type) }?.id

    /** Records the current form as the baseline to detect later edits against. */
    private fun captureBaseline() {
        baseline.value = _uiState.value.snapshot()
    }

    /** The user-editable fields whose change counts as an unsaved edit. */
    private data class FormSnapshot(
        val type: TransactionType,
        val name: String,
        val amountInput: String,
        val accountId: Long?,
        val categoryId: Long?,
        val frequency: RecurrenceFrequency,
        val startDate: LocalDate,
        val endDate: LocalDate?,
        val mode: RecurrenceMode,
        val isVariableAmount: Boolean,
        val color: Int,
        val icon: String,
    )

    private fun RecurringRuleEditorUiState.snapshot() = FormSnapshot(
        type = type,
        name = name,
        amountInput = amountInput,
        accountId = accountId,
        categoryId = categoryId,
        frequency = frequency,
        startDate = startDate,
        endDate = endDate,
        mode = mode,
        isVariableAmount = isVariableAmount,
        color = color,
        icon = icon,
    )

    private companion object {
        const val DEFAULT_FRACTION_DIGITS = 2
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
