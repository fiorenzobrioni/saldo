package com.callbackdev.saldo.core.common.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** How the app resolves light/dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Theme choices persisted across launches. Brand palette is the default. */
data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
)

/**
 * Small UI preferences persisted with DataStore. These are convenience hints
 * (not user data): losing them never loses money information.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * The account last used to record a movement. The editor preselects it so
     * the typical expense stays within the 3-tap budget; Phase 9 will add an
     * explicit default-account setting on top.
     */
    val lastUsedAccountId: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[LAST_USED_ACCOUNT_ID] }

    suspend fun setLastUsedAccountId(accountId: Long) {
        dataStore.edit { preferences -> preferences[LAST_USED_ACCOUNT_ID] = accountId }
    }

    val themePreferences: Flow<ThemePreferences> = dataStore.data.map { preferences ->
        ThemePreferences(
            mode = preferences[THEME_MODE]
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: ThemeMode.SYSTEM,
            useDynamicColor = preferences[USE_DYNAMIC_COLOR] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[THEME_MODE] = mode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[USE_DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val LAST_USED_ACCOUNT_ID = longPreferencesKey("last_used_account_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }
}
