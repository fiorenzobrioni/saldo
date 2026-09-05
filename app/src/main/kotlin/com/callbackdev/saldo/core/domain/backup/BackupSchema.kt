package com.callbackdev.saldo.core.domain.backup

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Versioned on-disk backup format (PLANNING ADR 5): a single JSON document
 * carrying every table of the database. The same schema serves the manual
 * file backup (SAF, ADR 13) today and any future cloud backup, so there is
 * exactly one export/restore code path.
 *
 * Stability rules for this schema:
 * - fields are primitives only (amounts as `Long` minor units, dates as epoch
 *   values, enums as their names), mirroring the storage representation, so
 *   the file never depends on domain or Room types;
 * - fields are only ever **added**, with defaults, never renamed or removed;
 *   any breaking change bumps [BackupFile.CURRENT_VERSION];
 * - decoding ignores unknown keys, so an older app can at least parse a newer
 *   file far enough to read its version and refuse it with a clear message.
 */
@Serializable
data class BackupFile(
    /** Discriminator that identifies a Saldo backup; always [FORMAT]. */
    val format: String = FORMAT,
    /** Schema version of [data]; see [CURRENT_VERSION]. */
    val version: Int,
    /** Instant of the export, epoch milliseconds. */
    val exportedAtEpochMilli: Long,
    /** `versionName` of the app that produced the file, for diagnostics. */
    val appVersion: String? = null,
    val data: BackupData,
) {
    companion object {
        const val FORMAT = "saldo-backup"
        const val CURRENT_VERSION = 1
    }
}

/** The full database content. Insert order on restore: parents before children. */
@Serializable
data class BackupData(
    val accounts: List<AccountBackup> = emptyList(),
    val categories: List<CategoryBackup> = emptyList(),
    val tags: List<TagBackup> = emptyList(),
    val recurringRules: List<RecurringRuleBackup> = emptyList(),
    val transactions: List<TransactionBackup> = emptyList(),
    val transactionTags: List<TransactionTagBackup> = emptyList(),
    /** Added with the budgets feature; older files simply restore none. */
    val budgets: List<BudgetBackup> = emptyList(),
    /** Added with the savings goals feature; older files simply restore none. */
    val savingsGoals: List<SavingsGoalBackup> = emptyList(),
    /**
     * The user's own settings, added when the file became a complete picture of
     * the app and not only of its database. Null on files written before that,
     * which restore their data and leave the settings of this install alone.
     */
    val settings: SettingsBackup? = null,
)

/**
 * Every setting the user chose (ADR 45), so a restore on a new device does not
 * ask them to configure the app from scratch. Each field is nullable and null means
 * "never set", which is not the same as the default value: a null
 * [firstDayOfWeek] keeps following the device locale, while a stored `MONDAY`
 * pins it regardless of locale.
 *
 * Deliberately **not** here, each for its own reason:
 * - the app-lock PIN and its options (ADR 39): a 6-digit hash in an exported
 *   file is brute-forced offline in seconds, and encrypting the export is the
 *   user's option, not a guarantee the format can rely on;
 * - the exchange rate cache (ADR 40) and the recurrence scan result (ADR 43):
 *   derived data, rebuilt on demand, never the user's own;
 * - the per-instance widget configuration: it belongs to widgets placed on
 *   *this* launcher, and their ids mean nothing on another device;
 * - device history and session state (last used account, last backup instant,
 *   onboarding flag, rate sync watermark, dismissed recap month): facts about
 *   this install, which a restore must not overwrite with another one's.
 */
@Serializable
data class SettingsBackup(
    val defaultAccountId: Long? = null,
    /** ISO 4217 code; null means the automatic primary currency. */
    val primaryCurrencyCode: String? = null,
    val currencyConversionEnabled: Boolean? = null,
    /** [com.callbackdev.saldo.core.common.prefs.ThemeMode] name. */
    val themeMode: String? = null,
    val useDynamicColor: Boolean? = null,
    val renewalReminderEnabled: Boolean? = null,
    val renewalReminderLeadDays: Int? = null,
    /** Fase 39 (F4): absent in older files. */
    val backupReminderEnabled: Boolean? = null,
    val backupReminderIntervalDays: Int? = null,
    /** Fase 39 (F5): the saved CSV column mappings; absent or empty in older files. */
    val csvColumnMappings: List<CsvColumnMappingBackup>? = null,
    /** [java.time.DayOfWeek] name; null keeps following the locale. */
    val firstDayOfWeek: String? = null,
    /** [com.callbackdev.saldo.core.common.prefs.CsvSeparator] name. */
    val csvSeparator: String? = null,
    /** Whether the export asks for a passphrase and encrypts the file. */
    val backupEncryptionEnabled: Boolean? = null,
    val dashboardShowBudget: Boolean? = null,
    val dashboardShowSafeToSpend: Boolean? = null,
    val dashboardShowRecentTransactions: Boolean? = null,
    val dashboardShowSavingsGoals: Boolean? = null,
    val dashboardShowCounterparties: Boolean? = null,
    val dashboardShowUpcoming: Boolean? = null,
    val dashboardShowRecapTeaser: Boolean? = null,
    val balanceAccountsExpandedByDefault: Boolean? = null,
)

/**
 * A saved CSV column mapping (Fase 39, F5): importer field *names* to column
 * indices, for a file with this header. Names, not enum ordinals, so a field
 * this version does not know is dropped on restore instead of shifting the rest.
 */
@Serializable
data class CsvColumnMappingBackup(
    val name: String,
    val header: List<String>,
    val fields: Map<String, Int>,
    /** `"."` or `","` when forced; null lets the file decide. */
    val decimalMark: String? = null,
)

