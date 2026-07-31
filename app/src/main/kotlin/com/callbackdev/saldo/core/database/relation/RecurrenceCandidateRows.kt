package com.callbackdev.saldo.core.database.relation

/**
 * Rows of the recurrence-detection candidate queries (Fase 19, ADR 43). One
 * file for the family, like [ForeignDashboardFlowsRow] and friends: they only
 * ever travel together.
 */

/** A candidate group keyed by exact amount: a fixed-price series (subscription). */
data class RecurrenceAmountGroupRow(
    val type: String,
    val accountId: Long,
    val categoryId: Long?,
    val currency: String,
    val amountMinor: Long,
    val count: Int,
)

/**
 * A candidate group keyed by case-folded description: a variable bill. The
 * key is `LOWER(TRIM(description))`, an ASCII-only fold; the real
 * accent-insensitive normalization happens in Kotlin on these few groups.
 */
data class RecurrenceDescriptionGroupRow(
    val type: String,
    val accountId: Long,
    val categoryId: Long?,
    val currency: String,
    val descriptionKey: String,
    val count: Int,
)

/** One movement of a candidate group: local day (ADR 7), signed amount, raw description. */
data class RecurrenceOccurrenceRow(
    val epochDay: Long,
    val amountMinor: Long,
    val description: String?,
)
