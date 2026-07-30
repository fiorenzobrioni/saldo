package com.callbackdev.saldo.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Room row for one ECB euro reference rate (ADR 40): how many units of
 * [currency] one euro bought on [dateEpochDay]. The whole table is a local,
 * rebuildable cache of a public feed - it is not user data, which is why it
 * stays out of the backup - but it is the only thing that keeps conversion
 * working offline, so it is a Room table (queryable by date, thousands of
 * rows) rather than a DataStore blob.
 *
 * @property dateEpochDay the rate's publication day (local date as epoch day).
 *   The ECB publishes on TARGET working days only; consumers resolve a
 *   missing day to the most recent earlier row.
 * @property currency ISO 4217 code of the quoted currency, never "EUR" (the
 *   euro is the base and would always be 1).
 * @property rate units of [currency] per euro, stored as the exact decimal
 *   string published by the feed. TEXT and not a scaled integer because rates
 *   have no fixed scale (0.85635 vs 186.27) and the domain parses them
 *   into [java.math.BigDecimal] anyway; storing the source string keeps the
 *   cache byte-faithful to what the ECB said.
 */
@Entity(
    tableName = "exchange_rates",
    primaryKeys = ["dateEpochDay", "currency"],
    indices = [Index("currency", "dateEpochDay")],
)
data class ExchangeRateEntity(
    val dateEpochDay: Long,
    val currency: String,
    val rate: String,
)
