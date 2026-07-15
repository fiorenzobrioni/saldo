package com.callbackdev.saldo.core.domain.backup

import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.TransactionType
import java.util.Currency

/**
 * Semantic validation of a decoded backup payload, run as part of
 * [BackupCodec.decode] so a bad file is rejected at the *inspect* step,
 * before the restore transaction ever replaces the current data.
 *
 * The restore transaction still validates on its own (enum `valueOf` and
 * currency checks in the entity mappers roll it back), but that safety net
 * only covers failures *during* the write: a value that decodes fine and
 * then crashes on the first read after commit (e.g. an unknown ISO 4217
 * code hitting `Currency.getInstance`) would slip through it and leave the
 * database unreadable. Everything the read path will trust is checked here.
 *
 * Throws [IllegalArgumentException] on the first violation; the codec maps
 * it to [BackupDecodeException.Corrupted].
 */
internal fun BackupData.validatePayload() {
    accounts.forEach { account ->
        AccountType.valueOf(account.type)
        requireKnownCurrency(account.currency)
        account.statementClosingDay?.let {
            require(it in DAY_OF_MONTH_RANGE) { "Account ${account.id} has an invalid closing day" }
        }
        account.paymentDueDay?.let {
            require(it in DAY_OF_MONTH_RANGE) { "Account ${account.id} has an invalid payment day" }
        }
    }
    categories.forEach { category -> CategoryType.valueOf(category.type) }
    recurringRules.forEach { rule ->
        TransactionType.valueOf(rule.type)
        RecurrenceFrequency.valueOf(rule.frequency)
        RecurrenceMode.valueOf(rule.mode)
        requireKnownCurrency(rule.currency)
    }
    transactions.forEach { transaction ->
        val type = TransactionType.valueOf(transaction.type)
        requireKnownCurrency(transaction.currency)
        transaction.transferCurrency?.let(::requireKnownCurrency)
        if (type == TransactionType.TRANSFER) {
            require(transaction.transferAccountId != null && transaction.transferAmountMinor != null) {
                "Transfer ${transaction.id} is missing its destination account or amount"
            }
        }
    }
    budgets.forEach { budget -> requireKnownCurrency(budget.currency) }
    require(budgets.count { it.categoryId == null } <= 1) {
        "More than one overall budget"
    }
}

private fun requireKnownCurrency(code: String) {
    Currency.getInstance(code)
}

/** Valid day-of-month values for the credit card cycle configuration. */
private const val MAX_DAY_OF_MONTH = 31
private val DAY_OF_MONTH_RANGE = 1..MAX_DAY_OF_MONTH
