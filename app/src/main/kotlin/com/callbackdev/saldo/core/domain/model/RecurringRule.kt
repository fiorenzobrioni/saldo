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
    /**
     * Paused (Fase 39, F3): the rule stays on file with its schedule intact but
     * generates nothing, posts no reminder, and is priced at zero everywhere
     * ([runsInMonthOf] is false). Occurrences skipped while paused are never
     * recovered: see [resumed].
     */
    val isPaused: Boolean = false,
)

/**
 * The rule active again after a pause. The generation watermark moves to the
 * day before [today] when it is behind, so the catch-up on the next run starts
 * from today and the occurrences skipped during the pause are not back-filled:
 * pausing means "do not charge me for a while", not "charge me later". A
 * watermark already at or past that day (a charge generated today) is kept, so
 * the same occurrence cannot be produced twice.
 */
fun RecurringRule.resumed(today: LocalDate): RecurringRule {
    val floor = today.minusDays(1)
    val watermark = lastGeneratedDate?.takeIf { it >= floor } ?: floor
    return copy(isPaused = false, lastGeneratedDate = watermark)
}

/**
 * Whether the rule carries a cost into the month containing [date]: it is not
 * paused, it has not ended yet, and its schedule starts no later than that
 * month's last day.
 *
 * The start bound is the end of the month, not [date] itself: a subscription
 * added on the 9th whose first charge lands on the 12th is a real monthly cost
 * right now, and pricing it at zero until it first charges would be just as
 * wrong as the opposite. A schedule that only begins next quarter, on the
 * other hand, costs nothing this month: counting it would inflate the monthly
 * total, the annual projection and the planned-savings rate from the moment
 * the rule is created.
 *
 * Shared on purpose - the recurrences hub, the dashboard card and the savings
 * projection must agree on what counts toward a "per month" figure.
 */
fun RecurringRule.runsInMonthOf(date: LocalDate): Boolean =
    !isPaused && !hasEndedBy(date) && startDate <= date.withDayOfMonth(date.lengthOfMonth())

/**
 * Whether the rule's schedule is over on [date]: no further occurrence will
 * ever be generated. The weaker companion of [runsInMonthOf], for the places that
 * *list* rules rather than price them: a rule starting next month still
 * belongs on screen, with its first charge date, even though it costs nothing
 * this month.
 */
fun RecurringRule.hasEndedBy(date: LocalDate): Boolean = endDate != null && endDate < date
