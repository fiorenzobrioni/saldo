package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A savings goal joined with the current state of its linked account and the
 * recurring transfers feeding it. Every figure is computed, never stored.
 *
 * [saved] is the linked account's balance (>= target means [isReached]);
 * [remaining] is target minus saved, floored at zero. [fraction] is saved over
 * target, deliberately not capped so the UI can print 100% exactly and show the
 * reached state. [suggestedMonthly] is what to set aside each month to reach the
 * target by [SavingsGoal.targetDate]: null without a date, when reached, or when
 * the date is not in a future month. [plannedMonthly] is the monthly-equivalent
 * of the recurring transfers landing in the account; [projectedDate] is when the
 * goal is reached at that rate (null when nothing is planned or already
 * reached); [onTrack] compares planned against suggested (null when either is
 * missing).
 */
data class SavingsGoalProgress(
    val goal: SavingsGoal,
    val account: Account?,
    val saved: BigDecimal,
    val remaining: BigDecimal,
    val fraction: Float,
    val isReached: Boolean,
    val suggestedMonthly: BigDecimal? = null,
    val plannedMonthly: BigDecimal = BigDecimal.ZERO,
    val projectedDate: LocalDate? = null,
    val onTrack: Boolean? = null,
)
