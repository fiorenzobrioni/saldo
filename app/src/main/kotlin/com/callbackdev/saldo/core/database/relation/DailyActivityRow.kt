package com.callbackdev.saldo.core.database.relation

/**
 * One local day's statistics activity: number of expense/income movements and
 * their signed spend total (refunds netted in). [epochDay] is the movement's
 * own local day (per-row offset, ADR 7) as days since the epoch.
 */
data class DailyActivityRow(
    val epochDay: Long,
    val count: Int,
    val spendMinor: Long?,
)
