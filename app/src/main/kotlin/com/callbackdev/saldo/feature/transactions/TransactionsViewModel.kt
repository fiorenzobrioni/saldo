package com.callbackdev.saldo.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.di.DefaultDispatcher
import com.callbackdev.saldo.core.common.prefs.CsvSeparator
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.CarryOverCalculator
import com.callbackdev.saldo.core.domain.usecase.DeleteFilteredTransactionsUseCase
import com.callbackdev.saldo.feature.transactions.export.TransactionsCsvExporter
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
import kotlinx.coroutines.flow.asStateFlow
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
@Suppress("LongParameterList") // Hilt wiring: one dependency per concern.
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val userPreferences: UserPreferencesRepository,
    private val csvExporter: TransactionsCsvExporter,
    private val deleteFilteredTransactions: DeleteFilteredTransactionsUseCase,
    private val clock: Clock,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val filters = MutableStateFlow(TransactionFilters.DEFAULT)

    /**
     * The search text, mirrored synchronously: the field's `value` must not
     * round-trip through the filtered-list combine (which re-filters the
     * whole ledger on a background dispatcher), or fast typing glitches.
     */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

    /** Filters plus the week-start setting, pre-combined to stay within combine's arity. */
    private val filterInputs = combine(
        filters,
        userPreferences.firstDayOfWeek,
        ::Pair,
    )

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeTransactions(),
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
        tagData,
        filterInputs,
    ) { transactions, accounts, categories, tags, (activeFilters, firstDayOfWeek) ->
        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = categories.associateBy { it.id }
        val today = LocalDate.now(clock)
        // Compiled once per pass: this loop runs over the whole ledger on
        // every keystroke of the search field.
        val compiled = TransactionFilterEngine.compile(activeFilters, today, firstDayOfWeek)
        val matching = transactions.filter { transaction ->
            compiled.matches(
                transaction = transaction,
                localDate = transaction.localDate,
                tagIds = tags.assignments[transaction.id].orEmpty(),
            )
        }
        val filtered = matching.map { transaction ->
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
            deletionImpacts = deletionImpacts(matching, accountById),
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

    /** Column separator of the CSV export, persisted across launches. */
    val csvSeparator: StateFlow<CsvSeparator> = userPreferences.csvSeparator
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CsvSeparator.SEMICOLON,
        )

    fun setCsvSeparator(separator: CsvSeparator) {
        viewModelScope.launch { userPreferences.setCsvSeparator(separator) }
    }

    /**
     * Exports the current view (active filters and search applied) as a CSV
     * file and emits its shareable uri; the screen hands it to the Share Sheet.
     */
    fun exportCsv() {
        viewModelScope.launch {
            val items = uiState.value.days.flatMap { it.items }
            if (items.isEmpty()) return@launch
            val result = suspendRunCatching {
                val tagsById = tagRepository.observeTags().first().associateBy { it.id }
                val tagNames = tagRepository.observeTagAssignments().first()
                    .mapValues { (_, ids) -> ids.mapNotNull { tagsById[it]?.name }.sorted() }
                csvExporter.export(
                    fileName = "saldo-export-${LocalDate.now(clock)}.csv",
                    items = items,
                    tagNames = tagNames,
                    separator = csvSeparator.value,
                )
            }
            result
                .onSuccess { uri -> _events.send(TransactionsEvent.CsvExported(uri)) }
                .onFailure { _events.send(TransactionsEvent.CsvExportFailed) }
        }
    }

    /** Replaces the search text, leaving the other filters untouched. */
    fun setQuery(query: String) {
        _searchQuery.value = query
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
        _searchQuery.value = newFilters.query
        filters.value = newFilters
    }

    /** Back to the default view (current month), search included. */
    fun clearFilters() {
        _searchQuery.value = TransactionFilters.DEFAULT.query
        filters.value = TransactionFilters.DEFAULT
    }

    /** Deletes a movement, capturing its tags first so undo can restore them. */
    fun delete(item: TransactionListItem) {
        viewModelScope.launch {
            suspendRunCatching {
                val tagIds = tagRepository.observeTagsForTransaction(item.id).first().map { it.id }
                transactionRepository.delete(item.transaction)
                tagIds
            }
                .onSuccess { tagIds ->
                    _events.send(TransactionsEvent.TransactionDeleted(item.transaction, tagIds))
                }
                .onFailure { _events.send(TransactionsEvent.WriteFailed) }
        }
    }

    /** Re-inserts a deleted movement (new id) and re-attaches its tags. */
    fun undoDelete(event: TransactionsEvent.TransactionDeleted) {
        viewModelScope.launch {
            suspendRunCatching {
                val newId = transactionRepository.upsert(event.transaction.copy(id = 0L))
                if (event.tagIds.isNotEmpty()) {
                    tagRepository.setTagsForTransaction(newId, event.tagIds)
                }
            }.onFailure { _events.send(TransactionsEvent.WriteFailed) }
        }
    }

    /**
     * Deletes every movement in the current filtered view. When
     * [preserveBalances] is set, each affected account keeps its current balance
     * via a carry-over adjustment (labelled [carryOverDescription]); otherwise
     * balances recompute. Captures each movement's tags first so undo can rebuild
     * it, and reports the created carry-overs so undo can remove them.
     */
    fun deleteFiltered(preserveBalances: Boolean, carryOverDescription: String) {
        viewModelScope.launch {
            val transactions = uiState.value.days
                .flatMap { it.items }
                .map { it.transaction }
            if (transactions.isEmpty()) return@launch
            suspendRunCatching {
                val assignments = tagRepository.observeTagAssignments().first()
                val restorable = transactions.map { it to assignments[it.id].orEmpty().toList() }
                val carryOverIds = deleteFilteredTransactions(
                    transactions = transactions,
                    preserveBalances = preserveBalances,
                    carryOverDescription = carryOverDescription,
                )
                restorable to carryOverIds
            }
                .onSuccess { (restorable, carryOverIds) ->
                    _events.send(
                        TransactionsEvent.FilteredDeleted(
                            restorable = restorable,
                            carryOverIds = carryOverIds,
                            count = restorable.size,
                        ),
                    )
                }
                .onFailure { _events.send(TransactionsEvent.WriteFailed) }
        }
    }

    /** Removes the carry-over adjustments, then re-inserts the deleted movements and their tags. */
    fun undoFilteredDelete(event: TransactionsEvent.FilteredDeleted) {
        viewModelScope.launch {
            suspendRunCatching {
                if (event.carryOverIds.isNotEmpty()) {
                    transactionRepository.deleteByIds(event.carryOverIds)
                }
                event.restorable.forEach { (transaction, tagIds) ->
                    val newId = transactionRepository.upsert(transaction.copy(id = 0L))
                    if (tagIds.isNotEmpty()) {
                        tagRepository.setTagsForTransaction(newId, tagIds)
                    }
                }
            }.onFailure { _events.send(TransactionsEvent.WriteFailed) }
        }
    }

    /**
     * Per-account balance change if [matching] were deleted without a carry-over:
     * the net effect removed from each balance, negated (deleting expenses raises
     * the balance). Skips accounts with a zero net; drives the delete sheet preview.
     */
    private fun deletionImpacts(
        matching: List<Transaction>,
        accountById: Map<Long, Account>,
    ): List<AccountBalanceImpact> =
        CarryOverCalculator.netByAccount(matching).mapNotNull { (accountId, net) ->
            if (net.signum() == 0) return@mapNotNull null
            accountById[accountId]?.let { AccountBalanceImpact(it, net.negate()) }
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
