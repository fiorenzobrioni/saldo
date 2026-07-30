package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.rates.ExchangeRate
import com.callbackdev.saldo.core.domain.rates.RateTable
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Access to the local cache of ECB reference rates (ADR 40). */
interface ExchangeRateRepository {

    /**
     * The cached rate history for every currency the ledger touches, rebuilt
     * when either the cache or the set of ledger currencies changes. Empty
     * table when there is nothing foreign to convert or nothing cached yet.
     */
    fun observeRateTable(): Flow<RateTable>

    /**
     * ISO codes of every currency the ledger touches (account currencies and
     * both legs of the movements). Feeds the "is there anything to convert"
     * gate of the sync policy.
     */
    fun observeLedgerCurrencies(): Flow<List<String>>

    /** Most recent cached day across all currencies; null when the cache is empty. */
    suspend fun latestCachedDay(): LocalDate?

    /** Oldest cached day; null when the cache is empty. */
    suspend fun earliestCachedDay(): LocalDate?

    /** Local day of the oldest movement, the backfill floor; null on an empty ledger. */
    suspend fun oldestMovementDay(): LocalDate?

    /** Persists a fetched batch, newest publication winning on conflicts. */
    suspend fun store(rates: List<ExchangeRate>)
}
