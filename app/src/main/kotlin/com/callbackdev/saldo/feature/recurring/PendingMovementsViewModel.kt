package com.callbackdev.saldo.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** A pending recurring movement resolved against its rule and account for display. */
data class PendingItem(
    val transaction: Transaction,
    val rule: RecurringRule?,
    val account: Account?,
) {
    val id: Long get() = transaction.id
    val isVariable: Boolean get() = rule?.isVariableAmount == true
    val name: String get() = transaction.description ?: rule?.name.orEmpty()

    /** Positive charge magnitude; zero for a variable-amount movement awaiting an amount. */
    val magnitude: BigDecimal get() = transaction.amount.abs()

    val date: LocalDate get() = transaction.timestamp.atOffset(transaction.zoneOffset).toLocalDate()
}

/** Immutable UI state for the pending-confirmation screen. */
data class PendingMovementsUiState(
    val isLoading: Boolean = true,
    val items: List<PendingItem> = emptyList(),
    val today: LocalDate = LocalDate.ofEpochDay(0),
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

/**
 * Drives the confirmation of pending recurring movements (confirm mode / variable
 * amount): the user confirms the amount, or skips (discards) the movement.
 */
@HiltViewModel
class PendingMovementsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    recurringRuleRepository: RecurringRuleRepository,
    accountRepository: AccountRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<PendingMovementsUiState> = combine(
        transactionRepository.observePendingTransactions(),
        recurringRuleRepository.observeRules(),
        accountRepository.observeAccountsWithBalance(),
    ) { pending, rules, accounts ->
        val ruleById = rules.associateBy { it.id }
        val accountById = accounts.associate { it.account.id to it.account }
        PendingMovementsUiState(
            isLoading = false,
            items = pending.map { transaction ->
                PendingItem(
                    transaction = transaction,
                    rule = transaction.recurringRuleId?.let { ruleById[it] },
                    account = accountById[transaction.accountId],
                )
            },
            today = LocalDate.now(clock),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PendingMovementsUiState(today = LocalDate.now(clock)),
    )

    /** Confirms [transaction] with [magnitude], applying the sign and clearing the pending flag. */
    fun confirm(transaction: Transaction, magnitude: BigDecimal) {
        val signed = if (transaction.type == TransactionType.EXPENSE) magnitude.negate() else magnitude
        viewModelScope.launch {
            transactionRepository.upsert(transaction.copy(amount = signed, isPending = false))
        }
    }

    /** Discards a pending movement the user does not want recorded. */
    fun skip(transaction: Transaction) {
        viewModelScope.launch { transactionRepository.delete(transaction) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
