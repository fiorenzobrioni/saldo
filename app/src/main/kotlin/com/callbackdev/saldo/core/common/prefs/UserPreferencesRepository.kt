package com.callbackdev.saldo.core.common.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Currency
import java.util.Locale
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
 * Which optional dashboard cards are shown. Everything defaults to visible;
 * the core cards (balance, today/month, pending, recurring) are not
 * configurable on purpose: they are the dashboard.
 */
data class DashboardCardPreferences(
    val showBudget: Boolean = true,
    val showSafeToSpend: Boolean = true,
    val showRecentTransactions: Boolean = true,
    val showSavingsGoals: Boolean = true,
    /** The self-expiring monthly recap teaser; off silences it for good. */
    val showRecapTeaser: Boolean = true,
)

/**
 * The CSV column separator offered by the movements export. [SEMICOLON] pairs
 * with comma decimals (what Excel expects in an Italian locale), [COMMA] with
 * dot decimals (the international CSV convention).
 */
enum class CsvSeparator(val symbol: Char) { SEMICOLON(';'), COMMA(',') }

/**
 * The week starts offered in Settings, consumed by the "This week" date
 * preset. The default comes from the locale, snapped to an offered option
 * (some locales start on Friday, which is not offered).
 */
object FirstDayOfWeek {
    val options: List<DayOfWeek> = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun localeDefault(): DayOfWeek = coerce(WeekFields.of(Locale.getDefault()).firstDayOfWeek)

    fun coerce(day: DayOfWeek): DayOfWeek = if (day in options) day else DayOfWeek.MONDAY
}

/**
 * Small UI preferences persisted with DataStore. These are convenience hints
 * (not user data): losing them never loses money information.
 *
 * Every exposed flow is deduplicated: `dataStore.data` re-emits on every
 * write of *any* key, and several of these flows sit upstream of
 * `flatMapLatest` pipelines (dashboard, stats) that would otherwise tear
 * down and rebuild their Room subscriptions on unrelated writes (e.g. the
 * last-used account saved with every movement).
 */