@Serializable
data class AccountBackup(
    val id: Long,
    val name: String,
    /** [com.callbackdev.saldo.core.domain.model.AccountType] name. */
    val type: String,
    /** ISO 4217 code. */
    val currency: String,
    val initialBalanceMinor: Long,
    val color: Int? = null,
    val icon: String? = null,
    val isIncludedInTotal: Boolean = true,
    /** Added with the budget-exclusion flag; older files default to included. */
    val isIncludedInBudget: Boolean = true,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtEpochMilli: Long = 0L,
    // Credit card fields, added with the credit card feature; older files and
    // non-credit-card accounts leave them null/false.
    val creditLimitMinor: Long? = null,
    val statementClosingDay: Int? = null,
    val paymentDueDay: Int? = null,
    val linkedAccountId: Long? = null,
    val statementAutoPost: Boolean = false,
    val lastSettledClosingEpochDay: Long? = null,
)

@Serializable
data class CategoryBackup(
    val id: Long,
    val name: String,
    /** [com.callbackdev.saldo.core.domain.model.CategoryType] name. */
    val type: String,
    val color: Int,
    val icon: String,
    val sortOrder: Int = 0,
    /**
     * Income tab's manual order. Null on backups written before per-tab
     * ordering: on import it falls back to [sortOrder], which was the shared key.
     */
    val sortOrderIncome: Int? = null,
    val isDefault: Boolean = false,
)

@Serializable
data class TagBackup(
    val id: Long,
    val name: String,
)

@Serializable
data class RecurringRuleBackup(
    val id: Long,
    val name: String,
    /** [com.callbackdev.saldo.core.domain.model.TransactionType] name. */
    val type: String,
    val currency: String,
    val accountId: Long,
    /** [com.callbackdev.saldo.core.domain.model.RecurrenceFrequency] name. */
    val frequency: String,
    val startDateEpochDay: Long,
    val amountMinor: Long? = null,
    val categoryId: Long? = null,
    val dayOfReference: Int? = null,
    val endDateEpochDay: Long? = null,
    /** [com.callbackdev.saldo.core.domain.model.RecurrenceMode] name. */
    val mode: String,
    val isVariableAmount: Boolean = false,
    val lastGeneratedEpochDay: Long? = null,
    val color: Int? = null,
    val icon: String? = null,
    val note: String? = null,
    val lastReminderEpochDay: Long? = null,
    val transferAccountId: Long? = null,
    val transferAmountMinor: Long? = null,
    val transferCurrency: String? = null,
    /** Fase 39 (F3): absent in older backups, which only had active rules. */
    val isPaused: Boolean = false,
)

@Serializable
data class TransactionBackup(
    val id: Long,
    /** [com.callbackdev.saldo.core.domain.model.TransactionType] name. */
    val type: String,
    val amountMinor: Long,
    val currency: String,
    val accountId: Long,
    val timestampEpochMilli: Long,
    val zoneOffsetSeconds: Int,
    val transferAccountId: Long? = null,
    val transferAmountMinor: Long? = null,
    val transferCurrency: String? = null,
    val categoryId: Long? = null,
    val description: String? = null,
    val note: String? = null,
    val isExcludedFromStats: Boolean = false,
    val isRefund: Boolean = false,
    val recurringRuleId: Long? = null,
    val isPending: Boolean = false,
    val recurringOccurrenceEpochDay: Long? = null,
    /**
     * Added with loans between people (ADR 34); older files restore movements
     * without a counterparty, which is what they were.
     */
    val counterparty: String? = null,
    /**
     * Added with future movements and their reminders (ADR 36). The watermark
     * travels with the flag, like the recurring rules' one, so restoring a
     * backup does not re-notify about a date already announced.
     */
    val hasReminder: Boolean = false,
    val lastReminderEpochDay: Long? = null,
)

@Serializable
data class TransactionTagBackup(
    val transactionId: Long,
    val tagId: Long,
)

@Serializable
data class BudgetBackup(
    val id: Long,
    /** Null for the overall monthly budget. */
    val categoryId: Long? = null,
    val amountMinor: Long,
    /** ISO 4217 code. */
    val currency: String,
    /** Notification watermarks (proleptic month), kept so a restore does not re-alert. */
    val lastNotified80EpochMonth: Long? = null,
    val lastNotified100EpochMonth: Long? = null,
)

@Serializable
data class SavingsGoalBackup(
    val id: Long,
    val name: String,
    val targetAmountMinor: Long,
    /** ISO 4217 code (matches the linked account's currency). */
    val currency: String,
    val accountId: Long,
    val targetDateEpochDay: Long? = null,
    val color: Int? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
)

/** What a backup contains, for the export confirmation and the guided restore. */
data class BackupSummary(
    val exportedAt: Instant,
    val appVersion: String?,
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val recurringRules: Int,
    val tags: Int,
    val budgets: Int = 0,
    val savingsGoals: Int = 0,
    /** Whether the file carries the settings too, stated in the restore gate. */
    val hasSettings: Boolean = false,
    /** Whether the file was read out of an encrypted container (Fase 22). */
    val isEncrypted: Boolean = false,
)

fun BackupFile.summary(isEncrypted: Boolean = false): BackupSummary = BackupSummary(
    exportedAt = Instant.ofEpochMilli(exportedAtEpochMilli),
    appVersion = appVersion,
    accounts = data.accounts.size,
    categories = data.categories.size,
    transactions = data.transactions.size,
    recurringRules = data.recurringRules.size,
    tags = data.tags.size,
    budgets = data.budgets.size,
    savingsGoals = data.savingsGoals.size,
    hasSettings = data.settings != null,
    isEncrypted = isEncrypted,
)
