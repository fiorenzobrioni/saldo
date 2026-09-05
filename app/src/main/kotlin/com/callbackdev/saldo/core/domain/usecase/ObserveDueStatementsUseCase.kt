package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.creditcard.BillingCycleCalculator
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes the confirm-mode credit card statements waiting to be paid, for the
 * dashboard and accounts "pay statement" call to action. Reactive: the balance
 * query it builds on re-emits on any account or movement change, so a new charge
 * updates the owed amount and a settlement removes the statement immediately.
 *
 * Auto-post cards are left out: their statements are charged automatically by
 * [ProcessDueCreditCardStatementsUseCase] on app start, so they never linger as
 * something the user must act on.
 */
@Singleton
class ObserveDueStatementsUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val settleStatement: SettleCreditCardStatementUseCase,
    private val clock: Clock,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<DueStatement>> =
        accountRepository.observeAccountsWithBalance().mapLatest { items ->
            val today = LocalDate.now(clock)
            items.map { it.account }
                .filter { it.awaitsManualStatement() }
                .flatMap { card -> card.dueStatements(today) }
        }

    private suspend fun Account.dueStatements(today: LocalDate): List<DueStatement> {
        val config = creditCard ?: return emptyList()
        val due = BillingCycleCalculator.dueStatements(today, config)
        return due.zip(settleStatement.statementAmounts(this, due)).mapNotNull { (cycle, amount) ->
            if (amount.signum() <= 0) return@mapNotNull null
            DueStatement(
                accountId = id,
                cardName = name,
                amount = amount,
                currency = currency,
                cycle = cycle,
                autoPosted = false,
            )
        }
    }

    private fun Account.awaitsManualStatement(): Boolean {
        val card = creditCard ?: return false
        return type == AccountType.CREDIT_CARD &&
            !isArchived &&
            card.linkedAccountId != null &&
            !card.autoPost
    }
}
