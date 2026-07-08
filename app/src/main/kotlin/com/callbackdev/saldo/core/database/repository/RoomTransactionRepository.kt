package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.mapper.toDomain
import com.callbackdev.saldo.core.database.mapper.toEntity
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.Currency
import javax.inject.Inject

class RoomTransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) : TransactionRepository {

    override fun observeTransactions(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

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

    override suspend fun getTransaction(id: Long): Transaction? =
        transactionDao.getById(id)?.toDomain()

    override suspend fun countForAccount(accountId: Long): Int =
        transactionDao.countForAccount(accountId)

    override suspend fun countForCategory(categoryId: Long): Int =
        transactionDao.countForCategory(categoryId)

    override suspend fun upsert(transaction: Transaction): Long {
        val entity = transaction.toEntity()
        return if (entity.id == 0L) {
            transactionDao.insert(entity)
        } else {
            transactionDao.update(entity)
            entity.id
        }
    }

    override suspend fun delete(transaction: Transaction) =
        transactionDao.delete(transaction.toEntity())
}
