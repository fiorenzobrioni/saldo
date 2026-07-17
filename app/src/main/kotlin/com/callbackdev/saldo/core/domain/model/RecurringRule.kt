package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/**
 * A rule that periodically generates a movement (an expense, an income or a
 * transfer), typically a subscription.
 *
 * The generation engine (idempotent generation, short months, catch-up) lives
 * in [com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator]. [amount]
 * is a positive magnitude (the sign is applied from [type] when a movement is
 * generated); it is null for variable-amount rules (the movement is created
 * pending and the user fills it in). [dayOfReference] is the day-of-month for
 * monthly-and-longer frequencies (clamped to the last day of short months by the
 * engine). [color] and [icon] drive the subscription avatar, mirroring accounts
 * and categories.
 *
 * ### Transfers
 * For a [TransactionType.TRANSFER] rule [accountId] is the source and
 * [transferAccountId] the destination, in [transferCurrency] (PLANNING ADR 2,
 * mirroring [Transaction]). Same-currency transfers can run automatically
 * ([amount] is the exact effect on both legs). Cross-currency transfers are
 * always [RecurrenceMode.CONFIRM]: [amount] fixes the source leg while the
 * received amount is entered at each confirmation (ADR 24), so [transferAmount]
 * is null on the rule.
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
    val color: Int? = null,
    val icon: String? = null,
    val note: String? = null,
    /** The occurrence date the last pre-renewal reminder was posted for (once per occurrence). */
    val lastReminderDate: LocalDate? = null,
    /** Destination account for a [TransactionType.TRANSFER] rule; null otherwise. */
    val transferAccountId: Long? = null,
    /**
     * Positive effect on the destination account of a transfer, in
     * [transferCurrency]. For same-currency transfers it equals [amount]; null
     * for cross-currency transfers (entered at confirmation) and non-transfers.
     */
    val transferAmount: BigDecimal? = null,
    /** Currency of the destination account for a transfer rule; null otherwise. */
    val transferCurrency: Currency? = null,
)
