package com.callbackdev.saldo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.callbackdev.saldo.core.database.converter.Converters
import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.dao.BudgetDao
import com.callbackdev.saldo.core.database.dao.CategoryDao
import com.callbackdev.saldo.core.database.dao.RecurringRuleDao
import com.callbackdev.saldo.core.database.dao.TagDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.entity.BudgetEntity
import com.callbackdev.saldo.core.database.entity.CategoryEntity
import com.callbackdev.saldo.core.database.entity.RecurringRuleEntity
import com.callbackdev.saldo.core.database.entity.TagEntity
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.database.entity.TransactionTagCrossRef

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TagEntity::class,
        TransactionTagCrossRef::class,
        RecurringRuleEntity::class,
        BudgetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SaldoDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun tagDao(): TagDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        const val NAME = "saldo.db"
    }
}
