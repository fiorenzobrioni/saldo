package com.callbackdev.saldo.feature.counterparties

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.CounterpartyBalance
import com.callbackdev.saldo.core.domain.model.CounterpartyLedger
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.usecase.ObserveCounterpartyBalancesUseCase
import com.callbackdev.saldo.navigation.FilteredTransactionsRoute
import com.callbackdev.saldo.navigation.TransactionEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Immutable UI state of the credits and debts screen. */
data class CounterpartiesUiState(
    val isLoading: Boolean = true,
    val ledger: CounterpartyLedger = CounterpartyLedger(),
) {
    val isEmpty: Boolean get() = !isLoading && ledger.isEmpty
}

/**
 * Credits and debts toward people (ADR 34). A read-only aggregation over the
 * movements that carry a counterparty: nothing is written from here, and the
 * two actions a row offers (open its movements, record a repayment) are both
 * navigation.
 */
@HiltViewModel
class CounterpartiesViewModel @Inject constructor(
    observeCounterpartyBalances: ObserveCounterpartyBalancesUseCase,
) : ViewModel() {

    val uiState: StateFlow<CounterpartiesUiState> = observeCounterpartyBalances()
        .map { ledger -> CounterpartiesUiState(isLoading = false, ledger = ledger) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CounterpartiesUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The person's movements, whole history, through the shared drill-down. */
internal fun drillDownRoute(entry: CounterpartyBalance): FilteredTransactionsRoute =
    FilteredTransactionsRoute(counterparty = entry.name)

/**
 * The editor route that records a repayment for [entry]: the opposite direction
 * of the open position, its residual amount, the same person, the loan section
 * already on. Null when nothing is open, which is when the action is not
 * offered at all.
 *
 * The position taken is the first open one, i.e. the primary currency's when
 * there is one (the amounts come ordered that way). Prefilling an amount is a
 * starting point, never a write: the real figure and date are the user's, and a
 * repayment that differs simply leaves the rest open.
 */
internal fun settlementRoute(entry: CounterpartyBalance): TransactionEditorRoute? {
    val open = entry.amounts.firstOrNull { it.amount.signum() != 0 } ?: return null
    // They owe you (negative): the money comes back in. You owe them: it goes out.
    val type = if (open.amount.signum() < 0) TransactionType.INCOME else TransactionType.EXPENSE
    return TransactionEditorRoute(
        initialTypeName = type.name,
        initialCounterparty = entry.name,
        initialAmountInput = open.amount.abs().toPlainString(),
    )
}
