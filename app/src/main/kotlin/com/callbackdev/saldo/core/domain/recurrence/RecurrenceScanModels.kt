package com.callbackdev.saldo.core.domain.recurrence

import com.callbackdev.saldo.core.domain.model.TransactionType
import java.util.Currency

/**
 * Descriptors of the candidate groups returned by the two aggregate SQL
 * queries of the recurrence scan (Fase 19, ADR 43), before their movements
 * are fetched. Plumbing between repository and use case, never persisted.
 */

/** A candidate keyed by exact amount: the fixed-price path (subscriptions). */
data class RecurrenceAmountGroup(
    val type: TransactionType,
    val accountId: Long,
    val categoryId: Long?,
    val currency: Currency,
    val amountMinor: Long,
    val count: Int,
)

/** A candidate keyed by case-folded description: the variable-bill path. */
data class RecurrenceDescriptionGroup(
    val type: TransactionType,
    val accountId: Long,
    val categoryId: Long?,
    val currency: Currency,
    val descriptionKey: String,
    val count: Int,
)

/**
 * The outcome of one explicit scan. [truncated] is the declared-cap promise
 * of the phase: true whenever a cap cut candidates or suggestions, so the UI
 * can say the pass was partial instead of pretending it was exhaustive.
 */
data class RecurrenceScanResult(
    val suggestions: List<RecurrenceSuggestion>,
    val truncated: Boolean,
)
