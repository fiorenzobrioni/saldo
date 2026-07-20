package com.callbackdev.saldo.core.common.csv

/**
 * Protects against CSV formula injection: a spreadsheet treats a cell that
 * starts with `=`, `+`, `-`, `@` (or a tab/carriage return) as a formula, so a
 * crafted text field (e.g. a description `=HYPERLINK(...)`) could execute when
 * the exported file is opened elsewhere. Since the import now lets external
 * data flow in, both sides use this guard.
 *
 * [guard] prefixes a leading apostrophe (the spreadsheet convention for "treat
 * as text") only to fields that begin with a trigger character; [strip] reverses
 * exactly that, so a round-trip through export and import restores the original
 * text. It is applied to text fields only (descriptions, notes, category,
 * account and tag names), never to amounts, dates or currency codes, so a
 * negative amount like `-12.50` is left untouched.
 */
object CsvFormulaGuard {

    private val TRIGGERS = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /** Prefixes an apostrophe when [field] starts with a formula trigger. */
    fun guard(field: String): String =
        if (field.isNotEmpty() && field.first() in TRIGGERS) "'$field" else field

    /** Removes an apostrophe added by [guard], leaving other leading quotes alone. */
    fun strip(field: String): String =
        if (field.length >= 2 && field.first() == '\'' && field[1] in TRIGGERS) {
            field.substring(1)
        } else {
            field
        }
}
