package com.callbackdev.saldo.feature.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.BudgetProgress
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.usecase.ObserveBudgetProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import java.util.Currency
import javax.inject.Inject

/** Immutable UI state of the budgets screen. */
data class BudgetsUiState(
    val isLoading: Boolean = true,
    val currency: Currency = fallbackCurrency,
    val month: YearMonth = YearMonth.now(),
    /** The overall monthly budget's progress, when one is set. */
    val overall: BudgetProgress? = null,
    /** Category budgets, closest to their cap first. */
    val categoryBudgets: List<BudgetProgress> = emptyList(),
) {
    val isEmpty: Boolean get() = overall == null && categoryBudgets.isEmpty()
}

/**
 * Budgets are plain rows joined with the month's spend; all the domain logic
 * lives in [ObserveBudgetProgressUseCase]. Budgets in currencies other than
 * the primary one are not listed (like every dashboard figure; they come back
 * when their currency becomes primary again).
 */
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    userPreferences: UserPreferencesRepository,
    observeBudgetProgress: ObserveBudgetProgressUseCase,
    clock: Clock,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BudgetsUiState> = combine(
        accountRepository.observeAccountsWithBalance(),
        userPreferences.primaryCurrencyOverride,
        ::Pair,
    )
        .flatMapLatest { (accounts, currencyOverride) ->
            val primary = primaryCurrency(accounts, currencyOverride)
            observeBudgetProgress(primary).map { progresses ->
                BudgetsUiState(
                    isLoading = false,
                    currency = primary,
                    month = YearMonth.now(clock),
                    overall = progresses.firstOrNull { it.budget.isOverall },
                    categoryBudgets = progresses.filterNot { it.budget.isOverall },
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = BudgetsUiState(month = YearMonth.now(clock)),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
