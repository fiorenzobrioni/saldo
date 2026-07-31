package com.callbackdev.saldo.core.common.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.callbackdev.saldo.core.domain.repository.SettingsBackupRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.userPreferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
)

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.userPreferencesStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsBackupModule {

    @Binds
    abstract fun bindSettingsBackupRepository(impl: SettingsBackupStore): SettingsBackupRepository
}
