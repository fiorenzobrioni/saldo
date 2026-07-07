package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

/**
 * A place where money sits: a bank account, a card, cash, a digital wallet.
 *
 * The current balance is never stored here: it is always computed as
 * `initialBalance + Σ movements` (PLANNING ADR 3). See [AccountWithBalance].
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
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.EPOCH,
)

/** An [Account] paired with its computed current balance. */
data class AccountWithBalance(
    val account: Account,
    val balance: BigDecimal,
)
