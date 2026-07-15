@file:Suppress("TooManyFunctions") // One to/from pair per backed-up table.

package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.entity.BudgetEntity
import com.callbackdev.saldo.core.database.entity.CategoryEntity
import com.callbackdev.saldo.core.database.entity.RecurringRuleEntity
import com.callbackdev.saldo.core.database.entity.TagEntity
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.database.entity.TransactionTagCrossRef
import com.callbackdev.saldo.core.domain.backup.AccountBackup
import com.callbackdev.saldo.core.domain.backup.BudgetBackup
import com.callbackdev.saldo.core.domain.backup.CategoryBackup
import com.callbackdev.saldo.core.domain.backup.RecurringRuleBackup
import com.callbackdev.saldo.core.domain.backup.TagBackup
import com.callbackdev.saldo.core.domain.backup.TransactionBackup
import com.callbackdev.saldo.core.domain.backup.TransactionTagBackup
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.TransactionType
import java.util.Currency

// Entity <-> backup-schema mapping. Field-by-field on purpose: the backup file
// must stay stable even if entities are ever renamed or refactored, so nothing
// here is derived via serialization of the entity itself. Enum names and
// currency codes are validated on the way in (valueOf/Currency.getInstance
// throw inside the restore transaction, which rolls back leaving the current
// data untouched); the same checks already ran at inspect time in BackupCodec,
// this is the last line of defense for callers that skip inspection.

fun AccountEntity.toBackup(): AccountBackup = AccountBackup(
    id = id,
    name = name,
    type = type.name,
    currency = currency,
    initialBalanceMinor = initialBalanceMinor,
    color = color,
    icon = icon,
    isIncludedInTotal = isIncludedInTotal,
    isIncludedInBudget = isIncludedInBudget,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAtEpochMilli = createdAtEpochMilli,
)

fun AccountBackup.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    currency = validatedCurrencyCode(currency),
    initialBalanceMinor = initialBalanceMinor,
    color = color,
    icon = icon,
    isIncludedInTotal = isIncludedInTotal,
    isIncludedInBudget = isIncludedInBudget,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAtEpochMilli = createdAtEpochMilli,
)

fun CategoryEntity.toBackup(): CategoryBackup = CategoryBackup(
    id = id,
    name = name,
    type = type.name,
    color = color,
    icon = icon,
    sortOrder = sortOrder,
    sortOrderIncome = sortOrderIncome,
    isDefault = isDefault,
)

fun CategoryBackup.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = CategoryType.valueOf(type),
    color = color,
    icon = icon,
    sortOrder = sortOrder,
    // Pre-per-tab backups have no income key: fall back to the old shared one.
    sortOrderIncome = sortOrderIncome ?: sortOrder,
    isDefault = isDefault,
)

fun TagEntity.toBackup(): TagBackup = TagBackup(id = id, name = name)

fun TagBackup.toEntity(): TagEntity = TagEntity(id = id, name = name)

fun RecurringRuleEntity.toBackup(): RecurringRuleBackup = RecurringRuleBackup(
    id = id,
    name = name,
    type = type.name,
    currency = currency,
    accountId = accountId,
    frequency = frequency.name,
    startDateEpochDay = startDateEpochDay,
    amountMinor = amountMinor,
    categoryId = categoryId,
    dayOfReference = dayOfReference,
    endDateEpochDay = endDateEpochDay,
    mode = mode.name,
    isVariableAmount = isVariableAmount,
    lastGeneratedEpochDay = lastGeneratedEpochDay,
    color = color,
    icon = icon,
    note = note,
    lastReminderEpochDay = lastReminderEpochDay,
)

fun RecurringRuleBackup.toEntity(): RecurringRuleEntity = RecurringRuleEntity(
    id = id,
    name = name,
    type = TransactionType.valueOf(type),
    currency = validatedCurrencyCode(currency),
    accountId = accountId,
    frequency = RecurrenceFrequency.valueOf(frequency),
    startDateEpochDay = startDateEpochDay,
    amountMinor = amountMinor,
    categoryId = categoryId,
    dayOfReference = dayOfReference,
    endDateEpochDay = endDateEpochDay,
    mode = RecurrenceMode.valueOf(mode),
    isVariableAmount = isVariableAmount,
    lastGeneratedEpochDay = lastGeneratedEpochDay,
    color = color,
    icon = icon,
    note = note,
    lastReminderEpochDay = lastReminderEpochDay,
)

fun TransactionEntity.toBackup(): TransactionBackup = TransactionBackup(
    id = id,
    type = type.name,
    amountMinor = amountMinor,
    currency = currency,
    accountId = accountId,
    timestampEpochMilli = timestampEpochMilli,
    zoneOffsetSeconds = zoneOffsetSeconds,
    transferAccountId = transferAccountId,
    transferAmountMinor = transferAmountMinor,
    transferCurrency = transferCurrency,
    categoryId = categoryId,
    description = description,
    note = note,
    isExcludedFromStats = isExcludedFromStats,
    isRefund = isRefund,
    recurringRuleId = recurringRuleId,
    isPending = isPending,
    recurringOccurrenceEpochDay = recurringOccurrenceEpochDay,
)

fun TransactionBackup.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    type = TransactionType.valueOf(type),
    amountMinor = amountMinor,
    currency = validatedCurrencyCode(currency),
    accountId = accountId,
    timestampEpochMilli = timestampEpochMilli,
    zoneOffsetSeconds = zoneOffsetSeconds,
    transferAccountId = transferAccountId,
    transferAmountMinor = transferAmountMinor,
    transferCurrency = transferCurrency?.let(::validatedCurrencyCode),
    categoryId = categoryId,
    description = description,
    note = note,
    isExcludedFromStats = isExcludedFromStats,
    isRefund = isRefund,
    recurringRuleId = recurringRuleId,
    isPending = isPending,
    recurringOccurrenceEpochDay = recurringOccurrenceEpochDay,
)

fun TransactionTagCrossRef.toBackup(): TransactionTagBackup =
    TransactionTagBackup(transactionId = transactionId, tagId = tagId)

fun TransactionTagBackup.toEntity(): TransactionTagCrossRef =
    TransactionTagCrossRef(transactionId = transactionId, tagId = tagId)

fun BudgetEntity.toBackup(): BudgetBackup = BudgetBackup(
    id = id,
    categoryId = categoryId,
    amountMinor = amountMinor,
    currency = currency,
    lastNotified80EpochMonth = lastNotified80EpochMonth,
    lastNotified100EpochMonth = lastNotified100EpochMonth,
)

fun BudgetBackup.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    categoryId = categoryId,
    amountMinor = amountMinor,
    currency = validatedCurrencyCode(currency),
    lastNotified80EpochMonth = lastNotified80EpochMonth,
    lastNotified100EpochMonth = lastNotified100EpochMonth,
)

/** Throws [IllegalArgumentException] on codes [java.util.Currency] does not know. */
private fun validatedCurrencyCode(code: String): String {
    Currency.getInstance(code)
    return code
}
