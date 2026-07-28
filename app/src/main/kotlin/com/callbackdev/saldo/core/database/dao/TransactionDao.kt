package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.database.relation.AccountTotalRow
import com.callbackdev.saldo.core.database.relation.CategoryTotalRow
import com.callbackdev.saldo.core.database.relation.DailyNetRow
import com.callbackdev.saldo.core.database.relation.DashboardTotalsRow
import com.callbackdev.saldo.core.database.relation.DailyActivityRow
import com.callbackdev.saldo.core.database.relation.MonthlyNetRow
import com.callbackdev.saldo.core.database.relation.MonthlyTotalRow
import com.callbackdev.saldo.core.database.relation.StatsPeriodTotalsRow
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

    /** Bulk insert with explicit ids, used by backup restore. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    /** One-shot dump of the whole ledger, pending movements included, for backup export. */
    @Query("SELECT * FROM transactions ORDER BY id ASC")
    suspend fun getAll(): List<TransactionEntity>

    /** Empties the table; only backup restore calls this, inside its transaction. */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    /**
     * Deletes a single chunk of ids in one statement. Callers go through
     * [deleteByIds], which chunks: SQLite caps the number of bound variables
     * per statement, so a very large filtered selection cannot be a single IN.
     */
    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIdsChunk(ids: List<Long>)

    /**
     * Deletes every movement whose id is in [ids]; tag cross-refs cascade
     * (`onDelete = CASCADE`). Chunked and wrapped in a single transaction so
     * the whole filtered selection is removed atomically.
     */
    @Transaction
    suspend fun deleteByIds(ids: List<Long>) {
        ids.chunked(ID_CHUNK_SIZE).forEach { deleteByIdsChunk(it) }
    }

    /**
     * Atomically deletes [ids] and inserts [inserts] (new rows, id == 0),
     * returning the ids assigned to the inserts. Backs the filtered delete that
     * preserves balances: the deletions and the carry-over adjustments must land
     * together or not at all.
     */
    @Transaction
    suspend fun deleteAndInsert(ids: List<Long>, inserts: List<TransactionEntity>): List<Long> {
        ids.chunked(ID_CHUNK_SIZE).forEach { deleteByIdsChunk(it) }
        return if (inserts.isEmpty()) emptyList() else insertAll(inserts)
    }

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
     * Uncategorized movements group under a NULL categoryId row, so the ring and
     * its center total cover the whole period's spend like the trend bars do.
     */
    @Query(
        """
        SELECT categoryId AS categoryId, SUM(amountMinor) AS totalMinor, COUNT(*) AS count
        FROM transactions
        WHERE type IN ('EXPENSE', 'INCOME')
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

    /**
     * The categories used most often for movements of [type] since
     * [sinceMillis], most used first, ties broken by the most recent use. Drives
     * the quick-add widget's grid, which is a shortcut list rather than a
     * statistic: it counts movements of any currency and any account, including
     * ones excluded from statistics, because "what I usually tap" has nothing to
     * do with what the charts add up. Pending movements are left out: they were
     * never confirmed by anyone.
     */
    @Query(
        """
        SELECT categoryId AS categoryId, 0 AS totalMinor, COUNT(*) AS count
        FROM transactions
        WHERE type = :type
            AND isPending = 0
            AND categoryId IS NOT NULL
            AND timestampEpochMilli >= :sinceMillis
        GROUP BY categoryId
        ORDER BY count DESC, MAX(timestampEpochMilli) DESC
        LIMIT :limit
        """,
    )
    suspend fun mostUsedCategories(
        type: String,
        sinceMillis: Long,
        limit: Int,
    ): List<CategoryTotalRow>

    /**
     * Per-month expense and income totals in `[startMillis, endMillis)` for the
     * statistics trend charts, restricted to [currency]. Months are the
     * movement's own local month (per-row offset, ADR 7). Refunds (INCOME with
     * isRefund = 1) count as negative spend, not income, mirroring how
     * [observeCategoryTotals] nets them against the category; transfers,
     * adjustments, excluded-from-stats and pending movements never count.
     */
    @Query(
        """
        SELECT
            strftime('%Y-%m', (timestampEpochMilli / 1000 + zoneOffsetSeconds), 'unixepoch')
                AS month,
            SUM(
                CASE WHEN type = 'EXPENSE' OR (type = 'INCOME' AND isRefund = 1)
                THEN amountMinor ELSE 0 END
            ) AS expenseMinor,
            SUM(
                CASE WHEN type = 'INCOME' AND isRefund = 0
                THEN amountMinor ELSE 0 END
            ) AS incomeMinor
        FROM transactions
        WHERE type IN ('EXPENSE', 'INCOME')
            AND isExcludedFromStats = 0
            AND isPending = 0
            AND currency = :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        GROUP BY month
        ORDER BY month
        """,
    )
    fun observeMonthlyTotals(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Flow<List<MonthlyTotalRow>>

    /**
     * How many movements the statistics of `[startMillis, endMillis)` leave out
     * for the sole reason of being in another currency: the exact filters of
     * [observeCategoryTotals] with the currency test inverted.
     *
     * Every statistic is scoped to one currency (conversion is a later
     * feature), so without this the charts would quietly under-report a period
     * that also holds foreign movements. Zero when there are none, which is the
     * normal single-currency case.
     */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE type IN ('EXPENSE', 'INCOME')
            AND isExcludedFromStats = 0
            AND isPending = 0
            AND currency <> :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        """,
    )
    fun observeOtherCurrencyCount(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Flow<Int>

    /**
     * Per-account signed spend totals in `[startMillis, endMillis)`, restricted
     * to [currency]. Same statistics rules as [observeMonthlyTotals]: refunds
     * net the spend, transfers/adjustments/excluded/pending never count.
     */
    @Query(
        """
        SELECT accountId AS accountId, SUM(amountMinor) AS totalMinor, COUNT(*) AS count
        FROM transactions
        WHERE (type = 'EXPENSE' OR (type = 'INCOME' AND isRefund = 1))
            AND isExcludedFromStats = 0
            AND isPending = 0
            AND currency = :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        GROUP BY accountId
        """,
    )
    fun observeAccountSpendTotals(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Flow<List<AccountTotalRow>>

    /**
     * Total signed spend in `[startMillis, endMillis)`, restricted to
     * [currency], with the same statistics rules as [observeMonthlyTotals]:
     * refunds net the spend, transfers/adjustments/excluded/pending never
     * count. Spend on accounts excluded from the budget (`isIncludedInBudget = 0`)
     * is also left out. Used for overall budget progress; NULL when nothing matches.
     */
    @Query(
        """
        SELECT SUM(t.amountMinor)
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE (t.type = 'EXPENSE' OR (t.type = 'INCOME' AND t.isRefund = 1))
            AND t.isExcludedFromStats = 0
            AND t.isPending = 0
            AND t.currency = :currency
            AND a.isIncludedInBudget = 1
            AND t.timestampEpochMilli >= :startMillis AND t.timestampEpochMilli < :endMillis
        """,
    )
    fun observeStatsSpendTotal(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Flow<Long?>

    /** One-shot variant of [observeStatsSpendTotal] for the budget threshold check. */
    @Query(
        """
        SELECT SUM(t.amountMinor)
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE (t.type = 'EXPENSE' OR (t.type = 'INCOME' AND t.isRefund = 1))
            AND t.isExcludedFromStats = 0
            AND t.isPending = 0
            AND t.currency = :currency
            AND a.isIncludedInBudget = 1
            AND t.timestampEpochMilli >= :startMillis AND t.timestampEpochMilli < :endMillis
        """,
    )
    suspend fun getStatsSpendTotal(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Long?

    /**
     * Per-category signed spend totals in `[startMillis, endMillis)`, restricted
     * to [currency]. Unlike [observeCategoryTotals] this keeps the spend filter
     * only (expenses plus refunds): pure incomes in a BOTH category must not
     * offset its budget. Spend on accounts excluded from the budget
     * (`isIncludedInBudget = 0`) is left out. Used for category budget progress.
     */
    @Query(
        """
        SELECT t.categoryId AS categoryId, SUM(t.amountMinor) AS totalMinor, COUNT(*) AS count
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE t.categoryId IS NOT NULL
            AND (t.type = 'EXPENSE' OR (t.type = 'INCOME' AND t.isRefund = 1))
            AND t.isExcludedFromStats = 0
            AND t.isPending = 0
            AND t.currency = :currency
            AND a.isIncludedInBudget = 1
            AND t.timestampEpochMilli >= :startMillis AND t.timestampEpochMilli < :endMillis
        GROUP BY t.categoryId
        """,
    )
    fun observeCategorySpendTotals(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Flow<List<CategoryTotalRow>>

    /** One-shot variant of [observeCategorySpendTotals] for the budget threshold check. */
    @Query(
        """
        SELECT t.categoryId AS categoryId, SUM(t.amountMinor) AS totalMinor, COUNT(*) AS count
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE t.categoryId IS NOT NULL
            AND (t.type = 'EXPENSE' OR (t.type = 'INCOME' AND t.isRefund = 1))
            AND t.isExcludedFromStats = 0
            AND t.isPending = 0
            AND t.currency = :currency
            AND a.isIncludedInBudget = 1
            AND t.timestampEpochMilli >= :startMillis AND t.timestampEpochMilli < :endMillis
        GROUP BY t.categoryId
        """,
    )
    suspend fun getCategorySpendTotals(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): List<CategoryTotalRow>

    /**
     * Net effect per local month on the balance of the accounts included in the
     * total (non-archived, denominated in [currency]), across the whole ledger.
     * Every type counts, exactly like [AccountDao.observeTotalBalance]: the
     * first leg covers expenses, incomes, adjustments and transfers out via the
     * signed amount; the second adds the incoming leg of transfers. Balance is
     * a cash figure, so excluded-from-stats movements still count; pending ones
     * never do. Feeds the balance-over-time statistic together with the initial
     * balances.
     */
    @Query(
        """
        SELECT month, SUM(deltaMinor) AS netMinor FROM (
            SELECT
                strftime('%Y-%m', (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds), 'unixepoch')
                    AS month,
                t.amountMinor AS deltaMinor
            FROM transactions t
            INNER JOIN accounts a ON a.id = t.accountId
            WHERE t.isPending = 0
                AND a.isIncludedInTotal = 1 AND a.isArchived = 0 AND a.currency = :currency
            UNION ALL
            SELECT
                strftime('%Y-%m', (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds), 'unixepoch')
                    AS month,
                t.transferAmountMinor AS deltaMinor
            FROM transactions t
            INNER JOIN accounts a ON a.id = t.transferAccountId
            WHERE t.type = 'TRANSFER' AND t.isPending = 0
                AND a.isIncludedInTotal = 1 AND a.isArchived = 0 AND a.currency = :currency
        )
        GROUP BY month
        ORDER BY month
        """,
    )
    fun observeMonthlyNetChanges(currency: String): Flow<List<MonthlyNetRow>>

    /**
     * Net effect per local day on the balance of the accounts included in the
     * total, limited to days in `[startEpochDay, endEpochDayExclusive)`. Same
     * rules as [observeMonthlyNetChanges]: every type counts, transfer legs
     * included, cash figure (excluded-from-stats movements still count),
     * pending never does. Days are the movement's own local day (ADR 7),
     * expressed as days since the epoch. Feeds the dashboard sparkline.
     */
    @Query(
        """
        SELECT epochDay, SUM(deltaMinor) AS netMinor FROM (
            SELECT
                (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds) / 86400 AS epochDay,
                t.amountMinor AS deltaMinor
            FROM transactions t
            INNER JOIN accounts a ON a.id = t.accountId
            WHERE t.isPending = 0
                AND a.isIncludedInTotal = 1 AND a.isArchived = 0 AND a.currency = :currency
            UNION ALL
            SELECT
                (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds) / 86400 AS epochDay,
                t.transferAmountMinor AS deltaMinor
            FROM transactions t
            INNER JOIN accounts a ON a.id = t.transferAccountId
            WHERE t.type = 'TRANSFER' AND t.isPending = 0
                AND a.isIncludedInTotal = 1 AND a.isArchived = 0 AND a.currency = :currency
        )
        WHERE epochDay >= :startEpochDay AND epochDay < :endEpochDayExclusive
        GROUP BY epochDay
        ORDER BY epochDay
        """,
    )
    fun observeDailyNetChanges(
        startEpochDay: Long,
        endEpochDayExclusive: Long,
        currency: String,
    ): Flow<List<DailyNetRow>>

    /**
     * Net effect of every movement strictly before [startEpochDay] (local day,
     * ADR 7) on the balance of the included accounts, same rules as
     * [observeDailyNetChanges]. NULL when nothing matches. Seeds the starting
     * level of the daily balance series.
     */
    @Query(
        """
        SELECT SUM(deltaMinor) FROM (
            SELECT
                (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds) / 86400 AS epochDay,
                t.amountMinor AS deltaMinor
            FROM transactions t
            INNER JOIN accounts a ON a.id = t.accountId
            WHERE t.isPending = 0
                AND a.isIncludedInTotal = 1 AND a.isArchived = 0 AND a.currency = :currency
            UNION ALL
            SELECT
                (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds) / 86400 AS epochDay,
                t.transferAmountMinor AS deltaMinor
            FROM transactions t
            INNER JOIN accounts a ON a.id = t.transferAccountId
            WHERE t.type = 'TRANSFER' AND t.isPending = 0
                AND a.isIncludedInTotal = 1 AND a.isArchived = 0 AND a.currency = :currency
        )
        WHERE epochDay < :startEpochDay
        """,
    )
    fun observeNetChangeBefore(startEpochDay: Long, currency: String): Flow<Long?>

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
     *
     * Movements on archived accounts are left out, so these cards agree with
     * the total balance sitting right above them (which drops archived
     * accounts too). Archiving therefore rewrites these figures retroactively,
     * exactly like it does for the balance and its history: the two must tell
     * the same story about the same set of accounts. The budget-exclusion flag
     * is a different axis and deliberately does not apply here - it only
     * governs budget progress and safe-to-spend.
     */
    @Suppress("LongParameterList") // One instant per window boundary; a DAO cannot take a POJO.
    @Query(
        """
        SELECT
            SUM(
                CASE WHEN t.type = 'EXPENSE'
                    AND t.timestampEpochMilli >= :todayStart AND t.timestampEpochMilli < :todayEnd
                THEN t.amountMinor ELSE 0 END
            ) AS todaySpendMinor,
            SUM(
                CASE WHEN t.type = 'INCOME'
                    AND t.timestampEpochMilli >= :todayStart AND t.timestampEpochMilli < :todayEnd
                THEN t.amountMinor ELSE 0 END
            ) AS todayIncomeMinor,
            SUM(
                CASE WHEN t.type = 'EXPENSE'
                    AND t.timestampEpochMilli >= :monthStart AND t.timestampEpochMilli < :monthEnd
                THEN t.amountMinor ELSE 0 END
            ) AS monthSpendMinor,
            SUM(
                CASE WHEN t.type = 'INCOME'
                    AND t.timestampEpochMilli >= :monthStart AND t.timestampEpochMilli < :monthEnd
                THEN t.amountMinor ELSE 0 END
            ) AS monthIncomeMinor,
            SUM(
                CASE WHEN t.type = 'EXPENSE'
                    AND t.timestampEpochMilli >= :monthStart AND t.timestampEpochMilli < :todayEnd
                THEN t.amountMinor ELSE 0 END
            ) AS monthToDateSpendMinor,
            SUM(
                CASE WHEN t.type = 'EXPENSE' AND t.recurringRuleId IS NULL
                    AND t.timestampEpochMilli >= :monthStart AND t.timestampEpochMilli < :todayEnd
                THEN t.amountMinor ELSE 0 END
            ) AS monthToDateNonRecurringSpendMinor,
            SUM(
                CASE WHEN t.type = 'EXPENSE'
                    AND t.timestampEpochMilli >= :previousStart
                    AND t.timestampEpochMilli < :previousToDateEnd
                THEN t.amountMinor ELSE 0 END
            ) AS previousToDateSpendMinor
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE t.isPending = 0 AND t.currency = :currency AND t.type IN ('EXPENSE', 'INCOME')
            AND a.isArchived = 0
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

    /**
     * One-shot statistics totals of `[startMillis, endMillis)` in a single
     * row: same filters and refund treatment as [observeMonthlyTotals], without
     * the per-month grouping. Feeds the monthly recap.
     */
    @Query(
        """
        SELECT
            SUM(
                CASE WHEN type = 'EXPENSE' OR (type = 'INCOME' AND isRefund = 1)
                THEN amountMinor ELSE NULL END
            ) AS expenseMinor,
            SUM(
                CASE WHEN type = 'INCOME' AND isRefund = 0
                THEN amountMinor ELSE NULL END
            ) AS incomeMinor
        FROM transactions
        WHERE type IN ('EXPENSE', 'INCOME')
            AND isExcludedFromStats = 0
            AND isPending = 0
            AND currency = :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        """,
    )
    suspend fun getStatsPeriodTotals(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): StatsPeriodTotalsRow

    /** One-shot twin of [observeCategoryTotals], for the monthly recap. */
    @Query(
        """
        SELECT categoryId AS categoryId, SUM(amountMinor) AS totalMinor, COUNT(*) AS count
        FROM transactions
        WHERE type IN ('EXPENSE', 'INCOME')
            AND isExcludedFromStats = 0
            AND isPending = 0
            AND currency = :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        GROUP BY categoryId
        """,
    )
    suspend fun getCategoryTotals(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): List<CategoryTotalRow>

    /**
     * The single biggest expense of the period under statistics rules
     * (excluded-from-stats and pending never count). Expense amounts are
     * negative, so the minimum signed amount is the biggest expense; ties
     * break on the earliest id for determinism.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE type = 'EXPENSE'
            AND isExcludedFromStats = 0
            AND isPending = 0
            AND currency = :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        ORDER BY amountMinor ASC, id ASC
        LIMIT 1
        """,
    )
    suspend fun getBiggestExpense(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): TransactionEntity?

    /**
     * Per-local-day movement count and signed spend total (same statistics
     * rules as [observeMonthlyTotals]) in `[startMillis, endMillis)`. Feeds
     * the recap's busiest-day figure; days without movements are absent.
     */
    @Query(
        """
        SELECT
            (timestampEpochMilli / 1000 + zoneOffsetSeconds) / 86400 AS epochDay,
            COUNT(*) AS count,
            SUM(
                CASE WHEN type = 'EXPENSE' OR (type = 'INCOME' AND isRefund = 1)
                THEN amountMinor ELSE 0 END
            ) AS spendMinor
        FROM transactions
        WHERE type IN ('EXPENSE', 'INCOME')
            AND isExcludedFromStats = 0
            AND isPending = 0
            AND currency = :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        GROUP BY epochDay
        ORDER BY epochDay
        """,
    )
    suspend fun getDailyActivity(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): List<DailyActivityRow>

    /**
     * Signed total of the period's rule-generated expenses under statistics
     * rules: what subscriptions and recurring charges actually cost in the
     * window. NULL when none. Feeds the monthly recap.
     */
    @Query(
        """
        SELECT SUM(amountMinor) FROM transactions
        WHERE recurringRuleId IS NOT NULL
            AND type = 'EXPENSE'
            AND isExcludedFromStats = 0
            AND isPending = 0
            AND currency = :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        """,
    )
    suspend fun getRecurringSpendTotal(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Long?

    companion object {
        /**
         * Ids per `IN (...)` chunk. Kept well under SQLite's default variable
         * limit (999) so a large filtered delete stays a handful of statements.
         */
        private const val ID_CHUNK_SIZE = 900
    }

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR transferAccountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    /** The distinct movement types filed under [categoryId], as stored enum names. */
    @Query("SELECT DISTINCT type FROM transactions WHERE categoryId = :categoryId")
    suspend fun distinctTypesForCategory(categoryId: Long): List<String>

    /**
     * Signed sum of a single account's own movements in `[startMilli, endMilli)`,
     * confirmed only, in minor units. Only movements whose source is the account
     * count (`accountId`), so incoming transfer legs (the statement settlement
     * itself) are excluded. Drives the credit card statement amount: the amount
     * owed for a closed cycle is the negation of this sum. Zero when none match.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE accountId = :accountId AND isPending = 0
            AND timestampEpochMilli >= :startMilli AND timestampEpochMilli < :endMilli
        """,
    )
    suspend fun sumOwnMovementsInWindow(accountId: Long, startMilli: Long, endMilli: Long): Long
}
