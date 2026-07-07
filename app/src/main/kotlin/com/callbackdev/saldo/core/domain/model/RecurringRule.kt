package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/**
 * A rule that periodically generates a movement (an expense or an income),
 * typically a subscription.
 *
 * Only the schema lives here in Phase 1: the generation engine (idempotent
 * generation, short months, catch-up) arrives in Phase 6. [amount] is null for
 * variable-amount rules (the movement is created pending and the user fills it in).
 * [dayOfReference] is the day-of-month for monthly-and-longer frequencies
 * (clamped to the last day of short months by the engine).
 */
data class RecurringRule(
    val name: String,
    val type: TransactionType,
    val currency: Currency,
    val accountId: Long,
    val frequency: RecurrenceFrequency,
    val startDate: LocalDate,
    val id: Long = 0L,
    val amount: BigDecimal? = null,
    val categoryId: Long? = null,
    val dayOfReference: Int? = null,
    val endDate: LocalDate? = null,
    val mode: RecurrenceMode = RecurrenceMode.AUTOMATIC,
    val isVariableAmount: Boolean = false,
    val lastGeneratedDate: LocalDate? = null,
    val note: String? = null,
)
