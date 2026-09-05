package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.mapper.toDomain
import com.callbackdev.saldo.core.database.mapper.toEntity
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.DailyNet
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

    override fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeAccountsWithBalanceAsOfToday(
        todayEpochDayExclusive: Long,
    ): Flow<List<AccountWithBalance>> =
        combine(
            accountDao.observeAllWithBalance(),
            accountDao.observeAllBalancesAsOf(todayEpochDayExclusive),
        ) { totalRows, todayRows ->
            val todayMinorByAccount = todayRows.associateBy({ it.accountId }, { it.balanceMinor })
            totalRows.map { row ->
                val base = row.toDomain()
                val todayBalance = todayMinorByAccount[base.account.id]
                    ?.let { MoneyMapper.toAmount(it, base.account.currency) }
                base.copy(
                    // Surfaced only on divergence, so a settled account stays a single line.
                    balanceAsOfToday = todayBalance?.takeIf { it.compareTo(base.balance) != 0 },
                )
            }
        }

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

    override fun observeDailyNetChanges(
        accountId: Long,
        currency: Currency,
        start: LocalDate,
        endExclusive: LocalDate,
    ): Flow<List<DailyNet>> =
        accountDao.observeDailyNetChanges(
            accountId = accountId,
            startEpochDay = start.toEpochDay(),
            endEpochDayExclusive = endExclusive.toEpochDay(),
        ).map { rows -> rows.map { it.toDomain(currency) } }

    override fun observeNetChangeBefore(
        accountId: Long,
        currency: Currency,
        start: LocalDate,
    ): Flow<BigDecimal> =
        accountDao.observeNetChangeBefore(accountId, start.toEpochDay())
            .map { MoneyMapper.toAmount(it ?: 0L, currency) }

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

    override suspend fun nextSortOrder(type: AccountType): Int =
        accountDao.maxSortOrder(type.name) + 1

    override suspend fun reorder(orderedActive: List<Account>) {
        // Reorder is within-type, so number each account among its own type's
        // members in the given order; the full-row update carries the rest of
        // the account through unchanged.
        val positionByType = mutableMapOf<AccountType, Int>()
        val reordered = orderedActive.map { account ->
            val index = positionByType.getOrDefault(account.type, 0)
            positionByType[account.type] = index + 1
            account.toEntity().copy(sortOrder = index)
        }
        accountDao.updateAll(reordered)
    }

    override suspend fun updateSettlementWatermark(accountId: Long, closing: LocalDate) =
        accountDao.updateSettlementWatermark(accountId, closing.toEpochDay())

    override suspend fun delete(account: Account) = accountDao.delete(account.toEntity())
}
