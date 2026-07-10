package com.callbackdev.saldo.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
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
 * Drives the subscriptions screen: the list of active recurring expenses with
 * their monthly-equivalent cost and next charge, plus the monthly total and the
 * annual projection. All figures derive reactively from the database.
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    recurringRuleRepository: RecurringRuleRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val sort = MutableStateFlow(SubscriptionSort.NEXT_CHARGE)

    val uiState: StateFlow<SubscriptionsUiState> = combine(
        recurringRuleRepository.observeRules(),
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
        sort,
    ) { rules, accounts, categories, sortOrder ->
        buildState(rules, accounts, categories, sortOrder)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SubscriptionsUiState(today = LocalDate.now(clock)),
    )

    fun onSortSelected(newSort: SubscriptionSort) {
        sort.update { newSort }
    }

    private fun buildState(
        rules: List<RecurringRule>,
        accounts: List<AccountWithBalance>,
        categories: List<Category>,
        sortOrder: SubscriptionSort,
    ): SubscriptionsUiState {
        val today = LocalDate.now(clock)
        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = categories.associateBy { it.id }

        val active = rules.filter { it.type == TransactionType.EXPENSE && it.isActiveOn(today) }
        val items = active
            .map { rule -> rule.toItem(today, accountById[rule.accountId], categoryById[rule.categoryId]) }
            .sortedWith(sortOrder.comparator())

        val primary = items
            .groupingBy { it.rule.currency }
            .eachCount()
            .maxByOrNull { it.value }?.key
            ?: SubscriptionsUiState.fallbackCurrency
        val monthlyTotal = items
            .filter { it.rule.currency == primary }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.monthlyEquivalent) }

        return SubscriptionsUiState(
            isLoading = false,
            items = items,
            monthlyTotal = monthlyTotal,
            annualProjection = monthlyTotal.multiply(BigDecimal(MONTHS_PER_YEAR)),
            activeCount = items.size,
            currency = primary,
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
