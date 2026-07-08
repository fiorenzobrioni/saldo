package com.callbackdev.saldo.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
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
) : ViewModel() {

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeTransactions(),
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
    ) { transactions, accounts, categories ->
        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = categories.associateBy { it.id }
        val items = transactions.map { transaction ->
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
            today = LocalDate.now(clock),
            days = items
                .groupBy { it.transaction.localDate }
                .map { (date, dayItems) ->
                    TransactionDayGroup(date, dayTotals(dayItems), dayItems)
                }
                .sortedByDescending { it.date },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TransactionsUiState(),
    )

    private val _events = Channel<TransactionsEvent>(Channel.BUFFERED)
    val events: Flow<TransactionsEvent> = _events.receiveAsFlow()

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

    /**
     * Net of expenses and incomes per currency; transfers and adjustments move
     * money around but are not spending, so they stay out of the day total.
     */
    private fun dayTotals(items: List<TransactionListItem>): List<DayTotal> = items
        .filter {
            it.transaction.type == TransactionType.EXPENSE ||
                it.transaction.type == TransactionType.INCOME
        }
        .groupBy { it.transaction.currency }
        .map { (currency, dayItems) ->
            DayTotal(
                amount = dayItems.fold(BigDecimal.ZERO) { acc, item ->
                    acc.add(item.transaction.amount)
                },
                currency = currency,
            )
        }
        .sortedBy { it.currency.currencyCode }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
