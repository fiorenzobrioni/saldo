package com.callbackdev.saldo.feature.transactions

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.Transaction
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

/** Immutable UI state of the transactions list. */
data class TransactionsUiState(
    val isLoading: Boolean = true,
    /** True when at least one active account exists: movements can be recorded. */
    val hasAccounts: Boolean = false,
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val days: List<TransactionDayGroup> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()
}

/** One-shot events consumed by the transactions screen. */
sealed interface TransactionsEvent {
    /** A movement was deleted by swipe; carries what undo needs to restore it. */
    data class TransactionDeleted(
        val transaction: Transaction,
        val tagIds: List<Long>,
    ) : TransactionsEvent
}

/** The calendar day of a movement in the timezone it was recorded in (ADR 7). */
internal val Transaction.localDate: LocalDate
    get() = timestamp.atOffset(zoneOffset).toLocalDate()
