package com.callbackdev.saldo.core.common.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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

    private companion object {
        val LAST_USED_ACCOUNT_ID = longPreferencesKey("last_used_account_id")
    }
}
