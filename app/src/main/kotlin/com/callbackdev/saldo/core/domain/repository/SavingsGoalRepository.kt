package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

/** Read/write access to savings goals (one per savings account). */
interface SavingsGoalRepository {

    /** Every goal, ordered for display. */
    fun observeGoals(): Flow<List<SavingsGoal>>

    /** One-shot read (e.g. for the editor). */
    suspend fun getGoals(): List<SavingsGoal>

    suspend fun getGoal(id: Long): SavingsGoal?

    /** The goal laid over [accountId], or null; enforces the one-goal-per-account rule. */
    suspend fun getGoalForAccount(accountId: Long): SavingsGoal?

    /** Inserts a new goal (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(goal: SavingsGoal): Long

    suspend fun deleteGoal(id: Long)
}
