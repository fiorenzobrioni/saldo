package com.callbackdev.saldo.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-shell counterpart of [TransactionUndoCoordinator]: exposes the deleted
 * movements to the app-level snackbar host and restores one on "Undo" with
 * the same semantics as the ledger's swipe-delete (re-insert with a fresh id,
 * then re-attach the tags).
 */
@HiltViewModel
class TransactionUndoViewModel @Inject constructor(
    coordinator: TransactionUndoCoordinator,
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    val events: Flow<TransactionUndoCoordinator.DeletedTransaction> = coordinator.events

    private val _undoFailed = Channel<Unit>(Channel.BUFFERED)
    val undoFailed: Flow<Unit> = _undoFailed.receiveAsFlow()

    fun undo(event: TransactionUndoCoordinator.DeletedTransaction) {
        viewModelScope.launch {
            suspendRunCatching {
                val newId = transactionRepository.upsert(event.transaction.copy(id = 0L))
                if (event.tagIds.isNotEmpty()) {
                    tagRepository.setTagsForTransaction(newId, event.tagIds)
                }
            }.onFailure { _undoFailed.send(Unit) }
        }
    }
}
