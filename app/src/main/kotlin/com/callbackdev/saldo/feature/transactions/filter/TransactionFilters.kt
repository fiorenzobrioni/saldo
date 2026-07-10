package com.callbackdev.saldo.feature.transactions.filter

import com.callbackdev.saldo.core.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/** Quick date ranges offered as chips; [CUSTOM] uses the explicit bounds. */
enum class DatePreset { ALL, THIS_MONTH, LAST_MONTH, LAST_90_DAYS, THIS_YEAR, CUSTOM }

/**
 * The combinable filters of the movements list. Empty sets and null bounds
 * mean "no restriction"; the whole state is immutable so it can live in a
 * [kotlinx.coroutines.flow.StateFlow] and be combined with the data flows.
 *
 * [amountMin]/[amountMax] are magnitudes: they match against the absolute
 * amount, so "at least 100" finds both a 100 expense and a 100 income.
 */
data class TransactionFilters(
    val query: String = "",
    val datePreset: DatePreset = DatePreset.ALL,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val types: Set<TransactionType> = emptySet(),
    val categoryIds: Set<Long> = emptySet(),
    val accountIds: Set<Long> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
    val amountMin: BigDecimal? = null,
    val amountMax: BigDecimal? = null,
) {
    /** True when anything beyond the search query restricts the list. */
    val hasActiveFilters: Boolean
        get() = datePreset != DatePreset.ALL ||
            types.isNotEmpty() ||
            categoryIds.isNotEmpty() ||
            accountIds.isNotEmpty() ||
            tagIds.isNotEmpty() ||
            amountMin != null ||
            amountMax != null

    /** True when the visible list is restricted in any way (filters or search). */
    val isActive: Boolean get() = hasActiveFilters || query.isNotBlank()

    /** Number of active filter groups, for the badge on the filter button. */
    val activeCount: Int
        get() = listOf(
            datePreset != DatePreset.ALL,
            types.isNotEmpty(),
            categoryIds.isNotEmpty(),
            accountIds.isNotEmpty(),
            tagIds.isNotEmpty(),
            amountMin != null || amountMax != null,
        ).count { it }

    companion object {
        val NONE = TransactionFilters()
    }
}
