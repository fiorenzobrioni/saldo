package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.DailyBalance
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.core.domain.rates.RateTable
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
 *
 * With conversion on (ADR 40) the same walk runs once per foreign currency of
 * the included accounts, and each day's foreign balance converts at the rate
 * in force on that day: a stock is valued at the rate of the day it refers
 * to, so the past points stay put while the head follows the latest rate. A
 * currency without rates contributes nothing, exactly like before the
 * feature.
 */
class ObserveDailyBalanceHistoryUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {

    /**
     * Balances at the end of each of [days] (ascending, contiguous), in
     * [currency], plus the converted balances of the included accounts in
     * [foreignCurrencies] when [rates] can convert them.
     */
    operator fun invoke(
        currency: Currency,
        days: List<LocalDate>,
        foreignCurrencies: List<Currency> = emptyList(),
        rates: RateTable = RateTable.EMPTY,
    ): Flow<List<DailyBalance>> {
        if (days.isEmpty()) return flowOf(emptyList())
        val primary = seriesIn(currency, days)
        val foreign = foreignCurrencies.distinct().filter { it != currency }
        if (foreign.isEmpty()) return primary
        val series = listOf(primary) + foreign.map { seriesIn(it, days) }
        return combine(series) { walked ->
            days.mapIndexed { index, day ->
                var total = walked[0][index].balance
                foreign.forEachIndexed { c, foreignCurrency ->
                    val balance = walked[c + 1][index].balance
                    val estimate =
                        CurrencyConverter.convertOn(balance, foreignCurrency, currency, day, rates)
                    if (estimate != null) total = total.add(estimate.amount)
                }
                DailyBalance(day, total)
            }
        }
    }

    /** The plain single-currency walk the pre-conversion dashboard ran. */
    private fun seriesIn(
        currency: Currency,
        days: List<LocalDate>,
    ): Flow<List<DailyBalance>> {
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
