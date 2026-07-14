package com.callbackdev.saldo.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.di.DefaultDispatcher
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.feature.transactions.FilteredTotal
import com.callbackdev.saldo.feature.transactions.TransactionDayGroup
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import com.callbackdev.saldo.feature.transactions.buildDayGroups
import com.callbackdev.saldo.feature.transactions.filter.DatePreset
import com.callbackdev.saldo.feature.transactions.filter.TransactionFilterEngine
import com.callbackdev.saldo.feature.transactions.filter.TransactionFilters
import com.callbackdev.saldo.feature.transactions.filteredTotals
import com.callbackdev.saldo.feature.transactions.localDate
import com.callbackdev.saldo.navigation.FilteredTransactionsRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Currency

/** Immutable UI state of the statistics drill-down list. */
data class FilteredTransactionsUiState(
    val isLoading: Boolean = true,
    /** The tapped category's or account's name; null for a pure period drill-down. */
    val title: String? = null,
    /** True when the list shows the uncategorized bucket (title resolved by the screen). */
    val isUncategorized: Boolean = false,
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val days: List<TransactionDayGroup> = emptyList(),
    val totals: List<FilteredTotal> = emptyList(),
    val count: Int = 0,
) {
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()
}

/**
 * Movements behind a tapped chart element: the route's window (and optional
 * category/account) seeds a [TransactionFilters] evaluated by the same engine
 * as the ledger tab. With [FilteredTransactionsRoute.statsScope] set, an extra
 * predicate mirrors the statistics queries (primary currency, excluded and
 * pending skipped, spend-only rows for an account drill-down), so the list and
 * its totals always agree with the tapped figure; the dashboard drill-downs
 * keep the cash view. Loading is windowed in SQL: the local-date bounds are
 * widened by one day per side to cover rows recorded in other offsets, and
 * the engine's per-day match does the exact cut.
 */
@Suppress("LongParameterList") // The route plus the DI graph of the aggregates the list resolves.
@HiltViewModel(assistedFactory = FilteredTransactionsViewModel.Factory::class)
class FilteredTransactionsViewModel @AssistedInject constructor(
    @Assisted private val route: FilteredTransactionsRoute,
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    tagRepository: TagRepository,
    userPreferences: UserPreferencesRepository,
    private val clock: Clock,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: FilteredTransactionsRoute): FilteredTransactionsViewModel
    }

    private val filters = TransactionFilters(
        datePreset = DatePreset.CUSTOM,
        customStart = LocalDate.ofEpochDay(route.startEpochDay),
        customEnd = LocalDate.ofEpochDay(route.endEpochDayExclusive - 1),
        categoryIds = setOfNotNull(route.categoryId),
        accountIds = setOfNotNull(route.accountId),
    )

    val uiState: StateFlow<FilteredTransactionsUiState> = combine(
        transactionRepository.observeTransactionsBetween(
            start = LocalDate.ofEpochDay(route.startEpochDay)
                .minusDays(1).atStartOfDay(clock.zone).toInstant(),
            end = LocalDate.ofEpochDay(route.endEpochDayExclusive)
                .plusDays(1).atStartOfDay(clock.zone).toInstant(),
        ),
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
        tagRepository.observeTagAssignments(),
        userPreferences.primaryCurrencyOverride,
    ) { transactions, accounts, categories, tagAssignments, currencyOverride ->
        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = categories.associateBy { it.id }
        val today = LocalDate.now(clock)
        val currency = primaryCurrency(accounts, currencyOverride)
        // The preset here is always CUSTOM: the week start is unused.
        val compiled = TransactionFilterEngine.compile(filters, today, DayOfWeek.MONDAY)
        val filtered = transactions
            .filter { transaction ->
                matchesStatsScope(transaction, currency) &&
                    compiled.matches(
                        transaction = transaction,
                        localDate = transaction.localDate,
                        tagIds = tagAssignments[transaction.id].orEmpty(),
                    )
            }
            .map { transaction ->
                TransactionListItem(
                    transaction = transaction,
                    account = accountById[transaction.accountId],
                    toAccount = transaction.transferAccountId?.let { accountById[it] },
                    category = transaction.categoryId?.let { categoryById[it] },
                )
            }
        FilteredTransactionsUiState(
            isLoading = false,
            title = route.categoryId?.let { categoryById[it]?.name }
                ?: route.accountId?.let { accountById[it]?.name },
            isUncategorized = route.uncategorizedOnly,
            today = today,
            days = buildDayGroups(filtered),
            totals = filteredTotals(filtered),
            count = filtered.size,
        )
    }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = FilteredTransactionsUiState(),
        )

    /**
     * Mirrors the statistics queries when the route is stats-scoped: only the
     * primary currency, never excluded-from-stats rows, only spend rows
     * (expenses plus refunds) charged to the account itself for an account
     * drill-down, and only uncategorized rows for the uncategorized slice.
     */
    private fun matchesStatsScope(transaction: Transaction, currency: Currency): Boolean {
        if (!route.statsScope) return true
        if (transaction.isExcludedFromStats || transaction.currency != currency) return false
        if (route.uncategorizedOnly && transaction.categoryId != null) return false
        val isRefund = transaction.type == TransactionType.INCOME && transaction.isRefund
        return if (route.accountId != null) {
            transaction.accountId == route.accountId &&
                (transaction.type == TransactionType.EXPENSE || isRefund)
        } else {
            transaction.type == TransactionType.EXPENSE || transaction.type == TransactionType.INCOME
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
