package com.callbackdev.saldo.core.database.relation

/**
 * Signed total (minor units) of the movements sharing one counterparty *and*
 * one currency, from a DAO query. The split by currency is deliberate: two
 * currencies never add up, so the aggregation keeps them apart and the domain
 * decides how to present them.
 */
data class CounterpartyTotalRow(
    val name: String,
    /** ISO 4217 code of [totalMinor]. */
    val currency: String,
    /** Negative when the money is out (a credit), positive when it came in. */
    val totalMinor: Long,
    val count: Int,
    /** Most recent local day (ADR 7) of the group, as days since the epoch. */
    val lastEpochDay: Long,
)
