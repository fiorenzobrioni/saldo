package com.callbackdev.saldo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.prefs.RenewalReminderPreferences
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Currency
import javax.inject.Inject

/** Drives the preference, theme and notification choices in Settings; values apply live app-wide. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferencesRepository,
) : ViewModel() {

    /** The explicit primary currency; null shows as "Automatic". */
    val primaryCurrencyOverride: StateFlow<Currency?> = userPreferences.primaryCurrencyOverride
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
