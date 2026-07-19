package com.callbackdev.saldo.core.database.relation

/**
 * Statistics totals of one arbitrary period in a single row: signed expense
 * sum (refunds netted in) and refund-free income sum, both NULL when nothing
 * matches. The unwindowed twin of [MonthlyTotalRow].
 */
data class StatsPeriodTotalsRow(
    val expenseMinor: Long?,
    val incomeMinor: Long?,
)
