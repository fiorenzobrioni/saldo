package com.callbackdev.saldo.core.common.applock

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

/** Marks the app-lock DataStore, distinct from the unqualified user-preferences one. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppLockPreferences

// A store of its own, not more keys in user_preferences (ADR 39): the PIN must
// not be wiped as a side effect of UserPreferencesRepository.clear() (its
// removal on "erase all data" is an explicit call instead), and it never rides
// along with anything that serializes the user preferences. A corrupted file
// is replaced with empty preferences: that degrades to "lock disabled", which
// loses the PIN but never bricks the app or touches the financial data.
private val Context.appLockStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_lock",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Module
@InstallIn(SingletonComponent::class)
object AppLockPreferencesModule {

    @Provides
    @Singleton
    @AppLockPreferences
    fun provideAppLockDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.appLockStore
}
