package com.callbackdev.saldo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.applock.AppLockRepository
import com.callbackdev.saldo.core.common.prefs.DashboardCardPreferences
import com.callbackdev.saldo.core.common.prefs.FirstDayOfWeek
import com.callbackdev.saldo.core.common.prefs.BackupReminderPreferences
import com.callbackdev.saldo.core.common.prefs.CsvColumnMappingStore
import com.callbackdev.saldo.core.common.prefs.SavedCsvMapping
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
import java.time.DayOfWeek
import java.util.Currency
import javax.inject.Inject

/** Drives the preference, theme and notification choices in Settings; values apply live app-wide. */
@HiltViewModel
@Suppress("TooManyFunctions") // A settings registry naturally has one setter per preference.
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferencesRepository,
    accountRepository: AccountRepository,
    appLockRepository: AppLockRepository,
    private val csvColumnMappingStore: CsvColumnMappingStore,
) : ViewModel() {

    /** The saved CSV column mappings (Fase 39, F5), listed and deleted from Data. */
    val csvMappings: StateFlow<List<SavedCsvMapping>> = csvColumnMappingStore.mappings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    fun onDeleteCsvMapping(name: String) {
        viewModelScope.launch { csvColumnMappingStore.delete(name) }
    }

    /** Whether the app lock is on; drives the Security entry's hint. */
    val appLockEnabled: StateFlow<Boolean> = appLockRepository.lockEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false,
        )

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

    /** First day of the week used by the "This week" filter. */
    val firstDayOfWeek: StateFlow<DayOfWeek> = userPreferences.firstDayOfWeek
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = FirstDayOfWeek.localeDefault(),
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

    val backupReminderPreferences: StateFlow<BackupReminderPreferences> =
        userPreferences.backupReminderPreferences
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = BackupReminderPreferences(),
            )

    /** Visibility of the optional dashboard cards. */
    val dashboardCardPreferences: StateFlow<DashboardCardPreferences> =
        userPreferences.dashboardCardPreferences
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DashboardCardPreferences(),
            )

    /** Whether the Total-balance card opens with its accounts expanded (on by default). */
    val balanceAccountsExpandedByDefault: StateFlow<Boolean> =
        userPreferences.balanceAccountsExpandedByDefault
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = true,
            )

    /**
     * Whether foreign accounts and movements enter the aggregates as
     * estimated countervalues (ADR 40). On by default.
     */
    val currencyConversionEnabled: StateFlow<Boolean> = userPreferences.currencyConversionEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = true,
        )

    /** Persists the primary-currency choice; null returns to automatic. */
    fun onPrimaryCurrencySelected(currency: Currency?) {
        viewModelScope.launch { userPreferences.setPrimaryCurrencyOverride(currency) }
    }

    /** Turns the automatic currency conversion (and its only network use) on or off. */
    fun onCurrencyConversionChanged(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setCurrencyConversionEnabled(enabled) }
    }

    /** Persists the default-account choice; null returns to automatic (last used). */
    fun onDefaultAccountSelected(accountId: Long?) {
        viewModelScope.launch { userPreferences.setDefaultAccountId(accountId) }
    }

    fun onFirstDayOfWeekSelected(day: DayOfWeek) {
        viewModelScope.launch { userPreferences.setFirstDayOfWeek(day) }
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

    fun onBackupReminderChanged(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setBackupReminderEnabled(enabled) }
    }

    fun onBackupReminderIntervalSelected(days: Int) {
        viewModelScope.launch { userPreferences.setBackupReminderIntervalDays(days) }
    }

    fun onShowBudgetCardChanged(shown: Boolean) {
        viewModelScope.launch { userPreferences.setShowBudgetCard(shown) }
    }

    fun onShowSafeToSpendChanged(shown: Boolean) {
        viewModelScope.launch { userPreferences.setShowSafeToSpendCard(shown) }
    }

    fun onShowRecentTransactionsChanged(shown: Boolean) {
        viewModelScope.launch { userPreferences.setShowRecentTransactions(shown) }
    }

    fun onShowRecapTeaserChanged(shown: Boolean) {
        viewModelScope.launch { userPreferences.setShowRecapTeaser(shown) }
    }

    fun onShowSavingsGoalsChanged(shown: Boolean) {
        viewModelScope.launch { userPreferences.setShowSavingsGoalsCard(shown) }
    }

    fun onShowCounterpartiesChanged(shown: Boolean) {
        viewModelScope.launch { userPreferences.setShowCounterpartiesCard(shown) }
    }

    fun onShowUpcomingChanged(shown: Boolean) {
        viewModelScope.launch { userPreferences.setShowUpcomingCard(shown) }
    }

    fun onShowRecurringChanged(shown: Boolean) {
        viewModelScope.launch { userPreferences.setShowRecurringCard(shown) }
    }

    fun onShowMonthComparisonChanged(shown: Boolean) {
        viewModelScope.launch { userPreferences.setShowMonthComparisonCard(shown) }
    }

    fun onBalanceAccountsExpandedDefaultChanged(expanded: Boolean) {
        viewModelScope.launch { userPreferences.setBalanceAccountsExpandedByDefault(expanded) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
