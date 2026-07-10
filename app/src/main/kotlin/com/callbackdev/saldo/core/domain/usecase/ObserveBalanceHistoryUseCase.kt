package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.MonthlyBalance
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Currency
import javax.inject.Inject

/**
 * End-of-month total balance of the included accounts for a window of months:
 * `Σ initialBalance + cumulative net effect` of every movement up to each
 * month's end (ADR 3: balances are always computed, never stored). The last
 * point of the series equals the current total balance shown on the dashboard.
 *
 * Like the dashboard total, the series is a cash figure over today's account
 * set: archiving an account or excluding it from the total rewrites history
 * retroactively, which keeps the two figures consistent with each other.
 */
class ObserveBalanceHistoryUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {

    /** Balances at the end of each of [months] (ascending), in [currency]. */
    operator fun invoke(
        currency: Currency,
        months: List<YearMonth>,
    ): Flow<List<MonthlyBalance>> = combine(
        accountRepository.observeInitialBalanceTotal(currency),
        transactionRepository.observeMonthlyNetChanges(currency),
    ) { initialTotal, changes ->
        if (months.isEmpty()) return@combine emptyList()
        val netByMonth = changes.associate { it.month to it.net }
        val first = months.first()
        var running = changes
            .filter { it.month < first }
            .fold(initialTotal) { acc, change -> acc.add(change.net) }
        months.map { month ->
            running = running.add(netByMonth[month] ?: BigDecimal.ZERO)
            MonthlyBalance(month, running)
        }
    }
}
