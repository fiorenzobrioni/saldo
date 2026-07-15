package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.creditcard.BillingCycle
import com.callbackdev.saldo.core.domain.creditcard.BillingCycleCalculator
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A credit card statement that has come due, for the caller to notify about.
 *
 * @property autoPosted true when the statement was already charged automatically
 *   (auto-post card); false when it is only awaiting the user's confirmation.
 */
data class DueStatement(
    val accountId: Long,
    val cardName: String,
    val amount: BigDecimal,
    val currency: Currency,
    val cycle: BillingCycle,
    val autoPosted: Boolean,
)

/**
 * Processes every credit card whose statement has come due on [today], run from
 * the app-start catch-up and the periodic worker (same schedule as recurring
 * generation, PLANNING ADR 4).
 *
 * Auto-post cards are settled immediately (each due cycle in turn); confirm-mode
 * cards are only reported, so the user can pay them from the notification or the
 * dashboard. Returns the statements with a positive amount owed, so the caller
 * can post the right notifications; zero-amount cycles are settled silently
 * (auto) or skipped (confirm).
 */
@Singleton
class ProcessDueCreditCardStatementsUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val settleStatement: SettleCreditCardStatementUseCase,
    private val clock: Clock,
) {

    suspend operator fun invoke(today: LocalDate = LocalDate.now(clock)): List<DueStatement> {
        // One-shot read of the current accounts, filtered to configured, linked,
        // non-archived credit cards.
        val cards = accountRepository.observeAccountsWithBalance().first()
            .map { it.account }
            .filter { it.isSettleableCreditCard() }
        return cards.flatMap { card -> processCard(card, today) }
    }

    private suspend fun processCard(card: Account, today: LocalDate): List<DueStatement> {
        val config = card.creditCard ?: return emptyList()
        return if (config.autoPost) {
            postAll(card, today)
        } else {
            reportPending(card, today)
        }
    }

    /** Auto-post: settle each due cycle in turn until none remain. */
    private suspend fun postAll(card: Account, today: LocalDate): List<DueStatement> {
        val posted = mutableListOf<DueStatement>()
        repeat(BillingCycleCalculator.MAX_LOOKBACK) {
            when (val result = settleStatement(card.id, today)) {
                is StatementSettlement.Settled -> {
                    if (result.amount.signum() > 0) {
                        posted += DueStatement(
                            accountId = card.id,
                            cardName = card.name,
                            amount = result.amount,
                            currency = card.currency,
                            cycle = result.cycle,
                            autoPosted = true,
                        )
                    }
                }
                else -> return posted
            }
        }
        return posted
    }

    /** Confirm mode: report each due cycle with something owed, without posting. */
    private suspend fun reportPending(card: Account, today: LocalDate): List<DueStatement> {
        val config = card.creditCard ?: return emptyList()
        return BillingCycleCalculator.dueStatements(today, config).mapNotNull { cycle ->
            val amount = settleStatement.statementAmount(card, cycle)
            if (amount.signum() <= 0) return@mapNotNull null
            DueStatement(
                accountId = card.id,
                cardName = card.name,
                amount = amount,
                currency = card.currency,
                cycle = cycle,
                autoPosted = false,
            )
        }
    }

    private fun Account.isSettleableCreditCard(): Boolean =
        type == AccountType.CREDIT_CARD &&
            !isArchived &&
            creditCard?.linkedAccountId != null
}
