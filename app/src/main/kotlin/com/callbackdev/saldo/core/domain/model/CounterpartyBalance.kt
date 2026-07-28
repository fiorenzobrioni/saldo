package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/** A counterparty's signed position in one currency; two currencies never add up. */
data class CounterpartyAmount(
    val currency: Currency,
    /** Negative when they owe you, positive when you owe them. */
    val amount: BigDecimal,
)

/**
 * What one person owes you or you owe them, as the signed sum of the movements
 * carrying their name (ADR 34).
 *
 * The sign follows the ledger's own convention rather than a separate one:
 * lending money is an expense (negative), so a **negative** total means the
 * money is still out and they owe you; a **positive** total means you received
 * money that is not yours, so you owe them. Repayments are movements in the
 * opposite direction, which is why partial ones need no special handling: they
 * simply move the sum toward zero.
 */
data class CounterpartyBalance(
    /** Display spelling: the one used most recently. */
    val name: String,
    /** Signed positions, one per currency, the primary one first when present. */
    val amounts: List<CounterpartyAmount>,
    val movementCount: Int,
    /** Local day (ADR 7) of the most recent movement with this counterparty. */
    val lastActivity: LocalDate,
) {
    /** True once every currency is back to zero: nothing is owed either way. */
    val isSettled: Boolean get() = amounts.all { it.amount.signum() == 0 }

    /** The signed position in [currency], or null when there is none. */
    fun amountIn(currency: Currency): BigDecimal? =
        amounts.firstOrNull { it.currency == currency }?.amount

    /** Positions in every currency but [currency], for the secondary line. */
    fun otherAmounts(currency: Currency): List<CounterpartyAmount> =
        amounts.filter { it.currency != currency }
}

/**
 * The whole "who owes whom" picture: one entry per person plus the two headline
 * totals, in the primary currency.
 *
 * The two totals are kept separate and never netted: 200 lent to one friend and
 * 200 borrowed from another is not the same situation as owing nobody anything,
 * and a single net figure would say it is.
 */
data class CounterpartyLedger(
    val entries: List<CounterpartyBalance> = emptyList(),
    val currency: Currency = fallbackCurrency,
    /** Positive magnitude of what others owe you, in [currency]. */
    val owedToYou: BigDecimal = BigDecimal.ZERO,
    /** Positive magnitude of what you owe others, in [currency]. */
    val youOwe: BigDecimal = BigDecimal.ZERO,
    /** True when some position sits in a currency other than [currency]. */
    val hasOtherCurrencies: Boolean = false,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /** True when there is something open in either direction, in [currency]. */
    val hasOpenPositions: Boolean
        get() = owedToYou.signum() > 0 || youOwe.signum() > 0

    /**
     * True when at least one person has an open position in any currency: what
     * decides whether the dashboard card has anything to say. Broader than
     * [hasOpenPositions], which only looks at the primary currency.
     */
    val hasOpenEntries: Boolean get() = entries.any { !it.isSettled }
}
