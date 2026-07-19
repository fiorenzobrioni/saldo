package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure computation behind the "delete but keep current balances" cleanup.
 *
 * A balance is always computed (`initialBalance + Σ movements`, ADR 3), so
 * deleting movements removes their net effect from the balance. To leave the
 * current balance untouched, each affected account gets a single carry-over
 * [TransactionType.ADJUSTMENT] that adds back exactly what the deletion removes.
 * Adjustments are excluded from statistics at query level (ADR 8), so the
 * carry-over never counts as spend or income.
 */
object CarryOverCalculator {

    /**
     * The net effect the [transactions] apply to each account's balance, keyed
     * by account id. For every movement the signed [Transaction.amount] lands on
     * its [Transaction.accountId]; a transfer also lands its
     * [Transaction.transferAmount] on the destination account. Insertion order
     * is preserved so callers render a stable list.
     */
    fun netByAccount(transactions: List<Transaction>): Map<Long, BigDecimal> {
        val net = LinkedHashMap<Long, BigDecimal>()
        transactions.forEach { transaction ->
            net.merge(transaction.accountId, transaction.amount, BigDecimal::add)
            if (transaction.type == TransactionType.TRANSFER) {
                val destination = transaction.transferAccountId
                val destinationAmount = transaction.transferAmount
                if (destination != null && destinationAmount != null) {
                    net.merge(destination, destinationAmount, BigDecimal::add)
                }
            }
        }
        return net
    }

    /**
     * The carry-over [TransactionType.ADJUSTMENT] movements needed to preserve
     * every balance after deleting [transactions]. One per affected account with
     * a non-zero net (in the account's own currency), dated at the most recent
     * deleted movement so the collapse sits at the boundary of the removed range
     * and the kept period's balance-over-time is unchanged. Accounts absent from
     * [accountsById] are skipped. Each carry-over carries [description] so it is
     * recognisable in the ledger. Empty when [transactions] is empty.
     */
    fun adjustments(
        transactions: List<Transaction>,
        accountsById: Map<Long, Account>,
        description: String? = null,
    ): List<Transaction> {
        if (transactions.isEmpty()) return emptyList()
        val boundary = transactions.maxByOrNull { it.timestamp } ?: return emptyList()
        return netByAccount(transactions).mapNotNull { (accountId, rawNet) ->
            val account = accountsById[accountId] ?: return@mapNotNull null
            val net = rawNet.setScale(MoneyMapper.fractionDigits(account.currency), RoundingMode.HALF_UP)
            if (net.signum() == 0) return@mapNotNull null
            Transaction(
                type = TransactionType.ADJUSTMENT,
                amount = net,
                currency = account.currency,
                accountId = accountId,
                timestamp = boundary.timestamp,
                zoneOffset = boundary.zoneOffset,
                description = description,
            )
        }
    }
}
