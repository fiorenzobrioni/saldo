package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.database.relation.CategoryTotalRow
import com.callbackdev.saldo.core.database.relation.DashboardTotalsRow
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // A data-access interface naturally has many queries.
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    /**
     * Insert that silently skips rows violating the unique
     * (recurringRuleId, recurringOccurrenceEpochDay) index, returning -1.
     * Used by recurring generation as the backstop against duplicates.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflicts(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    /** The confirmed ledger; pending recurring movements are excluded. */
    @Query("SELECT * FROM transactions WHERE isPending = 0 ORDER BY timestampEpochMilli DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** Movements awaiting confirmation (confirm mode / variable amount), oldest first. */
    @Query("SELECT * FROM transactions WHERE isPending = 1 ORDER BY timestampEpochMilli ASC, id ASC")
    fun observePending(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE isPending = 0
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        ORDER BY timestampEpochMilli DESC, id DESC
        """,
    )
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE isPending = 0 AND (accountId = :accountId OR transferAccountId = :accountId)
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
            AND isPending = 0
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

    /** The latest confirmed movements, capped in SQL so the dashboard never loads the full ledger. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE isPending = 0
        ORDER BY timestampEpochMilli DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    /**
     * Every dashboard figure in one aggregate row: today's and this month's
     * expense/income sums plus the month-to-date and previous-month-to-date
     * spend, restricted to [currency]. Transfers and adjustments are excluded by
     * the type filter; pending movements never count. Unlike statistics, these
     * are cash figures: movements flagged out of statistics still count.
     */
    @Suppress("LongParameterList") // One instant per window boundary; a DAO cannot take a POJO.
    @Query(
        """
        SELECT
            SUM(
                CASE WHEN type = 'EXPENSE'
                    AND timestampEpochMilli >= :todayStart AND timestampEpochMilli < :todayEnd
                THEN amountMinor ELSE 0 END
            ) AS todaySpendMinor,
            SUM(
                CASE WHEN type = 'INCOME'
                    AND timestampEpochMilli >= :todayStart AND timestampEpochMilli < :todayEnd
                THEN amountMinor ELSE 0 END
            ) AS todayIncomeMinor,
            SUM(
                CASE WHEN type = 'EXPENSE'
                    AND timestampEpochMilli >= :monthStart AND timestampEpochMilli < :monthEnd
                THEN amountMinor ELSE 0 END
            ) AS monthSpendMinor,
            SUM(
                CASE WHEN type = 'INCOME'
                    AND timestampEpochMilli >= :monthStart AND timestampEpochMilli < :monthEnd
                THEN amountMinor ELSE 0 END
            ) AS monthIncomeMinor,
            SUM(
                CASE WHEN type = 'EXPENSE'
                    AND timestampEpochMilli >= :monthStart AND timestampEpochMilli < :todayEnd
                THEN amountMinor ELSE 0 END
            ) AS monthToDateSpendMinor,
            SUM(
                CASE WHEN type = 'EXPENSE'
                    AND timestampEpochMilli >= :previousStart AND timestampEpochMilli < :previousToDateEnd
                THEN amountMinor ELSE 0 END
            ) AS previousToDateSpendMinor
        FROM transactions
        WHERE isPending = 0 AND currency = :currency AND type IN ('EXPENSE', 'INCOME')
        """,
    )
    fun observeDashboardTotals(
        todayStart: Long,
        todayEnd: Long,
        monthStart: Long,
        monthEnd: Long,
        previousStart: Long,
        previousToDateEnd: Long,
        currency: String,
    ): Flow<DashboardTotalsRow>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR transferAccountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int
}
