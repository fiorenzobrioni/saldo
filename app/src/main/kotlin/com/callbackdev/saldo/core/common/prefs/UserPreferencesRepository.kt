package com.callbackdev.saldo.core.common.prefs

import androidx.datastore.core.DataStore
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
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.DISMISSED_RECAP_MONTH
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.FIRST_DAY_OF_WEEK
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.LAST_BACKUP_AT_EPOCH_MILLI
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.LAST_RATE_SYNC_ATTEMPT_EPOCH_MILLI
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.LAST_USED_ACCOUNT_ID
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.ONBOARDING_COMPLETED
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.PRIMARY_CURRENCY_CODE
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.RENEWAL_REMINDER_ENABLED
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.RENEWAL_REMINDER_LEAD_DAYS
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.THEME_MODE
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.USE_DYNAMIC_COLOR
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
    /**
     * Credits and debts toward people. On by default, but unlike the budget and
     * savings cards it never shows an invitation: with nobody owing anything the
     * card is simply absent, so a user who never lends money sees no trace of a
     * feature they do not use.
     */
    val showCounterparties: Boolean = true,
    /**
     * What is coming: future-dated movements and occurrences to confirm. Like
     * the credits card it has no invitation state - with nothing ahead there is
     * nothing to preview - but unlike it, it always has the "to confirm" card as
     * a second way in, so turning it off never buries the queue.
     */
    val showUpcoming: Boolean = true,
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

    /**
     * Whether foreign-currency accounts and movements enter the aggregates as
     * estimated countervalues in the primary currency (ADR 40). On by
     * default: the feature exists to fix a wrong headline figure, and with no
     * foreign data the default costs nothing because no fetch ever starts.
     * Off returns every surface to the strict single-currency behavior and
     * stops the only network traffic of the app outside backup and export.
     */
    val currencyConversionEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[CURRENCY_CONVERSION_ENABLED] ?: true }
            .distinctUntilChanged()

    suspend fun setCurrencyConversionEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[CURRENCY_CONVERSION_ENABLED] = enabled }
    }

    /**
     * Instant of the last ECB rate sync attempt (epoch millis), successful or
     * not; null if never tried. The sync policy throttles on it, so a feed
     * outage does not turn every app open into a network call.
     */
    val lastRateSyncAttemptEpochMilli: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[LAST_RATE_SYNC_ATTEMPT_EPOCH_MILLI] }
            .distinctUntilChanged()

    suspend fun setLastRateSyncAttempt(epochMilli: Long) {
        dataStore.edit { preferences -> preferences[LAST_RATE_SYNC_ATTEMPT_EPOCH_MILLI] = epochMilli }
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

    /**
     * Whether the export asks for a passphrase and encrypts the file (Fase 22).
     * Off by default: encryption is a deliberate choice with a real cost - a
     * lost passphrase is a lost backup - so it is never turned on for the user.
     * The passphrase itself is never stored, only this choice.
     */
    val backupEncryptionEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[BACKUP_ENCRYPTION_ENABLED] ?: false }
            .distinctUntilChanged()

    suspend fun setBackupEncryptionEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[BACKUP_ENCRYPTION_ENABLED] = enabled }
    }

    /** Visibility of the optional dashboard cards; everything shown by default. */
    val dashboardCardPreferences: Flow<DashboardCardPreferences> = dataStore.data.map { preferences ->
        DashboardCardPreferences(
            showBudget = preferences[DASHBOARD_SHOW_BUDGET_CARD] ?: true,
            showSafeToSpend = preferences[DASHBOARD_SHOW_SAFE_TO_SPEND] ?: true,
            showRecentTransactions = preferences[DASHBOARD_SHOW_RECENT_TRANSACTIONS] ?: true,
            showSavingsGoals = preferences[DASHBOARD_SHOW_SAVINGS_GOALS] ?: true,
            showCounterparties = preferences[DASHBOARD_SHOW_COUNTERPARTIES] ?: true,
            showUpcoming = preferences[DASHBOARD_SHOW_UPCOMING] ?: true,
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

    suspend fun setShowCounterpartiesCard(shown: Boolean) {
        dataStore.edit { preferences -> preferences[DASHBOARD_SHOW_COUNTERPARTIES] = shown }
    }

    suspend fun setShowUpcomingCard(shown: Boolean) {
        dataStore.edit { preferences -> preferences[DASHBOARD_SHOW_UPCOMING] = shown }
    }

    suspend fun setShowRecapTeaser(shown: Boolean) {
        dataStore.edit { preferences -> preferences[DASHBOARD_SHOW_RECAP_TEASER] = shown }
    }

    /**
     * Whether the Total-balance card opens with its per-account breakdown
     * already expanded. On by default (a fresh install shows the accounts). This
     * is only the starting state each time the app opens: the in-session
     * open/close toggle lives in the dashboard ViewModel, so it survives
     * scrolling and navigation and falls back to this default on a fresh launch.
     */
    val balanceAccountsExpandedByDefault: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BALANCE_ACCOUNTS_EXPANDED_DEFAULT] ?: true
    }.distinctUntilChanged()

    suspend fun setBalanceAccountsExpandedByDefault(expanded: Boolean) {
        dataStore.edit { preferences -> preferences[BALANCE_ACCOUNTS_EXPANDED_DEFAULT] = expanded }
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

    /**
     * Drops every stored preference, returning the app to its first-launch
     * defaults. Only the "erase all data" action calls this: clearing the
     * onboarding flag here is what makes the app open on the onboarding again,
     * exactly as a fresh install would.
     */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    /** Snaps a stored or requested lead time to the closest offered option. */
    private fun Int.coerceToAllowedLeadDays(): Int =
        RenewalReminderPreferences.allowedLeadDays.minByOrNull { kotlin.math.abs(it - this) }
            ?: RenewalReminderPreferences.DEFAULT_LEAD_DAYS
}
