package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/**
 * A place where money sits: a bank account, a card, cash, a digital wallet.
 *
 * The current balance is never stored here: it is always computed as
 * `initialBalance + Σ movements` (PLANNING ADR 3). See [AccountWithBalance].
 *
 * [creditCard] is non-null only for [AccountType.CREDIT_CARD] accounts: it
 * carries the billing cycle and the deferred-settlement configuration.
 */
data class Account(
    val name: String,
    val type: AccountType,
    val currency: Currency,
    val initialBalance: BigDecimal,
    val id: Long = 0L,
    val color: Int? = null,
    val icon: String? = null,
    val isIncludedInTotal: Boolean = true,
    val isIncludedInBudget: Boolean = true,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.EPOCH,
    val creditCard: CreditCardConfig? = null,
)

/**
 * Deferred credit card configuration (carta di credito a saldo).
 *
 * A credit card accrues spending as a negative balance over a billing cycle;
 * the whole cycle is then charged in one instalment to [linkedAccountId] as a
 * transfer. The cycle closes on [statementClosingDay] of each month and the
 * charge lands on [paymentDueDay] of the following month.
 *
 * @property statementClosingDay day of month the cycle closes, 1..31; a value
 *   beyond the month length means the last day of that month.
 * @property paymentDueDay day of the following month the statement is charged,
 *   1..31; clamped to the month length like [statementClosingDay].
 * @property linkedAccountId account charged for the statement; null until the
 *   user picks one (settlement is unavailable meanwhile).
 * @property creditLimit the card limit (fido) for the utilisation indicator;
 *   null leaves utilisation untracked.
 * @property autoPost true posts the statement transfer automatically on the due
 *   date; false waits for the user to confirm it (default).
 * @property lastSettledClosing closing date of the most recent settled cycle,
 *   the idempotency watermark; null when nothing has been settled yet.
 */
data class CreditCardConfig(
    val statementClosingDay: Int,
    val paymentDueDay: Int,
    val linkedAccountId: Long? = null,
    val creditLimit: BigDecimal? = null,
    val autoPost: Boolean = false,
    val lastSettledClosing: LocalDate? = null,
)

/**
 * An [Account] paired with its computed current balance.
 *
 * [balanceAsOfToday] is the balance counting only movements dated up to today;
 * it is non-null only when it differs from [balance], i.e. when future-dated
 * confirmed movements make the total run ahead of what is available today.
 */
data class AccountWithBalance(
    val account: Account,
    val balance: BigDecimal,
    val balanceAsOfToday: BigDecimal? = null,
)
