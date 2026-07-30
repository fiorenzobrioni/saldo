package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.callbackdev.saldo.core.database.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeRateDao {

    /**
     * Bulk upsert of a fetched batch. REPLACE and not ABORT: the feed may
     * republish a day (a correction, or an overlap between two fetch windows)
     * and the newest publication wins.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rates: List<ExchangeRateEntity>)

    /**
     * Every cached rate for [currencies], oldest first. The set is small in
     * practice (the currencies the user actually holds, not the whole
     * basket), so the domain keeps it in memory as a sorted table and
     * resolves "most recent rate on or before day X" with a binary search.
     */
    @Query(
        """
        SELECT * FROM exchange_rates
        WHERE currency IN (:currencies)
        ORDER BY currency ASC, dateEpochDay ASC
        """,
    )
    fun observeRates(currencies: List<String>): Flow<List<ExchangeRateEntity>>

    /**
     * Every cached rate from [fromEpochDay] on, for the whole basket, oldest
     * first. Feeds the exchange-rates screen, which shows every downloaded
     * currency rather than only the ledger's.
     */
    @Query(
        """
        SELECT * FROM exchange_rates
        WHERE dateEpochDay >= :fromEpochDay
        ORDER BY currency ASC, dateEpochDay ASC
        """,
    )
    fun observeRatesSince(fromEpochDay: Long): Flow<List<ExchangeRateEntity>>

    /** Most recent day with at least one cached rate; null when the cache is empty. */
    @Query("SELECT MAX(dateEpochDay) FROM exchange_rates")
    suspend fun latestDay(): Long?

    /** Oldest cached day; null when the cache is empty. Bounds the backfill. */
    @Query("SELECT MIN(dateEpochDay) FROM exchange_rates")
    suspend fun earliestDay(): Long?

    /**
     * ISO codes of every currency the ledger touches: account currencies plus
     * both legs of the movements (an archived account's history still feeds
     * the statistics, so archived accounts count too). Drives both the "is
     * there anything to convert" gate and the set of currencies the domain
     * loads rates for.
     */
    @Query(
        """
        SELECT DISTINCT currency FROM accounts
        UNION SELECT DISTINCT currency FROM transactions
        UNION SELECT DISTINCT transferCurrency FROM transactions WHERE transferCurrency IS NOT NULL
        """,
    )
    fun observeLedgerCurrencies(): Flow<List<String>>

    /**
     * Local day (ADR 7) of the oldest movement, or null on an empty ledger.
     * The backfill fetches history from here: converting a flow at the rate
     * of its own date (ADR 40) needs rates as old as the ledger itself.
     */
    @Query(
        """
        SELECT MIN((timestampEpochMilli / 1000 + zoneOffsetSeconds) / 86400)
        FROM transactions
        """,
    )
    suspend fun oldestMovementDay(): Long?

    /** Empties the cache; only "erase all data" calls this. */
    @Query("DELETE FROM exchange_rates")
    suspend fun deleteAll()
}
