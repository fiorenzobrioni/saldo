package com.callbackdev.saldo.budget

import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.CheckBudgetThresholdsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive leg of the budget alerts (the daily generation worker is the
 * other): watches the month's spend for every currency that has a budget and
 * re-runs the threshold check when it changes, so a manual expense that
 * crosses 80% or 100% notifies within moments, not on the next worker run.
 *
 * The observed flows are change signals only; the check itself re-reads
 * everything one-shot with a fresh clock. Debounce coalesces write bursts
 * (e.g. a restore), and the per-budget watermarks make the double trigger
 * path harmless. Started once from the application, on the application scope.
 */
@Singleton
class BudgetThresholdWatcher @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val checkBudgetThresholds: CheckBudgetThresholdsUseCase,
    private val notifier: BudgetNotifier,
    private val clock: Clock,
) {

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            budgetRepository.observeBudgets()
                .map { budgets -> budgets.map { it.currency.currencyCode }.distinct().sorted() }
                .distinctUntilChanged()
                .flatMapLatest { currencyCodes ->
                    if (currencyCodes.isEmpty()) return@flatMapLatest emptyFlow()
                    // The window only triggers; a stale one past midnight just
                    // fires a check that recomputes its own fresh window.
                    val windows = DashboardWindows.around(LocalDate.now(clock), clock.zone)
                    combine(currencyCodes.map { code -> spendSignal(windows, code) }) { }
                }
                .debounce(DEBOUNCE_MILLIS)
                .collect {
                    // A transient read/write failure must not kill the watcher;
                    // the next change or the daily worker retries naturally.
                    runCatching { notifier.notify(checkBudgetThresholds()) }
                }
        }
    }

    private fun spendSignal(windows: DashboardWindows, currencyCode: String) = combine(
        transactionRepository.observeStatsSpendTotal(
            windows.monthStart,
            windows.monthEnd,
            Currency.getInstance(currencyCode),
        ),
        transactionRepository.observeCategorySpendTotals(
            windows.monthStart,
            windows.monthEnd,
            Currency.getInstance(currencyCode),
        ),
        ::Pair,
    ).distinctUntilChanged()

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
    }
}
