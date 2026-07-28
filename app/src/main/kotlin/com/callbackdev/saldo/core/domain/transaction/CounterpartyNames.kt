package com.callbackdev.saldo.core.domain.transaction

import java.text.Normalizer

/**
 * How two counterparty spellings are told apart. A counterparty is free text
 * (ADR 34: a name, not an entity to administer), so "Luca", "luca" and "Lucà "
 * typed on three different days must still land on the same person, or the
 * aggregate would quietly split a debt in two.
 *
 * The key is case-, accent- and spacing-insensitive; it is never displayed and
 * never stored, only compared.
 */
object CounterpartyNames {

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")

    /** The comparison key of [name]; blank input yields an empty key. */
    fun key(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(WHITESPACE, " ")
            .lowercase()

    /** True when the two spellings name the same person. */
    fun sameName(first: String, second: String): Boolean = key(first) == key(second)
}