@Suppress("TooManyFunctions") // Flat settings registry: one Flow + setter pair per preference.
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * The account last used to record a movement. The editor preselects it so
     * the typical expense stays within the 3-tap budget; an explicit
     * [defaultAccountId], when set, takes precedence.
     */
    val lastUsedAccountId: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[LAST_USED_ACCOUNT_ID] }
            .distinctUntilChanged()

    suspend fun setLastUsedAccountId(accountId: Long) {
        dataStore.edit { preferences -> preferences[LAST_USED_ACCOUNT_ID] = accountId }
    }

    /**
     * The account the editor preselects for new movements; null means
     * automatic (the last used one). A stale id (archived or deleted
     * account) is skipped by the reader.
     */
    val defaultAccountId: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[DEFAULT_ACCOUNT_ID] }
            .distinctUntilChanged()

    suspend fun setDefaultAccountId(accountId: Long?) {
        dataStore.edit { preferences ->
            if (accountId == null) {
                preferences.remove(DEFAULT_ACCOUNT_ID)
            } else {
                preferences[DEFAULT_ACCOUNT_ID] = accountId
            }
        }
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
    }.distinctUntilChanged()

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
    }.distinctUntilChanged()

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
    }.distinctUntilChanged()

    suspend fun setRenewalReminderEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[RENEWAL_REMINDER_ENABLED] = enabled }
    }

    suspend fun setRenewalReminderLeadDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[RENEWAL_REMINDER_LEAD_DAYS] = days.coerceToAllowedLeadDays()
        }
    }

    /**
     * Whether the first-launch onboarding has been completed. Null means the
     * key was never written: a fresh install, or an install that predates the
     * flag (told apart by whether any account exists).
     */
    val onboardingCompleted: Flow<Boolean?> =
        dataStore.data.map { preferences -> preferences[ONBOARDING_COMPLETED] }
            .distinctUntilChanged()

    suspend fun setOnboardingCompleted() {
        dataStore.edit { preferences -> preferences[ONBOARDING_COMPLETED] = true }
    }

    /** First day of the week for the "This week" filter; defaults from the locale. */
    val firstDayOfWeek: Flow<DayOfWeek> = dataStore.data.map { preferences ->
        preferences[FIRST_DAY_OF_WEEK]
            ?.let { stored -> DayOfWeek.entries.firstOrNull { it.name == stored } }
            ?.let(FirstDayOfWeek::coerce)
            ?: FirstDayOfWeek.localeDefault()
    }.distinctUntilChanged()

    suspend fun setFirstDayOfWeek(day: DayOfWeek) {
        dataStore.edit { preferences ->
            preferences[FIRST_DAY_OF_WEEK] = FirstDayOfWeek.coerce(day).name
        }
    }

    /** Instant of the last successful backup export, epoch millis; null if never. */
    val lastBackupAtEpochMilli: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[LAST_BACKUP_AT_EPOCH_MILLI] }
            .distinctUntilChanged()

    suspend fun setLastBackupAt(epochMilli: Long) {
        dataStore.edit { preferences -> preferences[LAST_BACKUP_AT_EPOCH_MILLI] = epochMilli }
    }

    /** Visibility of the optional dashboard cards; everything shown by default. */
    val dashboardCardPreferences: Flow<DashboardCardPreferences> = dataStore.data.map { preferences ->
        DashboardCardPreferences(
            showBudget = preferences[DASHBOARD_SHOW_BUDGET_CARD] ?: true,
            showSafeToSpend = preferences[DASHBOARD_SHOW_SAFE_TO_SPEND] ?: true,
            showRecentTransactions = preferences[DASHBOARD_SHOW_RECENT_TRANSACTIONS] ?: true,
            showSavingsGoals = preferences[DASHBOARD_SHOW_SAVINGS_GOALS] ?: true,
            showRecapTeaser = preferences[DASHBOARD_SHOW_RECAP_TEASER] ?: true,
        )
    }.distinctUntilChanged()

    suspend fun setShowBudgetCard(shown: Boolean) {
        dataStore.edit { preferences -> preferences[DASHBOARD_SHOW_BUDGET_CARD] = shown }
    }

    suspend fun setShowSafeToSpendCard(shown: Boolean) {
        dataStore.edit { preferences -> preferences[DASHBOARD_SHOW_SAFE_TO_SPEND] = shown }
    }

    suspend fun setShowRecentTransactions(shown: Boolean) {
        dataStore.edit { preferences -> preferences[DASHBOARD_SHOW_RECENT_TRANSACTIONS] = shown }
    }

    suspend fun setShowSavingsGoalsCard(shown: Boolean) {
        dataStore.edit { preferences -> preferences[DASHBOARD_SHOW_SAVINGS_GOALS] = shown }
    }

    suspend fun setShowRecapTeaser(shown: Boolean) {
        dataStore.edit { preferences -> preferences[DASHBOARD_SHOW_RECAP_TEASER] = shown }
    }

    /** Column separator of the CSV export; semicolon by default (Excel friendly). */
    val csvSeparator: Flow<CsvSeparator> = dataStore.data.map { preferences ->
        preferences[CSV_SEPARATOR]
            ?.let { stored -> CsvSeparator.entries.firstOrNull { it.name == stored } }
            ?: CsvSeparator.SEMICOLON
    }.distinctUntilChanged()

    suspend fun setCsvSeparator(separator: CsvSeparator) {
        dataStore.edit { preferences -> preferences[CSV_SEPARATOR] = separator.name }
    }

    /**
     * The month whose recap teaser the user dismissed from the dashboard,
     * stored as "YYYY-MM"; null (or an unparsable value) means no dismissal.
     * The teaser self-expires after the first week of the month, so a single
     * key is enough: dismissing a new month overwrites the previous one.
     */
    val dismissedRecapMonth: Flow<YearMonth?> = dataStore.data.map { preferences ->
        preferences[DISMISSED_RECAP_MONTH]?.let { stored ->
            runCatching { YearMonth.parse(stored) }.getOrNull()
        }
    }.distinctUntilChanged()

    suspend fun setDismissedRecapMonth(month: YearMonth) {
        dataStore.edit { preferences -> preferences[DISMISSED_RECAP_MONTH] = month.toString() }
    }

    /** Snaps a stored or requested lead time to the closest offered option. */
    private fun Int.coerceToAllowedLeadDays(): Int =
        RenewalReminderPreferences.allowedLeadDays.minByOrNull { kotlin.math.abs(it - this) }
            ?: RenewalReminderPreferences.DEFAULT_LEAD_DAYS

    private companion object {
        val LAST_USED_ACCOUNT_ID = longPreferencesKey("last_used_account_id")
        val DEFAULT_ACCOUNT_ID = longPreferencesKey("default_account_id")
        val PRIMARY_CURRENCY_CODE = stringPreferencesKey("primary_currency_code")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val RENEWAL_REMINDER_ENABLED = booleanPreferencesKey("renewal_reminder_enabled")
        val RENEWAL_REMINDER_LEAD_DAYS = intPreferencesKey("renewal_reminder_lead_days")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")
        val LAST_BACKUP_AT_EPOCH_MILLI = longPreferencesKey("last_backup_at_epoch_milli")
        val CSV_SEPARATOR = stringPreferencesKey("csv_separator")
        val DASHBOARD_SHOW_BUDGET_CARD = booleanPreferencesKey("dashboard_show_budget_card")
        val DASHBOARD_SHOW_SAFE_TO_SPEND = booleanPreferencesKey("dashboard_show_safe_to_spend")
        val DASHBOARD_SHOW_RECENT_TRANSACTIONS =
            booleanPreferencesKey("dashboard_show_recent_transactions")
        val DASHBOARD_SHOW_SAVINGS_GOALS = booleanPreferencesKey("dashboard_show_savings_goals")
        val DASHBOARD_SHOW_RECAP_TEASER = booleanPreferencesKey("dashboard_show_recap_teaser")
        val DISMISSED_RECAP_MONTH = stringPreferencesKey("recap_dismissed_month")
    }
}
