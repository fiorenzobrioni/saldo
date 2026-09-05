package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.resumed
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Pauses or resumes a recurring rule (Fase 39, F3).
 *
 * Pausing only flips the flag: the schedule, the amount and the generation
 * watermark stay as they are, so nothing is lost and the rule keeps its place
 * on file. Resuming goes through [RecurringRule.resumed], which moves the
 * watermark to yesterday when it is behind: the next generation run starts
 * from today, and the occurrences that fell inside the pause are never
 * back-filled. That is the whole point of a pause as opposed to an end date
 * plus a new rule: the history is not rewritten, and nothing is charged for
 * the period the user asked to skip.
 *
 * Shared by the hub's quick action and the editor's switch so both paths
 * agree on what "resume" means.
 */
class SetRecurringRulePausedUseCase @Inject constructor(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(rule: RecurringRule, paused: Boolean, today: LocalDate = LocalDate.now(clock)) {
        val updated = when {
            paused -> rule.copy(isPaused = true)
            rule.isPaused -> rule.resumed(today)
            else -> return
        }
        if (updated == rule) return
        recurringRuleRepository.upsert(updated)
    }
}
