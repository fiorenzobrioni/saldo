package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal

/**
 * Aggregated total for a single category over a period, used by statistics.
 * [total] follows the signed convention (expenses negative, incomes/refunds
 * positive), so a category's net spend nets refunds automatically.
 */
data class CategoryTotal(
    /** Null for movements without a category (their own bucket in the ring). */
    val categoryId: Long?,
    val total: BigDecimal,
    val count: Int,
)
