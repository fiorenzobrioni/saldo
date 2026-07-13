package com.callbackdev.saldo.core.domain.recurrence

import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/**
 * Pure companion of [RecurrenceCalculator] for the safe-to-spend figure: how
 * much recurring money is still due between today and the end of the month.
 */
object UpcomingChargesCalculator {

    /**
     * Positive total of the remaining expense charges of [rules] in
     * [currency] from [today] (inclusive) to the end of its month.
     *
     * Only fixed-amount expense rules count: a variable-amount rule has no
     * knowable figure until confirmed. Each rule's window starts at the same
     * floor the dashboard uses (the day after `lastGeneratedDate`, when that
     * is in the future), so an occurrence already generated today is not
     * counted again: if it is confirmed it sits in the month's spend, if it
     * is pending the safe-to-spend committed figure carries it.
     */
    fun remainingExpenseChargesInMonth(
        rules: List<RecurringRule>,
        today: LocalDate,
        currency: Currency,
    ): BigDecimal {
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
        return rules
            .filter { rule ->
                rule.type == TransactionType.EXPENSE &&
                    rule.currency == currency &&
                    !rule.isVariableAmount &&
                    rule.amount != null
            }
            .fold(BigDecimal.ZERO) { acc, rule ->
                val floor = rule.lastGeneratedDate?.plusDays(1)?.takeIf { it > today } ?: today
                if (floor > endOfMonth) return@fold acc
                val occurrences = RecurrenceCalculator.occurrencesInClosedRange(rule, floor, endOfMonth)
                if (occurrences.isEmpty()) return@fold acc
                acc.add(rule.amount!!.multiply(BigDecimal(occurrences.size)))
            }
    }
}
