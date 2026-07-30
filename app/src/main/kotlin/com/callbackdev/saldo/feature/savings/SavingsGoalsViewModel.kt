package com.callbackdev.saldo.feature.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.SavingsGoalProgress
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.core.domain.rates.RateTable
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.usecase.ObserveConversionStateUseCase
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
    /** Saved and target summed across the goals the hero can count, for the hero. */
    val totalSaved: BigDecimal = BigDecimal.ZERO,
    val totalTarget: BigDecimal = BigDecimal.ZERO,
    /**
     * Whether some goals stay out of the totals above: every foreign goal
     * with conversion off, only the ones without a usable rate with it on.
     */
    val hasOtherCurrencies: Boolean = false,
    /** True when the hero totals include converted foreign goals (ADR 40). */
    val totalsEstimated: Boolean = false,
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
    observeConversionState: ObserveConversionStateUseCase,
) : ViewModel() {

    val uiState: StateFlow<SavingsGoalsUiState> = combine(
        observeSavingsGoalsProgress(),
        accountRepository.observeAccountsWithBalance(),
        userPreferences.primaryCurrencyOverride,
        observeConversionState(),
    ) { progresses, accounts, override, conversion ->
        val primary = primaryCurrency(accounts, override)
        val rates = if (conversion.active) conversion.rates else RateTable.EMPTY
        // Saved amounts are balances, so foreign goals enter the hero at the
        // latest known rate (ADR 40, stock rule); without a rate they stay
        // out and the notice says so, as before the feature.
        var totalSaved = BigDecimal.ZERO
        var totalTarget = BigDecimal.ZERO
        var estimated = false
        var leftOut = false
        progresses.forEach { progress ->
            val goalCurrency = progress.goal.currency
            if (goalCurrency == primary) {
                totalSaved = totalSaved.add(progress.saved.max(BigDecimal.ZERO))
                totalTarget = totalTarget.add(progress.goal.targetAmount)
                return@forEach
            }
            val saved = CurrencyConverter
                .convertAtLatest(progress.saved.max(BigDecimal.ZERO), goalCurrency, primary, rates)
            val target = CurrencyConverter
                .convertAtLatest(progress.goal.targetAmount, goalCurrency, primary, rates)
            if (saved == null || target == null) {
                leftOut = true
            } else {
                estimated = true
                totalSaved = totalSaved.add(saved.amount)
                totalTarget = totalTarget.add(target.amount)
            }
        }
        SavingsGoalsUiState(
            isLoading = false,
            goals = progresses,
            primaryCurrency = primary,
            totalSaved = totalSaved,
            totalTarget = totalTarget,
            hasOtherCurrencies = leftOut,
            totalsEstimated = estimated,
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
