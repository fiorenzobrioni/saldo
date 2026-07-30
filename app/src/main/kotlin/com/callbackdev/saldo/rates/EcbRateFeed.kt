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
        val lines = body.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()?.split(',')?.map { it.trim() } ?: return emptyList()
        val columns = Columns(
            currency = header.indexOf(COLUMN_CURRENCY),
            day = header.indexOf(COLUMN_DAY),
            rate = header.indexOf(COLUMN_RATE),
        )
        if (columns.currency < 0 || columns.day < 0 || columns.rate < 0) return emptyList()
        return lines.drop(1).mapNotNull { line -> parseRow(line, columns) }
    }

    /** Where each needed field sits in this body's rows. */
    private class Columns(val currency: Int, val day: Int, val rate: Int) {
        val max: Int get() = maxOf(currency, day, rate)
    }

    /** One row to one rate; any unreadable field drops the row. */
    private fun parseRow(line: String, columns: Columns): ExchangeRate? {
        val fields = line.split(',')
        if (fields.size <= columns.max) return null
        val currency = fields[columns.currency].trim()
        val day = runCatching { LocalDate.parse(fields[columns.day].trim()) }.getOrNull()
        val rate = runCatching { BigDecimal(fields[columns.rate].trim()) }.getOrNull()
        return if (currency.isEmpty() || day == null || rate == null || rate.signum() <= 0) {
            null
        } else {
            ExchangeRate(currency = currency, day = day, perEuro = rate)
        }
    }
}
