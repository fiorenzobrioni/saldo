package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.DailyBalance
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/**
 * End-of-day total balance of the included accounts for a window of days:
 * `Σ initialBalance + cumulative net effect` of every movement up to each
 * day's end (ADR 3: balances are always computed, never stored). The last
 * point of the series equals the current total balance shown on the
 * dashboard, whose sparkline this feeds.
 *
 * Like [ObserveBalanceHistoryUseCase], the series is a cash figure over
 * today's account set: archiving an account or excluding it from the total
 * rewrites history retroactively, which keeps the sparkline consistent with
 * the headline balance above it.
 */
class ObserveDailyBalanceHistoryUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {

    /** Balances at the end of each of [days] (ascending, contiguous), in [currency]. */
    operator fun invoke(
        currency: Currency,
        days: List<LocalDate>,
    ): Flow<List<DailyBalance>> {
        if (days.isEmpty()) return flowOf(emptyList())
        val first = days.first()
        return combine(
            accountRepository.observeInitialBalanceTotal(currency),
            transactionRepository.observeNetChangeBefore(currency, first),
            transactionRepository.observeDailyNetChanges(currency, first, days.last().plusDays(1)),
        ) { initialTotal, netBefore, changes ->
            val netByDay = changes.associate { it.date to it.net }
            var running = initialTotal.add(netBefore)
            days.map { day ->
                running = running.add(netByDay[day] ?: BigDecimal.ZERO)
                DailyBalance(day, running)
            }
        }
    }
}
