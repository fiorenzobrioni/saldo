@file:Suppress("TooManyFunctions") // One to/from pair per backed-up table.

package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.entity.CategoryEntity
import com.callbackdev.saldo.core.database.entity.RecurringRuleEntity
import com.callbackdev.saldo.core.database.entity.TagEntity
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.database.entity.TransactionTagCrossRef
import com.callbackdev.saldo.core.domain.backup.AccountBackup
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

// Entity <-> backup-schema mapping. Field-by-field on purpose: the backup file
// must stay stable even if entities are ever renamed or refactored, so nothing
// here is derived via serialization of the entity itself. Enum names are
// validated on the way in (valueOf throws inside the restore transaction,
// which rolls back leaving the current data untouched).

fun AccountEntity.toBackup(): AccountBackup = AccountBackup(
    id = id,
    name = name,
    type = type.name,
    currency = currency,
    initialBalanceMinor = initialBalanceMinor,
    color = color,
    icon = icon,
    isIncludedInTotal = isIncludedInTotal,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAtEpochMilli = createdAtEpochMilli,
)

fun AccountBackup.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    currency = currency,
    initialBalanceMinor = initialBalanceMinor,
    color = color,
    icon = icon,
    isIncludedInTotal = isIncludedInTotal,
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
    isDefault = isDefault,
)

fun CategoryBackup.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = CategoryType.valueOf(type),
    color = color,
    icon = icon,
    sortOrder = sortOrder,
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
    currency = currency,
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

fun TransactionTagCrossRef.toBackup(): TransactionTagBackup =
    TransactionTagBackup(transactionId = transactionId, tagId = tagId)

fun TransactionTagBackup.toEntity(): TransactionTagCrossRef =
    TransactionTagCrossRef(transactionId = transactionId, tagId = tagId)
