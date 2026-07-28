package com.callbackdev.saldo.core.domain.recurrence

import com.callbackdev.saldo.core.domain.model.DailyBalance
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.UpcomingMovement
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Currency

/**
 * End-of-month balance forecast for the dashboard sparkline's dashed tail: an
 * estimated end-of-day balance for each day from tomorrow to the last day of
 * [LocalDate.getMonth]. Pure date/amount math, fully unit-testable.
 *
 * The estimate walks day by day from the balance **as of today** - the point
 * the drawn sparkline ends on - subtracting the month's average daily spend and
 * applying, on their own dates, three kinds of things that are known to be
 * coming (ADR 36):
 *
 * 1. fixed-amount recurring expenses *and* incomes, from their schedules.
 *    Incomes matter: a salary landing on the 27th is the difference between a
 *    forecast that dips and one that recovers, and ignoring it would make the
 *    tail systematically pessimistic. Variable-amount rules have no knowable
 *    figure and are skipped, like in [UpcomingChargesCalculator];
 * 2. confirmed movements already dated in the future, which the headline
 *    balance carries but the "as of today" figure does not;
 * 3. the month's pending occurrences, which no balance carries at all until
 *    they are confirmed.
 *
 * The walk starts from the today figure precisely so each of these is added
 * exactly once, on the day it lands. Anchoring it to the headline balance
 * instead would book every future movement immediately, on the first day of the
 * tail, and disagree with the point the solid line ends on.
 *
 * The daily average is built from *non-recurring* month-to-date spend only
 * (manual movements), never the raw month total: a recurring charge already
 * booked this month would otherwise inflate the average and be counted a
 * second time against its explicit future occurrences. Recurring spending is
 * thus modelled once, on its dates.
 *
 * Double counting between (1) and the other two is what the occurrence guard
 * prevents: an upcoming movement that materializes a rule occurrence takes that
 * occurrence out of the schedule walk, whatever the generation watermark says.
 *
 * Known and accepted approximations (the tail is always presented as an
 * estimate): occurrences due today but not yet generated are skipped, since
 * catch-up folds them into the actual balance shortly; a pending occurrence
 * whose date has already passed is applied on the first forecast day, because
 * it is committed money that has not left yet and there is no future day of its
 * own to place it on.
 */
object BalanceForecastCalculator {

    /**
     * Estimated balances for each day after [today] through the end of its
     * month; empty when [today] is the last day of the month.
     *
     * [balanceAsOfToday] is the balance counting movements dated up to today,
     * i.e. the last point of the drawn sparkline, so the dashed tail attaches
     * exactly where the solid line ends. [upcoming] holds the confirmed
     * future-dated movements and the pending occurrences, already reduced to
     * their signed effect on that same total by [upcomingNetByDay].
     */
    fun projectToEndOfMonth(
        balanceAsOfToday: BigDecimal,
        today: LocalDate,
        nonRecurringMonthToDateSpend: BigDecimal,
        rules: List<RecurringRule>,
        upcoming: Map<LocalDate, BigDecimal>,
        materializedOccurrences: Set<RuleOccurrence>,
        currency: Currency,
    ): List<DailyBalance> {
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
        if (today >= endOfMonth) return emptyList()

        val dailySpend = nonRecurringMonthToDateSpend.max(BigDecimal.ZERO).divide(
            BigDecimal(today.dayOfMonth),
            MoneyMapper.fractionDigits(currency),
            RoundingMode.HALF_UP,
        )
        val netByDay = recurringNetByDay(rules, today, endOfMonth, materializedOccurrences, currency)

        var running = balanceAsOfToday
        return generateSequence(today.plusDays(1)) { it.plusDays(1) }
            .takeWhile { it <= endOfMonth }
            .map { day ->
                running = running
                    .subtract(dailySpend)
                    .add(netByDay[day] ?: BigDecimal.ZERO)
                    .add(upcoming[day] ?: BigDecimal.ZERO)
                DailyBalance(day, running)
            }
            .toList()
    }

    /**
     * Signed effect of [movements] on the total balance, keyed by the forecast
     * day they land on. Only movements touching an account that counts toward
     * the total in the shown currency contribute, which is what
     * [includedAccountIds] carries: the set already encodes "included in the
     * total, not archived, denominated in the primary currency", exactly like
     * the balance query's own filters.
     *
     * Both legs of a transfer count, each against its own account: a transfer
     * between two included accounts nets to zero, one toward an excluded
     * account (or another currency) legitimately does not.
     *
     * Dates before [firstForecastDay] collapse onto it: a pending occurrence
     * from earlier in the month is committed money that no balance carries yet,
     * and dropping it would make the tail optimistic by exactly its amount.
     */
    fun upcomingNetByDay(
        movements: List<UpcomingMovement>,
        includedAccountIds: Set<Long>,
        firstForecastDay: LocalDate,
        lastForecastDay: LocalDate,
    ): Map<LocalDate, BigDecimal> {
        if (firstForecastDay > lastForecastDay) return emptyMap()
        val net = mutableMapOf<LocalDate, BigDecimal>()
        movements.forEach { movement ->
            val day = movement.date.coerceAtLeast(firstForecastDay)
            if (day > lastForecastDay) return@forEach
            val transaction = movement.transaction
            if (transaction.accountId in includedAccountIds) {
                net.merge(day, transaction.amount, BigDecimal::add)
            }
            val destination = transaction.transferAccountId
                ?.takeIf { transaction.type == TransactionType.TRANSFER && it in includedAccountIds }
            val received = transaction.transferAmount
            if (destination != null && received != null) {
                net.merge(day, received, BigDecimal::add)
            }
        }
        return net
    }

    /**
     * Signed net effect of the fixed-amount recurring flows in [currency] on
     * each day after [today] through [endOfMonth]: expenses negative, incomes
     * positive. Each rule's window starts the day after `lastGeneratedDate`
     * when that is in the future, mirroring [UpcomingChargesCalculator], and
     * any occurrence already materialized by an upcoming movement is skipped:
     * the row itself is counted, the schedule that produced it is not.
     */
    private fun recurringNetByDay(
        rules: List<RecurringRule>,
        today: LocalDate,
        endOfMonth: LocalDate,
        materializedOccurrences: Set<RuleOccurrence>,
        currency: Currency,
    ): Map<LocalDate, BigDecimal> {
        val tomorrow = today.plusDays(1)
        val net = mutableMapOf<LocalDate, BigDecimal>()
        rules
            .filter { rule ->
                (rule.type == TransactionType.EXPENSE || rule.type == TransactionType.INCOME) &&
                    rule.currency == currency &&
                    !rule.isVariableAmount &&
                    rule.amount != null
            }
            .forEach { rule ->
                val floor = rule.lastGeneratedDate?.plusDays(1)?.takeIf { it > tomorrow } ?: tomorrow
                if (floor > endOfMonth) return@forEach
                val signed = if (rule.type == TransactionType.EXPENSE) rule.amount!!.negate() else rule.amount!!
                RecurrenceCalculator.occurrencesInClosedRange(rule, floor, endOfMonth)
                    .filterNot { date -> RuleOccurrence(rule.id, date) in materializedOccurrences }
                    .forEach { date -> net.merge(date, signed, BigDecimal::add) }
            }
        return net
    }
}

/**
 * One occurrence of one rule, the key that tells a schedule slot apart from the
 * movement that fills it. Keyed on the occurrence date rather than the
 * movement's own date: editing a generated movement's date moves the movement,
 * not the slot it came from.
 */
data class RuleOccurrence(val ruleId: Long, val date: LocalDate)
