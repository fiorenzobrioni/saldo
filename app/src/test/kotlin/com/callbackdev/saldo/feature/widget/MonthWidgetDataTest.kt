package com.callbackdev.saldo.feature.widget

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/**
 * What the month widgets say (ADR 54): the next charge and the sentence, as
 * choices pinned in no particular language.
 */
class MonthWidgetDataTest {

    private val eur = Currency.getInstance("EUR")
    private val today = LocalDate.of(2026, 10, 16)

    private fun rule(
        name: String,
        day: Int,
        type: TransactionType = TransactionType.EXPENSE,
        lastGenerated: LocalDate? = null,
        paused: Boolean = false,
        variable: Boolean = false,
        amount: String = "10.00",
    ) = RecurringRule(
        name = name,
        type = type,
        currency = eur,
        accountId = 1L,
        frequency = RecurrenceFrequency.MONTHLY,
        startDate = LocalDate.of(2026, 1, day),
        amount = BigDecimal(amount),
        lastGeneratedDate = lastGenerated,
        isPaused = paused,
        isVariableAmount = variable,
    )

    @Test
    fun `the nearest expense is the next charge`() {
        val next = nextCharge(listOf(rule("Rent", 1), rule("Gym", 23), rule("Phone", 28)), today)
        assertEquals("Gym", next?.name)
        assertEquals(LocalDate.of(2026, 10, 23), next?.date)
    }

    /** The user's case: a charge added today, due today, reads as today's until it is recorded. */
    @Test
    fun `a charge due today and not yet recorded is today's`() {
        val next = nextCharge(listOf(rule("Netflix", 16, lastGenerated = LocalDate.of(2026, 9, 16))), today)
        assertEquals(today, next?.date)
    }

    @Test
    fun `once recorded, today's charge moves on to its next date`() {
        val next = nextCharge(listOf(rule("Netflix", 16, lastGenerated = today)), today)
        assertEquals(LocalDate.of(2026, 11, 16), next?.date)
    }

    @Test
    fun `paused rules, incomes and transfers are not charges`() {
        val next = nextCharge(
            listOf(
                rule("Paused", 17, paused = true),
                rule("Salary", 18, type = TransactionType.INCOME),
                rule("Savings", 19, type = TransactionType.TRANSFER),
            ),
            today,
        )
        assertNull(next)
    }

    @Test
    fun `a variable charge has no amount`() {
        assertNull(nextCharge(listOf(rule("Power", 20, variable = true)), today)?.amount)
    }

    @Test
    fun `two charges on one day, the larger first, steadily`() {
        val next = nextCharge(listOf(rule("Small", 20, amount = "5.00"), rule("Large", 20, amount = "50.00")), today)
        assertEquals("Large", next?.name)
    }

    @Test
    fun `with a budget and figures, the sentence is what is left to spend`() {
        val words = monthWords(WidgetRendering.monthData(), showAmounts = true)
        assertTrue(words is MonthWords.CanSpend)
        assertEquals(BigDecimal("410.00"), (words as MonthWords.CanSpend).amount)
    }

    @Test
    fun `over the budget, the sentence says by how much`() {
        val words = monthWords(WidgetRendering.monthData(over = true), showAmounts = true)
        assertEquals(MonthWords.Over(BigDecimal("162.40")), words)
    }

    @Test
    fun `the app lock hides every figure, whatever the card's switch says`() {
        assertEquals(MonthWords.UsedPercent(64), monthWords(WidgetRendering.monthData(amounts = false), showAmounts = true))
        assertEquals(MonthWords.OverHidden, monthWords(WidgetRendering.monthData(amounts = false, over = true), showAmounts = true))
    }

    @Test
    fun `the card's own switch hides the figures too`() {
        assertEquals(MonthWords.UsedPercent(64), monthWords(WidgetRendering.monthData(), showAmounts = false))
    }

    @Test
    fun `with no budget the sentence is the month's spend, or the next charge when figures are hidden`() {
        assertEquals(MonthWords.Spent(BigDecimal("960.00")), monthWords(WidgetRendering.monthData(budget = false), showAmounts = true))
        val hidden = monthWords(WidgetRendering.monthData(budget = false, amounts = false), showAmounts = true)
        assertTrue(hidden is MonthWords.NextOnly)
    }

    @Test
    fun `before onboarding the card is a door into the app`() {
        assertEquals(MonthWords.Setup, monthWords(WidgetRendering.monthData(ready = false), showAmounts = true))
    }

    @Test
    fun `the share used is floored, never rounded up to the next percent`() {
        val budget = WidgetRendering.monthData().budget!!.copy(fraction = 0.7999f)
        assertEquals(79, budget.percent)
    }

    @Test
    fun `the month's elapsed share and days left count today`() {
        val data = WidgetRendering.monthData()
        assertEquals(16, data.daysLeft)
        assertEquals(16f / 31f, data.monthElapsed)
    }
}
