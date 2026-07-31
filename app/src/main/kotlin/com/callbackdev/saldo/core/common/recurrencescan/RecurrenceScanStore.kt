package com.callbackdev.saldo.core.common.recurrencescan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence of the recurrence scan (Fase 19, ADR 43): the last result with
 * its date, re-presented as-is when the hub opens (reading a saved result is
 * not a scan), and the dismissed suggestion keys. Nothing here ever triggers
 * a scan.
 */
@Singleton
class RecurrenceScanStore @Inject constructor(
    @RecurrenceScanPreferences private val dataStore: DataStore<Preferences>,
) {

    /** The last scan with its date; null when never scanned or unreadable. */
    val snapshot: Flow<RecurrenceScanSnapshot?> = dataStore.data
        .map { preferences -> preferences[LAST_SCAN]?.let(RecurrenceScanCodec::decode) }
        .distinctUntilChanged()

    /** Stable keys of the suggestions the user dismissed (ADR 43). */
    val dismissedKeys: Flow<Set<String>> = dataStore.data
        .map { preferences -> preferences[DISMISSED_KEYS].orEmpty() }
        .distinctUntilChanged()

    /**
     * Stores [result] as the scan of [scannedOn], atomically pruning the
     * dismissals whose series no longer shows up: a dismissed suggestion must
     * not reappear, but a key for a vanished series would otherwise sit in
     * the set forever.
     */
    suspend fun saveResult(result: RecurrenceScanResult, scannedOn: LocalDate) {
        dataStore.edit { preferences ->
            preferences[LAST_SCAN] =
                RecurrenceScanCodec.encode(RecurrenceScanSnapshot(scannedOn, result))
            val alive = result.suggestions.mapTo(mutableSetOf()) { it.key }
            val kept = preferences[DISMISSED_KEYS].orEmpty().filterTo(mutableSetOf()) { it in alive }
            if (kept.isEmpty()) preferences.remove(DISMISSED_KEYS) else preferences[DISMISSED_KEYS] = kept
        }
    }

    suspend fun dismiss(key: String) {
        dataStore.edit { preferences ->
            preferences[DISMISSED_KEYS] = preferences[DISMISSED_KEYS].orEmpty() + key
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val LAST_SCAN = stringPreferencesKey("recurrence_scan_last")
        val DISMISSED_KEYS = stringSetPreferencesKey("recurrence_scan_dismissed")
    }
}
