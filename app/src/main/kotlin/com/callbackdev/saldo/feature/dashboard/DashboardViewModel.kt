package com.callbackdev.saldo.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import com.callbackdev.saldo.feature.transactions.localDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/** Net expense/income of a time window, in the dashboard's primary currency. */
data class PeriodFlow(
    /** Sum of expenses (<= 0). */
    val spend: BigDecimal = BigDecimal.ZERO,
    /** Sum of incomes (>= 0). */
    val income: BigDecimal = BigDecimal.ZERO,
) {
    val net: BigDecimal get() = spend.add(income)
}

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
    val today: PeriodFlow = PeriodFlow(),
    val month: PeriodFlow = PeriodFlow(),
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
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    recurringRuleRepository: RecurringRuleRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        accountRepository.observeAccountsWithBalance(),
        transactionRepository.observeTransactions(),
        categoryRepository.observeCategories(),
        recurringRuleRepository.observeRules(),
        transactionRepository.observePendingTransactions(),
    ) { accounts, transactions, categories, rules, pending ->
        buildState(accounts, transactions, categories, rules, pending.size)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardUiState(date = LocalDate.now(clock)),
    )

    private fun buildState(
        accounts: List<AccountWithBalance>,
        transactions: List<Transaction>,
        categories: List<Category>,
        rules: List<RecurringRule>,
        pendingCount: Int,
    ): DashboardUiState {
        val today = LocalDate.now(clock)
        val active = accounts.filter { !it.account.isArchived }
        val included = active.filter { it.account.isIncludedInTotal }

        // The primary currency is the one shared by most accounts that count in
        // the total; multi-currency conversion is a later feature (VISION).
        val primary = included
            .groupingBy { it.account.currency }
            .eachCount()
            .maxByOrNull { it.value }?.key
            ?: DashboardUiState.fallbackCurrency
        val totalBalance = included
            .filter { it.account.currency == primary }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.balance) }

        val todayFlow = periodFlow(transactions, primary) { it.isEqual(today) }
        val monthFlow = periodFlow(transactions, primary) { it.sameMonthAs(today) }

        // "So far this month" against the same span of last month.
        val monthToDateSpend = spendMagnitude(transactions, primary) {
            it.sameMonthAs(today) && !it.isAfter(today)
        }
        val previousToDate = today.minusMonths(1)
        val previousSpend = spendMagnitude(transactions, primary) {
            it.sameMonthAs(previousToDate) && !it.isAfter(previousToDate)
        }
        val comparison =
            if (previousSpend.signum() > 0) monthToDateSpend.subtract(previousSpend) else null
        val previousReference = previousSpend.takeIf { it.signum() > 0 }
        val spentMore = monthToDateSpend > previousSpend

        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = categories.associateBy { it.id }
        val recent = transactions.take(RECENT_COUNT).map { transaction ->
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
            today = todayFlow,
            month = monthFlow,
            monthVsPreviousToDate = comparison,
            previousMonthSpendToDate = previousReference,
            spentMoreThanLastMonth = spentMore,
            subscriptions = subscriptionsSummary(rules, primary, today),
            pendingCount = pendingCount,
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

        val monthlyTotal = active
            .filter { it.currency == primary }
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
        return SubscriptionsSummary(monthlyTotal, active.size, next)
    }

    private fun periodFlow(
        transactions: List<Transaction>,
        currency: Currency,
        inWindow: (LocalDate) -> Boolean,
    ): PeriodFlow {
        var spend = BigDecimal.ZERO
        var income = BigDecimal.ZERO
        transactions.forEach { transaction ->
            if (transaction.currency != currency) return@forEach
            if (!inWindow(transaction.localDate)) return@forEach
            when (transaction.type) {
                TransactionType.EXPENSE -> spend = spend.add(transaction.amount)
                TransactionType.INCOME -> income = income.add(transaction.amount)
                else -> Unit
            }
        }
        return PeriodFlow(spend = spend, income = income)
    }

    /** Positive magnitude of expenses in the window (expenses are stored negative). */
    private fun spendMagnitude(
        transactions: List<Transaction>,
        currency: Currency,
        inWindow: (LocalDate) -> Boolean,
    ): BigDecimal = transactions
        .filter {
            it.currency == currency &&
                it.type == TransactionType.EXPENSE &&
                inWindow(it.localDate)
        }
        .fold(BigDecimal.ZERO) { acc, transaction -> acc.add(transaction.amount) }
        .negate()

    private fun LocalDate.sameMonthAs(other: LocalDate): Boolean =
        year == other.year && monthValue == other.monthValue

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val RECENT_COUNT = 7
    }
}
