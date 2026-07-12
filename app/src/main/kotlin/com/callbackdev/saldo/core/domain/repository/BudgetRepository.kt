package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.Budget
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Currency

/** Read/write access to monthly budgets (one overall, plus per-category caps). */
interface BudgetRepository {

    /** Every budget, overall first, then category budgets in creation order. */
    fun observeBudgets(): Flow<List<Budget>>

    /** One-shot read for the threshold check (which runs off the UI). */
    suspend fun getBudgets(): List<Budget>

    suspend fun getBudget(id: Long): Budget?

    /**
     * Creates or replaces the overall monthly budget. Runs transactionally:
     * SQLite's unique index cannot enforce a single NULL-category row, so this
     * upsert is the only write path for the overall budget.
     */
    suspend fun setOverallBudget(amount: BigDecimal, currency: Currency)

    suspend fun deleteOverallBudget()

    /** Creates or replaces the budget capping [categoryId]. */
    suspend fun upsertCategoryBudget(categoryId: Long, amount: BigDecimal, currency: Currency)

    suspend fun deleteBudget(id: Long)

    /** Advances the 80% notification watermark to [month]. */
    suspend fun markNotified80(id: Long, month: YearMonth)

    /** Advances both watermarks to [month] (crossing 100% implies 80%). */
    suspend fun markNotified100(id: Long, month: YearMonth)
}
