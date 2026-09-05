package com.callbackdev.saldo.feature.transactions.importer

import java.text.Normalizer
import java.util.Locale

/** A logical column the importer understands, independent of its header text. */
enum class CsvField {
    DATE,
    TYPE,
    CATEGORY,
    DESCRIPTION,
    ACCOUNT,
    TO_ACCOUNT,
    AMOUNT,
    CURRENCY,
    RECEIVED_AMOUNT,
    RECEIVED_CURRENCY,
    TAGS,
    NOTE,
    COUNTERPARTY,
    EXCLUDED_FROM_STATS,
    REFUND,
}

/** Which physical column index backs each recognized [CsvField]. */
data class ColumnMapping(val indexByField: Map<CsvField, Int>) {

    val mappedFields: Set<CsvField> get() = indexByField.keys

    fun has(field: CsvField): Boolean = field in indexByField

    /** The minimum needed to read a movement: a date and an amount column. */
    val isComplete: Boolean get() = has(CsvField.DATE) && has(CsvField.AMOUNT)

    /** Raw, untrimmed value of [field] in [row], or null when unmapped or short. */
    fun rawValue(row: List<String>, field: CsvField): String? =
        indexByField[field]?.let { row.getOrNull(it) }

    companion object {
        /**
         * Rebuilds a mapping saved by field *name* (Fase 39, F5). A name this
         * version does not know, or a negative index, is dropped rather than
         * failing the whole mapping.
         */
        fun fromNames(fields: Map<String, Int>): ColumnMapping = ColumnMapping(
            fields.entries.mapNotNull { (name, index) ->
                CsvField.entries.firstOrNull { it.name == name }
                    ?.takeIf { index >= 0 }
                    ?.let { it to index }
            }.toMap(),
        )
    }
}

/**
 * Maps a header row to [CsvField]s by name, so columns are matched by meaning
 * rather than position and a reordered or partial file still imports. Matching
 * is accent- and case-insensitive and ignores spaces and punctuation, and it
 * accepts both the app's current localized headers and a built-in set of
 * Italian and English aliases (what other apps and spreadsheets tend to emit).
 */
object CsvHeaderMapper {

    /** Built-in aliases per field, added to the app's own localized labels. */
    private val ALIASES: Map<CsvField, List<String>> = mapOf(
        CsvField.DATE to listOf("data", "date", "giorno", "day"),
        CsvField.TYPE to listOf("tipo", "type", "tipologia", "kind"),
        CsvField.CATEGORY to listOf("categoria", "category", "cat"),
        CsvField.DESCRIPTION to listOf(
            "descrizione", "description", "desc", "causale", "payee", "merchant", "beneficiario",
        ),
        CsvField.ACCOUNT to listOf(
            "conto", "account", "wallet", "portafoglio", "fromaccount", "contoorigine",
        ),
        CsvField.TO_ACCOUNT to listOf(
            "versoconto", "toaccount", "contodestinazione", "destinazione", "accountto",
        ),
        CsvField.AMOUNT to listOf("importo", "amount", "valore", "value", "somma"),
        CsvField.CURRENCY to listOf("valuta", "currency", "divisa", "ccy"),
        CsvField.RECEIVED_AMOUNT to listOf("importoricevuto", "amountreceived", "receivedamount"),
        CsvField.RECEIVED_CURRENCY to listOf("valutaricevuta", "currencyreceived", "receivedcurrency"),
        CsvField.TAGS to listOf("tag", "tags", "etichette", "labels"),
        CsvField.NOTE to listOf("nota", "note", "notes", "annotazioni", "memo"),
        CsvField.COUNTERPARTY to listOf("controparte", "counterparty", "persona", "person"),
        CsvField.EXCLUDED_FROM_STATS to listOf(
            "esclusodallestatistiche", "escluso", "excludedfromstats", "excluded", "nostats",
        ),
        CsvField.REFUND to listOf("rimborso", "refund", "reimbursement"),
    )

    /**
     * Builds a [ColumnMapping] from [header], preferring the app's own
     * [localizedLabels] and falling back to the built-in aliases. Returns null
     * when neither the date nor the amount column is recognized, the minimum
     * needed to read a movement; the partial matches are still available
     * through [suggest], as the starting point of a manual mapping.
     */
    fun map(header: List<String>, localizedLabels: Map<CsvField, String>): ColumnMapping? =
        ColumnMapping(suggest(header, localizedLabels)).takeIf { it.isComplete }

    /**
     * Every field whose header cell is recognized, complete or not. A field
     * maps to the first column whose normalized text matches; later duplicate
     * matches are ignored.
     */
    fun suggest(header: List<String>, localizedLabels: Map<CsvField, String>): Map<CsvField, Int> {
        val keys: Map<CsvField, Set<String>> = CsvField.entries.associateWith { field ->
            buildSet {
                localizedLabels[field]?.let { add(normalize(it)) }
                ALIASES[field].orEmpty().forEach { add(normalize(it)) }
            }
        }
        val normalizedHeader = header.map { normalize(it) }
        val indexByField = mutableMapOf<CsvField, Int>()
        normalizedHeader.forEachIndexed { index, cell ->
            if (cell.isEmpty()) return@forEachIndexed
            val field = CsvField.entries.firstOrNull { it !in indexByField && cell in keys.getValue(it) }
            if (field != null) indexByField[field] = index
        }
        return indexByField
    }

    /** Lowercases, strips accents, and drops everything but letters and digits. */
    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .filter { it.isLetterOrDigit() }

    private val DIACRITICS = "\\p{InCombiningDiacriticalMarks}+".toRegex()
}
