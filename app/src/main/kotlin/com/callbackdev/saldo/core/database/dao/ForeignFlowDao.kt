package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.callbackdev.saldo.core.database.relation.CategorySpendCurrencyDayRow
import com.callbackdev.saldo.core.database.relation.CurrencyCountRow
import com.callbackdev.saldo.core.database.relation.ForeignAccountDayRow
import com.callbackdev.saldo.core.database.relation.ForeignCategoryDayRow
import com.callbackdev.saldo.core.database.relation.ForeignDashboardFlowsRow
import com.callbackdev.saldo.core.database.relation.ForeignMonthlyDayRow
import com.callbackdev.saldo.core.database.relation.SpendCurrencyDayRow
import kotlinx.coroutines.flow.Flow

/**
 * Foreign residue queries (ADR 40): the exact filters of their
 * single-currency twins in [TransactionDao] with the currency test inverted,
 * grouped by (currency, local day of the movement, ADR 7). The day
 * granularity is what lets the domain convert each bucket at the rate of the
 * movement's own date. Their own DAO rather than more rows in
 * [TransactionDao]: the family is cohesive (it exists only for conversion)
 * and the twins stay untouched, so the single-currency case never even runs
 * these.
 */
@Dao
interface ForeignFlowDao {

    /**
     * Foreign twin of [TransactionDao.observeDashboardTotals]: the same seven window sums,
     * broken down per (currency, local day). Bounded by the widest window
     * (`previousStart` to `monthEnd`); a bucket outside a given sub-window
     * contributes zero to that column, exactly like the CASE in the twin.
     */
    @Suppress("LongParameterList") // One instant per window boundary; a DAO cannot take a POJO.
    @Query(
        """
        SELECT t.currency AS currency,
            (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds) / 86400 AS epochDay,
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
        WHERE t.isPending = 0 AND t.currency <> :currency AND t.type IN ('EXPENSE', 'INCOME')
            AND a.isArchived = 0
            AND t.timestampEpochMilli >= :previousStart AND t.timestampEpochMilli < :monthEnd
        GROUP BY t.currency, epochDay
        """,
    )
    fun observeForeignDashboardFlows(
        todayStart: Long,
        todayEnd: Long,
        monthStart: Long,
        monthEnd: Long,
        previousStart: Long,
        previousToDateEnd: Long,
        currency: String,
    ): Flow<List<ForeignDashboardFlowsRow>>

