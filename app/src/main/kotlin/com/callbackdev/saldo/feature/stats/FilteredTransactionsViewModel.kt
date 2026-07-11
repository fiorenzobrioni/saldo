package com.callbackdev.saldo.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.di.DefaultDispatcher
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
import java.time.LocalDate

/** Immutable UI state of the statistics drill-down list. */
data class FilteredTransactionsUiState(
    val isLoading: Boolean = true,
    /** The tapped category's or account's name; null for a pure period drill-down. */
    val title: String? = null,
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
 * as the ledger tab, so both screens always agree on what matches.
 */
@Suppress("LongParameterList") // The route plus the DI graph of the aggregates the list resolves.
@HiltViewModel(assistedFactory = FilteredTransactionsViewModel.Factory::class)
class FilteredTransactionsViewModel @AssistedInject constructor(
    @Assisted private val route: FilteredTransactionsRoute,
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    tagRepository: TagRepository,
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
        transactionRepository.observeTransactions(),
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
        tagRepository.observeTagAssignments(),
    ) { transactions, accounts, categories, tagAssignments ->
        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = categories.associateBy { it.id }
        val today = LocalDate.now(clock)
        val filtered = transactions
            .filter { transaction ->
                TransactionFilterEngine.matches(
                    transaction = transaction,
                    localDate = transaction.localDate,
                    tagIds = tagAssignments[transaction.id].orEmpty(),
                    filters = filters,
                    today = today,
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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
