package com.callbackdev.saldo.core.common.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.BACKUP_ENCRYPTION_ENABLED
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.BALANCE_ACCOUNTS_EXPANDED_DEFAULT
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.CSV_SEPARATOR
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.CURRENCY_CONVERSION_ENABLED
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.DASHBOARD_SHOW_BUDGET_CARD
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.DASHBOARD_SHOW_COUNTERPARTIES
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.DASHBOARD_SHOW_RECAP_TEASER
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.DASHBOARD_SHOW_RECENT_TRANSACTIONS
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.DASHBOARD_SHOW_SAFE_TO_SPEND
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.DASHBOARD_SHOW_SAVINGS_GOALS
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.DASHBOARD_SHOW_UPCOMING
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.DEFAULT_ACCOUNT_ID
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.FIRST_DAY_OF_WEEK
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.PRIMARY_CURRENCY_CODE
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.RENEWAL_REMINDER_ENABLED
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.RENEWAL_REMINDER_LEAD_DAYS
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.THEME_MODE
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.USE_DYNAMIC_COLOR
import com.callbackdev.saldo.core.domain.backup.SettingsBackup
import com.callbackdev.saldo.core.domain.repository.SettingsBackupRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the user's settings as one block, for export and restore
 * (ADR 45).
 *
 * It shares [UserPreferenceKeys] with [UserPreferencesRepository] instead of
 * going through its per-setting flows, for two reasons: the snapshot must see
 * *stored* values and not resolved defaults (a week start that follows the
 * locale must stay that way after a restore, not be frozen to the exporting
 * device's locale), and the restore must be a single atomic edit rather than a
 * dozen writes with the UI recomposing between them.
 *
 * Values are sanitized on the way in, never rejected: an unreadable theme name
 * or an unknown currency code is dropped, so a hand-edited file cannot plant a
 * value that every later read would have to defend against. A backup is never
 * refused over a setting - the data is what matters.
 */
@Singleton
class SettingsBackupStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsBackupRepository {

    override suspend fun snapshotSettings(): SettingsBackup {
        val preferences = dataStore.data.first()
        return SettingsBackup(
            defaultAccountId = preferences[DEFAULT_ACCOUNT_ID],
            primaryCurrencyCode = preferences[PRIMARY_CURRENCY_CODE],
            currencyConversionEnabled = preferences[CURRENCY_CONVERSION_ENABLED],
            themeMode = preferences[THEME_MODE],
            useDynamicColor = preferences[USE_DYNAMIC_COLOR],
            renewalReminderEnabled = preferences[RENEWAL_REMINDER_ENABLED],
            renewalReminderLeadDays = preferences[RENEWAL_REMINDER_LEAD_DAYS],
            firstDayOfWeek = preferences[FIRST_DAY_OF_WEEK],
            csvSeparator = preferences[CSV_SEPARATOR],
            backupEncryptionEnabled = preferences[BACKUP_ENCRYPTION_ENABLED],
            dashboardShowBudget = preferences[DASHBOARD_SHOW_BUDGET_CARD],
            dashboardShowSafeToSpend = preferences[DASHBOARD_SHOW_SAFE_TO_SPEND],
            dashboardShowRecentTransactions = preferences[DASHBOARD_SHOW_RECENT_TRANSACTIONS],
            dashboardShowSavingsGoals = preferences[DASHBOARD_SHOW_SAVINGS_GOALS],
            dashboardShowCounterparties = preferences[DASHBOARD_SHOW_COUNTERPARTIES],
            dashboardShowUpcoming = preferences[DASHBOARD_SHOW_UPCOMING],
            dashboardShowRecapTeaser = preferences[DASHBOARD_SHOW_RECAP_TEASER],
            balanceAccountsExpandedByDefault = preferences[BALANCE_ACCOUNTS_EXPANDED_DEFAULT],
        )
    }

    override suspend fun restoreSettings(settings: SettingsBackup) {
        dataStore.edit { preferences ->
            // Clear first, then write what the file carries: a key left behind
            // would be a setting of this device surviving inside a restored
            // configuration, which is neither state the user asked for.
            preferences.removeAll(UserPreferenceKeys.backedUp)

            preferences.setIfPresent(DEFAULT_ACCOUNT_ID, settings.defaultAccountId)
            preferences.setIfPresent(
                PRIMARY_CURRENCY_CODE,
                settings.primaryCurrencyCode?.takeIf(::isKnownCurrency),
            )
            preferences.setIfPresent(CURRENCY_CONVERSION_ENABLED, settings.currencyConversionEnabled)
            preferences.setIfPresent(THEME_MODE, settings.themeMode?.takeIf(::isKnownThemeMode))
            preferences.setIfPresent(USE_DYNAMIC_COLOR, settings.useDynamicColor)
            preferences.setIfPresent(RENEWAL_REMINDER_ENABLED, settings.renewalReminderEnabled)
            preferences.setIfPresent(
                RENEWAL_REMINDER_LEAD_DAYS,
                settings.renewalReminderLeadDays?.takeIf(::isOfferedLeadTime),
            )
            preferences.setIfPresent(
                FIRST_DAY_OF_WEEK,
                settings.firstDayOfWeek?.takeIf(::isOfferedWeekStart),
            )
            preferences.setIfPresent(CSV_SEPARATOR, settings.csvSeparator?.takeIf(::isKnownSeparator))
            preferences.setIfPresent(BACKUP_ENCRYPTION_ENABLED, settings.backupEncryptionEnabled)
            preferences.setIfPresent(DASHBOARD_SHOW_BUDGET_CARD, settings.dashboardShowBudget)
            preferences.setIfPresent(DASHBOARD_SHOW_SAFE_TO_SPEND, settings.dashboardShowSafeToSpend)
            preferences.setIfPresent(
                DASHBOARD_SHOW_RECENT_TRANSACTIONS,
                settings.dashboardShowRecentTransactions,
            )
            preferences.setIfPresent(DASHBOARD_SHOW_SAVINGS_GOALS, settings.dashboardShowSavingsGoals)
            preferences.setIfPresent(DASHBOARD_SHOW_COUNTERPARTIES, settings.dashboardShowCounterparties)
            preferences.setIfPresent(DASHBOARD_SHOW_UPCOMING, settings.dashboardShowUpcoming)
            preferences.setIfPresent(DASHBOARD_SHOW_RECAP_TEASER, settings.dashboardShowRecapTeaser)
            preferences.setIfPresent(
                BALANCE_ACCOUNTS_EXPANDED_DEFAULT,
                settings.balanceAccountsExpandedByDefault,
            )
        }
    }

    /** Writes [value] only when the file carried one; null means "not set". */
    private fun <T : Any> MutablePreferences.setIfPresent(key: Preferences.Key<T>, value: T?) {
        if (value != null) this[key] = value
    }

    /**
     * Drops [keys] whatever their value type: removal only needs the key's name,
     * so erasing the type here is safe and keeps the caller free of generics.
     */
    @Suppress("UNCHECKED_CAST")
    private fun MutablePreferences.removeAll(keys: List<Preferences.Key<*>>) {
        keys.forEach { key -> remove(key as Preferences.Key<Any>) }
    }

    private fun isKnownCurrency(code: String): Boolean =
        runCatching { Currency.getInstance(code) }.isSuccess

    private fun isKnownThemeMode(name: String): Boolean = ThemeMode.entries.any { it.name == name }

    private fun isKnownSeparator(name: String): Boolean = CsvSeparator.entries.any { it.name == name }

    private fun isOfferedLeadTime(days: Int): Boolean =
        days in RenewalReminderPreferences.allowedLeadDays

    /** A real day of the week is not enough: it has to be one Settings offers. */
    private fun isOfferedWeekStart(name: String): Boolean =
        DayOfWeek.entries.firstOrNull { it.name == name } in FirstDayOfWeek.options
}
