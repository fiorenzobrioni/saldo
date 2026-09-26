package com.callbackdev.saldo.notifications

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Currency

/**
 * Where a monthly budget stands in its month, for the story a budget
 * notification tells under its headline: how many days are left (today
 * included), how much of the month has gone, and what is left to spend per day.
 * Pure, so the arithmetic is pinned by `BudgetMonthTest` without a device.
 */
internal data class BudgetMonth(
    /** Days from today to the end of the month, today included: never below 1. */
    val daysLeft: Int,
    /** How much of the month has gone by the end of today, 0 to 100. */
    val elapsedPercent: Int,
) {
    /**
     * What is left to spend per day until the month ends, rounded down to the
     * currency's minor unit: an allowance that rounds up would promise money the
     * budget does not have. Null when nothing is left.
     */
    fun dailyAllowance(remaining: BigDecimal, currency: Currency): BigDecimal? {
        if (remaining.signum() <= 0) return null
        val scale = currency.defaultFractionDigits.coerceAtLeast(0)
        return remaining.divide(BigDecimal(daysLeft), scale, RoundingMode.DOWN)
            .takeIf { it.signum() > 0 }
    }

    companion object {
        fun of(today: LocalDate): BudgetMonth {
            val length = today.lengthOfMonth()
            return BudgetMonth(
                daysLeft = length - today.dayOfMonth + 1,
                elapsedPercent = today.dayOfMonth * PERCENT / length,
            )
        }

        private const val PERCENT = 100
    }
}
