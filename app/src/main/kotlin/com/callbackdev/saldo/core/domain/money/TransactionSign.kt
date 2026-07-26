package com.callbackdev.saldo.core.domain.money

import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import java.math.BigDecimal

/**
 * Sign convention of [Transaction.amount]: the effect on the source account.
 *
 * Shared rather than private to an editor because more than one surface now
 * builds movements (the full editor and the widget's quick entry), and a
 * duplicated sign rule is exactly the kind of divergence that corrupts
 * balances.
 */
object TransactionSign {

    fun signed(type: TransactionType, amount: BigDecimal): BigDecimal =
        when (type) {
            TransactionType.EXPENSE, TransactionType.TRANSFER -> amount.abs().negate()
            TransactionType.INCOME -> amount.abs()
            TransactionType.ADJUSTMENT -> amount
        }
}
