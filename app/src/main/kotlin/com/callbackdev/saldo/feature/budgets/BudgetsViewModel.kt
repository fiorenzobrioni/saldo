package com.callbackdev.saldo.feature.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.BudgetProgress
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.usecase.ObserveBudgetProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveConversionStateUseCase
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
    /** The overall monthly budget's progress in [currency], when one is set. */
    val overall: BudgetProgress? = null,
    /**
     * Overall budgets in other currencies (ADR 40): visible only with
     * conversion on, rendered as their own cards under the hero. Empty in the
     * ordinary single-currency case.
     */
    val otherOverall: List<BudgetProgress> = emptyList(),
    /** Category budgets, closest to their cap first. */
    val categoryBudgets: List<BudgetProgress> = emptyList(),
    /** Whether conversion is on with at least one usable rate (ADR 40). */
    val conversionActive: Boolean = false,
) {
    val isEmpty: Boolean get() = overall == null && otherOverall.isEmpty() && categoryBudgets.isEmpty()
}

/**
 * Budgets are plain rows joined with the month's spend; all the domain logic
 * lives in [ObserveBudgetProgressUseCase]. With conversion off, budgets in
 * currencies other than the primary one are not listed (they come back when
 * their currency becomes primary again); with conversion on they stay
 * visible, each measured in its own currency with foreign spend converted at
 * the rate of the movement's day (ADR 40, closing review limit 2).
 */
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    userPreferences: UserPreferencesRepository,
    observeBudgetProgress: ObserveBudgetProgressUseCase,
    observeConversionState: ObserveConversionStateUseCase,
    clock: Clock,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BudgetsUiState> = combine(
        accountRepository.observeAccountsWithBalance(),
        userPreferences.primaryCurrencyOverride,
        observeConversionState(),
        ::Triple,
    )
        .flatMapLatest { (accounts, currencyOverride, conversion) ->
            val primary = primaryCurrency(accounts, currencyOverride)
            observeBudgetProgress(primary, conversion).map { progresses ->
                val overall = progresses.filter { it.budget.isOverall }
                BudgetsUiState(
                    isLoading = false,
                    currency = primary,
                    month = YearMonth.now(clock),
                    overall = overall.firstOrNull { it.budget.currency == primary },
                    otherOverall = overall.filterNot { it.budget.currency == primary },
                    categoryBudgets = progresses.filterNot { it.budget.isOverall },
                    conversionActive = conversion.active,
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
