package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.DailyBalance
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/**
 * End-of-day balance of one account for a window of days: `initialBalance +
 * cumulative net effect` of its own movements up to each day's end (ADR 3:
 * balances are always computed, never stored). The last point equals the
 * account's balance as of that day, so the account detail sparkline ends on
 * the figure shown above it.
 *
 * The per-account twin of [ObserveDailyBalanceHistoryUseCase], without the
 * inclusion and currency filters: an account has one currency, and its own
 * history is worth showing whether or not it counts toward the total. No
 * conversion either: the figure is in the account's currency, the one its
 * movements are written in.
 */
class ObserveAccountBalanceHistoryUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
) {

    /** Balances at the end of each of [days] (ascending, contiguous), in the account's currency. */
    operator fun invoke(account: Account, days: List<LocalDate>): Flow<List<DailyBalance>> {
        if (days.isEmpty()) return flowOf(emptyList())
        val first = days.first()
        return combine(
            accountRepository.observeNetChangeBefore(account.id, account.currency, first),
            accountRepository.observeDailyNetChanges(
                accountId = account.id,
                currency = account.currency,
                start = first,
                endExclusive = days.last().plusDays(1),
            ),
        ) { netBefore, changes ->
            val netByDay = changes.associate { it.date to it.net }
            var running = account.initialBalance.add(netBefore)
            days.map { day ->
                running = running.add(netByDay[day] ?: BigDecimal.ZERO)
                DailyBalance(day, running)
            }
        }
    }
}
