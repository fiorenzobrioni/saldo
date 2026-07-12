package com.callbackdev.saldo.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/**
 * Drives the recurrences hub: active recurring expenses (subscriptions) and
 * recurring incomes, each with monthly-equivalent figures, the next
 * charge/credit, the monthly total and the annual projection. All figures
 * derive reactively from the database.
 */
@HiltViewModel
class RecurrencesViewModel @Inject constructor(
    recurringRuleRepository: RecurringRuleRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    userPreferences: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    private val sort = MutableStateFlow(SubscriptionSort.NEXT_CHARGE)

    val uiState: StateFlow<RecurrencesUiState> = combine(
        recurringRuleRepository.observeRules(),
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
        sort,
        userPreferences.primaryCurrencyOverride,
    ) { rules, accounts, categories, sortOrder, currencyOverride ->
        buildState(rules, accounts, categories, sortOrder, currencyOverride)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RecurrencesUiState(today = LocalDate.now(clock)),
    )

    fun onSortSelected(newSort: SubscriptionSort) {
        sort.update { newSort }
    }

    private fun buildState(
        rules: List<RecurringRule>,
        accounts: List<AccountWithBalance>,
        categories: List<Category>,
        sortOrder: SubscriptionSort,
        currencyOverride: Currency?,
    ): RecurrencesUiState {
        val today = LocalDate.now(clock)
        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = categories.associateBy { it.id }

        fun sectionFor(type: TransactionType): RecurrenceSection {
            val items = rules
                .filter { it.type == type && it.isActiveOn(today) }
                .map { rule -> rule.toItem(today, accountById[rule.accountId], categoryById[rule.categoryId]) }
                .sortedWith(sortOrder.comparator())

            // The explicit Settings choice keeps section totals consistent
            // with dashboard and stats; otherwise the section's own majority.
            val primary = currencyOverride
                ?: items
                    .groupingBy { it.rule.currency }
                    .eachCount()
                    .maxByOrNull { it.value }?.key
                ?: fallbackCurrency
            val primaryItems = items.filter { it.rule.currency == primary }
            val monthlyTotal = primaryItems
                .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.monthlyEquivalent) }

            return RecurrenceSection(
                items = items,
                monthlyTotal = monthlyTotal,
                annualProjection = monthlyTotal.multiply(BigDecimal(MONTHS_PER_YEAR)),
                // Same scope as monthlyTotal, so "N subscriptions - X/month" is coherent.
                activeCount = primaryItems.size,
                currency = primary,
            )
        }

        return RecurrencesUiState(
            isLoading = false,
            expenses = sectionFor(TransactionType.EXPENSE),
            incomes = sectionFor(TransactionType.INCOME),
            sort = sortOrder,
            today = today,
        )
    }

    private fun RecurringRule.toItem(today: LocalDate, account: Account?, category: Category?) =
        SubscriptionItem(
            rule = this,
            account = account,
            category = category,
            monthlyEquivalent = RecurrenceCalculator.monthlyEquivalent(this) ?: BigDecimal.ZERO,
            nextCharge = RecurrenceCalculator.nextOccurrence(this, nextChargeFloor(today)),
        )

    /**
     * Floor for the "next charge" lookup: today, or the day after the last
     * generated charge when today has already been charged, so a charge that just
     * fired is not shown again as upcoming.
     */
    private fun RecurringRule.nextChargeFloor(today: LocalDate): LocalDate {
        val afterGenerated = lastGeneratedDate?.plusDays(1)
        return if (afterGenerated != null && afterGenerated > today) afterGenerated else today
    }

    private fun RecurringRule.isActiveOn(today: LocalDate): Boolean =
        endDate == null || endDate >= today

    private fun SubscriptionSort.comparator(): Comparator<SubscriptionItem> = when (this) {
        SubscriptionSort.NEXT_CHARGE ->
            compareBy(nullsLast()) { it.nextCharge }
        SubscriptionSort.COST ->
            compareByDescending<SubscriptionItem> { it.monthlyEquivalent }
                .thenBy { it.rule.name.lowercase() }
        SubscriptionSort.NAME ->
            compareBy { it.rule.name.lowercase() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MONTHS_PER_YEAR = 12
    }
}
