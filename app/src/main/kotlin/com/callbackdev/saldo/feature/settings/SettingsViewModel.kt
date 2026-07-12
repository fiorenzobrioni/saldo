package com.callbackdev.saldo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.prefs.RenewalReminderPreferences
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Currency
import javax.inject.Inject

/** Drives the preference, theme and notification choices in Settings; values apply live app-wide. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferencesRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    /** The explicit primary currency; null shows as "Automatic". */
    val primaryCurrencyOverride: StateFlow<Currency?> = userPreferences.primaryCurrencyOverride
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    /** Active (non-archived) accounts offered by the default-account picker. */
    val activeAccounts: StateFlow<List<Account>> = accountRepository.observeAccountsWithBalance()
        .map { accounts -> accounts.map { it.account }.filter { !it.isArchived } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    /** The explicit default account id; null shows as "Automatic (last used)". */
    val defaultAccountId: StateFlow<Long?> = userPreferences.defaultAccountId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    val themePreferences: StateFlow<ThemePreferences> = userPreferences.themePreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ThemePreferences(),
        )

    val renewalReminderPreferences: StateFlow<RenewalReminderPreferences> =
        userPreferences.renewalReminderPreferences
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = RenewalReminderPreferences(),
            )

    /** Persists the primary-currency choice; null returns to automatic. */
    fun onPrimaryCurrencySelected(currency: Currency?) {
        viewModelScope.launch { userPreferences.setPrimaryCurrencyOverride(currency) }
    }

    /** Persists the default-account choice; null returns to automatic (last used). */
    fun onDefaultAccountSelected(accountId: Long?) {
        viewModelScope.launch { userPreferences.setDefaultAccountId(accountId) }
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    fun onDynamicColorChanged(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setUseDynamicColor(enabled) }
    }

    fun onRenewalReminderChanged(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setRenewalReminderEnabled(enabled) }
    }

    fun onRenewalLeadDaysSelected(days: Int) {
        viewModelScope.launch { userPreferences.setRenewalReminderLeadDays(days) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
