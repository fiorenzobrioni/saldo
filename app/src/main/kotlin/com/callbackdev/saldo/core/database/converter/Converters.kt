package com.callbackdev.saldo.core.database.converter

import androidx.room.TypeConverter
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * Room type converters. Enums are stored as their [Enum.name] so raw SQL can
 * filter on them (e.g. `WHERE type = 'TRANSFER'`). Instants are stored as epoch
 * millis directly on the entities, so no converter is needed for them.
 */
class Converters {

    @TypeConverter
    fun transactionTypeToString(value: TransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun accountTypeToString(value: AccountType): String = value.name

    @TypeConverter
    fun stringToAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun categoryTypeToString(value: CategoryType): String = value.name

    @TypeConverter
    fun stringToCategoryType(value: String): CategoryType = CategoryType.valueOf(value)

    @TypeConverter
    fun recurrenceFrequencyToString(value: RecurrenceFrequency): String = value.name

    @TypeConverter
    fun stringToRecurrenceFrequency(value: String): RecurrenceFrequency =
        RecurrenceFrequency.valueOf(value)

    @TypeConverter
    fun recurrenceModeToString(value: RecurrenceMode): String = value.name

    @TypeConverter
    fun stringToRecurrenceMode(value: String): RecurrenceMode = RecurrenceMode.valueOf(value)
}
