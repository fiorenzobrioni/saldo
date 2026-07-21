package com.callbackdev.saldo.core.database.relation

/**
 * An account id paired with its balance counting only movements dated up to a
 * cutoff day (local day, ADR 7), in minor units. Feeds the per-account
 * "as of today" figure shown when future-dated confirmed movements make the
 * account's total balance run ahead of what is available today.
 */
data class AccountBalanceAsOfRow(
    val accountId: Long,
    val balanceMinor: Long,
)
