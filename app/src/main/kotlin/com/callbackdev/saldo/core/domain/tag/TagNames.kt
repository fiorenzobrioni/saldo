package com.callbackdev.saldo.core.domain.tag

import java.util.Locale

/**
 * How tag spellings are normalized and compared, in one place: the editor's
 * inline creation and the management screen's rename both need "spesa" typed
 * twice with different casing to land on the same tag instead of minting a
 * near-duplicate the database cannot catch (the unique index on `tags.name`
 * compares bytes, so "Spesa" and "spesa" are different rows to it).
 *
 * The stored form keeps the user's casing and accents - a tag's name is the
 * user's own text - and only trims and collapses whitespace. The comparison
 * key case-folds on top of that, matching what the CSV importer already does;
 * accents are deliberately not folded, because "perù" and "peru" can be two
 * tags on purpose, unlike two spellings of the same person's name
 * ([com.callbackdev.saldo.core.domain.transaction.CounterpartyNames] folds
 * them, tags do not).
 */
object TagNames {

    private val WHITESPACE = Regex("\\s+")

    /** The stored form of [raw]: trimmed, inner whitespace collapsed, casing kept. */
    fun normalize(raw: String): String = raw.trim().replace(WHITESPACE, " ")

    /** The comparison key of [name]: normalized and case-folded, never displayed. */
    fun key(name: String): String = normalize(name).lowercase(Locale.ROOT)

    /** True when the two spellings would name the same tag. */
    fun sameName(first: String, second: String): Boolean = key(first) == key(second)
}
