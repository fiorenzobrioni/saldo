package com.callbackdev.saldo.core.database.relation

/** Signed spend total of one account over a period (refunds netted). */
data class AccountTotalRow(
    val accountId: Long,
    val totalMinor: Long,
    val count: Int,
)
