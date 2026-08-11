package com.callbackdev.saldo.feature.dashboard

import com.callbackdev.saldo.core.domain.model.DailyBalance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class MonthComparisonTest {

    @Test
    fun `normalizes both months to their own starting balance`() {
        val today = LocalDate.of(2026, 8, 3)
        // Walk from 2026-06-30 (baseline of July) through today: balance grows
        // by 1 every day in July and falls by 2 every day in August.
        val history = walk(
            start = LocalDate.of(2026, 6, 30),
            startBalance = BigDecimal("100"),
        ) { date -> if (date.monthValue == 7) BigDecimal.ONE else BigDecimal("-2") }
            .takeWhile { !it.date.isAfter(today) }
            .toList()

        val comparison = buildMonthComparison(history, today)!!

        assertEquals(31, comparison.previous.size)
        assertEquals(BigDecimal.ONE, comparison.previous.first())
        assertEquals(BigDecimal("31"), comparison.previous.last())
        // August leaves from July's closing balance, not from 100: the series
        // is anchored to its own month start.
        assertEquals(3, comparison.current.size)
        assertEquals(BigDecimal("-2"), comparison.current.first())
        assertEquals(BigDecimal("-6"), comparison.current.last())
    }

    @Test
    fun `handles short months`() {
        val today = LocalDate.of(2026, 3, 1)
        val history = walk(
            start = LocalDate.of(2026, 1, 31),
            startBalance = BigDecimal.ZERO,
        ) { BigDecimal.ONE }
            .takeWhile { !it.date.isAfter(today) }
            .toList()

        val comparison = buildMonthComparison(history, today)!!

        assertEquals(28, comparison.previous.size)
        assertEquals(1, comparison.current.size)
    }

    @Test
    fun `returns null when the walk does not cover the window`() {
        val today = LocalDate.of(2026, 8, 3)
        assertNull(buildMonthComparison(emptyList(), today))
        // Missing the previous month entirely (fresh install mid-month).
        val partial = walk(
            start = LocalDate.of(2026, 8, 1),
            startBalance = BigDecimal.ZERO,
        ) { BigDecimal.ONE }
            .takeWhile { !it.date.isAfter(today) }
            .toList()
        assertNull(buildMonthComparison(partial, today))
    }

    /** Infinite daily walk from [start], moving by [dailyNet] of each day. */
    private fun walk(
        start: LocalDate,
        startBalance: BigDecimal,
        dailyNet: (LocalDate) -> BigDecimal,
    ): Sequence<DailyBalance> = generateSequence(
        DailyBalance(start, startBalance),
    ) { previous ->
        val date = previous.date.plusDays(1)
        DailyBalance(date, previous.balance.add(dailyNet(date)))
    }
}
