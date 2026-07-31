package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.ForeignFlowDao
import com.callbackdev.saldo.core.database.dao.RecurrenceCandidateDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.mapper.toDomain
import com.callbackdev.saldo.core.database.mapper.toEntity
import com.callbackdev.saldo.core.database.relation.CategorySpendCurrencyDayRow
import com.callbackdev.saldo.core.database.relation.RecurrenceOccurrenceRow
import com.callbackdev.saldo.core.database.relation.SpendCurrencyDayRow
import com.callbackdev.saldo.core.domain.model.AccountTotal
import com.callbackdev.saldo.core.domain.model.CategorySpendDayTotal
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.CounterpartyTotal
import com.callbackdev.saldo.core.domain.model.CurrencyMovementCount
import com.callbackdev.saldo.core.domain.model.DailyActivity
import com.callbackdev.saldo.core.domain.model.DailyNet
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.ForeignAccountDayTotal
import com.callbackdev.saldo.core.domain.model.ForeignCategoryDayTotal
import com.callbackdev.saldo.core.domain.model.ForeignDashboardDayFlows
import com.callbackdev.saldo.core.domain.model.ForeignMonthlyDayTotal
import com.callbackdev.saldo.core.domain.model.MonthlyNet
import com.callbackdev.saldo.core.domain.model.MonthlyTotal
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import com.callbackdev.saldo.core.domain.model.SpendDayTotal
import com.callbackdev.saldo.core.domain.model.StatsPeriodTotals
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.quickentry.DescriptionUsage
import com.callbackdev.saldo.core.domain.recurrence.CandidateOccurrence
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceAmountGroup
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceDescriptionGroup
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
    private val foreignFlowDao: ForeignFlowDao,
    private val recurrenceCandidateDao: RecurrenceCandidateDao,
) : TransactionRepository {

    override fun observeTransactions(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observePendingTransactions(): Flow<List<Transaction>> =
        transactionDao.observePending().map { rows -> rows.map { it.toDomain() } }

    override fun observeTransactionsFrom(day: LocalDate): Flow<List<Transaction>> =
        transactionDao.observeAfter(day.toEpochDay()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getDueReminders(from: LocalDate, to: LocalDate): List<Transaction> =
        transactionDao.getDueReminders(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }

    override suspend fun updateReminderWatermark(transactionId: Long, date: LocalDate) {
        transactionDao.updateReminderWatermark(transactionId, date.toEpochDay())
    }

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

    override suspend fun descriptionUsage(
        type: TransactionType,
        since: Instant,
        word: String,
        foldedWord: String,
        limit: Int,
    ): List<DescriptionUsage> =
        transactionDao.descriptionUsage(type.name, since.toEpochMilli(), word, foldedWord, limit)
            .map { DescriptionUsage(description = it.description, categoryId = it.categoryId) }

    override suspend fun recurrenceAmountGroups(
        since: Instant,
        minOccurrences: Int,
        limit: Int,
    ): List<RecurrenceAmountGroup> =
        recurrenceCandidateDao.recurrenceAmountGroups(since.toEpochMilli(), minOccurrences, limit)
            .mapNotNull { row ->
                val currency = runCatching { Currency.getInstance(row.currency) }.getOrNull()
                    ?: return@mapNotNull null
                RecurrenceAmountGroup(
                    type = TransactionType.valueOf(row.type),
                    accountId = row.accountId,
                    categoryId = row.categoryId,
                    currency = currency,
                    amountMinor = row.amountMinor,
                    count = row.count,
                )
            }

    override suspend fun recurrenceDescriptionGroups(
        since: Instant,
        minOccurrences: Int,
        limit: Int,
    ): List<RecurrenceDescriptionGroup> =
        recurrenceCandidateDao.recurrenceDescriptionGroups(since.toEpochMilli(), minOccurrences, limit)
            .mapNotNull { row ->
                val currency = runCatching { Currency.getInstance(row.currency) }.getOrNull()
                    ?: return@mapNotNull null
                RecurrenceDescriptionGroup(
                    type = TransactionType.valueOf(row.type),
                    accountId = row.accountId,
                    categoryId = row.categoryId,
                    currency = currency,
                    descriptionKey = row.descriptionKey,
                    count = row.count,
                )
            }

    override suspend fun recurrenceAmountGroupOccurrences(
        group: RecurrenceAmountGroup,
        since: Instant,
        limit: Int,
    ): List<CandidateOccurrence> =
        recurrenceCandidateDao.recurrenceAmountGroupOccurrences(
            type = group.type.name,
            accountId = group.accountId,
            categoryId = group.categoryId,
            currency = group.currency.currencyCode,
            amountMinor = group.amountMinor,
            sinceMillis = since.toEpochMilli(),
            limit = limit,
        ).map { it.toOccurrence() }

    override suspend fun recurrenceDescriptionGroupOccurrences(
        group: RecurrenceDescriptionGroup,
        since: Instant,
        limit: Int,
    ): List<CandidateOccurrence> =
        recurrenceCandidateDao.recurrenceDescriptionGroupOccurrences(
            type = group.type.name,
            accountId = group.accountId,
            categoryId = group.categoryId,
            currency = group.currency.currencyCode,
            descriptionKey = group.descriptionKey,
            sinceMillis = since.toEpochMilli(),
            limit = limit,
        ).map { it.toOccurrence() }

    private fun RecurrenceOccurrenceRow.toOccurrence(): CandidateOccurrence =
        CandidateOccurrence(
            date = LocalDate.ofEpochDay(epochDay),
            amountMinor = amountMinor,
            description = description,
        )

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

    override fun observeForeignDashboardFlows(
        windows: DashboardWindows,
        currency: Currency,
    ): Flow<List<ForeignDashboardDayFlows>> =
        foreignFlowDao.observeForeignDashboardFlows(
            todayStart = windows.todayStart.toEpochMilli(),
            todayEnd = windows.todayEnd.toEpochMilli(),
            monthStart = windows.monthStart.toEpochMilli(),
            monthEnd = windows.monthEnd.toEpochMilli(),
            previousStart = windows.previousStart.toEpochMilli(),
            previousToDateEnd = windows.previousToDateEnd.toEpochMilli(),
            currency = currency.currencyCode,
        ).map { rows ->
            rows.mapNotNull { row ->
                val rowCurrency = currencyOf(row.currency) ?: return@mapNotNull null
                ForeignDashboardDayFlows(
                    currency = rowCurrency,
                    day = LocalDate.ofEpochDay(row.epochDay),
                    today = PeriodTotals(
                        spend = MoneyMapper.toAmount(row.todaySpendMinor ?: 0L, rowCurrency),
                        income = MoneyMapper.toAmount(row.todayIncomeMinor ?: 0L, rowCurrency),
                    ),
                    month = PeriodTotals(
                        spend = MoneyMapper.toAmount(row.monthSpendMinor ?: 0L, rowCurrency),
                        income = MoneyMapper.toAmount(row.monthIncomeMinor ?: 0L, rowCurrency),
                    ),
                    monthToDateSpend = MoneyMapper
                        .toAmount(row.monthToDateSpendMinor ?: 0L, rowCurrency)
                        .negate(),
                    monthToDateNonRecurringSpend = MoneyMapper
                        .toAmount(row.monthToDateNonRecurringSpendMinor ?: 0L, rowCurrency)
                        .negate(),
                    previousToDateSpend = MoneyMapper
                        .toAmount(row.previousToDateSpendMinor ?: 0L, rowCurrency)
                        .negate(),
                )
            }
        }

    override fun observeForeignCategoryTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<ForeignCategoryDayTotal>> =
        foreignFlowDao.observeForeignCategoryTotals(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { rows ->
            rows.mapNotNull { row ->
                val rowCurrency = currencyOf(row.currency) ?: return@mapNotNull null
                ForeignCategoryDayTotal(
                    categoryId = row.categoryId,
                    currency = rowCurrency,
                    day = LocalDate.ofEpochDay(row.epochDay),
                    total = MoneyMapper.toAmount(row.totalMinor, rowCurrency),
                    count = row.count,
                )
            }
        }

    override fun observeForeignAccountSpendTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<ForeignAccountDayTotal>> =
        foreignFlowDao.observeForeignAccountSpendTotals(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { rows ->
            rows.mapNotNull { row ->
                val rowCurrency = currencyOf(row.currency) ?: return@mapNotNull null
                ForeignAccountDayTotal(
                    accountId = row.accountId,
                    currency = rowCurrency,
                    day = LocalDate.ofEpochDay(row.epochDay),
                    total = MoneyMapper.toAmount(row.totalMinor, rowCurrency),
                    count = row.count,
                )
            }
        }

    override fun observeForeignMonthlyTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<ForeignMonthlyDayTotal>> =
        foreignFlowDao.observeForeignMonthlyTotals(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { rows ->
            rows.mapNotNull { row ->
                val rowCurrency = currencyOf(row.currency) ?: return@mapNotNull null
                ForeignMonthlyDayTotal(
                    currency = rowCurrency,
                    day = LocalDate.ofEpochDay(row.epochDay),
                    expense = MoneyMapper.toAmount(row.expenseMinor ?: 0L, rowCurrency),
                    income = MoneyMapper.toAmount(row.incomeMinor ?: 0L, rowCurrency),
                )
            }
        }

    override fun observeOtherCurrencyCounts(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<CurrencyMovementCount>> =
        foreignFlowDao.observeOtherCurrencyCounts(
            start.toEpochMilli(),
            end.toEpochMilli(),
            currency.currencyCode,
        ).map { rows -> rows.map { CurrencyMovementCount(it.currency, it.count) } }

    override fun observeSpendByCurrencyDay(
        start: Instant,
        end: Instant,
    ): Flow<List<SpendDayTotal>> =
        foreignFlowDao.observeSpendByCurrencyDay(start.toEpochMilli(), end.toEpochMilli())
            .map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun getSpendByCurrencyDay(
        start: Instant,
        end: Instant,
    ): List<SpendDayTotal> =
        foreignFlowDao.getSpendByCurrencyDay(start.toEpochMilli(), end.toEpochMilli())
            .mapNotNull { it.toDomainOrNull() }

    override fun observeCategorySpendByCurrencyDay(
        start: Instant,
        end: Instant,
    ): Flow<List<CategorySpendDayTotal>> =
        foreignFlowDao.observeCategorySpendByCurrencyDay(start.toEpochMilli(), end.toEpochMilli())
            .map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun getCategorySpendByCurrencyDay(
        start: Instant,
        end: Instant,
    ): List<CategorySpendDayTotal> =
        foreignFlowDao.getCategorySpendByCurrencyDay(start.toEpochMilli(), end.toEpochMilli())
            .mapNotNull { it.toDomainOrNull() }

    private fun SpendCurrencyDayRow.toDomainOrNull(): SpendDayTotal? {
        val rowCurrency = currencyOf(currency) ?: return null
        return SpendDayTotal(
            currency = rowCurrency,
            day = LocalDate.ofEpochDay(epochDay),
            total = MoneyMapper.toAmount(totalMinor, rowCurrency),
        )
    }

    private fun CategorySpendCurrencyDayRow.toDomainOrNull(): CategorySpendDayTotal? {
        val rowCurrency = currencyOf(currency) ?: return null
        return CategorySpendDayTotal(
            categoryId = categoryId,
            currency = rowCurrency,
            day = LocalDate.ofEpochDay(epochDay),
            total = MoneyMapper.toAmount(totalMinor, rowCurrency),
        )
    }

    /** A stored code that is not ISO 4217 cannot be scaled; its rows are dropped. */
    private fun currencyOf(code: String): Currency? =
        runCatching { Currency.getInstance(code) }.getOrNull()
}
