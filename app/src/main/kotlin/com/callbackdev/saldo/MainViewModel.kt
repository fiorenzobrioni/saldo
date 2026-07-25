package com.callbackdev.saldo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.usecase.AppResetCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the app shows at launch: nothing yet, the onboarding, or the app itself. */
enum class LaunchGate { LOADING, ONBOARDING, APP }

/**
 * Decides once per process whether the first-launch onboarding should show.
 * Existing installs must never see it: when the flag predates the update
 * (never written) but the database already has accounts, the flag is set
 * silently and the app opens normally. Any read failure also opens the app:
 * blocking an existing user is worse than skipping the onboarding.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferences: UserPreferencesRepository,
    accountRepository: AccountRepository,
    resetCoordinator: AppResetCoordinator,
) : ViewModel() {

    private val _gate = MutableStateFlow(LaunchGate.LOADING)
    val gate: StateFlow<LaunchGate> = _gate.asStateFlow()

    init {
        // An "erase all data" leaves the app in the state of a fresh install, so
        // it must also land where a fresh install lands: on the onboarding,
        // without the user having to restart the app to be asked their currency.
        viewModelScope.launch {
            resetCoordinator.events.collect { _gate.value = LaunchGate.ONBOARDING }
        }
        viewModelScope.launch {
            val completed = runCatching { userPreferences.onboardingCompleted.first() }
                .getOrDefault(true)
            _gate.value = if (completed == true) {
                LaunchGate.APP
            } else {
                val hasAccounts = runCatching {
                    accountRepository.observeAccountsWithBalance().first().isNotEmpty()
                }.getOrDefault(true)
                if (hasAccounts) {
                    runCatching { userPreferences.setOnboardingCompleted() }
                    LaunchGate.APP
                } else {
                    LaunchGate.ONBOARDING
                }
            }
        }
    }

    /** Marks onboarding done and switches to the app; called by the last page. */
    fun completeOnboarding() {
        _gate.value = LaunchGate.APP
        viewModelScope.launch {
            runCatching { userPreferences.setOnboardingCompleted() }
        }
    }
}
