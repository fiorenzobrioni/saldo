package com.callbackdev.saldo.feature.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.SavingsGoalProgress
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.usecase.ObserveSavingsGoalsProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.util.Currency
import javax.inject.Inject

/** Immutable UI state for the savings goals list. */
data class SavingsGoalsUiState(
    val isLoading: Boolean = true,
    val goals: List<SavingsGoalProgress> = emptyList(),
    val primaryCurrency: Currency = fallbackCurrency,
    /** Saved and target summed across the primary-currency goals, for the hero. */
    val totalSaved: BigDecimal = BigDecimal.ZERO,
    val totalTarget: BigDecimal = BigDecimal.ZERO,
    /** Whether some goals are in another currency (excluded from the totals above). */
    val hasOtherCurrencies: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && goals.isEmpty()
}

/**
 * Drives the savings goals list: every goal with its progress, plus a
 * primary-currency total across them. Figures come from
 * [ObserveSavingsGoalsProgressUseCase]; the primary currency mirrors the rest
 * of the app (explicit override, else the account majority).
 */
@HiltViewModel
class SavingsGoalsViewModel @Inject constructor(
    observeSavingsGoalsProgress: ObserveSavingsGoalsProgressUseCase,
    accountRepository: AccountRepository,
    userPreferences: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SavingsGoalsUiState> = combine(
        observeSavingsGoalsProgress(),
        accountRepository.observeAccountsWithBalance(),
        userPreferences.primaryCurrencyOverride,
    ) { progresses, accounts, override ->
        val primary = primaryCurrency(accounts, override)
        val primaryGoals = progresses.filter { it.goal.currency == primary }
        SavingsGoalsUiState(
            isLoading = false,
            goals = progresses,
            primaryCurrency = primary,
            totalSaved = primaryGoals.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.saved.max(BigDecimal.ZERO)) },
            totalTarget = primaryGoals.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.goal.targetAmount) },
            hasOtherCurrencies = progresses.any { it.goal.currency != primary },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SavingsGoalsUiState(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
