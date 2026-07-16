package com.callbackdev.saldo.core.domain.creditcard

import com.callbackdev.saldo.core.domain.model.CreditCardConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BillingCycleCalculatorTest {

    private fun config(
        closing: Int = 20,
        due: Int = 5,
        lastSettled: LocalDate? = null,
    ) = CreditCardConfig(
        statementClosingDay = closing,
        paymentDueDay = due,
        lastSettledClosing = lastSettled,
    )

    @Test
    fun `closing on or before returns this month once the closing day is reached`() {
        val closing = BillingCycleCalculator.closingOnOrBefore(LocalDate.of(2024, 3, 25), 20)
        assertEquals(LocalDate.of(2024, 3, 20), closing)
    }

    @Test
    fun `closing on or before falls back to the previous month before the closing day`() {
        val closing = BillingCycleCalculator.closingOnOrBefore(LocalDate.of(2024, 3, 10), 20)
        assertEquals(LocalDate.of(2024, 2, 20), closing)
    }

    @Test
    fun `closing day of 31 clamps to the last day of a short month`() {
        val closing = BillingCycleCalculator.closingOnOrBefore(LocalDate.of(2024, 2, 15), 31)
        // February 2024 is a leap year: the last day is the 29th.
        assertEquals(LocalDate.of(2024, 1, 31), closing)
        val febEnd = BillingCycleCalculator.closingOnOrBefore(LocalDate.of(2024, 3, 1), 31)
        assertEquals(LocalDate.of(2024, 2, 29), febEnd)
    }

    @Test
    fun `payment due is the configured day of the following month`() {
        val due = BillingCycleCalculator.paymentDueFor(LocalDate.of(2024, 1, 31), 5)
        assertEquals(LocalDate.of(2024, 2, 5), due)
    }

    @Test
    fun `cycle window runs from the day after the previous closing to the closing`() {
        val cycle = BillingCycleCalculator.cycleEndingOn(LocalDate.of(2024, 3, 20), config())
        assertEquals(LocalDate.of(2024, 2, 21), cycle.start)
        assertEquals(LocalDate.of(2024, 3, 20), cycle.closing)
        assertEquals(LocalDate.of(2024, 4, 5), cycle.paymentDue)
    }

    @Test
    fun `due statements skips the newest cycle that has not reached its payment date`() {
        // Today is past the March 20 closing but before its April 5 due date; the
        // owed statement is the previous cycle (closed Feb 20, due March 5).
        val due = BillingCycleCalculator.dueStatements(
            LocalDate.of(2024, 3, 25),
            config(lastSettled = LocalDate.of(2024, 1, 20)),
        )
        assertEquals(1, due.size)
        assertEquals(LocalDate.of(2024, 2, 20), due.single().closing)
        assertEquals(LocalDate.of(2024, 3, 5), due.single().paymentDue)
    }

    @Test
    fun `due statements is empty once the last cycle is settled`() {
        val due = BillingCycleCalculator.dueStatements(
            LocalDate.of(2024, 3, 25),
            config(lastSettled = LocalDate.of(2024, 2, 20)),
        )
        assertEquals(emptyList<BillingCycle>(), due)
    }

    @Test
    fun `due statements returns every unsettled due cycle oldest first`() {
        // Device off for two months: cycles closed Feb 20 (due Mar 5) and Mar 20
        // (due Apr 5) are both due on Apr 25 and unsettled since the Jan 20 mark.
        val due = BillingCycleCalculator.dueStatements(
            LocalDate.of(2024, 4, 25),
            config(lastSettled = LocalDate.of(2024, 1, 20)),
        )
        assertEquals(
            listOf(LocalDate.of(2024, 2, 20), LocalDate.of(2024, 3, 20)),
            due.map { it.closing },
        )
    }

    @Test
    fun `a card seeded at creation owes nothing until its next cycle comes due`() {
        // Seeded watermark = the closing before creation (Feb 20). Right after,
        // the open March 20 cycle is not due until April 5, so nothing is owed.
        val due = BillingCycleCalculator.dueStatements(
            LocalDate.of(2024, 3, 3),
            config(lastSettled = LocalDate.of(2024, 2, 20)),
        )
        assertEquals(emptyList<BillingCycle>(), due)
    }
}
