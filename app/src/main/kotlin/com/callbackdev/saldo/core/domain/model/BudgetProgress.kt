package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal

/** How far a budget is through its monthly limit. */
enum class BudgetLevel {
    /** Below the warning threshold: on track. */
    UNDER,

    /** At or past 80% of the limit but still below it. */
    WARNING,

    /** At or past the limit. */
    OVER,
    ;

    companion object {
        private const val WARNING_NUMERATOR = 8L
        private const val WARNING_DENOMINATOR = 10L

        /**
         * Level from minor-unit magnitudes (both >= 0): integer arithmetic
         * only, so 79.99% is never rounded up to the warning band.
         */
        fun of(spentMinor: Long, limitMinor: Long): BudgetLevel = when {
            spentMinor >= limitMinor -> OVER
            spentMinor * WARNING_DENOMINATOR >= limitMinor * WARNING_NUMERATOR -> WARNING
            else -> UNDER
        }
    }
}

/**
 * A budget joined with the current month's spend. [spent] is a positive
 * magnitude of the statistics spend (refunds netted, never below zero);
 * [fraction] is spent over limit, deliberately not capped at 1 so the UI can
 * print overshoots ("112%"); [category] is null for the overall budget.
 */
data class BudgetProgress(
    val budget: Budget,
    val category: Category?,
    val spent: BigDecimal,
    val fraction: Float,
    val level: BudgetLevel,
)
