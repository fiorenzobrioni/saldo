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
)

fun BackupFile.summary(): BackupSummary = BackupSummary(
    exportedAt = Instant.ofEpochMilli(exportedAtEpochMilli),
    appVersion = appVersion,
    accounts = data.accounts.size,
    categories = data.categories.size,
    transactions = data.transactions.size,
    recurringRules = data.recurringRules.size,
    tags = data.tags.size,
    budgets = data.budgets.size,
)
