package com.callbackdev.saldo.rates

import com.callbackdev.saldo.core.common.di.IoDispatcher
import com.callbackdev.saldo.core.domain.rates.ExchangeRate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.inject.Inject

/**
 * Fetches ECB reference rates over plain [HttpURLConnection]: the only
 * network code in the app outside backup and export must not be the reason a
 * networking library appears (ADR 40). Inbound traffic only - the request
 * carries a start date and nothing else.
 */
class EcbRateClient @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * All rates published from [startDay] to today, for the whole basket.
     * A failure returns `Result.failure` and the caller keeps the cache it
     * has: offline the last known rate, with its date, always wins over an
     * error state (ADR 40).
     */
    suspend fun fetchRatesSince(startDay: LocalDate): Result<List<ExchangeRate>> =
        withContext(ioDispatcher) {
            runCatching {
                val connection =
                    URL(EcbRateFeed.requestUrl(startDay)).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                    connection.readTimeout = READ_TIMEOUT_MILLIS
                    connection.setRequestProperty("Accept", "text/csv")
                    when (val code = connection.responseCode) {
                        HttpURLConnection.HTTP_OK ->
                            connection.inputStream.bufferedReader()
                                .use { it.readText() }
                                .let(EcbRateFeed::parseCsv)
                        // The SDMX API answers 404 to a window with no
                        // observations (e.g. a weekend top-up): an empty
                        // batch, not an error.
                        HttpURLConnection.HTTP_NOT_FOUND -> emptyList()
                        else -> error("Unexpected HTTP $code from the ECB rate feed")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}
