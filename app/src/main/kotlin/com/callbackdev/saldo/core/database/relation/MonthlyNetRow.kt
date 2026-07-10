package com.callbackdev.saldo.core.database.relation

/**
 * Net effect of one month's movements on the balance of the included accounts,
 * every type counted (transfer legs included). [month] is `strftime('%Y-%m', ...)`.
 */
data class MonthlyNetRow(
    val month: String,
    val netMinor: Long,
)
