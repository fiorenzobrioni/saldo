package com.callbackdev.saldo.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.hasEndedBy
import com.callbackdev.saldo.core.domain.model.runsInMonthOf
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.common.time.midnightTicker
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

    /**
     * Sort choice and the current day, pre-combined to stay within combine's
     * arity. The midnight ticker re-anchors "today" so the next charge dates
     * and the active-rule filter stay correct while the hub is left open.
     */
    private val sortAndToday = combine(sort, midnightTicker(clock), ::Pair)

    val uiState: StateFlow<RecurrencesUiState> = combine(
        recurringRuleRepository.observeRules(),
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
        sortAndToday,
        userPreferences.primaryCurrencyOverride,
    ) { rules, accounts, categories, (sortOrder, today), currencyOverride ->
        buildState(rules, accounts, categories, sortOrder, currencyOverride, today)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RecurrencesUiState(today = LocalDate.now(clock)),
    )

    fun onSortSelected(newSort: SubscriptionSort) {
        sort.update { newSort }
    }

    @Suppress("LongParameterList") // One argument per combined source plus the resolved day.
    private fun buildState(
        rules: List<RecurringRule>,
        accounts: List<AccountWithBalance>,
        categories: List<Category>,
        sortOrder: SubscriptionSort,
        currencyOverride: Currency?,
        today: LocalDate,
    ): RecurrencesUiState {
        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = categories.associateBy { it.id }

        fun sectionFor(type: TransactionType): RecurrenceSection {
            // Listed: everything not yet over. A rule starting next quarter is
            // real and its first charge date is worth seeing, so it stays on
            // screen even though it is priced at zero below.
            val items = rules
                .filter { it.type == type && !it.hasEndedBy(today) }
                .map { rule ->
                    rule.toItem(
                        today = today,
                        account = accountById[rule.accountId],
                        category = categoryById[rule.categoryId],
                        transferAccount = accountById[rule.transferAccountId],
                    )
                }
                .sortedWith(sortOrder.comparator())

            // The explicit Settings choice keeps section totals consistent
            // with dashboard and stats; otherwise the section's own majority.
            val primary = currencyOverride
                ?: items
                    .groupingBy { it.rule.currency }
                    .eachCount()
                    .maxByOrNull { it.value }?.key
                ?: fallbackCurrency
            // Priced: only the rules that carry a cost into this month. A rule
            // starting later this month counts (it is a real monthly cost); one
            // starting next quarter does not, and counting it would inflate the
            // total and the annual projection from the moment it is created.
            val running = items.filter { it.rule.currency == primary && it.rule.runsInMonthOf(today) }
            val monthlyTotal = running
                .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.monthlyEquivalent) }

            return RecurrenceSection(
                items = items,
                monthlyTotal = monthlyTotal,
                annualProjection = monthlyTotal.multiply(BigDecimal(MONTHS_PER_YEAR)),
                // Same scope as monthlyTotal, so "N subscriptions - X/month" is coherent.
                activeCount = running.size,
                currency = primary,
            )
        }

        val transfers = sectionFor(TransactionType.TRANSFER)
        // Planned savings: the monthly-equivalent of transfers landing in a
        // savings account, the honest seed of Savings Goals (v2.0).
        val savingsItems = transfers.items.filter {
            accountById[it.rule.transferAccountId]?.type == AccountType.SAVINGS &&
                it.rule.runsInMonthOf(today)
        }
        val savingsCurrency = currencyOverride
            ?: savingsItems.groupingBy { it.rule.currency }.eachCount().maxByOrNull { it.value }?.key
            ?: fallbackCurrency
        val plannedMonthlySavings = savingsItems
            .filter { it.rule.currency == savingsCurrency }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.monthlyEquivalent) }

        return RecurrencesUiState(
            isLoading = false,
            expenses = sectionFor(TransactionType.EXPENSE),
            incomes = sectionFor(TransactionType.INCOME),
            transfers = transfers,
            plannedMonthlySavings = plannedMonthlySavings,
            savingsCurrency = savingsCurrency,
            sort = sortOrder,
            today = today,
        )
    }

    private fun RecurringRule.toItem(
        today: LocalDate,
        account: Account?,
        category: Category?,
        transferAccount: Account?,
    ) = SubscriptionItem(
        rule = this,
        account = account,
        category = category,
        monthlyEquivalent = RecurrenceCalculator.monthlyEquivalent(this) ?: BigDecimal.ZERO,
        nextCharge = RecurrenceCalculator.nextOccurrence(this, nextChargeFloor(today)),
        transferAccount = transferAccount,
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
