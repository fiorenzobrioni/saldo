package com.callbackdev.saldo.core.database.di

import android.content.Context
import androidx.room.Room
import com.callbackdev.saldo.core.database.SaldoDatabase
import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.dao.CategoryDao
import com.callbackdev.saldo.core.database.dao.RecurringRuleDao
import com.callbackdev.saldo.core.database.dao.TagDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.migration.ALL_MIGRATIONS
import com.callbackdev.saldo.core.database.repository.RoomTransactionRunner
import com.callbackdev.saldo.core.database.seed.DatabaseSeedCallback
import com.callbackdev.saldo.core.domain.repository.TransactionRunner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        seedCallback: DatabaseSeedCallback,
    ): SaldoDatabase =
        Room.databaseBuilder(context, SaldoDatabase::class.java, SaldoDatabase.NAME)
            .addCallback(seedCallback)
            .apply { ALL_MIGRATIONS.forEach { addMigrations(it) } }
            .build()

    @Provides
    fun provideAccountDao(database: SaldoDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideCategoryDao(database: SaldoDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTransactionDao(database: SaldoDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideTagDao(database: SaldoDatabase): TagDao = database.tagDao()

    @Provides
    fun provideRecurringRuleDao(database: SaldoDatabase): RecurringRuleDao =
        database.recurringRuleDao()

    @Provides
    fun provideTransactionRunner(database: SaldoDatabase): TransactionRunner =
        RoomTransactionRunner(database)
}
