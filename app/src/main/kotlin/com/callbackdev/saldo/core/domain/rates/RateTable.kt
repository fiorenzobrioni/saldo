package com.callbackdev.saldo.core.domain.rates

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A rate together with the day it was published, so every estimate can name
 * the day its rate came from (ADR 40: the estimate is always declared).
 */
data class RateSample(
    val day: LocalDate,
    val perEuro: BigDecimal,
)

/**
 * An in-memory, immutable view of the cached ECB rate history, resolving
 * "the rate that applies on day X" per currency.
 *
 * Resolution rule (ADR 40): the most recent rate on or before the day, which
 * covers weekends and TARGET holidays with the last published rate; a day
 * before the first cached row resolves to the oldest known rate, declared
 * through [RateSample.day] like every other sample. A currency with no rows
 * at all (outside the ECB basket, or an empty cache) resolves to null and the
 * caller degrades exactly like the app before conversion existed.
 *
 * The table holds only the currencies the ledger touches, a handful in
 * practice, each as parallel arrays sorted by day for binary search.
 */
class RateTable private constructor(
    private val byCurrency: Map<String, Series>,
) {

    private class Series(val days: LongArray, val perEuro: Array<BigDecimal>)

    val isEmpty: Boolean get() = byCurrency.isEmpty()

    /** Whether [currencyCode] has at least one cached rate. */
    fun covers(currencyCode: String): Boolean = byCurrency.containsKey(currencyCode)

    /**
     * The rate in force on [day]: the most recent sample on or before it, or
     * the oldest known one for days that predate the whole cache. Null only
     * when the currency has no samples at all.
     */
    fun onOrBefore(currencyCode: String, day: LocalDate): RateSample? {
        val series = byCurrency[currencyCode] ?: return null
        val epochDay = day.toEpochDay()
        val index = floorIndex(series.days, epochDay)
        // Days before the first sample fall back to the oldest known rate,
        // declared as such by carrying its own (older) day.
        val resolved = if (index < 0) 0 else index
        return RateSample(LocalDate.ofEpochDay(series.days[resolved]), series.perEuro[resolved])
    }

    /** The most recent sample for [currencyCode]; null when it has none. */
    fun latest(currencyCode: String): RateSample? {
        val series = byCurrency[currencyCode] ?: return null
        val last = series.days.lastIndex
        return RateSample(LocalDate.ofEpochDay(series.days[last]), series.perEuro[last])
    }

    /** Largest index with `days[index] <= epochDay`, or -1 when all are later. */
    private fun floorIndex(days: LongArray, epochDay: Long): Int {
        var low = 0
        var high = days.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (days[mid] <= epochDay) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    companion object {
        val EMPTY = RateTable(emptyMap())

        /** Builds a table from cache rows; rows of the same currency may come in any order. */
        fun of(rates: List<ExchangeRate>): RateTable {
            if (rates.isEmpty()) return EMPTY
            val byCurrency = rates
                .groupBy { it.currency }
                .mapValues { (_, rows) ->
                    val sorted = rows.sortedBy { it.day }
                    Series(
                        days = LongArray(sorted.size) { sorted[it].day.toEpochDay() },
                        perEuro = Array(sorted.size) { sorted[it].perEuro },
                    )
                }
            return RateTable(byCurrency)
        }
    }
}
