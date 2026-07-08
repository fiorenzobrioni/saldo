package com.callbackdev.saldo.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
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
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        accountRepository.observeAccountsWithBalance(),
        transactionRepository.observeTransactions(),
        categoryRepository.observeCategories(),
    ) { accounts, transactions, categories ->
        buildState(accounts, transactions, categories)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardUiState(date = LocalDate.now(clock)),
    )

    private fun buildState(
        accounts: List<AccountWithBalance>,
        transactions: List<Transaction>,
        categories: List<Category>,
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
            recent = recent,
            date = today,
        )
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
