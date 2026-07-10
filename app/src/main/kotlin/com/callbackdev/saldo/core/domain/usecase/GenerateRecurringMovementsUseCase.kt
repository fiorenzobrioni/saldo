package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRunner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

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
 * the same day produces nothing. Three layers keep concurrent or interrupted
 * runs from duplicating movements: a process-wide [Mutex] serializes runs, each
 * rule's inserts and watermark advance commit in a single database transaction,
 * and the unique (rule, occurrence) index rejects any duplicate that still
 * slips through (the insert is skipped silently and not re-notified).
 *
 * Automatic fixed-amount rules produce confirmed movements. Confirm-mode and
 * variable-amount rules produce **pending** movements (excluded from balances
 * until the user confirms them). Returns everything created, so the caller can
 * post the appropriate notifications.
 */
@Singleton
class GenerateRecurringMovementsUseCase @Inject constructor(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {

    private val mutex = Mutex()

    suspend operator fun invoke(today: LocalDate = LocalDate.now(clock)): List<GeneratedMovement> =
        mutex.withLock {
            recurringRuleRepository.getRules().flatMap { rule -> generateForRule(rule, today) }
        }

    private suspend fun generateForRule(rule: RecurringRule, today: LocalDate): List<GeneratedMovement> {
        val pending = rule.isVariableAmount || rule.mode == RecurrenceMode.CONFIRM
        // A non-pending rule needs a fixed amount to materialize automatically.
        val eligible = pending || rule.amount != null
        val from = rule.lastGeneratedDate?.plusDays(1) ?: rule.startDate
        val occurrences = if (!eligible || from > today) {
            emptyList()
        } else {
            RecurrenceCalculator.occurrencesInClosedRange(rule, from, today)
        }
        if (occurrences.isEmpty()) return emptyList()
        return transactionRunner.inTransaction {
            val generated = occurrences.mapNotNull { date ->
                val id = transactionRepository.insertIfAbsent(rule.toMovement(date, pending, clock.zone))
                if (id == SKIPPED_DUPLICATE) return@mapNotNull null
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
            recurringRuleRepository.upsert(rule.copy(lastGeneratedDate = occurrences.last()))
            generated
        }
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
            recurringOccurrenceDate = date,
        )
    }

    private companion object {
        const val GENERATION_HOUR = 12

        /** [TransactionRepository.insertIfAbsent] result for an already-generated occurrence. */
        const val SKIPPED_DUPLICATE = -1L
    }
}
