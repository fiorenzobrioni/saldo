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
import java.util.Currency
import javax.inject.Inject

/** A movement created by [GenerateRecurringMovementsUseCase], for the caller to notify about. */
data class GeneratedMovement(
    val transactionId: Long,
    val ruleId: Long,
    val ruleName: String,
    /** The charge magnitude; null for a variable-amount pending movement. */
    val amount: BigDecimal?,
    val currency: Currency,
    val date: LocalDate,
    /** True when the movement awaits confirmation (confirm mode / variable amount). */
    val isPending: Boolean,
)

/**
 * Materializes the movements owed by recurring rules up to today. Runs on app
 * start (catch-up, PLANNING ADR 4) and from the periodic worker, and is
 * **idempotent**: every rule advances its `lastGeneratedDate`, so re-running for
 * the same day produces nothing.
 *
 * Automatic fixed-amount rules produce confirmed movements. Confirm-mode and
 * variable-amount rules produce **pending** movements (excluded from balances
 * until the user confirms them). Returns everything created, so the caller can
 * post the appropriate notifications.
 */
class GenerateRecurringMovementsUseCase @Inject constructor(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(today: LocalDate = LocalDate.now(clock)): List<GeneratedMovement> =
        recurringRuleRepository.getRules().flatMap { rule -> generateForRule(rule, today) }

    private suspend fun generateForRule(rule: RecurringRule, today: LocalDate): List<GeneratedMovement> {
        val pending = rule.isVariableAmount || rule.mode == RecurrenceMode.CONFIRM
        // A non-pending rule needs a fixed amount to materialize automatically.
        if (!pending && rule.amount == null) return emptyList()

        val from = rule.lastGeneratedDate?.plusDays(1) ?: rule.startDate
        val occurrences = if (from > today) {
            emptyList()
        } else {
            RecurrenceCalculator.occurrencesInClosedRange(rule, from, today)
        }
        val generated = occurrences.map { date ->
            val id = transactionRepository.upsert(rule.toMovement(date, pending, clock.zone))
            GeneratedMovement(
                transactionId = id,
                ruleId = rule.id,
                ruleName = rule.name,
                amount = rule.amount.takeUnless { rule.isVariableAmount },
                currency = rule.currency,
                date = date,
                isPending = pending,
            )
        }
        occurrences.lastOrNull()?.let { last ->
            recurringRuleRepository.upsert(rule.copy(lastGeneratedDate = last))
        }
        return generated
    }

    private fun RecurringRule.toMovement(date: LocalDate, pending: Boolean, zone: ZoneId): Transaction {
        // Noon avoids the DST/midnight edge, so the movement's local date equals
        // the occurrence date regardless of timezone shifts.
        val zoned = date.atTime(GENERATION_HOUR, 0).atZone(zone)
        // Variable amount is unknown until confirmed; store zero in the meantime.
        val magnitude = if (isVariableAmount) BigDecimal.ZERO else (amount ?: BigDecimal.ZERO)
        val signed = if (type == TransactionType.EXPENSE) magnitude.negate() else magnitude
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
            isPending = pending,
        )
    }

    private companion object {
        const val GENERATION_HOUR = 12
    }
}
