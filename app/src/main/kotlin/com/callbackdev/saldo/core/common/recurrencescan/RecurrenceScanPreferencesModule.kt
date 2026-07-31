package com.callbackdev.saldo.core.common.recurrencescan

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifies the DataStore holding the recurrence scan result (Fase 19, ADR 43). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RecurrenceScanPreferences

/**
 * Dedicated store, separate from user preferences: a corrupted file must cost
 * the last scan result (rebuildable with one tap), never a user setting.
 */
private val Context.recurrenceScanStore: DataStore<Preferences> by preferencesDataStore(
    name = "recurrence_scan",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Module
@InstallIn(SingletonComponent::class)
object RecurrenceScanPreferencesModule {

    @Provides
    @Singleton
    @RecurrenceScanPreferences
    fun provideRecurrenceScanDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.recurrenceScanStore
}
