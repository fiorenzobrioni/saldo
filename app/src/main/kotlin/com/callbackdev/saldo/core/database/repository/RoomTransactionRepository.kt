package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.mapper.toDomain
import com.callbackdev.saldo.core.database.mapper.toEntity
import com.callbackdev.saldo.core.domain.model.AccountTotal
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.CounterpartyTotal
import com.callbackdev.saldo.core.domain.model.DailyActivity
import com.callbackdev.saldo.core.domain.model.DailyNet
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.MonthlyNet
import com.callbackdev.saldo.core.domain.model.MonthlyTotal
import com.callbackdev.saldo.core.domain.model.StatsPeriodTotals
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

@Suppress("TooManyFunctions") // A data-access implementation naturally has many queries.
class RoomTransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) : TransactionRepository {

    override fun observeTransactions(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observePendingTransactions(): Flow<List<Transaction>> =
        transactionDao.observePending().map { rows -> rows.map { it.toDomain() } }

    override fun observeTransactionsBetween(
        start: Instant,
        end: Instant,
    ): Flow<List<Transaction>> =
        transactionDao.observeBetween(start.toEpochMilli(), end.toEpochMilli())
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeTransactionsForAccount(accountId: Long): Flow<List<Transaction>> =
        transactionDao.observeForAccount(accountId).map { rows -> rows.map { it.toDomain() } }

    override fun observeCategoryTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<CategoryTotal>> =
        transactionDao.observeCategoryTotals(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { rows -> rows.map { it.toDomain(currency) } }

    override fun observeMonthlyTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<MonthlyTotal>> =
        transactionDao.observeMonthlyTotals(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { rows -> rows.map { it.toDomain(currency) } }

    override fun observeAccountSpendTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<AccountTotal>> =
        transactionDao.observeAccountSpendTotals(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { rows -> rows.map { it.toDomain(currency) } }

    override fun observeStatsSpendTotal(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<BigDecimal> =
        transactionDao.observeStatsSpendTotal(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { MoneyMapper.toAmount(it ?: 0L, currency) }

    override suspend fun getStatsSpendTotal(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): BigDecimal = MoneyMapper.toAmount(
        transactionDao.getStatsSpendTotal(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ) ?: 0L,
        currency,
    )

    override fun observeCategorySpendTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<CategoryTotal>> =
        transactionDao.observeCategorySpendTotals(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { rows -> rows.map { it.toDomain(currency) } }

    override suspend fun getCategorySpendTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): List<CategoryTotal> =
        transactionDao.getCategorySpendTotals(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { it.toDomain(currency) }

    override fun observeMonthlyNetChanges(currency: Currency): Flow<List<MonthlyNet>> =
        transactionDao.observeMonthlyNetChanges(currency.currencyCode)
            .map { rows -> rows.map { it.toDomain(currency) } }

    override fun observeDailyNetChanges(
        currency: Currency,
        start: LocalDate,
        endExclusive: LocalDate,
    ): Flow<List<DailyNet>> =
        transactionDao.observeDailyNetChanges(
            startEpochDay = start.toEpochDay(),
            endEpochDayExclusive = endExclusive.toEpochDay(),
            currency = currency.currencyCode,
        ).map { rows -> rows.map { it.toDomain(currency) } }

    override fun observeNetChangeBefore(
        currency: Currency,
        start: LocalDate,
    ): Flow<BigDecimal> =
        transactionDao.observeNetChangeBefore(start.toEpochDay(), currency.currencyCode)
            .map { MoneyMapper.toAmount(it ?: 0L, currency) }

    override suspend fun getStatsPeriodTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): StatsPeriodTotals {
        val row = transactionDao.getStatsPeriodTotals(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        )
        return StatsPeriodTotals(
            expense = MoneyMapper.toAmount(row.expenseMinor ?: 0L, currency),
            income = MoneyMapper.toAmount(row.incomeMinor ?: 0L, currency),
        )
    }

    override suspend fun getCategoryTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): List<CategoryTotal> =
        transactionDao.getCategoryTotals(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { it.toDomain(currency) }

    override suspend fun getBiggestExpense(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Transaction? =
        transactionDao.getBiggestExpense(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        )?.toDomain()

    override suspend fun getDailyActivity(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): List<DailyActivity> =
        transactionDao.getDailyActivity(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { it.toDomain(currency) }

    override suspend fun getRecurringSpendTotal(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): BigDecimal = MoneyMapper.toAmount(
        transactionDao.getRecurringSpendTotal(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ) ?: 0L,
        currency,
    )

    override fun observeRecentTransactions(limit: Int): Flow<List<Transaction>> =
        transactionDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeDashboardTotals(
        windows: DashboardWindows,
        currency: Currency,
    ): Flow<DashboardTotals> =
        transactionDao.observeDashboardTotals(
            todayStart = windows.todayStart.toEpochMilli(),
            todayEnd = windows.todayEnd.toEpochMilli(),
            monthStart = windows.monthStart.toEpochMilli(),
            monthEnd = windows.monthEnd.toEpochMilli(),
            previousStart = windows.previousStart.toEpochMilli(),
            previousToDateEnd = windows.previousToDateEnd.toEpochMilli(),
            currency = currency.currencyCode,
        ).map { row -> row.toDomain(currency) }

    override suspend fun mostUsedCategoryIds(
        type: TransactionType,
        since: Instant,
        limit: Int,
    ): List<Long> =
        transactionDao.mostUsedCategories(type.name, since.toEpochMilli(), limit)
            .mapNotNull { it.categoryId }

    override fun observeCounterpartyTotals(): Flow<List<CounterpartyTotal>> =
        transactionDao.observeCounterpartyTotals().map { rows -> rows.map { it.toDomain() } }

    override fun observeCounterpartyNames(): Flow<List<String>> =
        transactionDao.observeCounterpartyNames()

    override suspend fun getTransaction(id: Long): Transaction? =
        transactionDao.getById(id)?.toDomain()

    override suspend fun countForAccount(accountId: Long): Int =
        transactionDao.countForAccount(accountId)

    override fun observeOtherCurrencyCount(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<Int> = transactionDao.observeOtherCurrencyCount(
        start.toEpochMilli(),
        end.toEpochMilli(),
        currency.currencyCode,
    )

    override suspend fun countForCategory(categoryId: Long): Int =
        transactionDao.countForCategory(categoryId)

    override suspend fun transactionTypesForCategory(categoryId: Long): Set<TransactionType> =
        transactionDao.distinctTypesForCategory(categoryId)
            .mapNotNullTo(mutableSetOf()) { name ->
                runCatching { TransactionType.valueOf(name) }.getOrNull()
            }

    override suspend fun sumOwnMovements(
        accountId: Long,
        start: Instant,
        end: Instant,
        currency: Currency,
    ): BigDecimal = MoneyMapper.toAmount(
        transactionDao.sumOwnMovementsInWindow(accountId, start.toEpochMilli(), end.toEpochMilli()),
        currency,
    )

    override suspend fun upsert(transaction: Transaction): Long {
        val entity = transaction.toEntity()
        return if (entity.id == 0L) {
            transactionDao.insert(entity)
        } else {
            transactionDao.update(entity)
            entity.id
        }
    }

    override suspend fun insertIfAbsent(transaction: Transaction): Long =
        transactionDao.insertIgnoringConflicts(transaction.toEntity())

    override suspend fun delete(transaction: Transaction) =
        transactionDao.delete(transaction.toEntity())

    override suspend fun deleteByIds(ids: List<Long>) =
        transactionDao.deleteByIds(ids)

    override suspend fun deleteAndInsert(
        ids: List<Long>,
        inserts: List<Transaction>,
    ): List<Long> = transactionDao.deleteAndInsert(ids, inserts.map { it.toEntity() })
}
