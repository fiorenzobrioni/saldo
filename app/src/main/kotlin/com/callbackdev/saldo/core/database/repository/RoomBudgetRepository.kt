package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.BudgetDao
import com.callbackdev.saldo.core.database.entity.BudgetEntity
import com.callbackdev.saldo.core.database.mapper.toDomain
import com.callbackdev.saldo.core.database.mapper.toEpochMonth
import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Currency
import javax.inject.Inject

class RoomBudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionRunner: TransactionRunner,
) : BudgetRepository {

    override fun observeBudgets(): Flow<List<Budget>> =
        budgetDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getBudgets(): List<Budget> =
        budgetDao.getAll().map { it.toDomain() }

    override suspend fun getBudget(id: Long): Budget? =
        budgetDao.getById(id)?.toDomain()

    override suspend fun setOverallBudget(amount: BigDecimal, currency: Currency) {
        upsert(categoryId = null, amount = amount, currency = currency)
    }

    override suspend fun deleteOverallBudget() {
        transactionRunner.inTransaction {
            budgetDao.getOverall()?.let { budgetDao.deleteById(it.id) }
        }
    }

    override suspend fun upsertCategoryBudget(categoryId: Long, amount: BigDecimal, currency: Currency) {
        upsert(categoryId = categoryId, amount = amount, currency = currency)
    }

    override suspend fun deleteBudget(id: Long) = budgetDao.deleteById(id)

    override suspend fun markNotified80(id: Long, month: YearMonth) =
        budgetDao.markNotified80(id, month.toEpochMonth())

    override suspend fun markNotified100(id: Long, month: YearMonth) =
        budgetDao.markNotified100(id, month.toEpochMonth())

    /**
     * Select-then-write inside a transaction: the unique index protects
     * category budgets, while the overall budget (NULL category, which the
     * index cannot constrain) stays unique because this is its only write path.
     * Replacing the amount preserves the row id and both notification
     * watermarks: re-editing a budget mid-month must not re-arm its alerts.
     */
    private suspend fun upsert(categoryId: Long?, amount: BigDecimal, currency: Currency) {
        transactionRunner.inTransaction {
            val existing =
                if (categoryId == null) budgetDao.getOverall() else budgetDao.getByCategoryId(categoryId)
            if (existing == null) {
                budgetDao.insert(
                    BudgetEntity(
                        categoryId = categoryId,
                        amountMinor = MoneyMapper.toMinorUnits(amount, currency),
                        currency = currency.currencyCode,
                    ),
                )
            } else {
                budgetDao.update(
                    existing.copy(
                        amountMinor = MoneyMapper.toMinorUnits(amount, currency),
                        currency = currency.currencyCode,
                    ),
                )
            }
        }
    }
}
