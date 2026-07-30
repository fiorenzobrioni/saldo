package com.callbackdev.saldo.core.domain.rates

import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Currency

/**
 * The single place where money changes currency (ADR 40). Everything is
 * [BigDecimal]; the intermediate euro leg keeps [INTERMEDIATE_SCALE] decimals
 * and the result is rounded half-up to the target currency's own scale, the
 * same mode [MoneyMapper] uses, declared here once for every caller.
 *
 * Conversion is always a presentation-time estimate: nothing returned by this
 * object is ever persisted (ADR 40, first condition).
 */
object CurrencyConverter {

    private const val EUR_CODE = "EUR"

    /**
     * Scale of the intermediate euro amount. Rates span five orders of
     * magnitude (0.85 GBP to 18000 IDR per euro), so the division keeps far
     * more digits than any target currency displays and the only rounding
     * that can show is the final one to the target scale.
     */
    private const val INTERMEDIATE_SCALE = 12

    /**
     * A converted amount and the publication day of the rate it leans on.
     * A null [rateDay] means no rate was involved (same-currency input): the
     * figure is exact, not an estimate, and needs no declaration.
     */
    data class Estimate(
        val amount: BigDecimal,
        val rateDay: LocalDate?,
    )

    /**
     * Converts a flow: [amount] in [from] becomes its value in [to] at the
     * rate in force on [day], the movement's own local day (ADR 40: a past
     * aggregate must stay put while rates move). Null when either currency
     * has no cached rate at all - the caller degrades to the pre-conversion
     * behavior instead of showing a made-up figure.
     */
    fun convertOn(
        amount: BigDecimal,
        from: Currency,
        to: Currency,
        day: LocalDate,
        rates: RateTable,
    ): Estimate? = convert(amount, from, to, rates) { code -> rates.onOrBefore(code, day) }

    /**
     * Converts a stock: [amount] in [from] becomes its value in [to] at the
     * most recent known rate (ADR 40: a balance is worth what the money is
     * worth now, not what it was worth when it arrived). Null when either
     * currency has no cached rate at all.
     */
    fun convertAtLatest(
        amount: BigDecimal,
        from: Currency,
        to: Currency,
        rates: RateTable,
    ): Estimate? = convert(amount, from, to, rates) { code -> rates.latest(code) }

    private inline fun convert(
        amount: BigDecimal,
        from: Currency,
        to: Currency,
        rates: RateTable,
        sample: (String) -> RateSample?,
    ): Estimate? {
        if (from == to) return Estimate(amount, rateDay = null)
        if (rates.isEmpty) return null
        val fromLeg = legOf(from, sample) ?: return null
        val toLeg = legOf(to, sample) ?: return null
        val inEuro = amount.divide(fromLeg.perEuro, INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
        val converted = inEuro
            .multiply(toLeg.perEuro)
            .setScale(MoneyMapper.fractionDigits(to), RoundingMode.HALF_UP)
        // With two quoted legs the older publication day is the binding one:
        // the estimate is only as fresh as its stalest rate.
        val rateDay = listOfNotNull(fromLeg.day, toLeg.day).minOrNull()
        return Estimate(converted, rateDay)
    }

    /**
     * One leg of the cross rate. The euro is the base of every ECB quote, so
     * its leg is exactly 1 with no publication day of its own.
     */
    private class Leg(val perEuro: BigDecimal, val day: LocalDate?)

    private inline fun legOf(currency: Currency, sample: (String) -> RateSample?): Leg? {
        if (currency.currencyCode == EUR_CODE) return Leg(BigDecimal.ONE, day = null)
        val resolved = sample(currency.currencyCode) ?: return null
        return Leg(resolved.perEuro, resolved.day)
    }
}
