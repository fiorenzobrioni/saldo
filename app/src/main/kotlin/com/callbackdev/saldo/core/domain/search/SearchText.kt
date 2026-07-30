package com.callbackdev.saldo.core.domain.search

import java.text.Normalizer

/**
 * The one text normalization used to compare what the user typed with what the
 * user wrote in the past: the ledger search, and the quick text entry that
 * matches words against category names and past descriptions (ADR 42). One
 * algorithm on purpose: two surfaces that "search the same words" must agree
 * on what a word is.
 */
object SearchText {

    private val DIACRITICS = Regex("\\p{Mn}+")

    /** Lowercases and strips diacritics, so "perche" matches "PERCHÉ". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()
}
