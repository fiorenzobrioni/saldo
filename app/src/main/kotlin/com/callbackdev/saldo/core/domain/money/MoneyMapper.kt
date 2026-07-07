package com.callbackdev.saldo.core.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * Converts monetary amounts between the domain representation ([BigDecimal]) and
 * the storage representation ([Long] minor units, e.g. cents).
 *
 * The scale is derived from the currency: EUR/USD use 2 fraction digits, JPY 0,
 * and so on ([Currency.getDefaultFractionDigits]). Pseudo-currencies that report
 * a negative number of digits (e.g. XXX) are treated as 0.
 *
 * Never use [Float]/[Double] for money (domain rule): all rounding goes through
 * [BigDecimal] with [RoundingMode.HALF_UP].
 */
object MoneyMapper {

    /** Fraction digits for [currency], never negative. */
    fun fractionDigits(currency: Currency): Int =
        currency.defaultFractionDigits.coerceAtLeast(0)

    /**
     * Converts a domain [amount] to minor units for storage. Amounts with more
     * decimals than the currency allows are rounded half-up.
     */
    fun toMinorUnits(amount: BigDecimal, currency: Currency): Long =
        amount
            .movePointRight(fractionDigits(currency))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

    /**
     * Converts stored [minorUnits] back to a domain amount, scaled to the
     * currency's fraction digits (e.g. `4500` in EUR becomes `45.00`).
     */
    fun toAmount(minorUnits: Long, currency: Currency): BigDecimal =
        BigDecimal.valueOf(minorUnits, fractionDigits(currency))
}
