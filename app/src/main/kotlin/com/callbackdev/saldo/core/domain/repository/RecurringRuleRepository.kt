package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.RecurringRule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Read/write access to recurring rules. The generation engine lives in Phase 6. */
interface RecurringRuleRepository {

    fun observeRules(): Flow<List<RecurringRule>>

    /** One-shot snapshot of every rule, for the generation engine. */
    suspend fun getRules(): List<RecurringRule>

    suspend fun getRule(id: Long): RecurringRule?

    /** Inserts a new rule (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(rule: RecurringRule): Long

    suspend fun delete(rule: RecurringRule)

    /** How many rules charge or credit the account, for the deletion guard. */
    suspend fun countForAccount(accountId: Long): Int

    /** Records that a pre-renewal reminder was posted for the occurrence on [date]. */
    suspend fun updateLastReminderDate(ruleId: Long, date: LocalDate)

    /**
     * Advances the generation watermark to [date] without touching the rest
     * of the rule, so a concurrent edit from the editor is never overwritten.
     */
    suspend fun updateLastGeneratedDate(ruleId: Long, date: LocalDate)
}
