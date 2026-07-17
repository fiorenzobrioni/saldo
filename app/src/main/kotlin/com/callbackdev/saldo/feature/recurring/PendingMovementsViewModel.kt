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
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/** A pending recurring movement resolved against its rule and account for display. */
data class PendingItem(
    val transaction: Transaction,
    val rule: RecurringRule?,
    val account: Account?,
    /** Destination account of a pending transfer; null for expense/income movements. */
    val transferAccount: Account? = null,
) {
    val id: Long get() = transaction.id
    val isVariable: Boolean get() = rule?.isVariableAmount == true
    val name: String get() = transaction.description ?: rule?.name.orEmpty()

    val isTransfer: Boolean get() = transaction.type == TransactionType.TRANSFER

    /**
     * A transfer whose legs hold different currencies: the received amount cannot
     * be fixed up front, so it is entered here at confirmation (PLANNING ADR 24).
     */
    val isCrossCurrencyTransfer: Boolean
        get() = isTransfer && transaction.transferCurrency != null &&
            transaction.transferCurrency != transaction.currency

    /** True when the user must still type an amount before this can be recorded. */
    val needsAmountEntry: Boolean get() = isVariable || isCrossCurrencyTransfer

    /** Currency the confirmation amount is entered in (the destination leg for a transfer). */
    val entryCurrency: Currency
        get() = if (isTransfer) transaction.transferCurrency ?: transaction.currency else transaction.currency

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
                    transferAccount = transaction.transferAccountId?.let { accountById[it] },
                )
            },
            today = LocalDate.now(clock),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PendingMovementsUiState(today = LocalDate.now(clock)),
    )

    private val _events = Channel<PendingMovementsEvent>(Channel.BUFFERED)
    val events: Flow<PendingMovementsEvent> = _events.receiveAsFlow()

    /** Confirms [transaction] with [magnitude], applying the sign and clearing the pending flag. */
    fun confirm(transaction: Transaction, magnitude: BigDecimal) {
        val isCrossCurrencyTransfer = transaction.type == TransactionType.TRANSFER &&
            transaction.transferCurrency != null &&
            transaction.transferCurrency != transaction.currency
        val updated = when {
            transaction.type != TransactionType.TRANSFER -> {
                val signed =
                    if (transaction.type == TransactionType.EXPENSE) magnitude.negate() else magnitude
                transaction.copy(amount = signed, isPending = false)
            }
            // Cross-currency: the magnitude is the received (destination) amount;
            // the source leg was fixed at generation and stays as is.
            isCrossCurrencyTransfer ->
                transaction.copy(transferAmount = magnitude, isPending = false)
            // Same-currency: both legs move by the confirmed magnitude.
            else ->
                transaction.copy(amount = magnitude.negate(), transferAmount = magnitude, isPending = false)
        }
        viewModelScope.launch {
            suspendRunCatching { transactionRepository.upsert(updated) }
                .onFailure { _events.send(PendingMovementsEvent.WriteFailed) }
        }
    }

    /** Discards a pending movement the user does not want recorded. */
    fun skip(transaction: Transaction) {
        viewModelScope.launch {
            suspendRunCatching { transactionRepository.delete(transaction) }
                .onFailure { _events.send(PendingMovementsEvent.WriteFailed) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** One-shot outcomes surfaced as snackbars. */
sealed interface PendingMovementsEvent {
    /** A write failed: the movement is still pending, let the user retry. */
    data object WriteFailed : PendingMovementsEvent
}
