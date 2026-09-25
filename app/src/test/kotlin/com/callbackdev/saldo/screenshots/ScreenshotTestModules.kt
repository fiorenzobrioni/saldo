package com.callbackdev.saldo.screenshots

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.callbackdev.saldo.core.common.di.ClockModule
import com.callbackdev.saldo.core.common.prefs.PreferencesModule
import com.callbackdev.saldo.core.database.SaldoDatabase
import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.dao.BudgetDao
import com.callbackdev.saldo.core.database.dao.CategoryDao
import com.callbackdev.saldo.core.database.dao.ExchangeRateDao
import com.callbackdev.saldo.core.database.dao.ForeignFlowDao
import com.callbackdev.saldo.core.database.dao.RecurrenceCandidateDao
import com.callbackdev.saldo.core.database.dao.RecurringRuleDao
import com.callbackdev.saldo.core.database.dao.SavingsGoalDao
import com.callbackdev.saldo.core.database.dao.TagDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.di.DatabaseModule
import com.callbackdev.saldo.core.database.repository.RoomTransactionRunner
import com.callbackdev.saldo.core.database.seed.DatabaseSeedCallback
import com.callbackdev.saldo.core.domain.repository.TransactionRunner
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.time.Clock
import javax.inject.Singleton

/*
 * The screenshot tests run the real screens on the real repositories and use
 * cases; only the three things that tie the app to a device are swapped here.
 */

/** An in-memory database, seeded with the localized default categories like a fresh install. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object InMemoryDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        seedCallback: DatabaseSeedCallback,
    ): SaldoDatabase =
        Room.inMemoryDatabaseBuilder(context, SaldoDatabase::class.java)
            .addCallback(seedCallback)
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
    fun provideRecurringRuleDao(database: SaldoDatabase): RecurringRuleDao = database.recurringRuleDao()

    @Provides
    fun provideBudgetDao(database: SaldoDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun provideSavingsGoalDao(database: SaldoDatabase): SavingsGoalDao = database.savingsGoalDao()

    @Provides
    fun provideExchangeRateDao(database: SaldoDatabase): ExchangeRateDao = database.exchangeRateDao()

    @Provides
    fun provideForeignFlowDao(database: SaldoDatabase): ForeignFlowDao = database.foreignFlowDao()

    @Provides
    fun provideRecurrenceCandidateDao(database: SaldoDatabase): RecurrenceCandidateDao =
        database.recurrenceCandidateDao()

    @Provides
    fun provideTransactionRunner(database: SaldoDatabase): TransactionRunner =
        RoomTransactionRunner(database)
}

/** The sample ledger's "now": every relative date on screen reads from it. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ClockModule::class])
object FixedClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = SampleLedger.clock
}

/**
 * A fresh preferences file per test: the app's `preferencesDataStore` delegate
 * is a process-wide singleton, and would carry one test's settings into the next.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PreferencesModule::class])
object IsolatedPreferencesModule {

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("user_preferences", ".preferences_pb").apply { delete() }
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }
}
