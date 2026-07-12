package com.callbackdev.saldo.core.domain.recurrence

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class UpcomingChargesCalculatorTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")

    private fun rule(
        id: Long = 1L,
        type: TransactionType = TransactionType.EXPENSE,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        startDate: LocalDate = LocalDate.of(2026, 1, 20),
        dayOfReference: Int? = 20,
        amount: BigDecimal? = BigDecimal("10.00"),
        currency: Currency = eur,
        lastGenerated: LocalDate? = null,
        endDate: LocalDate? = null,
        isVariable: Boolean = false,
    ) = RecurringRule(
        id = id,
        name = "rule-$id",
        type = type,
        currency = currency,
        accountId = 1L,
        frequency = frequency,
        startDate = startDate,
        amount = amount,
        dayOfReference = dayOfReference,
        endDate = endDate,
        isVariableAmount = isVariable,
        lastGeneratedDate = lastGenerated,
    )

    private fun remaining(rules: List<RecurringRule>, today: LocalDate) =
        UpcomingChargesCalculator.remainingExpenseChargesInMonth(rules, today, eur)

    @Test
    fun `sums the charges still due between today and month end`() = assertEquals(
        // The 20th of July is ahead of the 12th: one 10.00 charge.
        BigDecimal("10.00"),
        remaining(listOf(rule()), LocalDate.of(2026, 7, 12)),
    )

    @Test
    fun `a charge already generated today is not counted again`() {
        val today = LocalDate.of(2026, 7, 20)
        assertEquals(
            BigDecimal.ZERO,
            remaining(listOf(rule(lastGenerated = today)), today),
        )
    }

    @Test
    fun `a charge due today but not yet generated is counted`() = assertEquals(
        BigDecimal("10.00"),
        remaining(listOf(rule(lastGenerated = LocalDate.of(2026, 6, 20))), LocalDate.of(2026, 7, 20)),
    )

    @Test
    fun `weekly rules count every remaining occurrence of the month`() = assertEquals(
        // Mondays 13, 20, 27 July from Sunday the 12th.
        BigDecimal("30.00"),
        remaining(
            listOf(
                rule(
                    frequency = RecurrenceFrequency.WEEKLY,
                    startDate = LocalDate.of(2026, 7, 6),
                    dayOfReference = null,
                ),
            ),
            LocalDate.of(2026, 7, 12),
        ),
    )

    @Test
    fun `variable incomes and foreign currencies are skipped`() = assertEquals(
        BigDecimal.ZERO,
        remaining(
            listOf(
                rule(id = 1L, isVariable = true, amount = null),
                rule(id = 2L, type = TransactionType.INCOME),
                rule(id = 3L, currency = usd),
            ),
            LocalDate.of(2026, 7, 12),
        ),
    )

    @Test
    fun `an ended rule contributes nothing`() = assertEquals(
        BigDecimal.ZERO,
        remaining(
            listOf(rule(endDate = LocalDate.of(2026, 7, 10))),
            LocalDate.of(2026, 7, 12),
        ),
    )

    @Test
    fun `short months clamp the day of reference`() = assertEquals(
        // Day 31 in June clamps to the 30th, still inside the month.
        BigDecimal("10.00"),
        remaining(
            listOf(rule(startDate = LocalDate.of(2026, 1, 31), dayOfReference = 31)),
            LocalDate.of(2026, 6, 29),
        ),
    )
}
