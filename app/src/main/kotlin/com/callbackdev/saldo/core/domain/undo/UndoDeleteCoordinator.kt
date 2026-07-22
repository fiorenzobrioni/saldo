package com.callbackdev.saldo.core.domain.undo

import com.callbackdev.saldo.core.domain.model.Budget as BudgetModel
import com.callbackdev.saldo.core.domain.model.SavingsGoal as SavingsGoalModel
import com.callbackdev.saldo.core.domain.model.Transaction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A deleted entity carrying what undo needs to rebuild it. */
sealed interface UndoableDelete {

    /** A movement deleted from its editor, with the tag ids to re-attach. */
    data class Movement(
        val transaction: Transaction,
        val tagIds: List<Long>,
    ) : UndoableDelete

    /** A deleted budget cap (overall or per category). */
    data class Budget(val budget: BudgetModel) : UndoableDelete

    /** A deleted savings goal (the linked account is untouched by deletion). */
    data class Goal(val goal: SavingsGoalModel) : UndoableDelete
}

/**
 * Hands an entity deleted from its editor over to the app shell, which shows
 * the undo snackbar on whatever screen the editor returns to. A buffered
 * [Channel] because there is exactly one consumer (the app-level snackbar
 * host) and an event must survive the editor closing before the host
 * collects it. Only used for deletions whose undo restores everything; flows
 * with side effects an undo cannot rebuild (recurring rules, categories with
 * reassignment, accounts) keep their confirmation dialogs instead.
 */
@Singleton
class UndoDeleteCoordinator @Inject constructor() {

    private val _events = Channel<UndoableDelete>(Channel.BUFFERED)
    val events: Flow<UndoableDelete> = _events.receiveAsFlow()

    suspend fun publish(event: UndoableDelete) {
        _events.send(event)
    }
}
