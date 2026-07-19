package com.callbackdev.saldo.core.domain.recurrence

import com.callbackdev.saldo.core.domain.model.DailyBalance
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
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
 * The estimate walks day by day from the current balance, subtracting the
 * month's average daily spend (month-to-date spend / days elapsed, the same
 * "media giornaliera" heuristic as the recap) and applying fixed-amount
 * recurring expenses *and* incomes on their due dates. Incomes matter: a
 * salary landing on the 27th is the difference between a forecast that dips
 * and one that recovers, and ignoring it would make the tail systematically
 * pessimistic. Variable-amount rules have no knowable figure and are skipped,
 * like in [UpcomingChargesCalculator].
 *
 * Known and accepted approximations (the tail is always presented as an
 * estimate): the daily average also spreads recurring charges already paid
 * this month, and occurrences due today but not yet generated are skipped
 * (catch-up will fold them into the actual balance shortly).
 */
object BalanceForecastCalculator {

    /**
     * Estimated balances for each day after [today] through the end of its
     * month; empty when [today] is the last day of the month. The walk starts
     * from [currentBalance] (the dashboard's headline figure), so the dashed
     * tail attaches exactly where the historical sparkline ends.
     */
    fun projectToEndOfMonth(
        currentBalance: BigDecimal,
        today: LocalDate,
        monthToDateSpend: BigDecimal,
        rules: List<RecurringRule>,
        currency: Currency,
    ): List<DailyBalance> {
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
        if (today >= endOfMonth) return emptyList()

        val dailySpend = monthToDateSpend.max(BigDecimal.ZERO).divide(
            BigDecimal(today.dayOfMonth),
            MoneyMapper.fractionDigits(currency),
            RoundingMode.HALF_UP,
        )
        val netByDay = recurringNetByDay(rules, today, endOfMonth, currency)

        var running = currentBalance
        return generateSequence(today.plusDays(1)) { it.plusDays(1) }
            .takeWhile { it <= endOfMonth }
            .map { day ->
                running = running.subtract(dailySpend).add(netByDay[day] ?: BigDecimal.ZERO)
                DailyBalance(day, running)
            }
            .toList()
    }

    /**
     * Signed net effect of the fixed-amount recurring flows in [currency] on
     * each day after [today] through [endOfMonth]: expenses negative, incomes
     * positive. Each rule's window starts the day after `lastGeneratedDate`
     * when that is in the future, mirroring [UpcomingChargesCalculator], so a
     * pre-generated occurrence is never counted twice.
     */
    private fun recurringNetByDay(
        rules: List<RecurringRule>,
        today: LocalDate,
        endOfMonth: LocalDate,
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
                RecurrenceCalculator.occurrencesInClosedRange(rule, floor, endOfMonth).forEach { date ->
                    net.merge(date, signed, BigDecimal::add)
                }
            }
        return net
    }
}
