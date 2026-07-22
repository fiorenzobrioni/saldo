package com.callbackdev.saldo.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.SavingsGoalRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.undo.UndoDeleteCoordinator
import com.callbackdev.saldo.core.domain.undo.UndoableDelete
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-shell counterpart of [UndoDeleteCoordinator]: exposes editor deletions
 * to the app-level snackbar host and restores one on "Undo". Movements come
 * back with a fresh id plus their tags (the ledger swipe-delete semantics);
 * budgets go back through the repository's scope-aware write paths (their
 * notification watermarks restart, so a restored budget can notify again);
 * savings goals are re-inserted as they were.
 */
@HiltViewModel
class UndoDeleteViewModel @Inject constructor(
    coordinator: UndoDeleteCoordinator,
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val budgetRepository: BudgetRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
) : ViewModel() {

    val events: Flow<UndoableDelete> = coordinator.events

    private val _undoFailed = Channel<Unit>(Channel.BUFFERED)
    val undoFailed: Flow<Unit> = _undoFailed.receiveAsFlow()

    fun undo(event: UndoableDelete) {
        viewModelScope.launch {
            suspendRunCatching {
                when (event) {
                    is UndoableDelete.Movement -> restoreMovement(event)
                    is UndoableDelete.Budget -> restoreBudget(event.budget)
                    is UndoableDelete.Goal ->
                        savingsGoalRepository.upsert(event.goal.copy(id = 0L))
                }
            }.onFailure { _undoFailed.send(Unit) }
        }
    }

    private suspend fun restoreMovement(event: UndoableDelete.Movement) {
        val newId = transactionRepository.upsert(event.transaction.copy(id = 0L))
        if (event.tagIds.isNotEmpty()) {
            tagRepository.setTagsForTransaction(newId, event.tagIds)
        }
    }

    private suspend fun restoreBudget(budget: Budget) {
        val categoryId = budget.categoryId
        if (categoryId == null) {
            budgetRepository.setOverallBudget(budget.amount, budget.currency)
        } else {
            budgetRepository.upsertCategoryBudget(categoryId, budget.amount, budget.currency)
        }
    }
}
