package com.callbackdev.saldo.core.database.relation

/**
 * Net effect of one local day's movements on the balance of the included
 * accounts, every type counted (transfer legs included). [epochDay] is the
 * movement's own local day (per-row offset, ADR 7) as days since the epoch.
 */
data class DailyNetRow(
    val epochDay: Long,
    val netMinor: Long,
)
