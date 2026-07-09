package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Materializes the movements owed by recurring rules up to today. Runs on app
 * start (catch-up, PLANNING ADR 4) and is **idempotent**: every rule advances its
 * `lastGeneratedDate`, so re-running for the same day produces nothing.
 *
 * Scope for now: fixed-amount automatic rules. Confirm-mode and variable-amount
 * rules (which need a pending movement and a notification) are handled in a later
 * increment and are skipped here.
 */
class GenerateRecurringMovementsUseCase @Inject constructor(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
) {

    /** Generates due movements up to [today]; returns how many were created. */
    suspend operator fun invoke(today: LocalDate = LocalDate.now(clock)): Int =
        recurringRuleRepository.getRules().sumOf { rule -> generateForRule(rule, today) }

    private suspend fun generateForRule(rule: RecurringRule, today: LocalDate): Int {
        val amount = rule.amount
        if (rule.mode != RecurrenceMode.AUTOMATIC || rule.isVariableAmount || amount == null) return 0

        val from = rule.lastGeneratedDate?.plusDays(1) ?: rule.startDate
        val occurrences = if (from > today) {
            emptyList()
        } else {
            RecurrenceCalculator.occurrencesInClosedRange(rule, from, today)
        }
        occurrences.forEach { date ->
            transactionRepository.upsert(rule.toMovement(amount, date, clock.zone))
        }
        occurrences.lastOrNull()?.let { last ->
            recurringRuleRepository.upsert(rule.copy(lastGeneratedDate = last))
        }
        return occurrences.size
    }

    private fun RecurringRule.toMovement(
        amount: BigDecimal,
        date: LocalDate,
        zone: ZoneId,
    ): Transaction {
        // Noon avoids the DST/midnight edge, so the movement's local date equals
        // the occurrence date regardless of timezone shifts.
        val zoned = date.atTime(GENERATION_HOUR, 0).atZone(zone)
        val signed = if (type == TransactionType.EXPENSE) amount.negate() else amount
        return Transaction(
            type = type,
            amount = signed,
            currency = currency,
            accountId = accountId,
            timestamp = zoned.toInstant(),
            zoneOffset = zoned.offset,
            categoryId = categoryId,
            description = name,
            recurringRuleId = id,
        )
    }

    private companion object {
        const val GENERATION_HOUR = 12
    }
}
