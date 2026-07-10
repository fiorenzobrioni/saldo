package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.relation.AccountWithBalanceRow
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

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
}
