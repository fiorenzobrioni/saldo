package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency

/**
 * A single ledger movement.
 *
 * ### Amount sign convention
 * [amount] is the *signed effect on [accountId]'s balance*, expressed in [currency]:
 * - [TransactionType.EXPENSE]: negative
 * - [TransactionType.INCOME]: positive
 * - [TransactionType.ADJUSTMENT]: signed delta (either direction)
 * - [TransactionType.TRANSFER]: negative (funds leave the source account)
 *
 * ### Transfers
 * A transfer is a single record (PLANNING ADR 2). [accountId] is the source and
 * [transferAccountId] the destination. [transferAmount] is the positive effect on
 * the destination in [transferCurrency]; for same-currency transfers it equals
 * `-amount`. Different currencies (the user types both legs) are supported by the
 * separate destination amount/currency.
 *
 * A movement's [currency] is assumed to match its [accountId]'s currency, so
 * per-account balances never mix currencies.
 */
data class Transaction(
    val type: TransactionType,
    val amount: BigDecimal,
    val currency: Currency,
    val accountId: Long,
    val timestamp: Instant,
    val zoneOffset: ZoneOffset,
    val id: Long = 0L,
    val transferAccountId: Long? = null,
    val transferAmount: BigDecimal? = null,
    val transferCurrency: Currency? = null,
    val categoryId: Long? = null,
    val description: String? = null,
    val note: String? = null,
    val isExcludedFromStats: Boolean = false,
    val isRefund: Boolean = false,
    val recurringRuleId: Long? = null,
    /**
     * True for a recurring movement awaiting confirmation (confirm mode / variable
     * amount). Pending movements do not affect balances or statistics until confirmed.
     */
    val isPending: Boolean = false,
    /**
     * For generated recurring movements, the schedule occurrence this movement
     * materializes; null for manual movements. Identifies the occurrence even if
     * the user later edits the movement's date, and backs the unique constraint
     * that prevents double generation.
     */
    val recurringOccurrenceDate: LocalDate? = null,
)
