package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.callbackdev.saldo.core.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // A data-access interface naturally has many queries.
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity): Long

    /** Bulk insert with explicit ids, used by backup restore. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(budgets: List<BudgetEntity>): List<Long>

    /** Empties the table; only backup restore calls this, inside its transaction. */
    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM budgets ORDER BY categoryId IS NOT NULL, id ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    /** One-shot read for the threshold check (which runs off the UI). */
    @Query("SELECT * FROM budgets ORDER BY categoryId IS NOT NULL, id ASC")
    suspend fun getAll(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId")
    suspend fun getByCategoryId(categoryId: Long): BudgetEntity?

    /** The overall monthly budget, when set. */
    @Query("SELECT * FROM budgets WHERE categoryId IS NULL")
    suspend fun getOverall(): BudgetEntity?

    /**
     * Advances the 80% notification watermark. A targeted UPDATE (not a
     * full-row update) so it cannot clobber a concurrent budget edit.
     */
    @Query("UPDATE budgets SET lastNotified80EpochMonth = :epochMonth WHERE id = :id")
    suspend fun markNotified80(id: Long, epochMonth: Long)

    /**
     * Advances both watermarks: crossing 100% implies 80%, so a month that
     * jumps straight over the limit produces a single notification.
     */
    @Query(
        "UPDATE budgets SET lastNotified80EpochMonth = :epochMonth, " +
            "lastNotified100EpochMonth = :epochMonth WHERE id = :id",
    )
    suspend fun markNotified100(id: Long, epochMonth: Long)
}
