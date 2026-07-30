package com.callbackdev.saldo.core.domain.rates

import java.math.BigDecimal
import java.time.LocalDate

/**
 * One ECB euro reference rate (ADR 40): on [day], one euro bought [perEuro]
 * units of [currency]. The euro itself never appears as a row: it is the base
 * of every quote and would always be 1.
 *
 * @property currency ISO 4217 code of the quoted currency.
 * @property day publication day of the rate.
 * @property perEuro units of [currency] per euro, always positive.
 */
data class ExchangeRate(
    val currency: String,
    val day: LocalDate,
    val perEuro: BigDecimal,
)
