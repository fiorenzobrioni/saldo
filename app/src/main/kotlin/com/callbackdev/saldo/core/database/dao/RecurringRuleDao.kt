package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.callbackdev.saldo.core.database.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // A data-access interface naturally has many queries.
interface RecurringRuleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: RecurringRuleEntity): Long

    /** Bulk insert with explicit ids, used by backup restore. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rules: List<RecurringRuleEntity>): List<Long>

    /** Empties the table; only backup restore calls this, inside its transaction. */
    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAll()

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)

    @Query("SELECT * FROM recurring_rules ORDER BY id ASC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    /** One-shot read for the generation engine (which runs off the UI). */
    @Query("SELECT * FROM recurring_rules ORDER BY id ASC")
    suspend fun getAll(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    /** Rules charging the account, for the account deletion guard. */
    @Query("SELECT COUNT(*) FROM recurring_rules WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    /**
     * Advances the pre-renewal reminder watermark. A targeted UPDATE (not a
     * full-row update) so it cannot clobber a concurrent generation write.
     */
    @Query("UPDATE recurring_rules SET lastReminderEpochDay = :epochDay WHERE id = :id")
    suspend fun updateLastReminder(id: Long, epochDay: Long)

    /**
     * Advances the generation watermark. A targeted UPDATE (not a full-row
     * update) so it cannot clobber a rule edit saved while a generation run
     * is in flight.
     */
    @Query("UPDATE recurring_rules SET lastGeneratedEpochDay = :epochDay WHERE id = :id")
    suspend fun updateLastGenerated(id: Long, epochDay: Long)
}
