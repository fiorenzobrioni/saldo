package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/**
 * Raw aggregate of the movements sharing one counterparty spelling and one
 * currency, as the database groups them. Merging the spellings that differ only
 * by case or accents is domain work
 * ([com.callbackdev.saldo.core.domain.usecase.ObserveCounterpartyBalancesUseCase]),
 * so this stays the unmerged figure.
 *
 * [total] is signed like every ledger amount: negative when the money is out.
 */
data class CounterpartyTotal(
    val name: String,
    val currency: Currency,
    val total: BigDecimal,
    val count: Int,
    /** Most recent local day (ADR 7) of the group. */
    val lastActivity: LocalDate,
)
