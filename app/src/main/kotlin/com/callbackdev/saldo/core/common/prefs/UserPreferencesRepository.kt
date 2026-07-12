package com.callbackdev.saldo.core.common.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/** How the app resolves light/dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Theme choices persisted across launches. Brand palette is the default. */
data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
)

/**
 * Pre-renewal reminder ("Netflix renews in 3 days") preferences. Off by
 * default: a notification that appears unrequested after an update is worse
 * than one extra Settings tap.
 */
data class RenewalReminderPreferences(
    val enabled: Boolean = false,
    val leadDays: Int = DEFAULT_LEAD_DAYS,
) {
    companion object {
        const val DEFAULT_LEAD_DAYS = 3

        /** The lead times offered in Settings, in days before the charge. */
        val allowedLeadDays: List<Int> = listOf(1, 2, 3, 7)
    }
}

/**
 * The CSV column separator offered by the movements export. [SEMICOLON] pairs
 * with comma decimals (what Excel expects in an Italian locale), [COMMA] with
 * dot decimals (the international CSV convention).
 */
enum class CsvSeparator(val symbol: Char) { SEMICOLON(';'), COMMA(',') }

/**
 * Small UI preferences persisted with DataStore. These are convenience hints
 * (not user data): losing them never loses money information.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * The account last used to record a movement. The editor preselects it so
     * the typical expense stays within the 3-tap budget; Phase 9 will add an
     * explicit default-account setting on top.
     */
    val lastUsedAccountId: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[LAST_USED_ACCOUNT_ID] }

    suspend fun setLastUsedAccountId(accountId: Long) {
        dataStore.edit { preferences -> preferences[LAST_USED_ACCOUNT_ID] = accountId }
    }

    /**
     * The user-chosen primary currency; null means automatic (the currency
     * shared by most accounts included in the total). Stored as an ISO 4217
     * code; an invalid stored code reads back as automatic.
     */
    val primaryCurrencyOverride: Flow<Currency?> = dataStore.data.map { preferences ->
        preferences[PRIMARY_CURRENCY_CODE]?.let { code ->
            runCatching { Currency.getInstance(code) }.getOrNull()
        }
    }

    suspend fun setPrimaryCurrencyOverride(currency: Currency?) {
        dataStore.edit { preferences ->
            if (currency == null) {
                preferences.remove(PRIMARY_CURRENCY_CODE)
            } else {
                preferences[PRIMARY_CURRENCY_CODE] = currency.currencyCode
            }
        }
    }

    val themePreferences: Flow<ThemePreferences> = dataStore.data.map { preferences ->
        ThemePreferences(
            mode = preferences[THEME_MODE]
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: ThemeMode.SYSTEM,
            useDynamicColor = preferences[USE_DYNAMIC_COLOR] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[THEME_MODE] = mode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[USE_DYNAMIC_COLOR] = enabled }
    }

    val renewalReminderPreferences: Flow<RenewalReminderPreferences> = dataStore.data.map { preferences ->
        RenewalReminderPreferences(
            enabled = preferences[RENEWAL_REMINDER_ENABLED] ?: false,
            leadDays = (preferences[RENEWAL_REMINDER_LEAD_DAYS] ?: RenewalReminderPreferences.DEFAULT_LEAD_DAYS)
                .coerceToAllowedLeadDays(),
        )
    }

    suspend fun setRenewalReminderEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[RENEWAL_REMINDER_ENABLED] = enabled }
    }

    suspend fun setRenewalReminderLeadDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[RENEWAL_REMINDER_LEAD_DAYS] = days.coerceToAllowedLeadDays()
        }
    }

    /** Instant of the last successful backup export, epoch millis; null if never. */
    val lastBackupAtEpochMilli: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[LAST_BACKUP_AT_EPOCH_MILLI] }

    suspend fun setLastBackupAt(epochMilli: Long) {
        dataStore.edit { preferences -> preferences[LAST_BACKUP_AT_EPOCH_MILLI] = epochMilli }
    }

    /** Column separator of the CSV export; semicolon by default (Excel friendly). */
    val csvSeparator: Flow<CsvSeparator> = dataStore.data.map { preferences ->
        preferences[CSV_SEPARATOR]
            ?.let { stored -> CsvSeparator.entries.firstOrNull { it.name == stored } }
            ?: CsvSeparator.SEMICOLON
    }

    suspend fun setCsvSeparator(separator: CsvSeparator) {
        dataStore.edit { preferences -> preferences[CSV_SEPARATOR] = separator.name }
    }

    /** Snaps a stored or requested lead time to the closest offered option. */
    private fun Int.coerceToAllowedLeadDays(): Int =
        RenewalReminderPreferences.allowedLeadDays.minByOrNull { kotlin.math.abs(it - this) }
            ?: RenewalReminderPreferences.DEFAULT_LEAD_DAYS

    private companion object {
        val LAST_USED_ACCOUNT_ID = longPreferencesKey("last_used_account_id")
        val PRIMARY_CURRENCY_CODE = stringPreferencesKey("primary_currency_code")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val RENEWAL_REMINDER_ENABLED = booleanPreferencesKey("renewal_reminder_enabled")
        val RENEWAL_REMINDER_LEAD_DAYS = intPreferencesKey("renewal_reminder_lead_days")
        val LAST_BACKUP_AT_EPOCH_MILLI = longPreferencesKey("last_backup_at_epoch_milli")
        val CSV_SEPARATOR = stringPreferencesKey("csv_separator")
    }
}
