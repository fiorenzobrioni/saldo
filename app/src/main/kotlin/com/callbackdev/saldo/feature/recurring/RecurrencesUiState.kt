package com.callbackdev.saldo.feature.recurring

import androidx.annotation.StringRes
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/** One recurring rule resolved against its account and category, with derived figures. */
data class SubscriptionItem(
    val rule: RecurringRule,
    val account: Account?,
    val category: Category?,
    /** Positive monthly-equivalent amount (non-monthly rules normalized over the month). */
    val monthlyEquivalent: BigDecimal,
    /** The next upcoming charge/credit date, or null if the rule has ended. */
    val nextCharge: LocalDate?,
) {
    val id: Long get() = rule.id
}

/** How each recurrences list is ordered. */
enum class SubscriptionSort {
    NEXT_CHARGE,
    COST,
    NAME,
}

/** Sort label adapted to the tab: "next charge/cost" for expenses, "next credit/amount" for incomes. */
@StringRes
fun SubscriptionSort.labelRes(type: TransactionType): Int = when (this) {
    SubscriptionSort.NEXT_CHARGE ->
        if (type == TransactionType.INCOME) {
            R.string.incomes_sort_next_credit
        } else {
            R.string.subscriptions_sort_next_charge
        }

    SubscriptionSort.COST ->
        if (type == TransactionType.INCOME) R.string.incomes_sort_amount else R.string.subscriptions_sort_cost

    SubscriptionSort.NAME -> R.string.subscriptions_sort_name
}

/** The figures of one recurrences tab (subscriptions or recurring incomes). */
data class RecurrenceSection(
    val items: List<SubscriptionItem> = emptyList(),
    /** Sum of monthly-equivalent amounts in [currency]. */
    val monthlyTotal: BigDecimal = BigDecimal.ZERO,
    /** The monthly total projected over a year (monthlyTotal * 12). */
    val annualProjection: BigDecimal = BigDecimal.ZERO,
    val activeCount: Int = 0,
    val currency: Currency = fallbackCurrency,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/** Immutable UI state for the recurrences hub (Subscriptions and Incomes tabs). */
data class RecurrencesUiState(
    val isLoading: Boolean = true,
    val expenses: RecurrenceSection = RecurrenceSection(),
    val incomes: RecurrenceSection = RecurrenceSection(),
    val sort: SubscriptionSort = SubscriptionSort.NEXT_CHARGE,
    val today: LocalDate = LocalDate.ofEpochDay(0),
) {
    /** The section backing the tab showing rules of [type]. */
    fun section(type: TransactionType): RecurrenceSection =
        if (type == TransactionType.INCOME) incomes else expenses
}
