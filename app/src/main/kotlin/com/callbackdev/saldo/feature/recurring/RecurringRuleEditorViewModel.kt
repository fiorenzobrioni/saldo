package com.callbackdev.saldo.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.navigation.RecurringRuleEditorRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/** Immutable UI state of the subscription (recurring-rule) editor. */
data class RecurringRuleEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val amountInput: String = "",
    val accountId: Long? = null,
    val accounts: List<Account> = emptyList(),
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val startDate: LocalDate = LocalDate.ofEpochDay(0),
    val endDate: LocalDate? = null,
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
    }
}

/** One-shot events consumed by the editor screen. */
sealed interface RecurringRuleEditorEvent {
    data object Saved : RecurringRuleEditorEvent
    data object Deleted : RecurringRuleEditorEvent

    /** The rule to edit no longer exists: leave the screen. */
    data object RuleMissing : RecurringRuleEditorEvent
}

@Suppress("TooManyFunctions") // One callback per form field is the natural shape of an editor.
@HiltViewModel(assistedFactory = RecurringRuleEditorViewModel.Factory::class)
class RecurringRuleEditorViewModel @AssistedInject constructor(
    @Assisted private val route: RecurringRuleEditorRoute,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: RecurringRuleEditorRoute): RecurringRuleEditorViewModel
    }

    val frequencies: List<RecurrenceFrequency> = RecurrenceFrequency.entries

    private val _uiState = MutableStateFlow(
        RecurringRuleEditorUiState(
            isNew = route.ruleId == null,
            startDate = LocalDate.now(clock),
        ),
    )
    val uiState: StateFlow<RecurringRuleEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<RecurringRuleEditorEvent>(Channel.BUFFERED)
    val events: Flow<RecurringRuleEditorEvent> = _events.receiveAsFlow()

    /** The persisted rule being edited; null in create mode. Preserves untouched fields. */
    private var existing: RecurringRule? = null
    private var userPickedIcon = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val accounts = accountRepository.observeAccountsWithBalance().first()
            .map { it.account }
            .filter { !it.isArchived }
        val categories = categoryRepository.observeCategories().first()
            .filter { it.type == CategoryType.EXPENSE || it.type == CategoryType.BOTH }

        val ruleId = route.ruleId
        if (ruleId == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    accounts = accounts,
                    accountId = accounts.firstOrNull()?.id,
                    categories = categories,
                    categoryId = defaultCategoryId(categories),
                )
            }
            return
        }

        val rule = recurringRuleRepository.getRule(ruleId)
        if (rule == null) {
            _events.send(RecurringRuleEditorEvent.RuleMissing)
            return
        }
        existing = rule
        userPickedIcon = true
        _uiState.update {
            it.copy(
                isLoading = false,
                isNew = false,
                name = rule.name,
                amountInput = rule.amount?.stripTrailingZeros()?.toPlainString().orEmpty(),
                accounts = accounts,
                accountId = rule.accountId,
                categories = categories,
                categoryId = rule.categoryId,
                frequency = rule.frequency,
                startDate = rule.startDate,
                endDate = rule.endDate,
                color = rule.color ?: CategoryVisuals.colors.first(),
                icon = rule.icon ?: RecurringRuleEditorUiState.DEFAULT_ICON,
            )
        }
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
            recurringRuleRepository.delete(rule)
            _events.send(RecurringRuleEditorEvent.Deleted)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading) return
        val rule = buildValidRule(state, existing, LocalDate.now(clock))
        if (rule == null) {
            _uiState.update { it.copy(showValidation = true) }
            return
        }
        viewModelScope.launch {
            recurringRuleRepository.upsert(rule)
            _events.send(RecurringRuleEditorEvent.Saved)
        }
    }

    /** Builds the rule from valid form state, or null when a required field is missing. */
    private fun buildValidRule(
        state: RecurringRuleEditorUiState,
        base: RecurringRule?,
        today: LocalDate,
    ): RecurringRule? {
        val amount = MoneyInput.parse(state.amountInput)?.takeIf { it.signum() > 0 }
        val account = state.account
        if (!state.isNameValid || amount == null || account == null) return null
        val rule = RecurringRule(
            id = base?.id ?: 0L,
            name = state.name.trim(),
            type = TransactionType.EXPENSE,
            currency = account.currency,
            accountId = account.id,
            frequency = state.frequency,
            startDate = state.startDate,
            amount = amount,
            categoryId = state.categoryId,
            dayOfReference = state.startDate.dayOfMonth,
            endDate = state.endDate,
            color = state.color,
            icon = state.icon,
            note = base?.note,
        )
        // Preserve progress on edit; on create, skip past occurrences so an
        // existing subscription is not back-filled with history.
        return rule.copy(
            lastGeneratedDate = base?.lastGeneratedDate
                ?: RecurrenceCalculator.latestOccurrenceBefore(rule, today),
        )
    }

    private fun defaultCategoryId(categories: List<Category>): Long? =
        categories.firstOrNull { it.icon == RecurringRuleEditorUiState.DEFAULT_ICON }?.id

    private companion object {
        const val DEFAULT_FRACTION_DIGITS = 2
    }
}