    /** Foreign twin of [TransactionDao.observeCategoryTotals], per (category, currency, local day). */
    @Query(
        """
        SELECT categoryId AS categoryId, currency AS currency,
            (timestampEpochMilli / 1000 + zoneOffsetSeconds) / 86400 AS epochDay,
            SUM(amountMinor) AS totalMinor, COUNT(*) AS count
        FROM transactions
        WHERE type IN ('EXPENSE', 'INCOME')
            AND isExcludedFromStats = 0
            AND isPending = 0
            AND currency <> :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        GROUP BY categoryId, currency, epochDay
        """,
    )
    fun observeForeignCategoryTotals(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Flow<List<ForeignCategoryDayRow>>

    /** Foreign twin of [TransactionDao.observeAccountSpendTotals], per (account, currency, local day). */
    @Query(
        """
        SELECT accountId AS accountId, currency AS currency,
            (timestampEpochMilli / 1000 + zoneOffsetSeconds) / 86400 AS epochDay,
            SUM(amountMinor) AS totalMinor, COUNT(*) AS count
        FROM transactions
        WHERE (type = 'EXPENSE' OR (type = 'INCOME' AND isRefund = 1))
            AND isExcludedFromStats = 0
            AND isPending = 0
            AND currency <> :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        GROUP BY accountId, currency, epochDay
        """,
    )
    fun observeForeignAccountSpendTotals(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Flow<List<ForeignAccountDayRow>>

    /**
     * Foreign twin of [TransactionDao.observeMonthlyTotals], per (currency, local day); the
     * domain re-buckets the days into months after converting each one.
     */
    @Query(
        """
        SELECT currency AS currency,
            (timestampEpochMilli / 1000 + zoneOffsetSeconds) / 86400 AS epochDay,
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
            AND currency <> :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        GROUP BY currency, epochDay
        """,
    )
    fun observeForeignMonthlyTotals(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Flow<List<ForeignMonthlyDayRow>>

    /**
     * [TransactionDao.observeOtherCurrencyCount] broken down per currency, so the screen can
     * tell converted currencies apart from the ones no rate covers.
     */
    @Query(
        """
        SELECT currency AS currency, COUNT(*) AS count FROM transactions
        WHERE type IN ('EXPENSE', 'INCOME')
            AND isExcludedFromStats = 0
            AND isPending = 0
            AND currency <> :currency
            AND timestampEpochMilli >= :startMillis AND timestampEpochMilli < :endMillis
        GROUP BY currency
        """,
    )
    fun observeOtherCurrencyCounts(
        startMillis: Long,
        endMillis: Long,
        currency: String,
    ): Flow<List<CurrencyCountRow>>

    /**
     * Budget-relevant spend per (currency, local day), every currency
     * included: one subscription serves every budget, whatever its currency,
     * with the domain converting each bucket into the budget's own currency.
     * Same filters as [TransactionDao.observeStatsSpendTotal].
     */
    @Query(
        """
        SELECT t.currency AS currency,
            (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds) / 86400 AS epochDay,
            SUM(t.amountMinor) AS totalMinor
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE (t.type = 'EXPENSE' OR (t.type = 'INCOME' AND t.isRefund = 1))
            AND t.isExcludedFromStats = 0
            AND t.isPending = 0
            AND a.isIncludedInBudget = 1
            AND t.timestampEpochMilli >= :startMillis AND t.timestampEpochMilli < :endMillis
        GROUP BY t.currency, epochDay
        """,
    )
    fun observeSpendByCurrencyDay(
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<SpendCurrencyDayRow>>

    /** One-shot variant of [observeSpendByCurrencyDay] for the budget threshold check. */
    @Query(
        """
        SELECT t.currency AS currency,
            (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds) / 86400 AS epochDay,
            SUM(t.amountMinor) AS totalMinor
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE (t.type = 'EXPENSE' OR (t.type = 'INCOME' AND t.isRefund = 1))
            AND t.isExcludedFromStats = 0
            AND t.isPending = 0
            AND a.isIncludedInBudget = 1
            AND t.timestampEpochMilli >= :startMillis AND t.timestampEpochMilli < :endMillis
        GROUP BY t.currency, epochDay
        """,
    )
    suspend fun getSpendByCurrencyDay(
        startMillis: Long,
        endMillis: Long,
    ): List<SpendCurrencyDayRow>

    /**
     * Per-category twin of [observeSpendByCurrencyDay]; same filters as
     * [TransactionDao.observeCategorySpendTotals].
     */
    @Query(
        """
        SELECT t.categoryId AS categoryId, t.currency AS currency,
            (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds) / 86400 AS epochDay,
            SUM(t.amountMinor) AS totalMinor
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE t.categoryId IS NOT NULL
            AND (t.type = 'EXPENSE' OR (t.type = 'INCOME' AND t.isRefund = 1))
            AND t.isExcludedFromStats = 0
            AND t.isPending = 0
            AND a.isIncludedInBudget = 1
            AND t.timestampEpochMilli >= :startMillis AND t.timestampEpochMilli < :endMillis
        GROUP BY t.categoryId, t.currency, epochDay
        """,
    )
    fun observeCategorySpendByCurrencyDay(
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<CategorySpendCurrencyDayRow>>

    /** One-shot variant of [observeCategorySpendByCurrencyDay] for the budget threshold check. */
    @Query(
        """
        SELECT t.categoryId AS categoryId, t.currency AS currency,
            (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds) / 86400 AS epochDay,
            SUM(t.amountMinor) AS totalMinor
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE t.categoryId IS NOT NULL
            AND (t.type = 'EXPENSE' OR (t.type = 'INCOME' AND t.isRefund = 1))
            AND t.isExcludedFromStats = 0
            AND t.isPending = 0
            AND a.isIncludedInBudget = 1
            AND t.timestampEpochMilli >= :startMillis AND t.timestampEpochMilli < :endMillis
        GROUP BY t.categoryId, t.currency, epochDay
        """,
    )
    suspend fun getCategorySpendByCurrencyDay(
        startMillis: Long,
        endMillis: Long,
    ): List<CategorySpendCurrencyDayRow>
}
