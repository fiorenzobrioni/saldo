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
import javax.inject.Inject

/** Drives the theme and notification choices in Settings; values apply live app-wide. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferencesRepository,
) : ViewModel() {

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
