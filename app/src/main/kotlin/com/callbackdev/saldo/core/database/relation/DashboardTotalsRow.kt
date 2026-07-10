package com.callbackdev.saldo.core.database.relation

/**
 * Single-row projection of the dashboard aggregate query. Sums are null when
 * no movement matches (SQL SUM over zero rows).
 */
data class DashboardTotalsRow(
    val todaySpendMinor: Long?,
    val todayIncomeMinor: Long?,
    val monthSpendMinor: Long?,
    val monthIncomeMinor: Long?,
    val monthToDateSpendMinor: Long?,
    val previousToDateSpendMinor: Long?,
)
