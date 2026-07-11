package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.AccountTotal
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.MonthlyNet
import com.callbackdev.saldo.core.domain.model.MonthlyTotal
import com.callbackdev.saldo.core.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.Currency

/** Read/write access to movements and their statistical aggregates. */
@Suppress("TooManyFunctions") // A data-access interface naturally has many queries.
interface TransactionRepository {

    /** Confirmed movements, most recent first (pending recurring movements excluded). */
    fun observeTransactions(): Flow<List<Transaction>>

    /** Recurring movements awaiting confirmation, oldest first. */
    fun observePendingTransactions(): Flow<List<Transaction>>

    /** Movements whose instant is in `[start, end)`, most recent first. */
    fun observeTransactionsBetween(start: Instant, end: Instant): Flow<List<Transaction>>

    /** Movements that touch [accountId] as source or transfer destination. */
    fun observeTransactionsForAccount(accountId: Long): Flow<List<Transaction>>

    /**
     * Per-category totals for expenses and incomes in `[start, end)`, restricted
     * to [currency]. Transfers and adjustments are excluded at query level
     * (PLANNING ADR 8), as are movements flagged out of statistics.
     */
    fun observeCategoryTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<CategoryTotal>>

    /**
     * Per-month expense/income totals in `[start, end)` (the movement's own
     * local month, ADR 7), restricted to [currency]. Statistics rules: refunds
     * net the spend instead of counting as income; transfers, adjustments,
     * excluded-from-stats and pending movements never count.
     */
    fun observeMonthlyTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<MonthlyTotal>>

    /**
     * Per-account signed spend totals in `[start, end)`, restricted to
     * [currency], with the same statistics rules as [observeMonthlyTotals].
     */
    fun observeAccountSpendTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<AccountTotal>>

    /**
     * Net effect per local month on the balance of the accounts included in
     * the total (every type, transfer legs included; cash figure, so
     * excluded-from-stats movements still count), across the whole ledger.
     */
    fun observeMonthlyNetChanges(currency: Currency): Flow<List<MonthlyNet>>

    /** The latest confirmed movements, capped in SQL. */
    fun observeRecentTransactions(limit: Int): Flow<List<Transaction>>

    /**
     * The dashboard's aggregate figures for [windows], restricted to [currency],
     * computed by the database in a single query. Cash figures: movements
     * flagged out of statistics still count (unlike [observeCategoryTotals]).
     */
    fun observeDashboardTotals(
        windows: DashboardWindows,
        currency: Currency,
    ): Flow<DashboardTotals>

    suspend fun getTransaction(id: Long): Transaction?

    /** Number of movements that touch [accountId] as source or transfer destination. */
    suspend fun countForAccount(accountId: Long): Int

    /** Number of movements labelled with [categoryId]. */
    suspend fun countForCategory(categoryId: Long): Int

    /** Inserts a new movement (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(transaction: Transaction): Long

    /**
     * Inserts a new movement unless it collides with an already-generated
     * recurring occurrence (same rule and occurrence date); returns the new id,
     * or -1 when the movement already existed and nothing was written.
     */
    suspend fun insertIfAbsent(transaction: Transaction): Long

    suspend fun delete(transaction: Transaction)
}
