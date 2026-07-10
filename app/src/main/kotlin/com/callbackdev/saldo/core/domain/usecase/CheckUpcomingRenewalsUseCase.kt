package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/** An upcoming charge/credit within the reminder window, for the caller to notify about. */
data class UpcomingRenewal(
    val ruleId: Long,
    val ruleName: String,
    val type: TransactionType,
    /** The charge magnitude; null for a variable-amount rule. */
    val amount: BigDecimal?,
    val currency: Currency,
    val dueDate: LocalDate,
    /** Whole days from today to [dueDate] (0 = due today). */
    val daysUntil: Int,
)

/**
 * Finds the recurring charges and credits due within the user's pre-renewal
 * reminder window ("Netflix renews in 3 days"). Returns an empty list when the
 * reminder setting is off.
 *
 * Each occurrence is reported **once**: the rule's `lastReminderDate` watermark
 * records the occurrence already reminded, so a daily run inside the window does
 * not repeat itself, and a run that was skipped (device off) still reminds at
 * the first chance, even closer to the due date than the configured lead.
 *
 * Run this **after** [GenerateRecurringMovementsUseCase] so an occurrence due
 * today is generated (and floored past), not announced as upcoming. The
 * watermark advances even if the notification is later suppressed (permission
 * revoked): a user who disabled notifications loses nothing but the reminder.
 */
@Singleton
class CheckUpcomingRenewalsUseCase @Inject constructor(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(today: LocalDate = LocalDate.now(clock)): List<UpcomingRenewal> {
        val prefs = userPreferences.renewalReminderPreferences.first()
        if (!prefs.enabled) return emptyList()
        return recurringRuleRepository.getRules().mapNotNull { rule ->
            val renewal = rule.upcomingRenewal(today, prefs.leadDays) ?: return@mapNotNull null
            recurringRuleRepository.updateLastReminderDate(rule.id, renewal.dueDate)
            renewal
        }
    }

    private fun RecurringRule.upcomingRenewal(today: LocalDate, leadDays: Int): UpcomingRenewal? {
        // Same floor as the hub's "next charge": skip occurrences already generated,
        // so a charge that fired today is not announced as upcoming.
        val afterGenerated = lastGeneratedDate?.plusDays(1)
        val floor = if (afterGenerated != null && afterGenerated > today) afterGenerated else today
        val next = RecurrenceCalculator.nextOccurrence(this, floor) ?: return null
        val daysUntil = ChronoUnit.DAYS.between(today, next).toInt()
        val alreadyReminded = lastReminderDate != null && lastReminderDate >= next
        return if (daysUntil > leadDays || alreadyReminded) {
            null
        } else {
            UpcomingRenewal(
                ruleId = id,
                ruleName = name,
                type = type,
                amount = amount.takeUnless { isVariableAmount },
                currency = currency,
                dueDate = next,
                daysUntil = daysUntil,
            )
        }
    }
}
