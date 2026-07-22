package com.callbackdev.saldo.feature.transactions

import com.callbackdev.saldo.core.domain.model.Transaction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a movement deleted from the editor over to the app shell, which shows
 * the undo snackbar on whatever screen the editor returns to (ledger,
 * dashboard, stats). A buffered [Channel] because there is exactly one
 * consumer (the app-level snackbar host) and an event must survive the editor
 * closing before the host collects it.
 */
@Singleton
class TransactionUndoCoordinator @Inject constructor() {

    private val _events = Channel<DeletedTransaction>(Channel.BUFFERED)
    val events: Flow<DeletedTransaction> = _events.receiveAsFlow()

    suspend fun publish(event: DeletedTransaction) {
        _events.send(event)
    }

    /** A deleted movement with the tag ids undo needs to rebuild it. */
    data class DeletedTransaction(
        val transaction: Transaction,
        val tagIds: List<Long>,
    )
}
