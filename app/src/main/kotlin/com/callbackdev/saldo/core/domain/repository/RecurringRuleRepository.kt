package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.RecurringRule
import kotlinx.coroutines.flow.Flow

/** Read/write access to recurring rules. The generation engine lives in Phase 6. */
interface RecurringRuleRepository {

    fun observeRules(): Flow<List<RecurringRule>>

    suspend fun getRule(id: Long): RecurringRule?

    /** Inserts a new rule (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(rule: RecurringRule): Long

    suspend fun delete(rule: RecurringRule)
}
