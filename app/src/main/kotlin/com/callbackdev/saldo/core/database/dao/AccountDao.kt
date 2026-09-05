package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.relation.AccountBalanceAsOfRow
import com.callbackdev.saldo.core.database.relation.AccountWithBalanceRow
import com.callbackdev.saldo.core.database.relation.DailyNetRow
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // A data-access interface naturally has many queries.
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    /** Bulk insert with explicit ids, used by backup restore. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

    @Update
    suspend fun update(account: AccountEntity)

    /** Persists a new ordering (used by manual reorder). */
    @Update
    suspend fun updateAll(accounts: List<AccountEntity>)

    /**
     * Highest sortOrder among accounts of [type], or -1 when none exist, so a
     * freshly created account appends to the end of its own type group.
     */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM accounts WHERE type = :type")
    suspend fun maxSortOrder(type: String): Int

    /**
     * Advances the credit card settlement watermark on its own, without a
     * full-row update: settlement runs concurrently with the editor, and a
     * full upsert of the account read at the start of a run would clobber an
     * edit the user saved meanwhile (same reasoning as the recurring watermark).
     */
    @Query("UPDATE accounts SET lastSettledClosingEpochDay = :closingEpochDay WHERE id = :accountId")
    suspend fun updateSettlementWatermark(accountId: Long, closingEpochDay: Long)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    /** One-shot dump of every account, for backup export. */
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAll(): List<AccountEntity>

    /** Empties the table; only backup restore calls this, inside its transaction. */
    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    /**
     * Every account with its balance = `initialBalance + Σ movements`.
     * The first sub-select adds movements whose source is the account
     * (expenses, incomes, adjustments, transfers out); the second adds the
     * incoming leg of transfers whose destination is the account.
     */
    @Query(
        """
        SELECT a.*,
            a.initialBalanceMinor
            + COALESCE((
                SELECT SUM(t.amountMinor) FROM transactions t
                WHERE t.accountId = a.id AND t.isPending = 0
            ), 0)
            + COALESCE((
                SELECT SUM(t2.transferAmountMinor) FROM transactions t2
                WHERE t2.type = 'TRANSFER' AND t2.transferAccountId = a.id AND t2.isPending = 0
            ), 0) AS balanceMinor
        FROM accounts a
        ORDER BY a.sortOrder ASC, a.id ASC
        """,
    )
    fun observeAllWithBalance(): Flow<List<AccountWithBalanceRow>>

    /**
     * Every account's balance counting only movements whose local day (ADR 7)
     * is before [endEpochDayExclusive], i.e. the balance "as of today" when the
     * cutoff is today + 1. Same shape as [observeAllWithBalance] but with the
     * day filter, so future-dated confirmed movements are left out. The local
     * day is derived per movement from its own stored offset, exactly like the
     * dashboard's daily net series.
     */
    @Query(
        """
        SELECT a.id AS accountId,
            a.initialBalanceMinor
            + COALESCE((
                SELECT SUM(t.amountMinor) FROM transactions t
                WHERE t.accountId = a.id AND t.isPending = 0
                    AND (t.timestampEpochMilli / 1000 + t.zoneOffsetSeconds) / 86400 < :endEpochDayExclusive
            ), 0)
            + COALESCE((
                SELECT SUM(t2.transferAmountMinor) FROM transactions t2
                WHERE t2.type = 'TRANSFER' AND t2.transferAccountId = a.id AND t2.isPending = 0
                    AND (t2.timestampEpochMilli / 1000 + t2.zoneOffsetSeconds) / 86400 < :endEpochDayExclusive
            ), 0) AS balanceMinor
        FROM accounts a
        ORDER BY a.sortOrder ASC, a.id ASC
        """,
    )
    fun observeAllBalancesAsOf(endEpochDayExclusive: Long): Flow<List<AccountBalanceAsOfRow>>

    /** Current balance of a single account, in minor units; null if the account is missing. */
    @Query(
        """
        SELECT
            (SELECT initialBalanceMinor FROM accounts WHERE id = :accountId)
            + COALESCE((
                SELECT SUM(amountMinor) FROM transactions
                WHERE accountId = :accountId AND isPending = 0
            ), 0)
            + COALESCE((
                SELECT SUM(transferAmountMinor) FROM transactions
                WHERE type = 'TRANSFER' AND transferAccountId = :accountId AND isPending = 0
            ), 0)
        """,
    )
    fun observeBalance(accountId: Long): Flow<Long?>

    /**
     * Total balance across accounts that are included in the total, not archived,
     * and denominated in [currency].
     */
    @Query(
        """
        SELECT
            COALESCE((
                SELECT SUM(initialBalanceMinor) FROM accounts
                WHERE isIncludedInTotal = 1 AND isArchived = 0 AND currency = :currency
            ), 0)
            + COALESCE((
                SELECT SUM(t.amountMinor) FROM transactions t
                INNER JOIN accounts a ON t.accountId = a.id
                WHERE t.isPending = 0 AND a.isIncludedInTotal = 1
                    AND a.isArchived = 0 AND a.currency = :currency
            ), 0)
            + COALESCE((
                SELECT SUM(t.transferAmountMinor) FROM transactions t
                INNER JOIN accounts a ON t.transferAccountId = a.id
                WHERE t.type = 'TRANSFER' AND t.isPending = 0 AND a.isIncludedInTotal = 1
                    AND a.isArchived = 0 AND a.currency = :currency
            ), 0)
        """,
    )
    fun observeTotalBalance(currency: String): Flow<Long>

    /**
     * Sum of the initial balances of the accounts included in the total,
     * non-archived and denominated in [currency]. The starting point of the
     * balance-over-time statistic.
     */
    @Query(
        """
        SELECT COALESCE(SUM(initialBalanceMinor), 0) FROM accounts
        WHERE isIncludedInTotal = 1 AND isArchived = 0 AND currency = :currency
        """,
    )
    fun observeInitialBalanceTotal(currency: String): Flow<Long>

    /**
     * Net effect per local day (ADR 7) of one account's movements on its own
     * balance, limited to days in `[startEpochDay, endEpochDayExclusive)`: the
     * account's own movements plus the incoming legs of transfers into it, every
     * type counted, pending never. The per-account twin of
     * [TransactionDao.observeDailyNetChanges], with no currency filter (an
     * account has one currency by construction). Feeds the account detail
     * sparkline.
     */
    @Query(
        """
        SELECT epochDay, SUM(deltaMinor) AS netMinor FROM (
            SELECT
                (timestampEpochMilli / 1000 + zoneOffsetSeconds) / 86400 AS epochDay,
                amountMinor AS deltaMinor
            FROM transactions
            WHERE accountId = :accountId AND isPending = 0
            UNION ALL
            SELECT
                (timestampEpochMilli / 1000 + zoneOffsetSeconds) / 86400 AS epochDay,
                transferAmountMinor AS deltaMinor
            FROM transactions
            WHERE type = 'TRANSFER' AND transferAccountId = :accountId AND isPending = 0
        )
        WHERE epochDay >= :startEpochDay AND epochDay < :endEpochDayExclusive
        GROUP BY epochDay
        ORDER BY epochDay
        """,
    )
    fun observeDailyNetChanges(
        accountId: Long,
        startEpochDay: Long,
        endEpochDayExclusive: Long,
    ): Flow<List<DailyNetRow>>

    /**
     * Net effect of every movement of one account whose local day precedes
     * [startEpochDay], same rules as [observeDailyNetChanges]. NULL when
     * nothing matches. Seeds the account detail's daily balance series.
     */
    @Query(
        """
        SELECT SUM(deltaMinor) FROM (
            SELECT
                (timestampEpochMilli / 1000 + zoneOffsetSeconds) / 86400 AS epochDay,
                amountMinor AS deltaMinor
            FROM transactions
            WHERE accountId = :accountId AND isPending = 0
            UNION ALL
            SELECT
                (timestampEpochMilli / 1000 + zoneOffsetSeconds) / 86400 AS epochDay,
                transferAmountMinor AS deltaMinor
            FROM transactions
            WHERE type = 'TRANSFER' AND transferAccountId = :accountId AND isPending = 0
        )
        WHERE epochDay < :startEpochDay
        """,
    )
    fun observeNetChangeBefore(accountId: Long, startEpochDay: Long): Flow<Long?>
}
