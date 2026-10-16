package com.callbackdev.saldo.feature.widget

import com.callbackdev.saldo.core.domain.model.BudgetLevel
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

/**
 * What the two month widgets («In words» and «The month») draw, read in one pass
 * by [MonthWidgetDataLoader]: the overall monthly budget and how far into it the
 * month is, what the month has cost when there is no budget, and the next
 * recurring charge (ADR 54).
 *
 * Pure on purpose: the sentence the card prints ([monthWords]) and the next
 * charge ([nextCharge]) are decided here and pinned by JVM tests, and the
 * renderer only formats what this says.
 */
data class MonthWidgetData(
    val today: LocalDate,
    val currency: Currency,
    /** False before onboarding: with no account at all the card can only open the app. */
    val hasAccounts: Boolean,
    /** The overall monthly budget, or null when the reader has none in [currency]. */
    val budget: MonthBudget?,
    /** Positive magnitude spent from the 1st through today (the statistics spend). */
    val spent: BigDecimal,
    /** The next recurring expense, today included until it is recorded. */
    val next: NextCharge?,
    /**
     * False while the app lock is on (ADR 54): the PIN guards the figures in the
     * app, and a home screen is not behind it. The card then says the same
     * things without a single amount.
     */
    val amountsAllowed: Boolean,
    /** True when a figure includes amounts converted at their own day's rate (ADR 40). */
    val includesEstimates: Boolean = false,
) {
    val month: YearMonth get() = YearMonth.from(today)

    /** Days of the month left, today included. */
    val daysLeft: Int get() = today.lengthOfMonth() - today.dayOfMonth + 1

    /** How much of the month has gone by, today included: the ring's inner band. */
    val monthElapsed: Float get() = today.dayOfMonth.toFloat() / today.lengthOfMonth()
}

/**
 * The overall budget as the cards read it: the limit and the statistics spend
 * against it (the budget card's figures), and what is still safely spendable
 * once the charges already committed this month are counted (the dashboard's
 * "safe to spend", which is what a sentence about the rest of the month means).
 */
data class MonthBudget(
    val limit: BigDecimal,
    val spent: BigDecimal,
    val fraction: Float,
    val level: BudgetLevel,
    /** The limit minus the spend and every charge still committed this month; negative when over. */
    val safeToSpend: BigDecimal,
    /** [safeToSpend] over the days left, floored; null when nothing remains. */
    val perDay: BigDecimal?,
) {
    /** Whole percent spent, floored, so 79.9% never reads as 80. */
    val percent: Int get() = (fraction * PERCENT).toInt().coerceAtLeast(0)
}

/** The next recurring expense: what, when, and how much when the amount is fixed. */
data class NextCharge(
    val name: String,
    val date: LocalDate,
    val amount: BigDecimal?,
    val currency: Currency,
)

/**
 * The next recurring expense on or after [today]. A rule's next charge follows
 * the hub's floor: an occurrence already recorded is behind it, so a charge
 * that fired today moves on to its next date the moment it is recorded, and
 * one due today and not yet recorded reads as today's. Paused rules charge
 * nothing and are skipped; incomes and transfers are not charges.
 */
fun nextCharge(rules: List<RecurringRule>, today: LocalDate): NextCharge? = rules
    .asSequence()
    .filter { it.type == TransactionType.EXPENSE && !it.isPaused }
    .mapNotNull { rule ->
        val afterGenerated = rule.lastGeneratedDate?.plusDays(1)
        val floor = if (afterGenerated != null && afterGenerated > today) afterGenerated else today
        RecurrenceCalculator.nextOccurrence(rule, floor)?.let { date ->
            NextCharge(
                name = rule.name,
                date = date,
                amount = rule.amount.takeUnless { rule.isVariableAmount },
                currency = rule.currency,
            )
        }
    }
    // The nearest first; on the same day the larger charge, then the name, so
    // the card does not switch between two charges of one day on every redraw.
    .sortedWith(compareBy<NextCharge>({ it.date }, { -(it.amount?.toDouble() ?: 0.0) }, { it.name }))
    .firstOrNull()

/**
 * The sentence a month card leads with, as a choice the renderer words: which
 * situation the month is in, never a string, so the choice is pinned by tests
 * in any language.
 */
sealed interface MonthWords {
    /** Under budget, figures shown: what is still safe to spend, and per day. */
    data class CanSpend(val amount: BigDecimal, val perDay: BigDecimal?) : MonthWords

    /** Spend and committed charges past the budget, figures shown: by how much. */
    data class Over(val amount: BigDecimal) : MonthWords

    /** A budget, figures hidden: how much of it the month has used. */
    data class UsedPercent(val percent: Int) : MonthWords

    /** A budget past its limit, figures hidden. */
    data object OverHidden : MonthWords

    /** No budget, figures shown: what the month has cost so far. */
    data class Spent(val amount: BigDecimal) : MonthWords

    /** No budget, figures hidden: the next charge is all there is to say. */
    data class NextOnly(val next: NextCharge) : MonthWords

    /** No budget, figures hidden, no charge ahead. */
    data object Quiet : MonthWords

    /** No account yet: the card is a door into the app. */
    data object Setup : MonthWords
}

/**
 * Which sentence [data] calls for (ADR 54). With a budget the sentence is about
 * what is left, the dashboard's safe-to-spend, because that is the question a
 * glance at the home screen asks; with figures hidden it is the share used,
 * which says the same without an amount.
 */
fun monthWords(data: MonthWidgetData, showAmounts: Boolean): MonthWords {
    if (!data.hasAccounts) return MonthWords.Setup
    val amounts = showAmounts && data.amountsAllowed
    val budget = data.budget
    return when {
        budget != null && amounts ->
            if (budget.safeToSpend.signum() > 0) {
                MonthWords.CanSpend(budget.safeToSpend, budget.perDay)
            } else {
                MonthWords.Over(budget.safeToSpend.negate())
            }

        budget != null ->
            if (budget.level == BudgetLevel.OVER) MonthWords.OverHidden else MonthWords.UsedPercent(budget.percent)
        amounts -> MonthWords.Spent(data.spent)
        else -> data.next?.let { MonthWords.NextOnly(it) } ?: MonthWords.Quiet
    }
}

private const val PERCENT = 100
