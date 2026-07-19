package com.callbackdev.saldo.core.domain.recurrence

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class BalanceForecastCalculatorTest {

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
        isVariableAmount = isVariable,
        lastGeneratedDate = lastGenerated,
    )

    private fun forecast(
        balance: String = "100.00",
        today: LocalDate = LocalDate.of(2026, 7, 10),
        nonRecurringMonthToDateSpend: String = "0.00",
        rules: List<RecurringRule> = emptyList(),
    ) = BalanceForecastCalculator.projectToEndOfMonth(
        currentBalance = BigDecimal(balance),
        today = today,
        nonRecurringMonthToDateSpend = BigDecimal(nonRecurringMonthToDateSpend),
        rules = rules,
        currency = eur,
    )

    @Test
    fun `covers every day from tomorrow to the end of the month`() {
        val points = forecast(today = LocalDate.of(2026, 7, 10))

        assertEquals(21, points.size)
        assertEquals(LocalDate.of(2026, 7, 11), points.first().date)
        assertEquals(LocalDate.of(2026, 7, 31), points.last().date)
    }

    @Test
    fun `the last day of the month yields no forecast`() {
        assertTrue(forecast(today = LocalDate.of(2026, 7, 31)).isEmpty())
    }

    @Test
    fun `the average daily spend is subtracted each day`() {
        // 50.00 spent in 10 days: 5.00/day for the remaining 21 days.
        val points = forecast(balance = "100.00", nonRecurringMonthToDateSpend = "50.00")

        assertEquals(BigDecimal("95.00"), points.first().balance)
        assertEquals(BigDecimal("-5.00"), points.last().balance)
    }

    @Test
    fun `the daily average is rounded to the currency scale`() {
        // 10.00 / 3 days elapsed = 3.33/day.
        val points = forecast(
            balance = "100.00",
            today = LocalDate.of(2026, 7, 3),
            nonRecurringMonthToDateSpend = "10.00",
        )

        assertEquals(BigDecimal("96.67"), points.first().balance)
    }

    @Test
    fun `an already-charged recurrence does not bend the tail when there is no variable spend`() {
        // The user's case: a 1.00 monthly rule fired on the 1st, no other spend.
        // Its amount lands in the balance, not in the non-recurring average (0),
        // and its next occurrence is next month, so the tail stays flat.
        val points = forecast(
            balance = "100.00",
            today = LocalDate.of(2026, 7, 10),
            nonRecurringMonthToDateSpend = "0.00",
            rules = listOf(
                rule(amount = BigDecimal("1.00"), dayOfReference = 1, lastGenerated = LocalDate.of(2026, 7, 1)),
            ),
        )

        assertEquals(BigDecimal("100.00"), points.first().balance)
        assertEquals(BigDecimal("100.00"), points.last().balance)
    }

    @Test
    fun `a recurring expense lands on its due date`() {
        val points = forecast(rules = listOf(rule()))

        val byDate = points.associate { it.date to it.balance }
        assertEquals(BigDecimal("100.00"), byDate[LocalDate.of(2026, 7, 19)])
        assertEquals(BigDecimal("90.00"), byDate[LocalDate.of(2026, 7, 20)])
        assertEquals(BigDecimal("90.00"), points.last().balance)
    }

    @Test
    fun `a recurring income raises the balance on its due date`() {
        val points = forecast(
            rules = listOf(rule(type = TransactionType.INCOME, amount = BigDecimal("2000.00"), dayOfReference = 27)),
        )

        val byDate = points.associate { it.date to it.balance }
        assertEquals(BigDecimal("100.00"), byDate[LocalDate.of(2026, 7, 26)])
        assertEquals(BigDecimal("2100.00"), byDate[LocalDate.of(2026, 7, 27)])
    }

    @Test
    fun `weekly rules count every remaining occurrence`() {
        // Mondays 13, 20, 27 July from Friday the 10th.
        val points = forecast(
            rules = listOf(
                rule(
                    frequency = RecurrenceFrequency.WEEKLY,
                    startDate = LocalDate.of(2026, 7, 6),
                    dayOfReference = null,
                ),
            ),
        )

        assertEquals(BigDecimal("70.00"), points.last().balance)
    }

    @Test
    fun `variable-amount and foreign-currency rules are ignored`() {
        val points = forecast(
            rules = listOf(
                rule(id = 1L, isVariable = true, amount = null),
                rule(id = 2L, currency = usd),
            ),
        )

        assertEquals(BigDecimal("100.00"), points.last().balance)
    }

    @Test
    fun `an occurrence already generated ahead of time is not counted again`() {
        val points = forecast(
            rules = listOf(rule(lastGenerated = LocalDate.of(2026, 7, 20))),
        )

        assertEquals(BigDecimal("100.00"), points.last().balance)
    }

    @Test
    fun `expenses and incomes on the same day net out`() {
        val points = forecast(
            rules = listOf(
                rule(id = 1L, amount = BigDecimal("30.00")),
                rule(id = 2L, type = TransactionType.INCOME, amount = BigDecimal("50.00")),
            ),
        )

        val byDate = points.associate { it.date to it.balance }
        assertEquals(BigDecimal("120.00"), byDate[LocalDate.of(2026, 7, 20)])
    }
}
