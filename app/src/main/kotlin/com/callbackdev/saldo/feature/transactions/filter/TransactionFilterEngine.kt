package com.callbackdev.saldo.feature.transactions.filter

import com.callbackdev.saldo.core.domain.model.Transaction
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Pure, JVM-testable filtering of movements. Filtering runs in memory over the
 * observed ledger (the list already loads it in full): this keeps a single
 * code path, and lets the search be accent- and case-insensitive via Unicode
 * normalization, which SQLite `LIKE` (ASCII-only case folding) cannot do.
 */
object TransactionFilterEngine {

    private val DIACRITICS = Regex("\\p{Mn}+")
    private const val LAST_90_DAYS_LENGTH = 90L
    private const val DAYS_PER_WEEK = 7L

    /** Lowercases and strips diacritics, so "perche" matches "PERCHÉ". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()

    /**
     * The inclusive local-date range selected by [filters], or null when the
     * date is unrestricted. A custom range missing both bounds is unrestricted;
     * a single missing bound is open on that side. [firstDayOfWeek] anchors
     * the [DatePreset.THIS_WEEK] window (a Settings preference).
     */
    fun dateRange(
        filters: TransactionFilters,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): ClosedRange<LocalDate>? =
        when (filters.datePreset) {
            DatePreset.ALL -> null
            DatePreset.THIS_WEEK -> {
                val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                start..start.plusDays(DAYS_PER_WEEK - 1)
            }
            DatePreset.THIS_MONTH ->
                today.withDayOfMonth(1)..today.with(TemporalAdjusters.lastDayOfMonth())
            DatePreset.LAST_MONTH -> {
                val firstOfLastMonth = today.withDayOfMonth(1).minusMonths(1)
                firstOfLastMonth..firstOfLastMonth.with(TemporalAdjusters.lastDayOfMonth())
            }
            DatePreset.LAST_90_DAYS -> today.minusDays(LAST_90_DAYS_LENGTH - 1)..today
            DatePreset.THIS_YEAR ->
                today.withDayOfYear(1)..today.with(TemporalAdjusters.lastDayOfYear())
            DatePreset.CUSTOM -> when {
                filters.customStart == null && filters.customEnd == null -> null
                else -> (filters.customStart ?: LocalDate.MIN)..(filters.customEnd ?: LocalDate.MAX)
            }
        }

    /**
     * Whether [transaction] passes every active filter. [localDate] is the
     * movement's calendar day in the timezone it was recorded in (ADR 7);
     * [tagIds] are the tags attached to it. Tag and account filters match on
     * "any of"; the account filter also matches a transfer's destination.
     */
    @Suppress("LongParameterList") // Pure function: every argument is one input of the match.
    fun matches(
        transaction: Transaction,
        localDate: LocalDate,
        tagIds: Set<Long>,
        filters: TransactionFilters,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Boolean = matchesDate(localDate, filters, today, firstDayOfWeek) &&
        matchesType(transaction, filters) &&
        matchesCategory(transaction, filters) &&
        matchesAccount(transaction, filters) &&
        matchesTags(tagIds, filters) &&
        matchesAmount(transaction, filters) &&
        matchesQuery(transaction, filters)

    private fun matchesDate(
        localDate: LocalDate,
        filters: TransactionFilters,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Boolean {
        val range = dateRange(filters, today, firstDayOfWeek) ?: return true
        return localDate in range
    }

    private fun matchesType(transaction: Transaction, filters: TransactionFilters): Boolean =
        filters.types.isEmpty() || transaction.type in filters.types

    private fun matchesCategory(transaction: Transaction, filters: TransactionFilters): Boolean =
        filters.categoryIds.isEmpty() || transaction.categoryId in filters.categoryIds

    private fun matchesAccount(transaction: Transaction, filters: TransactionFilters): Boolean =
        filters.accountIds.isEmpty() ||
            transaction.accountId in filters.accountIds ||
            transaction.transferAccountId in filters.accountIds

    private fun matchesTags(tagIds: Set<Long>, filters: TransactionFilters): Boolean =
        filters.tagIds.isEmpty() || filters.tagIds.any { it in tagIds }

    private fun matchesAmount(transaction: Transaction, filters: TransactionFilters): Boolean {
        if (filters.amountMin == null && filters.amountMax == null) return true
        val magnitude = transaction.amount.abs()
        return (filters.amountMin == null || magnitude >= filters.amountMin) &&
            (filters.amountMax == null || magnitude <= filters.amountMax)
    }

    private fun matchesQuery(transaction: Transaction, filters: TransactionFilters): Boolean {
        if (filters.query.isBlank()) return true
        val needle = normalize(filters.query.trim())
        return listOfNotNull(transaction.description, transaction.note)
            .any { normalize(it).contains(needle) }
    }
}
