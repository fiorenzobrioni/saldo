package com.callbackdev.saldo.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.di.DefaultDispatcher
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.feature.transactions.filter.DatePreset
import com.callbackdev.saldo.feature.transactions.filter.TransactionFilterEngine
import com.callbackdev.saldo.feature.transactions.filter.TransactionFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val clock: Clock,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val filters = MutableStateFlow(TransactionFilters.NONE)

    /** Tags and their assignments, pre-combined to stay within combine's arity. */
    private data class TagData(
        val tags: List<Tag>,
        val assignments: Map<Long, Set<Long>>,
    )

    private val tagData = combine(
        tagRepository.observeTags(),
        tagRepository.observeTagAssignments(),
        ::TagData,
    )

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeTransactions(),
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
        tagData,
        filters,
    ) { transactions, accounts, categories, tags, activeFilters ->
        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = categories.associateBy { it.id }
        val today = LocalDate.now(clock)
        val filtered = transactions
            .filter { transaction ->
                TransactionFilterEngine.matches(
                    transaction = transaction,
                    localDate = transaction.localDate,
                    tagIds = tags.assignments[transaction.id].orEmpty(),
                    filters = activeFilters,
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
        TransactionsUiState(
            isLoading = false,
            hasAccounts = accounts.any { !it.account.isArchived },
            hasAnyTransactions = transactions.isNotEmpty(),
            today = today,
            filters = activeFilters,
            days = buildDayGroups(filtered),
            filteredTotals = filteredTotals(filtered),
            filteredCount = filtered.size,
            filterCategories = categories,
            filterAccounts = accounts.map { it.account }.sortedBy { it.isArchived },
            filterTags = tags.tags,
        )
    }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TransactionsUiState(),
        )

    private val _events = Channel<TransactionsEvent>(Channel.BUFFERED)
    val events: Flow<TransactionsEvent> = _events.receiveAsFlow()

    /** Replaces the search text, leaving the other filters untouched. */
    fun setQuery(query: String) {
        filters.update { it.copy(query = query) }
    }

    /** Selects a date preset chip; custom bounds only survive on [DatePreset.CUSTOM]. */
    fun setDatePreset(preset: DatePreset) {
        filters.update {
            if (preset == DatePreset.CUSTOM) {
                it.copy(datePreset = preset)
            } else {
                it.copy(datePreset = preset, customStart = null, customEnd = null)
            }
        }
    }

    /** Applies an explicit date range picked by the user. */
    fun setCustomRange(start: LocalDate, end: LocalDate) {
        filters.update { it.copy(datePreset = DatePreset.CUSTOM, customStart = start, customEnd = end) }
    }

    /** Replaces the whole filter state (the sheet commits its edited copy here). */
    fun applyFilters(newFilters: TransactionFilters) {
        filters.value = newFilters
    }

    /** Back to the unfiltered ledger, search included. */
    fun clearFilters() {
        filters.value = TransactionFilters.NONE
    }

    /** Deletes a movement, capturing its tags first so undo can restore them. */
    fun delete(item: TransactionListItem) {
        viewModelScope.launch {
            val tagIds = tagRepository.observeTagsForTransaction(item.id).first().map { it.id }
            transactionRepository.delete(item.transaction)
            _events.send(TransactionsEvent.TransactionDeleted(item.transaction, tagIds))
        }
    }

    /** Re-inserts a deleted movement (new id) and re-attaches its tags. */
    fun undoDelete(event: TransactionsEvent.TransactionDeleted) {
        viewModelScope.launch {
            val newId = transactionRepository.upsert(event.transaction.copy(id = 0L))
            if (event.tagIds.isNotEmpty()) {
                tagRepository.setTagsForTransaction(newId, event.tagIds)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
