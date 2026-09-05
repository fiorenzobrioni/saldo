package com.callbackdev.saldo.core.common.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.callbackdev.saldo.core.common.prefs.UserPreferenceKeys.CSV_COLUMN_MAPPINGS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A CSV column mapping the user saved under a name (Fase 39, F5): which
 * column of a file with this [header] holds each importer field, and the
 * decimal convention they forced, if any. Re-applied automatically to the next
 * file with the same header.
 *
 * [fields] is keyed by the importer's field *names* rather than its enum: this
 * store lives below the feature layer, and a name it does not know (a field
 * removed in a later version, a hand-edited backup) is dropped on read instead
 * of breaking the whole list.
 */
@Serializable
data class SavedCsvMapping(
    val name: String,
    /** The header cells of the file the mapping was made for, as read. */
    val header: List<String>,
    /** Importer field name to column index. */
    val fields: Map<String, Int>,
    /** `"."` or `","` when the user forced the decimal mark; null lets the file decide. */
    val decimalMark: String? = null,
) {
    /** Whether a file with [header] is the one this mapping was made for. */
    fun matches(header: List<String>): Boolean = normalizeHeader(this.header) == normalizeHeader(header)

    fun hasSameNameAs(other: String): Boolean = name.trim().equals(other.trim(), ignoreCase = true)

    companion object {
        /** Case- and whitespace-insensitive, ignoring empty trailing cells (a stray separator). */
        fun normalizeHeader(header: List<String>): List<String> =
            header.map { it.trim().lowercase(Locale.ROOT) }.dropLastWhile { it.isEmpty() }
    }
}

/** JSON for the saved mappings; an unreadable value decodes to no mappings rather than failing. */
object CsvMappingCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedCsvMapping.serializer())

    fun encode(mappings: List<SavedCsvMapping>): String = json.encodeToString(serializer, mappings)

    fun decode(raw: String?): List<SavedCsvMapping> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
}

/**
 * Persistence of the saved CSV column mappings, one JSON value in the user
 * preferences so it travels with the settings backup (ADR 45). Names are
 * unique, case-insensitively: saving under an existing name replaces it.
 */
@Singleton
class CsvColumnMappingStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val mappings: Flow<List<SavedCsvMapping>> = dataStore.data
        .map { preferences -> CsvMappingCodec.decode(preferences[CSV_COLUMN_MAPPINGS]) }
        .distinctUntilChanged()

    suspend fun save(mapping: SavedCsvMapping) {
        dataStore.edit { preferences ->
            val kept = CsvMappingCodec.decode(preferences[CSV_COLUMN_MAPPINGS])
                .filterNot { it.hasSameNameAs(mapping.name) }
            preferences[CSV_COLUMN_MAPPINGS] = CsvMappingCodec.encode(kept + mapping)
        }
    }

    suspend fun delete(name: String) {
        dataStore.edit { preferences ->
            val kept = CsvMappingCodec.decode(preferences[CSV_COLUMN_MAPPINGS]).filterNot { it.hasSameNameAs(name) }
            if (kept.isEmpty()) {
                preferences.remove(CSV_COLUMN_MAPPINGS)
            } else {
                preferences[CSV_COLUMN_MAPPINGS] = CsvMappingCodec.encode(kept)
            }
        }
    }

    /** The saved mapping made for a file with exactly this [header], if any. */
    suspend fun findForHeader(header: List<String>): SavedCsvMapping? =
        mappings.first().firstOrNull { it.matches(header) }
}
