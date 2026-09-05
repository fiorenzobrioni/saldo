package com.callbackdev.saldo.core.common.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CsvColumnMappingStoreTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val store = CsvColumnMappingStore(dataStore)

    private val bankX = SavedCsvMapping(
        name = "Bank X",
        header = listOf("Data operazione", "Descrizione", "Importo EUR"),
        fields = mapOf("DATE" to 0, "DESCRIPTION" to 1, "AMOUNT" to 2),
        decimalMark = ",",
    )

    @Test
    fun `a saved mapping is found again by its header, case and spacing aside`() = runTest {
        store.save(bankX)

        val found = store.findForHeader(listOf(" data operazione", "DESCRIZIONE", "Importo EUR ", ""))

        assertEquals(bankX, found)
        assertNull(store.findForHeader(listOf("Data operazione", "Importo EUR")))
    }

    @Test
    fun `saving under an existing name replaces it, case-insensitively`() = runTest {
        store.save(bankX)
        store.save(bankX.copy(name = "bank x", decimalMark = "."))

        val all = store.mappings.first()

        assertEquals(1, all.size)
        assertEquals(".", all.single().decimalMark)
    }

    @Test
    fun `deleting the last mapping clears the value, deleting one keeps the others`() = runTest {
        store.save(bankX)
        store.save(bankX.copy(name = "Bank Y", header = listOf("Date", "Amount")))

        store.delete("Bank X")
        assertEquals(listOf("Bank Y"), store.mappings.first().map { it.name })

        store.delete("bank y")
        assertTrue(store.mappings.first().isEmpty())
        assertNull(dataStore.data.first()[UserPreferenceKeys.CSV_COLUMN_MAPPINGS])
    }

    @Test
    fun `an unreadable value decodes to no mappings instead of failing`() {
        assertTrue(CsvMappingCodec.decode("not json").isEmpty())
        assertTrue(CsvMappingCodec.decode(null).isEmpty())
    }

    @Test
    fun `the codec round-trips and tolerates unknown keys`() {
        val encoded = CsvMappingCodec.encode(listOf(bankX))
        assertEquals(listOf(bankX), CsvMappingCodec.decode(encoded))

        val withExtra = encoded.replaceFirst("{", """{"futureField":1,""")
        assertEquals(listOf(bankX), CsvMappingCodec.decode(withExtra))
    }

    /** A DataStore over an in-memory preferences value, enough for save/read/delete. */
    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
