package com.callbackdev.saldo.feature.transactions

import android.net.Uri
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.feature.transactions.filter.TransactionFilters
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/** A movement resolved against its account(s) and category for display. */
data class TransactionListItem(
    val transaction: Transaction,
    val account: Account?,
    val toAccount: Account?,
    val category: Category?,
) {
    val id: Long get() = transaction.id
}

/** Net of expenses and incomes of one day, per currency. */
data class DayTotal(
    val amount: BigDecimal,
    val currency: Currency,
)

/** All movements of a calendar day (grouped by the movement's own offset, ADR 7). */
data class TransactionDayGroup(
    val date: LocalDate,
    val totals: List<DayTotal>,
    val items: List<TransactionListItem>,
)

/**
 * Expenses and incomes of the filtered view in one currency. Transfers and
 * adjustments show up in the list but move money around rather than spend it,
 * so they stay out of these figures (same rule as the day totals).
 */
data class FilteredTotal(
    val currency: Currency,
    /** Sum of expenses (negative or zero). */
    val expenses: BigDecimal,
    /** Sum of incomes (positive or zero). */
    val incomes: BigDecimal,
) {
    val net: BigDecimal get() = expenses.add(incomes)
}

/** Immutable UI state of the transactions list. */
data class TransactionsUiState(
    val isLoading: Boolean = true,
    /** True when at least one active account exists: movements can be recorded. */
    val hasAccounts: Boolean = false,
    /** True when the ledger has at least one movement, before any filtering. */
    val hasAnyTransactions: Boolean = false,
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val filters: TransactionFilters = TransactionFilters.DEFAULT,
    /** The filtered ledger, grouped by day. */
    val days: List<TransactionDayGroup> = emptyList(),
    /** Per-currency totals of the filtered view, plus the movement count. */
    val filteredTotals: List<FilteredTotal> = emptyList(),
    val filteredCount: Int = 0,
    /** Choices offered by the filter sheet. */
    val filterCategories: List<Category> = emptyList(),
    val filterAccounts: List<Account> = emptyList(),
    val filterTags: List<Tag> = emptyList(),
) {
    /** The ledger itself is empty: show the first-run empty state. */
    val isEmpty: Boolean get() = !isLoading && !hasAnyTransactions

    /** Movements exist but none passes the active filters. */
    val isNoResults: Boolean get() = !isLoading && hasAnyTransactions && days.isEmpty()
}

/** One-shot events consumed by the transactions screen. */
sealed interface TransactionsEvent {
    /** A movement was deleted by swipe; carries what undo needs to restore it. */
    data class TransactionDeleted(
        val transaction: Transaction,
        val tagIds: List<Long>,
    ) : TransactionsEvent

    /** The CSV export is ready to be handed to the system Share Sheet. */
    data class CsvExported(val uri: Uri) : TransactionsEvent

    data object CsvExportFailed : TransactionsEvent

    /** A delete or undo failed: nothing changed, let the user retry. */
    data object WriteFailed : TransactionsEvent
}

/** The calendar day of a movement in the timezone it was recorded in (ADR 7). */
internal val Transaction.localDate: LocalDate
    get() = timestamp.atOffset(zoneOffset).toLocalDate()
