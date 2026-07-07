package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.database.relation.CategoryTotalRow
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY timestampEpochMilli DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        ORDER BY timestampEpochMilli DESC, id DESC
        """,
    )
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId OR transferAccountId = :accountId
        ORDER BY timestampEpochMilli DESC, id DESC
        """,
    )
    fun observeForAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /**
     * Per-category signed totals for expenses and incomes in `[startMillis, endMillis)`,
     * restricted to [currency]. Transfers and adjustments are excluded by the type
     * filter (and carry no category); movements flagged out of statistics are skipped.
     */
    @Query(
        """
        SELECT categoryId AS categoryId, SUM(amountMinor) AS totalMinor, COUNT(*) AS count
        FROM transactions
        WHERE categoryId IS NOT NULL
            AND type IN ('EXPENSE', 'INCOME')
            AND isExcludedFromStats = 0
            AND currency = :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        GROUP BY categoryId
        """,
    )
    fun observeCategoryTotals(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Flow<List<CategoryTotalRow>>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR transferAccountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int
}
