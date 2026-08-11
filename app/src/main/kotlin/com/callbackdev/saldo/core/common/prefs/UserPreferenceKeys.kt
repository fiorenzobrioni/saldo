package com.callbackdev.saldo.core.common.prefs

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * The keys of the `user_preferences` store, in one place because two classes
 * need them: [UserPreferencesRepository], which reads and writes them one at a
 * time for the UI, and [SettingsBackupStore], which snapshots and restores them
 * as a block. Two private lists would drift apart at the first setting added,
 * and a setting missing from a backup is exactly the kind of gap that is only
 * noticed on a new phone.
 */
internal object UserPreferenceKeys {
    val LAST_USED_ACCOUNT_ID = longPreferencesKey("last_used_account_id")
    val DEFAULT_ACCOUNT_ID = longPreferencesKey("default_account_id")
    val PRIMARY_CURRENCY_CODE = stringPreferencesKey("primary_currency_code")
    val CURRENCY_CONVERSION_ENABLED = booleanPreferencesKey("currency_conversion_enabled")
    val LAST_RATE_SYNC_ATTEMPT_EPOCH_MILLI = longPreferencesKey("last_rate_sync_attempt_epoch_milli")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val RENEWAL_REMINDER_ENABLED = booleanPreferencesKey("renewal_reminder_enabled")
    val RENEWAL_REMINDER_LEAD_DAYS = intPreferencesKey("renewal_reminder_lead_days")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")
    val LAST_BACKUP_AT_EPOCH_MILLI = longPreferencesKey("last_backup_at_epoch_milli")
    val BACKUP_ENCRYPTION_ENABLED = booleanPreferencesKey("backup_encryption_enabled")
    val CSV_SEPARATOR = stringPreferencesKey("csv_separator")
    val DASHBOARD_SHOW_BUDGET_CARD = booleanPreferencesKey("dashboard_show_budget_card")
    val DASHBOARD_SHOW_SAFE_TO_SPEND = booleanPreferencesKey("dashboard_show_safe_to_spend")
    val DASHBOARD_SHOW_RECENT_TRANSACTIONS = booleanPreferencesKey("dashboard_show_recent_transactions")
    val DASHBOARD_SHOW_SAVINGS_GOALS = booleanPreferencesKey("dashboard_show_savings_goals")
    val DASHBOARD_SHOW_COUNTERPARTIES = booleanPreferencesKey("dashboard_show_counterparties")
    val DASHBOARD_SHOW_UPCOMING = booleanPreferencesKey("dashboard_show_upcoming")
    val DASHBOARD_SHOW_RECURRING = booleanPreferencesKey("dashboard_show_recurring")
    val DASHBOARD_SHOW_MONTH_COMPARISON = booleanPreferencesKey("dashboard_show_month_comparison")
    val DASHBOARD_SHOW_RECAP_TEASER = booleanPreferencesKey("dashboard_show_recap_teaser")
    val BALANCE_ACCOUNTS_EXPANDED_DEFAULT = booleanPreferencesKey("balance_accounts_expanded_default")
    val MONTH_COMPARISON_EXPANDED = booleanPreferencesKey("month_comparison_expanded")
    val DISMISSED_RECAP_MONTH = stringPreferencesKey("recap_dismissed_month")

    /**
     * The keys a backup carries, and therefore the exact set a restore may
     * touch. Everything else in this store describes *this* install and must
     * survive a restore untouched: what account was used last, when this device
     * last exported, whether its onboarding is done, the rate sync watermark and
     * the recap teaser already dismissed here.
     */
    val backedUp: List<Preferences.Key<*>> = listOf(
        DEFAULT_ACCOUNT_ID,
        PRIMARY_CURRENCY_CODE,
        CURRENCY_CONVERSION_ENABLED,
        THEME_MODE,
        USE_DYNAMIC_COLOR,
        RENEWAL_REMINDER_ENABLED,
        RENEWAL_REMINDER_LEAD_DAYS,
        FIRST_DAY_OF_WEEK,
        CSV_SEPARATOR,
        BACKUP_ENCRYPTION_ENABLED,
        DASHBOARD_SHOW_BUDGET_CARD,
        DASHBOARD_SHOW_SAFE_TO_SPEND,
        DASHBOARD_SHOW_RECENT_TRANSACTIONS,
        DASHBOARD_SHOW_SAVINGS_GOALS,
        DASHBOARD_SHOW_COUNTERPARTIES,
        DASHBOARD_SHOW_UPCOMING,
        DASHBOARD_SHOW_RECURRING,
        DASHBOARD_SHOW_MONTH_COMPARISON,
        DASHBOARD_SHOW_RECAP_TEASER,
        BALANCE_ACCOUNTS_EXPANDED_DEFAULT,
        MONTH_COMPARISON_EXPANDED,
    )
}
