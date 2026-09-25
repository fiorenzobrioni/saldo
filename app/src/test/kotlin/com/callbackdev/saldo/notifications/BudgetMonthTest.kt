package com.callbackdev.saldo.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency

/**
 * The arithmetic under a budget notification's story, and the quiet hours
 * every notification keeps: what the expanded body says has to be right on the
 * short months and the last day, where it is easiest to get wrong.
 */
class BudgetMonthTest {

    private val euro = Currency.getInstance("EUR")
    private val yen = Currency.getInstance("JPY")

    @Test
    fun `the days left count today, down to the last day`() {
        assertEquals(30, BudgetMonth.of(LocalDate.of(2026, 9, 1)).daysLeft)
        assertEquals(7, BudgetMonth.of(LocalDate.of(2026, 9, 24)).daysLeft)
        assertEquals(1, BudgetMonth.of(LocalDate.of(2026, 9, 30)).daysLeft)
        assertEquals(1, BudgetMonth.of(LocalDate.of(2028, 2, 29)).daysLeft)
    }

    @Test
    fun `the month gone by includes today and ends at 100`() {
        assertEquals(3, BudgetMonth.of(LocalDate.of(2026, 9, 1)).elapsedPercent)
        assertEquals(80, BudgetMonth.of(LocalDate.of(2026, 9, 24)).elapsedPercent)
        assertEquals(100, BudgetMonth.of(LocalDate.of(2026, 2, 28)).elapsedPercent)
    }

    /** Rounded down: an allowance that rounded up would promise money the budget does not have. */
    @Test
    fun `the daily allowance rounds down to the currency's minor unit`() {
        val month = BudgetMonth.of(LocalDate.of(2026, 9, 24))
        assertEquals(BigDecimal("14.28"), month.dailyAllowance(BigDecimal("100.00"), euro))
        assertEquals(BigDecimal("142"), month.dailyAllowance(BigDecimal("1000"), yen))
    }

    @Test
    fun `nothing left means no allowance, never a zero or a negative one`() {
        val month = BudgetMonth.of(LocalDate.of(2026, 9, 24))
        assertNull(month.dailyAllowance(BigDecimal.ZERO, euro))
        assertNull(month.dailyAllowance(BigDecimal("-5.00"), euro))
        assertNull(month.dailyAllowance(BigDecimal("0.03"), euro))
    }

    @Test
    fun `quiet hours run from ten at night to seven in the morning`() {
        assertTrue(SaldoNotifications.isQuiet(LocalTime.of(22, 0)))
        assertTrue(SaldoNotifications.isQuiet(LocalTime.of(3, 0)))
        assertTrue(SaldoNotifications.isQuiet(LocalTime.of(6, 59)))
        assertFalse(SaldoNotifications.isQuiet(LocalTime.of(7, 0)))
        assertFalse(SaldoNotifications.isQuiet(LocalTime.of(21, 59)))
    }

    @Test
    fun `the expanded body keeps the headline and puts the story under it`() {
        assertEquals("Spesi 400 €", SaldoNotifications.expanded("Spesi 400 €", emptyList()))
        assertEquals("A\n\nB\nC", SaldoNotifications.expanded("A", listOf("B", "C")))
    }
}
