package com.callbackdev.saldo.feature.transactions

import com.callbackdev.saldo.core.domain.model.TransactionType
import java.math.BigDecimal

/**
 * Day-grouping and totals of a list of movements, shared by the ledger tab and
 * the drill-down list. Pure functions, JVM-testable.
 */

/** Groups [items] into calendar days (per-movement offset, ADR 7), newest first. */
internal fun buildDayGroups(items: List<TransactionListItem>): List<TransactionDayGroup> = items
    .groupBy { it.transaction.localDate }
    .map { (date, dayItems) -> TransactionDayGroup(date, dayTotals(dayItems), dayItems) }
    .sortedByDescending { it.date }

/**
 * Net of expenses and incomes per currency; transfers and adjustments move
 * money around but are not spending, so they stay out of the day total.
 */
internal fun dayTotals(items: List<TransactionListItem>): List<DayTotal> = items
    .filter { it.transaction.type.isSpendingOrIncome }
    .groupBy { it.transaction.currency }
    .map { (currency, dayItems) ->
        DayTotal(
            amount = dayItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.transaction.amount) },
            currency = currency,
        )
    }
    .sortedBy { it.currency.currencyCode }

/**
 * Per-currency expense and income totals of the whole (filtered) view, for the
 * always-visible summary bar. Same exclusion rule as [dayTotals].
 */
internal fun filteredTotals(items: List<TransactionListItem>): List<FilteredTotal> = items
    .filter { it.transaction.type.isSpendingOrIncome }
    .groupBy { it.transaction.currency }
    .map { (currency, currencyItems) ->
        val (incomes, expenses) = currencyItems.partition { it.transaction.amount.signum() >= 0 }
        FilteredTotal(
            currency = currency,
            expenses = expenses.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.transaction.amount) },
            incomes = incomes.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.transaction.amount) },
        )
    }
    .sortedBy { it.currency.currencyCode }

private val TransactionType.isSpendingOrIncome: Boolean
    get() = this == TransactionType.EXPENSE || this == TransactionType.INCOME
