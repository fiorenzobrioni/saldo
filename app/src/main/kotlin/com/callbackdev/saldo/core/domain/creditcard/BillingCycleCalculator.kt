package com.callbackdev.saldo.core.domain.creditcard

import com.callbackdev.saldo.core.domain.model.CreditCardConfig
import java.time.LocalDate

/**
 * A single credit card billing cycle: the movements dated in `[start, closing]`
 * form one statement, charged to the linked account on [paymentDue].
 *
 * @property start first day of the cycle (inclusive).
 * @property closing statement closing day (inclusive), the last day of the cycle.
 * @property paymentDue day the statement is charged to the linked account, in the
 *   month after [closing].
 */
data class BillingCycle(
    val start: LocalDate,
    val closing: LocalDate,
    val paymentDue: LocalDate,
)

/**
 * Pure billing cycle arithmetic for a [CreditCardConfig]. Handles short months
 * by clamping the configured day to the month length (a closing day of 31 means
 * "last day of the month" for February, April, and so on), so cycles never skip
 * or overlap. No Android or clock dependency: everything is derived from
 * [LocalDate] inputs, which keeps it unit-testable.
 */
object BillingCycleCalculator {

    /** The [day] clamped to the length of [reference]'s month. */
    private fun clampedDayInMonthOf(reference: LocalDate, day: Int): LocalDate =
        reference.withDayOfMonth(minOf(day, reference.lengthOfMonth()))

    /**
     * The most recent closing date on or before [date]: this month's closing if
     * [date] has reached it, otherwise the previous month's.
     */
    fun closingOnOrBefore(date: LocalDate, closingDay: Int): LocalDate {
        val thisMonth = clampedDayInMonthOf(date, closingDay)
        return if (!date.isBefore(thisMonth)) thisMonth else previousClosing(thisMonth, closingDay)
    }

    /** The closing date one month before [closing]. */
    private fun previousClosing(closing: LocalDate, closingDay: Int): LocalDate =
        clampedDayInMonthOf(closing.minusMonths(1), closingDay)

    /** The payment due date of a statement that closed on [closing]. */
    fun paymentDueFor(closing: LocalDate, paymentDueDay: Int): LocalDate =
        clampedDayInMonthOf(closing.plusMonths(1), paymentDueDay)

    /** The cycle whose closing date is [closing] (its start is the day after the previous closing). */
    fun cycleEndingOn(closing: LocalDate, config: CreditCardConfig): BillingCycle =
        BillingCycle(
            start = previousClosing(closing, config.statementClosingDay).plusDays(1),
            closing = closing,
            paymentDue = paymentDueFor(closing, config.paymentDueDay),
        )

    /**
     * Closed cycles whose payment due date has arrived (`paymentDue <= today`)
     * and that are not yet settled (closing after [lastSettledClosing], or all
     * of them when null), oldest first. Normally at most one; several only when
     * the device was off across more than one due date, so the caller settles
     * each in turn.
     *
     * The scan starts at the most recent closing and walks backwards: the newest
     * cycle often has not reached its due date yet (it is skipped, not stopped
     * on), while the previous cycle is the one actually owed. It stops at the
     * first already-settled closing, and is bounded to [MAX_LOOKBACK] cycles so
     * a first-ever settlement (null watermark) cannot walk back indefinitely.
     */
    fun dueStatements(
        today: LocalDate,
        config: CreditCardConfig,
        lastSettledClosing: LocalDate? = config.lastSettledClosing,
    ): List<BillingCycle> {
        val result = ArrayDeque<BillingCycle>()
        var closing = closingOnOrBefore(today, config.statementClosingDay)
        var guard = 0
        while (guard < MAX_LOOKBACK) {
            guard++
            // Reached the last settled cycle (or older): everything before it is done.
            if (lastSettledClosing != null && !closing.isAfter(lastSettledClosing)) break
            val cycle = cycleEndingOn(closing, config)
            if (!cycle.paymentDue.isAfter(today)) {
                result.addFirst(cycle)
            }
            // Keep walking back: an even older cycle may still be due and unsettled.
            closing = previousClosing(closing, config.statementClosingDay)
        }
        return result.toList()
    }

    /** Upper bound on how many past cycles a single run will settle. */
    const val MAX_LOOKBACK = 24
}
