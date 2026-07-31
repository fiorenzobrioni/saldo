package com.callbackdev.saldo.feature.recurring

import androidx.annotation.StringRes
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceSuggestion
import com.callbackdev.saldo.navigation.RecurringRuleEditorRoute
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
    /** Destination account for a transfer rule; null for expense/income rules. */
    val transferAccount: Account? = null,
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
    SubscriptionSort.NEXT_CHARGE -> when (type) {
        TransactionType.INCOME -> R.string.incomes_sort_next_credit
        TransactionType.TRANSFER -> R.string.transfers_sort_next_transfer
        else -> R.string.subscriptions_sort_next_charge
    }

    SubscriptionSort.COST ->
        if (type == TransactionType.EXPENSE) R.string.subscriptions_sort_cost else R.string.incomes_sort_amount

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

/**
 * One recurrence suggestion resolved for display (Fase 19, ADR 43): the
 * detected series, its category when it still exists, and the amount as
 * money for formatting and prefill.
 */
data class RecurrenceSuggestionItem(
    val suggestion: RecurrenceSuggestion,
    val category: Category?,
    /** Positive amount of [suggestion], scaled to its currency. */
    val amount: BigDecimal,
)

/**
 * The rule editor route prefilled from a suggestion: type, name (description
 * or category), amount, frequency, the next expected occurrence as start date,
 * account and category. A starting point the user reviews, never a write.
 */
fun RecurrenceSuggestionItem.toEditorRoute(): RecurringRuleEditorRoute = RecurringRuleEditorRoute(
    initialTypeName = suggestion.type.name,
    initialName = suggestion.name ?: category?.name,
    initialAmountInput = amount.toPlainString(),
    initialVariableAmount = suggestion.isVariableAmount,
    initialFrequencyName = suggestion.frequency.name,
    initialStartDateEpochDay = suggestion.nextOccurrence.toEpochDay(),
    initialAccountId = suggestion.accountId,
    initialCategoryId = suggestion.categoryId,
)

/** State of the explicit recurrence scan as the hub shows it (ADR 43). */
data class RecurrenceScanUi(
    /** True while a tapped scan is running. */
    val isScanning: Boolean = false,
    /** Day of the last persisted scan; null when never scanned (or unreadable). */
    val lastScan: LocalDate? = null,
    /** True when the last scan completed and found nothing at all. */
    val foundNothing: Boolean = false,
    /** True when the last scan hit a declared cap: the result is partial and says so. */
    val truncated: Boolean = false,
)

/** Immutable UI state for the recurrences hub (Subscriptions, Incomes and Transfers tabs). */
data class RecurrencesUiState(
    val isLoading: Boolean = true,
    val expenses: RecurrenceSection = RecurrenceSection(),
    val incomes: RecurrenceSection = RecurrenceSection(),
    val transfers: RecurrenceSection = RecurrenceSection(),
    /**
     * Monthly-equivalent sum of the recurring transfers whose destination is a
     * savings account: the "planned savings" figure, the honest seed of the
     * Savings Goals feature (v2.0). Zero when there are none.
     */
    val plannedMonthlySavings: BigDecimal = BigDecimal.ZERO,
    val savingsCurrency: Currency = fallbackCurrency,
    val sort: SubscriptionSort = SubscriptionSort.NEXT_CHARGE,
    val today: LocalDate = LocalDate.ofEpochDay(0),
    /** The last scan's suggestions still worth showing: not dismissed, not covered by a rule. */
    val suggestions: List<RecurrenceSuggestionItem> = emptyList(),
    val scan: RecurrenceScanUi = RecurrenceScanUi(),
) {
    /** The section backing the tab showing rules of [type]. */
    fun section(type: TransactionType): RecurrenceSection = when (type) {
        TransactionType.INCOME -> incomes
        TransactionType.TRANSFER -> transfers
        else -> expenses
    }

    /** The suggestions belonging to the tab showing rules of [type]. */
    fun suggestionsFor(type: TransactionType): List<RecurrenceSuggestionItem> =
        suggestions.filter { it.suggestion.type == type }
}
