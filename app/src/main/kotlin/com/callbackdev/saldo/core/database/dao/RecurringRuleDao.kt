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
interface RecurringRuleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: RecurringRuleEntity): Long

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

    /**
     * Advances the pre-renewal reminder watermark. A targeted UPDATE (not a
     * full-row update) so it cannot clobber a concurrent generation write.
     */
    @Query("UPDATE recurring_rules SET lastReminderEpochDay = :epochDay WHERE id = :id")
    suspend fun updateLastReminder(id: Long, epochDay: Long)
}
