package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.CounterpartyAmount
import com.callbackdev.saldo.core.domain.model.CounterpartyBalance
import com.callbackdev.saldo.core.domain.model.CounterpartyLedger
import com.callbackdev.saldo.core.domain.model.CounterpartyTotal
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.transaction.CounterpartyNames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.util.Currency
import javax.inject.Inject

/**
 * Builds the "who owes whom" view out of the movements that carry a
 * counterparty (ADR 34). There is no register of loans: the balance per person
 * is the signed sum of their movements, which is why a partial repayment needs
 * no code of its own.
 *
 * The domain work this use case exists for is the merging: the database groups
 * by the exact stored spelling, and "Luca" typed once with a capital and once
 * without would otherwise read as two people owing half each
 * ([CounterpartyNames]). Currencies are never merged - they do not add up - so a
 * person can hold one position per currency, and only the primary-currency ones
 * feed the two headline totals.
 */
class ObserveCounterpartyBalancesUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferencesRepository,
) {

    operator fun invoke(): Flow<CounterpartyLedger> = combine(
        transactionRepository.observeCounterpartyTotals(),
        accountRepository.observeAccountsWithBalance(),
        userPreferences.primaryCurrencyOverride,
    ) { totals, accounts, override ->
        ledgerOf(totals, primaryCurrency(accounts, override))
    }

    private fun ledgerOf(totals: List<CounterpartyTotal>, currency: Currency): CounterpartyLedger {
        val entries = totals
            .groupBy { CounterpartyNames.key(it.name) }
            .values
            .map { group -> balanceOf(group, currency) }
            .sortedWith(entryOrder)

        // Only the primary currency adds up into the two headline figures; the
        // rest is surfaced as a notice instead of being silently converted.
        var owedToYou = BigDecimal.ZERO
        var youOwe = BigDecimal.ZERO
        entries.forEach { entry ->
            val amount = entry.amountIn(currency) ?: return@forEach
            when {
                amount.signum() < 0 -> owedToYou = owedToYou.add(amount.negate())
                amount.signum() > 0 -> youOwe = youOwe.add(amount)
            }
        }
        return CounterpartyLedger(
            entries = entries,
            currency = currency,
            owedToYou = owedToYou,
            youOwe = youOwe,
            hasOtherCurrencies = entries.any { entry ->
                entry.otherAmounts(currency).any { it.amount.signum() != 0 }
            },
        )
    }

    /** One person: the spellings merged, one signed position per currency. */
    private fun balanceOf(
        group: List<CounterpartyTotal>,
        currency: Currency,
    ): CounterpartyBalance {
        val amounts = group
            .groupBy { it.currency }
            .map { (rowCurrency, rows) ->
                CounterpartyAmount(
                    currency = rowCurrency,
                    amount = rows.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.total) },
                )
            }
            .sortedWith(
                compareByDescending<CounterpartyAmount> { it.currency == currency }
                    .thenBy { it.currency.currencyCode },
            )
        return CounterpartyBalance(
            // The spelling of the most recent movement wins: correcting a typo
            // by writing the name properly once renames the person everywhere.
            name = group.maxWith(latestFirst).name,
            amounts = amounts,
            movementCount = group.sumOf { it.count },
            lastActivity = group.maxOf { it.lastActivity },
        )
    }

    private companion object {
        /** Latest activity wins, ties broken on the spelling for determinism. */
        val latestFirst: Comparator<CounterpartyTotal> =
            compareBy<CounterpartyTotal> { it.lastActivity }.thenBy { it.name }

        /**
         * Open positions first, most recent activity first within each half:
         * what is still owed is what the screen is for, and settled people stay
         * available (with their history) without crowding the top.
         */
        val entryOrder: Comparator<CounterpartyBalance> =
            compareBy<CounterpartyBalance> { it.isSettled }
                .thenByDescending { it.lastActivity }
                .thenBy { it.name }
    }
}
