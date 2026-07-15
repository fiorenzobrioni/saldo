package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.mapper.toDomain
import com.callbackdev.saldo.core.database.mapper.toEntity
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

class RoomAccountRepository @Inject constructor(
    private val accountDao: AccountDao,
) : AccountRepository {

    override fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>> =
        accountDao.observeAllWithBalance().map { rows -> rows.map { it.toDomain() } }

    override fun observeAccount(id: Long): Flow<Account?> =
        accountDao.observeById(id).map { it?.toDomain() }

    override fun observeAccountBalance(id: Long): Flow<BigDecimal> =
        combine(accountDao.observeById(id), accountDao.observeBalance(id)) { entity, minor ->
            val currency = entity?.let { Currency.getInstance(it.currency) }
                ?: return@combine BigDecimal.ZERO
            MoneyMapper.toAmount(minor ?: 0L, currency)
        }

    override fun observeTotalBalance(currency: Currency): Flow<BigDecimal> =
        accountDao.observeTotalBalance(currency.currencyCode)
            .map { MoneyMapper.toAmount(it, currency) }

    override fun observeInitialBalanceTotal(currency: Currency): Flow<BigDecimal> =
        accountDao.observeInitialBalanceTotal(currency.currencyCode)
            .map { MoneyMapper.toAmount(it, currency) }

    override suspend fun getAccount(id: Long): Account? = accountDao.getById(id)?.toDomain()

    override suspend fun upsert(account: Account): Long {
        val entity = account.toEntity()
        return if (entity.id == 0L) {
            accountDao.insert(entity)
        } else {
            accountDao.update(entity)
            entity.id
        }
    }

    override suspend fun updateSettlementWatermark(accountId: Long, closing: LocalDate) =
        accountDao.updateSettlementWatermark(accountId, closing.toEpochDay())

    override suspend fun delete(account: Account) = accountDao.delete(account.toEntity())
}
