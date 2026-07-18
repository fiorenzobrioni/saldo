package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.callbackdev.saldo.core.database.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // A data-access interface naturally has many queries.
interface SavingsGoalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: SavingsGoalEntity): Long

    /** Bulk insert with explicit ids, used by backup restore. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(goals: List<SavingsGoalEntity>): List<Long>

    /** Empties the table; only backup restore calls this, inside its transaction. */
    @Query("DELETE FROM savings_goals")
    suspend fun deleteAll()

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM savings_goals ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<SavingsGoalEntity>>

    /** One-shot dump of every goal, for backup export. */
    @Query("SELECT * FROM savings_goals ORDER BY id ASC")
    suspend fun getAll(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: Long): SavingsGoalEntity?

    /** The goal laid over [accountId], if any: enforces the one-goal-per-account rule. */
    @Query("SELECT * FROM savings_goals WHERE accountId = :accountId")
    suspend fun getByAccountId(accountId: Long): SavingsGoalEntity?
}
