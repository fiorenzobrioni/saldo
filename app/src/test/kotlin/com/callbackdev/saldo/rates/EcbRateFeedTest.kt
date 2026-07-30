package com.callbackdev.saldo.rates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class EcbRateFeedTest {

    private val header = "KEY,FREQ,CURRENCY,CURRENCY_DENOM,EXR_TYPE,EXR_SUFFIX,TIME_PERIOD,OBS_VALUE"

    @Test
    fun `builds the SDMX request for the whole basket from a start day`() {
        val url = EcbRateFeed.requestUrl(LocalDate.of(2026, 7, 24))

        assertEquals(
            "https://data-api.ecb.europa.eu/service/data/EXR/D..EUR.SP00.A" +
                "?startPeriod=2026-07-24&format=csvdata&detail=dataonly",
            url,
        )
    }

    @Test
    fun `parses the csvdata body into rates`() {
        val body = """
            $header
            EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2026-07-24,1.1377
            EXR.D.GBP.EUR.SP00.A,D,GBP,EUR,SP00,A,2026-07-24,0.85388
            EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2026-07-27,1.1389
        """.trimIndent()

        val rates = EcbRateFeed.parseCsv(body)

        assertEquals(3, rates.size)
        val usdFriday = rates.first()
        assertEquals("USD", usdFriday.currency)
        assertEquals(LocalDate.of(2026, 7, 24), usdFriday.day)
        assertEquals(BigDecimal("1.1377"), usdFriday.perEuro)
    }

    @Test
    fun `locates the columns by header name, not by position`() {
        val body = """
            OBS_VALUE,TIME_PERIOD,CURRENCY
            1.1377,2026-07-24,USD
        """.trimIndent()

        val rates = EcbRateFeed.parseCsv(body)

        assertEquals(1, rates.size)
        assertEquals("USD", rates.single().currency)
        assertEquals(BigDecimal("1.1377"), rates.single().perEuro)
    }

    @Test
    fun `drops unreadable rows instead of failing the batch`() {
        val body = """
            $header
            EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2026-07-24,
            EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,not-a-date,1.1377
            EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2026-07-24,not-a-number
            EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2026-07-24,-1
            EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2026-07-27,1.1389
        """.trimIndent()

        val rates = EcbRateFeed.parseCsv(body)

        assertEquals(1, rates.size)
        assertEquals(LocalDate.of(2026, 7, 27), rates.single().day)
    }

    @Test
    fun `an empty or headerless body parses to nothing`() {
        assertTrue(EcbRateFeed.parseCsv("").isEmpty())
        assertTrue(EcbRateFeed.parseCsv("no,useful,columns\n1,2,3").isEmpty())
    }
}
