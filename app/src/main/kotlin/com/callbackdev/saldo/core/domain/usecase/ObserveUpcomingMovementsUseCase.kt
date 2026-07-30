package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.UpcomingLedger
import com.callbackdev.saldo.core.domain.model.UpcomingMovement
import com.callbackdev.saldo.core.domain.model.UpcomingOrigin
import com.callbackdev.saldo.core.domain.model.localDate
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.rates.ConversionState
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.core.domain.rates.RateTable
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/**
 * What is coming: confirmed movements dated in the future and occurrences still
 * to confirm, in one list ordered by date (ADR 36).
 *
 * The two live in different states in the ledger - a future movement is a full
 * member of it that no window scoped to today can see, a pending one is in no
 * figure at all - and that is exactly why they belong in the same list. From
 * the user's side there is one question, "what is going to happen", and it
 * deserves one answer in one place.
 *
 * A pending occurrence dated in the past stays in the list: it is still waiting
 * for an answer, and hiding it because its day has gone by is how a
 * confirmation queue silently grows.
 */
class ObserveUpcomingMovementsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferencesRepository,
    private val observeConversionState: ObserveConversionStateUseCase,
    private val clock: Clock,
) {

    /**
     * The upcoming ledger in the primary currency, from tomorrow on. The totals
     * count only that currency and only expenses and incomes;
     * [UpcomingLedger.hasOtherCurrencies] flags what they leave out.
     */
    operator fun invoke(today: LocalDate = LocalDate.now(clock)): Flow<UpcomingLedger> = combine(
        movements(today),
        accountRepository.observeAccountsWithBalance(),
        userPreferences.primaryCurrencyOverride,
        observeConversionState(),
    ) { items, accounts, override, conversion ->
        ledgerOf(items, primaryCurrency(accounts, override), conversion)
    }

    /**
     * The upcoming movements alone, currency-agnostic and unaggregated: the
     * dashboard forecast reduces them to a per-day effect on the balance and
     * has no use for the totals.
     */
    fun movements(today: LocalDate = LocalDate.now(clock)): Flow<List<UpcomingMovement>> = combine(
        transactionRepository.observeTransactionsFrom(today.plusDays(1)),
        transactionRepository.observePendingTransactions(),
    ) { future, pending ->
        (future.map { it.upcoming() } + pending.map { it.upcoming() })
            // Date first, then the ledger's own tie-break, so two movements on
            // the same day keep a stable order across emissions.
            .sortedWith(compareBy({ it.date }, { it.transaction.timestamp }, { it.id }))
    }

    private fun ledgerOf(
        items: List<UpcomingMovement>,
        currency: Currency,
        conversion: ConversionState,
    ): UpcomingLedger {
        val rates = if (conversion.active) conversion.rates else RateTable.EMPTY
        var converted = false
        var leftOut = false
        // Foreign flows enter at the rate of their own (future) day, which
        // resolves to the latest known one (ADR 40); no rate means the
        // movement stays out of the totals and the notice says so.
        fun magnitudeOf(type: TransactionType): BigDecimal = items
            .filter { it.transaction.type == type }
            .fold(BigDecimal.ZERO) { acc, item ->
                val transaction = item.transaction
                if (transaction.currency == currency) {
                    acc.add(item.amount.abs())
                } else {
                    val estimate = CurrencyConverter
                        .convertOn(item.amount.abs(), transaction.currency, currency, item.date, rates)
                    if (estimate == null) {
                        leftOut = true
                        acc
                    } else {
                        converted = true
                        acc.add(estimate.amount)
                    }
                }
            }

        val outgoing = magnitudeOf(TransactionType.EXPENSE)
        val incoming = magnitudeOf(TransactionType.INCOME)
        return UpcomingLedger(
            items = items,
            outgoing = outgoing,
            incoming = incoming,
            currency = currency,
            pendingCount = items.count { it.isPending },
            hasOtherCurrencies = leftOut,
            includesEstimates = converted,
        )
    }

    private fun Transaction.upcoming(): UpcomingMovement = UpcomingMovement(
        transaction = this,
        // A pending movement is due on its occurrence date; a confirmed one on
        // its own. Both read as "when it is due".
        date = if (isPending) recurringOccurrenceDate ?: localDate else localDate,
        origin = when {
            isPending -> UpcomingOrigin.PENDING
            recurringRuleId != null -> UpcomingOrigin.RECURRING
            else -> UpcomingOrigin.MANUAL
        },
    )

    /** Positive total of the [type] movements in [currency]; zero when none. */
    private fun List<UpcomingMovement>.magnitudeOf(
        type: TransactionType,
        currency: Currency,
    ): BigDecimal = filter { it.transaction.type == type && it.transaction.currency == currency }
        .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount.abs()) }
}
