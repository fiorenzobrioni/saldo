package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.ExchangeRateDao
import com.callbackdev.saldo.core.database.entity.ExchangeRateEntity
import com.callbackdev.saldo.core.domain.rates.ExchangeRate
import com.callbackdev.saldo.core.domain.rates.RateTable
import com.callbackdev.saldo.core.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

class RoomExchangeRateRepository @Inject constructor(
    private val exchangeRateDao: ExchangeRateDao,
) : ExchangeRateRepository {

    /**
     * Loads only the ledger's own currencies into memory: the cache may hold
     * the whole ECB basket (the fetch takes everything, ADR 40), but the
     * table serving the converters stays a handful of series. EUR is the
     * base of every quote and never has rows.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRateTable(): Flow<RateTable> = observeLedgerCurrencies()
        .map { codes -> codes.filter { it != EUR_CODE }.sorted() }
        .distinctUntilChanged()
        .flatMapLatest { codes -> exchangeRateDao.observeRates(codes) }
        .map { rows -> RateTable.of(rows.mapNotNull { it.toDomain() }) }

    override fun observeLedgerCurrencies(): Flow<List<String>> =
        exchangeRateDao.observeLedgerCurrencies().distinctUntilChanged()

    override fun observeRatesSince(from: LocalDate): Flow<List<ExchangeRate>> =
        exchangeRateDao.observeRatesSince(from.toEpochDay())
            .map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun latestCachedDay(): LocalDate? =
        exchangeRateDao.latestDay()?.let(LocalDate::ofEpochDay)

    override suspend fun earliestCachedDay(): LocalDate? =
        exchangeRateDao.earliestDay()?.let(LocalDate::ofEpochDay)

    override suspend fun oldestMovementDay(): LocalDate? =
        exchangeRateDao.oldestMovementDay()?.let(LocalDate::ofEpochDay)

    override suspend fun store(rates: List<ExchangeRate>) {
        if (rates.isEmpty()) return
        exchangeRateDao.upsertAll(
            rates.map {
                ExchangeRateEntity(
                    dateEpochDay = it.day.toEpochDay(),
                    currency = it.currency,
                    rate = it.perEuro.toPlainString(),
                )
            },
        )
    }

    /** A row that fails to parse is dropped: better one missing day than a crash on a corrupt cache. */
    private fun ExchangeRateEntity.toDomain(): ExchangeRate? {
        val parsed = runCatching { BigDecimal(rate) }.getOrNull() ?: return null
        if (parsed.signum() <= 0) return null
        return ExchangeRate(
            currency = currency,
            day = LocalDate.ofEpochDay(dateEpochDay),
            perEuro = parsed,
        )
    }

    private companion object {
        const val EUR_CODE = "EUR"
    }
}
