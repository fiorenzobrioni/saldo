package com.callbackdev.saldo.rates

import com.callbackdev.saldo.core.domain.rates.ExchangeRate
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The shape of the ECB rate feed (ADR 40): URL building and CSV parsing,
 * pure and JVM-testable. The endpoint is the public SDMX data API, dataflow
 * `EXR`, daily euro reference rates for the whole basket
 * (`D..EUR.SP00.A`: every currency, quoted against the euro, spot
 * reference). No key, no account; `startPeriod` makes the same request serve
 * both the daily top-up and the historical backfill.
 */
object EcbRateFeed {

    /** First day the ECB ever published euro reference rates. */
    val FEED_START: LocalDate = LocalDate.of(1999, 1, 4)

    /** CSV columns the parser needs, located by header name, not position. */
    private const val COLUMN_CURRENCY = "CURRENCY"
    private const val COLUMN_DAY = "TIME_PERIOD"
    private const val COLUMN_RATE = "OBS_VALUE"

    fun requestUrl(startDay: LocalDate): String =
        "https://data-api.ecb.europa.eu/service/data/EXR/D..EUR.SP00.A" +
            "?startPeriod=$startDay&format=csvdata&detail=dataonly"

    /**
     * Parses the `csvdata` body into rates, dropping any row it cannot read
     * (blank observation, unparsable number or date) instead of failing the
     * whole batch: one missing day degrades to the previous day's rate by
     * the lookup rule, a rejected batch would degrade everything.
     *
     * The EXR flow's fields never contain commas or quotes, so a plain split
     * is enough; the header row names the columns and is trusted over their
     * position.
     */
    fun parseCsv(body: String): List<ExchangeRate> {
        val lines = body.lineSequence().filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) return emptyList()
        val header = lines.next().split(',').map { it.trim() }
        val currencyIndex = header.indexOf(COLUMN_CURRENCY)
        val dayIndex = header.indexOf(COLUMN_DAY)
        val rateIndex = header.indexOf(COLUMN_RATE)
        if (currencyIndex < 0 || dayIndex < 0 || rateIndex < 0) return emptyList()

        val rates = mutableListOf<ExchangeRate>()
        while (lines.hasNext()) {
            val fields = lines.next().split(',')
            val maxIndex = maxOf(currencyIndex, dayIndex, rateIndex)
            if (fields.size <= maxIndex) continue
            val currency = fields[currencyIndex].trim()
            if (currency.isEmpty()) continue
            val day = runCatching { LocalDate.parse(fields[dayIndex].trim()) }.getOrNull() ?: continue
            val rate = runCatching { BigDecimal(fields[rateIndex].trim()) }.getOrNull() ?: continue
            if (rate.signum() <= 0) continue
            rates += ExchangeRate(currency = currency, day = day, perEuro = rate)
        }
        return rates
    }
}
