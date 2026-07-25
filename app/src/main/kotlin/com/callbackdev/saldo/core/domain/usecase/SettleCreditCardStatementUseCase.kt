package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.creditcard.BillingCycle
import com.callbackdev.saldo.core.domain.creditcard.BillingCycleCalculator
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRunner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of settling a credit card statement. */
sealed interface StatementSettlement {
    /** A statement was settled: [amount] was transferred to the card (may be zero if nothing was owed). */
    data class Settled(
        val accountId: Long,
        val cardName: String,
        val cycle: BillingCycle,
        val amount: BigDecimal,
    ) : StatementSettlement

    /** No closed statement is due for this card right now. */
    data object NothingDue : StatementSettlement

    /** The card is not a credit card, has no linked account, or the linked account is missing/mismatched. */
    data object NotSettleable : StatementSettlement
}

/**
 * Settles the oldest due credit card statement of one card: it posts a single
 * transfer from the linked account to the card for the cycle's outstanding
 * amount, zeroing that cycle, and advances the settlement watermark so the same
 * cycle is never charged twice.
 *
 * Drives both the manual "pay statement" action (confirm mode) and the
 * automatic posting (auto mode, via [ProcessDueCreditCardStatementsUseCase]).
 * Idempotent and concurrency-safe: a process-wide [Mutex] serializes runs, the
 * transfer insert and the watermark advance commit in one transaction, and the
 * watermark itself rejects an already-settled cycle.
 */
@Singleton
class SettleCreditCardStatementUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {

    private val mutex = Mutex()

    /**
     * Settles the oldest statement of [accountId] that is due on [today] and
     * actually owes something.
     *
     * Empty cycles (a month with no spending on the card) are consumed on the
     * way: the watermark jumps straight to the first cycle with a balance, so
     * this lands on the very statement the "pay statement" call to action
     * advertises. Reporting shows only non-empty statements
     * ([ObserveDueStatementsUseCase]), so settling the empty ones one tap at a
     * time would look like a button that does nothing. When every due cycle is
     * empty the watermark still advances past all of them, keeping the run
     * idempotent.
     */
    suspend operator fun invoke(
        accountId: Long,
        today: LocalDate = LocalDate.now(clock),
    ): StatementSettlement = mutex.withLock {
        val account = accountRepository.getAccount(accountId) ?: return StatementSettlement.NotSettleable
        val config = account.creditCard ?: return StatementSettlement.NotSettleable
        val linkedId = config.linkedAccountId ?: return StatementSettlement.NotSettleable
        val linked = accountRepository.getAccount(linkedId)?.takeIf { it.currency == account.currency }
            ?: return StatementSettlement.NotSettleable

        val due = BillingCycleCalculator.dueStatements(today, config)
        if (due.isEmpty()) return StatementSettlement.NothingDue
        val owed = due.firstNotNullOfOrNull { cycle ->
            statementAmount(account, cycle).takeIf { it.signum() > 0 }?.let { cycle to it }
        }
        // All due cycles empty: settle through to the newest so the next run
        // finds nothing due instead of re-walking the same empty statements.
        val (cycle, amount) = owed ?: (due.last() to BigDecimal.ZERO)
        transactionRunner.inTransaction {
            if (amount.signum() > 0) {
                transactionRepository.upsert(settlementTransfer(account, linked, cycle, amount))
            }
            accountRepository.updateSettlementWatermark(account.id, cycle.closing)
        }
        StatementSettlement.Settled(account.id, account.name, cycle, amount)
    }

    /**
     * The amount owed for [cycle]: the negation of the card's own signed
     * movements dated in the cycle (expenses are negative, so the owed amount is
     * positive), never below zero. Read-only, so it also serves the confirm-mode
     * preview that shows a statement without posting it.
     */
    suspend fun statementAmount(account: Account, cycle: BillingCycle): BigDecimal {
        val start = cycle.start.atStartOfDay(clock.zone).toInstant()
        val end = cycle.closing.plusDays(1).atStartOfDay(clock.zone).toInstant()
        val net = transactionRepository.sumOwnMovements(account.id, start, end, account.currency)
        return net.negate().max(BigDecimal.ZERO)
    }

    private fun settlementTransfer(
        card: Account,
        linked: Account,
        cycle: BillingCycle,
        amount: BigDecimal,
    ): Transaction {
        // Dated on the real charge date (payment due), even if posted or
        // confirmed later. Noon avoids the DST/midnight local-date edge.
        val zoned = cycle.paymentDue.atTime(GENERATION_HOUR, 0).atZone(clock.zone)
        return Transaction(
            type = TransactionType.TRANSFER,
            amount = amount.negate(),
            currency = linked.currency,
            accountId = linked.id,
            timestamp = zoned.toInstant(),
            zoneOffset = zoned.offset,
            transferAccountId = card.id,
            transferAmount = amount,
            transferCurrency = card.currency,
        )
    }

    private companion object {
        const val GENERATION_HOUR = 12
    }
}
