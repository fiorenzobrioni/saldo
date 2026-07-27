package com.callbackdev.saldo.core.database.relation

/**
 * Single-row projection of one account's cash totals in a window. Sums are
 * null when no movement matches (SQL SUM over zero rows).
 */
data class AccountPeriodTotalsRow(
    val spendMinor: Long?,
    val incomeMinor: Long?,
)
