package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.YearMonth
import java.util.Currency

/**
 * A monthly spending cap. A budget with a null [categoryId] is the overall
 * monthly budget (at most one exists, enforced by the repository); otherwise
 * it caps a single expense category.
 *
 * [amount] is a positive magnitude in [currency]; progress against it uses the
 * statistics spend figure (refunds net the spend, transfers/adjustments/
 * excluded/pending movements never count), so category budgets always match
 * the statistics aggregates. The two watermarks record the last month each
 * threshold notification was posted for (once per threshold per month).
 */
data class Budget(
    val id: Long = 0L,
    val categoryId: Long?,
    val amount: BigDecimal,
    val currency: Currency,
    val lastNotified80Month: YearMonth? = null,
    val lastNotified100Month: YearMonth? = null,
) {
    val isOverall: Boolean get() = categoryId == null
}
