package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.Currency

/** Read/write access to movements and their statistical aggregates. */
interface TransactionRepository {

    /** All movements, most recent first. */
    fun observeTransactions(): Flow<List<Transaction>>

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

    suspend fun getTransaction(id: Long): Transaction?

    /** Number of movements that touch [accountId] as source or transfer destination. */
    suspend fun countForAccount(accountId: Long): Int

    /** Number of movements labelled with [categoryId]. */
    suspend fun countForCategory(categoryId: Long): Int

    /** Inserts a new movement (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(transaction: Transaction): Long

    suspend fun delete(transaction: Transaction)
}
