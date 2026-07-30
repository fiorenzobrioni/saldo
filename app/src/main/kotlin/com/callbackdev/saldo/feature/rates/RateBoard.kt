package com.callbackdev.saldo.feature.rates

import com.callbackdev.saldo.core.domain.rates.ExchangeRate
import com.callbackdev.saldo.core.domain.rates.RateTable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Currency

/**
 * One currency of the exchange-rates board: the latest value of 1 unit of the
 * base currency in [currency], its recent published history and the change
 * against the previous publication.
 */
data class RateRow(
    val currency: Currency,
    /** Units of [currency] per 1 unit of the base currency, at [day]. */
    val perBase: BigDecimal,
    /** Publication day of the latest sample. */
    val day: LocalDate,
    /**
     * Signed relative change against the previous published sample
     * (e.g. 0.0012 = +0.12%); null with fewer than two samples.
     */
    val changeFraction: BigDecimal?,
    /** The last published values (oldest first, [perBase] scale), for the sparkline. */
    val history: List<BigDecimal>,
    /** Whether the ledger actually uses this currency. */
    val inUse: Boolean,
)

/**
 * Pure builder of the exchange-rates board (ADR 40): cross rates against the
 * chosen base via the euro, one row per cached currency. The ECB publishes on
 * TARGET working days only, so the history is a series of published samples,
 * not of calendar days; weekends simply do not exist in it.
 */
internal object RateBoard {

    private const val EUR_CODE = "EUR"

    /** Value scale: enough for GBP-sized rates without drowning IDR-sized ones. */
    private const val VALUE_SCALE = 4

    private const val CHANGE_SCALE = 6

    /** Published samples per sparkline: about a week and a half of TARGET days. */
    private const val MAX_SAMPLES = 7

    /**
     * The board rows, usable currencies only, in-use first then alphabetical.
     * Empty when the cache holds nothing. [base] must be the euro or a basket
     * currency (the caller falls back to the euro otherwise).
     */
    fun build(
        rates: List<ExchangeRate>,
        base: Currency,
        ledgerCurrencies: Set<String>,
        maxSamples: Int = MAX_SAMPLES,
    ): List<RateRow> {
        if (rates.isEmpty()) return emptyList()
        val table = RateTable.of(rates)
        val baseCode = base.currencyCode
        if (baseCode != EUR_CODE && !table.covers(baseCode)) return emptyList()

        val byCurrency = rates.groupBy { it.currency }
        val quoted = byCurrency.keys.filter { it != baseCode }
        // The euro is the quote base and has no rows of its own; against any
        // other base it becomes a row, walked on the base's own days.
        val codes = if (baseCode == EUR_CODE) quoted else quoted + EUR_CODE

        return codes.mapNotNull { code ->
            val days = sampleDaysOf(code, byCurrency, baseCode).takeLast(maxSamples)
            if (days.isEmpty()) return@mapNotNull null
            val history = days.map { day -> perBaseOn(code, day, baseCode, table) }
            val previous = history.getOrNull(history.size - 2)
            val latest = history.last()
            RateRow(
                currency = Currency.getInstance(code),
                perBase = latest,
                day = days.last(),
                changeFraction = previous
                    ?.takeIf { it.signum() != 0 }
                    ?.let { latest.subtract(it).divide(it, CHANGE_SCALE, RoundingMode.HALF_UP) },
                history = history,
                inUse = code in ledgerCurrencies,
            )
        }.sortedWith(
            compareByDescending<RateRow> { it.inUse }.thenBy { it.currency.currencyCode },
        )
    }

    /** The published days a row is walked on: its own, or the base's for the euro row. */
    private fun sampleDaysOf(
        code: String,
        byCurrency: Map<String, List<ExchangeRate>>,
        baseCode: String,
    ): List<LocalDate> {
        val source = if (code == EUR_CODE) baseCode else code
        return byCurrency[source].orEmpty().map { it.day }.sorted()
    }

    /** Units of [code] per 1 unit of [baseCode] on [day], both legs via the euro. */
    private fun perBaseOn(
        code: String,
        day: LocalDate,
        baseCode: String,
        table: RateTable,
    ): BigDecimal {
        val quotedPerEuro = if (code == EUR_CODE) {
            BigDecimal.ONE
        } else {
            checkNotNull(table.onOrBefore(code, day)).perEuro
        }
        val basePerEuro = if (baseCode == EUR_CODE) {
            BigDecimal.ONE
        } else {
            checkNotNull(table.onOrBefore(baseCode, day)).perEuro
        }
        return quotedPerEuro.divide(basePerEuro, VALUE_SCALE, RoundingMode.HALF_UP)
    }
}
