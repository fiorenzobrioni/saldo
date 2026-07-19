package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Deletes a set of movements (the current filtered view of the ledger), with an
 * option to keep the affected accounts' current balances.
 *
 * A balance is computed, never stored (ADR 3), so a plain delete lowers it by
 * the deleted net. When [preserveBalances] is set the deletion is paired, in a
 * single atomic write, with one carry-over [com.callbackdev.saldo.core.domain.model.TransactionType.ADJUSTMENT]
 * per affected account (see [CarryOverCalculator]); balances stay put and the
 * carry-overs, being adjustments, never enter statistics (ADR 8).
 */
class DeleteFilteredTransactionsUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {

    /**
     * Deletes [transactions]. Returns the ids of the carry-over movements
     * created (empty when [preserveBalances] is false or nothing needed a
     * carry-over), so the caller can remove them on undo.
     */
    suspend operator fun invoke(
        transactions: List<Transaction>,
        preserveBalances: Boolean,
        carryOverDescription: String? = null,
    ): List<Long> {
        if (transactions.isEmpty()) return emptyList()
        val ids = transactions.map { it.id }
        if (!preserveBalances) {
            transactionRepository.deleteByIds(ids)
            return emptyList()
        }
        val accountsById = accountRepository.observeAccountsWithBalance().first()
            .associate { it.account.id to it.account }
        val carryOvers = CarryOverCalculator.adjustments(
            transactions = transactions,
            accountsById = accountsById,
            description = carryOverDescription,
        )
        return transactionRepository.deleteAndInsert(ids, carryOvers)
    }
}
