package com.callbackdev.saldo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.callbackdev.saldo.core.database.converter.Converters
import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.dao.BudgetDao
import com.callbackdev.saldo.core.database.dao.CategoryDao
import com.callbackdev.saldo.core.database.dao.ExchangeRateDao
import com.callbackdev.saldo.core.database.dao.RecurringRuleDao
import com.callbackdev.saldo.core.database.dao.SavingsGoalDao
import com.callbackdev.saldo.core.database.dao.TagDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.entity.BudgetEntity
import com.callbackdev.saldo.core.database.entity.CategoryEntity
import com.callbackdev.saldo.core.database.entity.ExchangeRateEntity
import com.callbackdev.saldo.core.database.entity.RecurringRuleEntity
import com.callbackdev.saldo.core.database.entity.SavingsGoalEntity
import com.callbackdev.saldo.core.database.entity.TagEntity
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.database.entity.TransactionTagCrossRef

/**
 * Current schema version, the single source of truth referenced by the
 * [Database] annotation and the migration tests. Bump it on every schema change,
 * always paired with an explicit [androidx.room.migration.Migration] in
 * `ALL_MIGRATIONS` and an exported `N.json` (the generic migration test then
 * covers it automatically). Version 1 is the baseline: while the app is
 * unpublished a schema change may still be folded into this baseline instead of
 * a migration (a reset that forces a reinstall on the test device), but that is
 * a deliberate exception, not the default.
 */
const val SALDO_DATABASE_VERSION = 4

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TagEntity::class,
        TransactionTagCrossRef::class,
        RecurringRuleEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class,
        ExchangeRateEntity::class,
    ],
    version = SALDO_DATABASE_VERSION,
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
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun exchangeRateDao(): ExchangeRateDao

    companion object {
        const val NAME = "saldo.db"
    }
}
