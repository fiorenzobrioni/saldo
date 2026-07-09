package com.callbackdev.saldo.feature.recurring

import androidx.annotation.StringRes
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurringRule
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/** One subscription resolved against its account and category, with derived figures. */
data class SubscriptionItem(
    val rule: RecurringRule,
    val account: Account?,
    val category: Category?,
    /** Positive monthly-equivalent cost (non-monthly charges normalized over the month). */
    val monthlyEquivalent: BigDecimal,
    /** The next upcoming charge date, or null if the rule has ended. */
    val nextCharge: LocalDate?,
) {
    val id: Long get() = rule.id
}

/** How the subscription list is ordered. */
enum class SubscriptionSort(@param:StringRes val labelRes: Int) {
    NEXT_CHARGE(R.string.subscriptions_sort_next_charge),
    COST(R.string.subscriptions_sort_cost),
    NAME(R.string.subscriptions_sort_name),
}

/** Immutable UI state for the subscriptions screen. */
data class SubscriptionsUiState(
    val isLoading: Boolean = true,
    val items: List<SubscriptionItem> = emptyList(),
    /** Sum of monthly-equivalent costs in [currency]. */
    val monthlyTotal: BigDecimal = BigDecimal.ZERO,
    /** The monthly total projected over a year (monthlyTotal * 12). */
    val annualProjection: BigDecimal = BigDecimal.ZERO,
    val activeCount: Int = 0,
    val currency: Currency = fallbackCurrency,
    val sort: SubscriptionSort = SubscriptionSort.NEXT_CHARGE,
    val today: LocalDate = LocalDate.ofEpochDay(0),
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()

    companion object {
        val fallbackCurrency: Currency =
            runCatching { Currency.getInstance(java.util.Locale.getDefault()) }.getOrNull()
                ?: Currency.getInstance("EUR")
    }
}
