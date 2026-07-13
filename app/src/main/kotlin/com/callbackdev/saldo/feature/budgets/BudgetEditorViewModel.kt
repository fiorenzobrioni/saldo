package com.callbackdev.saldo.feature.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.navigation.BudgetEditorRoute
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
import java.util.Currency

/** What a budget caps: the whole month or a single expense category. */
sealed interface BudgetScope {
    data object Overall : BudgetScope
    data class ForCategory(val category: Category) : BudgetScope
}

/** Immutable UI state of the budget editor form. */
data class BudgetEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** Budgets are always in the primary currency (ADR 18). */
    val currency: Currency = fallbackCurrency,
    val scope: BudgetScope? = null,
    /** Scopes still available in create mode (existing budgets are excluded). */
    val scopeOptions: List<BudgetScope> = emptyList(),
    val amountInput: String = "",
    /** Set on a failed save attempt to surface field errors. */
    val showValidation: Boolean = false,
    val showDeleteDialog: Boolean = false,
) {
    val isScopeValid: Boolean get() = scope != null
    val isAmountValid: Boolean get() = (MoneyInput.parse(amountInput)?.signum() ?: 0) > 0
}

/** One-shot events consumed by the editor screen. */
sealed interface BudgetEditorEvent {
    data object Saved : BudgetEditorEvent
    data object Deleted : BudgetEditorEvent

    /** The budget to edit no longer exists: leave the screen. */
    data object BudgetMissing : BudgetEditorEvent

    /** A write failed: stay on the screen and let the user retry. */
    data object WriteFailed : BudgetEditorEvent
}

/**
 * Plain CRUD on [BudgetRepository] (ADR 12, no pass-through use case). In
 * create mode the scope picker offers the overall budget (when not set yet)
 * and every expense-capable category without a budget; in edit mode the scope
 * is fixed and only the amount changes, which preserves the row id and its
 * notification watermarks.
 */
@HiltViewModel(assistedFactory = BudgetEditorViewModel.Factory::class)
@Suppress("TooManyFunctions") // An editor naturally has one handler per field plus the delete flow.
class BudgetEditorViewModel @AssistedInject constructor(
    @Assisted private val route: BudgetEditorRoute,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferencesRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: BudgetEditorRoute): BudgetEditorViewModel
    }

    private val _uiState = MutableStateFlow(BudgetEditorUiState())
    val uiState: StateFlow<BudgetEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<BudgetEditorEvent>(Channel.BUFFERED)
    val events: Flow<BudgetEditorEvent> = _events.receiveAsFlow()

    /** The persisted budget being edited; null in create mode. */
    private var existing: Budget? = null

    /** Guards against a double-tap on save creating two budgets; reset on failure. */
    private var isSaving = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val accounts = accountRepository.observeAccountsWithBalance().first()
        val override = userPreferences.primaryCurrencyOverride.first()
        val currency = primaryCurrency(accounts, override)
        val budgets = budgetRepository.getBudgets()
        val budgetId = route.budgetId
        if (budgetId == null) {
            loadCreateMode(currency, budgets)
        } else {
            loadEditMode(currency, budgets, budgetId)
        }
    }

    private suspend fun loadCreateMode(currency: Currency, budgets: List<Budget>) {
        val cappedCategoryIds = budgets.mapNotNull { it.categoryId }.toSet()
        val options = buildList {
            if (budgets.none { it.isOverall }) add(BudgetScope.Overall)
            categoryRepository.observeCategories().first()
                .filter { it.type != CategoryType.INCOME && it.id !in cappedCategoryIds }
                .forEach { add(BudgetScope.ForCategory(it)) }
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                isNew = true,
                currency = currency,
                scope = options.firstOrNull(),
                scopeOptions = options,
            )
        }
    }

    private suspend fun loadEditMode(currency: Currency, budgets: List<Budget>, budgetId: Long) {
        val budget = budgets.firstOrNull { it.id == budgetId }
        val scope = budget?.let { scopeOf(it) }
        if (budget == null || scope == null) {
            _events.send(BudgetEditorEvent.BudgetMissing)
            return
        }
        existing = budget
        _uiState.update {
            it.copy(
                isLoading = false,
                isNew = false,
                // Editing keeps the budget's own currency, even when it is no
                // longer the primary one: amounts must not silently change unit.
                currency = budget.currency,
                scope = scope,
                amountInput = budget.amount.stripTrailingZeros().toPlainString(),
            )
        }
    }

    /** Resolves the edited budget's scope; null when its category vanished. */
    private suspend fun scopeOf(budget: Budget): BudgetScope? = when (val categoryId = budget.categoryId) {
        null -> BudgetScope.Overall
        else -> categoryRepository.getCategory(categoryId)?.let { BudgetScope.ForCategory(it) }
    }

    fun onScopeSelected(scope: BudgetScope) {
        if (!_uiState.value.isNew) return
        _uiState.update { it.copy(scope = scope) }
    }

    fun onAmountChanged(raw: String) {
        _uiState.update {
            it.copy(
                amountInput = MoneyInput.sanitize(
                    raw,
                    MoneyMapper.fractionDigits(it.currency),
                    allowNegative = false,
                ),
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || isSaving) return
        val amount = MoneyInput.parse(state.amountInput)
        val scope = state.scope
        if (scope == null || amount == null || amount.signum() <= 0) {
            _uiState.update { it.copy(showValidation = true) }
            return
        }
        isSaving = true
        viewModelScope.launch {
            val result = suspendRunCatching { persist(scope, amount, state.currency) }
            isSaving = false
            _events.send(
                if (result.isSuccess) BudgetEditorEvent.Saved else BudgetEditorEvent.WriteFailed,
            )
        }
    }

    private suspend fun persist(scope: BudgetScope, amount: BigDecimal, currency: Currency) {
        when (scope) {
            BudgetScope.Overall -> budgetRepository.setOverallBudget(amount, currency)
            is BudgetScope.ForCategory ->
                budgetRepository.upsertCategoryBudget(scope.category.id, amount, currency)
        }
    }

    fun requestDelete() = _uiState.update { it.copy(showDeleteDialog = true) }

    fun dismissDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = false) }

    fun confirmDelete() {
        val budget = existing ?: return
        _uiState.update { it.copy(showDeleteDialog = false) }
        viewModelScope.launch {
            val result = suspendRunCatching { budgetRepository.deleteBudget(budget.id) }
            _events.send(
                if (result.isSuccess) BudgetEditorEvent.Deleted else BudgetEditorEvent.WriteFailed,
            )
        }
    }
}
