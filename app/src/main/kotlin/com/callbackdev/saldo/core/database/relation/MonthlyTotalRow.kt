package com.callbackdev.saldo.core.database.relation

/**
 * One month of statistics totals, grouped by the movement's own local date
 * (ADR 7). [month] is `strftime('%Y-%m', ...)`, e.g. "2026-07". [expenseMinor]
 * is signed (refunds included, so it can exceed zero in a refund-heavy month);
 * [incomeMinor] excludes refunds.
 */
data class MonthlyTotalRow(
    val month: String,
    val expenseMinor: Long,
    val incomeMinor: Long,
)
