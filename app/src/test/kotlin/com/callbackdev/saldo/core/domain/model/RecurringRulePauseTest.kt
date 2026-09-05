package com.callbackdev.saldo.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class RecurringRulePauseTest {

    private val today: LocalDate = LocalDate.of(2026, 7, 9)

    private fun rule(isPaused: Boolean = false, lastGenerated: LocalDate? = null) = RecurringRule(
        name = "Gym",
        type = TransactionType.EXPENSE,
        currency = Currency.getInstance("EUR"),
        accountId = 1L,
        frequency = RecurrenceFrequency.MONTHLY,
        startDate = LocalDate.of(2026, 1, 5),
        amount = BigDecimal("30.00"),
        dayOfReference = 5,
        lastGeneratedDate = lastGenerated,
        isPaused = isPaused,
    )

    @Test
    fun `a paused rule does not run in any month even when its schedule covers it`() {
        assertTrue(rule().runsInMonthOf(today))
        assertFalse(rule(isPaused = true).runsInMonthOf(today))
    }

    @Test
    fun `resumed clears the flag and moves a stale watermark to the day before`() {
        val resumed = rule(isPaused = true, lastGenerated = LocalDate.of(2026, 4, 5)).resumed(today)

        assertFalse(resumed.isPaused)
        assertEquals(LocalDate.of(2026, 7, 8), resumed.lastGeneratedDate)
    }

    @Test
    fun `resumed seeds the watermark on a rule that never generated`() {
        assertEquals(LocalDate.of(2026, 7, 8), rule(isPaused = true).resumed(today).lastGeneratedDate)
    }

    @Test
    fun `resumed keeps a watermark at or past yesterday`() {
        assertEquals(today, rule(isPaused = true, lastGenerated = today).resumed(today).lastGeneratedDate)
        assertEquals(
            LocalDate.of(2026, 7, 8),
            rule(isPaused = true, lastGenerated = LocalDate.of(2026, 7, 8)).resumed(today).lastGeneratedDate,
        )
    }
}
