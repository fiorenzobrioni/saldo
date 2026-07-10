package com.callbackdev.saldo.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/** The soonest upcoming subscription charge, for the dashboard card preview. */
data class NextSubscription(
    val name: String,
    /** Positive charge magnitude in [currency]. */
    val amount: BigDecimal,
    val currency: Currency,
    val date: LocalDate,
)

/** The subscriptions summary shown on the dashboard card. */
data class SubscriptionsSummary(
    val monthlyTotal: BigDecimal = BigDecimal.ZERO,
    val activeCount: Int = 0,
    val next: NextSubscription? = null,
) {
    val hasSubscriptions: Boolean get() = activeCount > 0
}

/** Immutable UI state for the "Today" dashboard. */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val hasAccounts: Boolean = false,
    val primaryCurrency: Currency = fallbackCurrency,
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    /** Active (non-archived) accounts with balances, for the expandable detail. */
    val accounts: List<AccountWithBalance> = emptyList(),
    val today: PeriodTotals = PeriodTotals(),
    val month: PeriodTotals = PeriodTotals(),
    /**
     * Signed difference between what has been spent so far this month and by the
     * same day last month (positive = more spent this month); null when there is
     * no baseline last month.
     */
    val monthVsPreviousToDate: BigDecimal? = null,
    /**
     * Positive magnitude of what had been spent by this same day last month, the
     * reference figure shown under the period cards; null when there is nothing
     * to compare against.
     */
    val previousMonthSpendToDate: BigDecimal? = null,
    /** Whether more has been spent so far this month than by this day last month. */
    val spentMoreThanLastMonth: Boolean = false,
    val subscriptions: SubscriptionsSummary = SubscriptionsSummary(),
    /** Number of recurring movements awaiting confirmation. */
    val pendingCount: Int = 0,
    val recent: List<TransactionListItem> = emptyList(),
    val date: LocalDate = LocalDate.ofEpochDay(0),
) {
    companion object {
        val fallbackCurrency: Currency =
            runCatching { Currency.getInstance(Locale.getDefault()) }.getOrNull()
                ?: Currency.getInstance("EUR")
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Everything the dashboard combines besides the accounts themselves. */
    private data class Sources(
        val totals: DashboardTotals,
        val recent: List<Transaction>,
        val categories: List<Category>,
        val rules: List<RecurringRule>,
        val pendingCount: Int,
    )

    /**
     * The account list drives the primary currency and the aggregate windows;
     * every figure is then computed by the database ([DashboardWindows],
     * [TransactionRepository.observeDashboardTotals]) instead of loading the
     * ledger in memory.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = accountRepository.observeAccountsWithBalance()
        .flatMapLatest { accounts ->
            val today = LocalDate.now(clock)
            val primary = primaryCurrency(accounts)
            combine(
                transactionRepository.observeDashboardTotals(
                    windows = DashboardWindows.around(today, clock.zone),
                    currency = primary,
                ),
                transactionRepository.observeRecentTransactions(RECENT_COUNT),
                categoryRepository.observeCategories(),
                recurringRuleRepository.observeRules(),
                transactionRepository.observePendingTransactions(),
            ) { totals, recent, categories, rules, pending ->
                buildState(
                    accounts = accounts,
                    primary = primary,
                    today = today,
                    sources = Sources(totals, recent, categories, rules, pending.size),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DashboardUiState(date = LocalDate.now(clock)),
        )

    /**
     * The primary currency is the one shared by most accounts that count in the
     * total; multi-currency conversion is a later feature (VISION).
     */
    private fun primaryCurrency(accounts: List<AccountWithBalance>): Currency = accounts
        .filter { !it.account.isArchived && it.account.isIncludedInTotal }
        .groupingBy { it.account.currency }
        .eachCount()
        .maxByOrNull { it.value }?.key
        ?: DashboardUiState.fallbackCurrency

    private fun buildState(
        accounts: List<AccountWithBalance>,
        primary: Currency,
        today: LocalDate,
        sources: Sources,
    ): DashboardUiState {
        val active = accounts.filter { !it.account.isArchived }
        val totalBalance = active
            .filter { it.account.isIncludedInTotal && it.account.currency == primary }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.balance) }

        val totals = sources.totals
        val previousReference = totals.previousMonthToDateSpend.takeIf { it.signum() > 0 }
        val comparison = previousReference?.let { totals.monthToDateSpend.subtract(it) }
        // Meaningful only when a baseline exists, like the two figures above.
        val spentMore = previousReference != null && totals.monthToDateSpend > previousReference

        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = sources.categories.associateBy { it.id }
        val recent = sources.recent.map { transaction ->
            TransactionListItem(
                transaction = transaction,
                account = accountById[transaction.accountId],
                toAccount = transaction.transferAccountId?.let { accountById[it] },
                category = transaction.categoryId?.let { categoryById[it] },
            )
        }

        return DashboardUiState(
            isLoading = false,
            hasAccounts = active.isNotEmpty(),
            primaryCurrency = primary,
            totalBalance = totalBalance,
            accounts = active,
            today = totals.today,
            month = totals.month,
            monthVsPreviousToDate = comparison,
            previousMonthSpendToDate = previousReference,
            spentMoreThanLastMonth = spentMore,
            subscriptions = subscriptionsSummary(sources.rules, primary, today),
            pendingCount = sources.pendingCount,
            recent = recent,
            date = today,
        )
    }

    /** Active recurring expenses: normalized monthly total, count, and next charge. */
    private fun subscriptionsSummary(
        rules: List<RecurringRule>,
        primary: Currency,
        today: LocalDate,
    ): SubscriptionsSummary {
        val active = rules.filter {
            it.type == TransactionType.EXPENSE && (it.endDate == null || it.endDate >= today)
        }
        if (active.isEmpty()) return SubscriptionsSummary()

        val primaryRules = active.filter { it.currency == primary }
        val monthlyTotal = primaryRules
            .fold(BigDecimal.ZERO) { acc, rule ->
                acc.add(RecurrenceCalculator.monthlyEquivalent(rule) ?: BigDecimal.ZERO)
            }
        val next = active
            .mapNotNull { rule ->
                val amount = rule.amount ?: return@mapNotNull null
                val floor = rule.lastGeneratedDate?.plusDays(1)?.takeIf { it > today } ?: today
                RecurrenceCalculator.nextOccurrence(rule, floor)?.let { date ->
                    NextSubscription(rule.name, amount, rule.currency, date)
                }
            }
            .minByOrNull { it.date }
        // The count shares the monthlyTotal's currency scope, so the card reads coherently.
        return SubscriptionsSummary(monthlyTotal, primaryRules.size, next)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val RECENT_COUNT = 7
    }
}
