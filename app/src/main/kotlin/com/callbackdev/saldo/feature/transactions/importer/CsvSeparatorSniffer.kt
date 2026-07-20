package com.callbackdev.saldo.feature.transactions.importer

/**
 * Guesses the column separator of a CSV document. Saldo exports with `;` or `,`
 * (the export lets the user choose), but a file from elsewhere might use a tab.
 * The winner is the candidate that appears most often on the first non-empty
 * line, counted outside quoted fields; ties fall back to the export default.
 */
object CsvSeparatorSniffer {

    /** Separators considered, most likely first (breaks ties deterministically). */
    private val CANDIDATES = listOf(';', ',', '\t')

    private const val DEFAULT = ';'

    fun detect(text: String): Char {
        // Sniff the header line, not a data line: the header holds column names,
        // never amounts, so a decimal comma in the data can never be mistaken
        // for the field separator here.
        val line = firstContentLine(text) ?: return DEFAULT
        val counts = countOutsideQuotes(line)
        val best = CANDIDATES.maxByOrNull { counts[it] ?: 0 } ?: DEFAULT
        return if ((counts[best] ?: 0) > 0) best else DEFAULT
    }

    /** First line that carries a non-blank character, BOM stripped. */
    private fun firstContentLine(text: String): String? =
        text.removePrefix("\uFEFF")
            .lineSequence()
            .firstOrNull { it.isNotBlank() }

    private fun countOutsideQuotes(line: String): Map<Char, Int> {
        val counts = mutableMapOf<Char, Int>()
        var inQuotes = false
        for (char in line) {
            if (char == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes && char in CANDIDATES) {
                counts[char] = (counts[char] ?: 0) + 1
            }
        }
        return counts
    }
}
