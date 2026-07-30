package com.callbackdev.saldo.rates

import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.rates.ExchangeRate
import com.callbackdev.saldo.core.domain.repository.ExchangeRateRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

/**
 * The gates of the sync policy (ADR 40): a fetch happens only with conversion
 * on, more than one currency in play, an open throttle window and a cache
 * actually behind. The clock is fixed on a Wednesday after the ECB
 * publication hour, so "today's rates" are expected to exist.
 */
class RateSyncManagerTest {

    private val today = LocalDate.of(2026, 7, 22)

    // 18:00 in Berlin (CEST, UTC+2) on a Wednesday: past the publication hour.
    private val clock: Clock =
        Clock.fixed(Instant.parse("2026-07-22T16:00:00Z"), ZoneId.of("Europe/Rome"))

    private val userPreferences = mockk<UserPreferencesRepository>()
    private val exchangeRateRepository = mockk<ExchangeRateRepository>()
    private val client = mockk<EcbRateClient>()

    private fun manager(
        enabled: Boolean = true,
        ledgerCurrencies: List<String> = listOf("EUR", "USD"),
        override: Currency? = null,
        lastAttempt: Long? = null,
        oldestMovementDay: LocalDate? = today.minusDays(10),
        earliestCached: LocalDate? = null,
        latestCached: LocalDate? = null,
        fetched: List<ExchangeRate> = emptyList(),
    ): RateSyncManager {
        every { userPreferences.currencyConversionEnabled } returns flowOf(enabled)
        every { userPreferences.primaryCurrencyOverride } returns flowOf(override)
        every { userPreferences.lastRateSyncAttemptEpochMilli } returns flowOf(lastAttempt)
        coEvery { userPreferences.setLastRateSyncAttempt(any()) } just Runs
        every { exchangeRateRepository.observeLedgerCurrencies() } returns flowOf(ledgerCurrencies)
        coEvery { exchangeRateRepository.oldestMovementDay() } returns oldestMovementDay
        coEvery { exchangeRateRepository.earliestCachedDay() } returns earliestCached
        coEvery { exchangeRateRepository.latestCachedDay() } returns latestCached
        coEvery { exchangeRateRepository.store(any()) } just Runs
        coEvery { client.fetchRatesSince(any()) } returns Result.success(fetched)
        return RateSyncManager(userPreferences, exchangeRateRepository, client, clock)
    }

    @Test
    fun `with conversion off nothing is fetched`() = runTest {
        manager(enabled = false).syncIfNeeded()

        coVerify(exactly = 0) { client.fetchRatesSince(any()) }
    }

    @Test
    fun `a single-currency ledger never generates traffic`() = runTest {
        manager(ledgerCurrencies = listOf("EUR")).syncIfNeeded()

        coVerify(exactly = 0) { client.fetchRatesSince(any()) }
    }

    @Test
    fun `an explicit primary override counts as a currency in play`() = runTest {
        manager(
            ledgerCurrencies = listOf("USD"),
            override = Currency.getInstance("EUR"),
        ).syncIfNeeded()

        coVerify(exactly = 1) { client.fetchRatesSince(any()) }
    }

    @Test
    fun `an empty cache backfills from the ledger's oldest movement and stores the batch`() = runTest {
        val oldest = today.minusDays(120)
        val fetched = listOf(ExchangeRate("USD", today, BigDecimal("1.14")))

        manager(oldestMovementDay = oldest, fetched = fetched).syncIfNeeded()

        coVerify(exactly = 1) { client.fetchRatesSince(oldest) }
        coVerify(exactly = 1) { exchangeRateRepository.store(fetched) }
        coVerify(exactly = 1) { userPreferences.setLastRateSyncAttempt(clock.millis()) }
    }

    @Test
    fun `a covered cache is left alone, with no attempt stamped`() = runTest {
        manager(
            oldestMovementDay = today.minusDays(10),
            earliestCached = today.minusDays(60),
            latestCached = today,
        ).syncIfNeeded()

        coVerify(exactly = 0) { client.fetchRatesSince(any()) }
        coVerify(exactly = 0) { userPreferences.setLastRateSyncAttempt(any()) }
    }

    @Test
    fun `a cache behind only at the head tops up from the day after its newest row`() = runTest {
        val latest = today.minusDays(3)

        manager(
            oldestMovementDay = today.minusDays(10),
            earliestCached = today.minusDays(60),
            latestCached = latest,
        ).syncIfNeeded()

        coVerify(exactly = 1) { client.fetchRatesSince(latest.plusDays(1)) }
    }

    @Test
    fun `a recent attempt throttles the next one, success or not`() = runTest {
        manager(lastAttempt = clock.millis() - 60_000L).syncIfNeeded()

        coVerify(exactly = 0) { client.fetchRatesSince(any()) }
    }

    @Test
    fun `a failed fetch stays silent and stores nothing`() = runTest {
        val manager = manager()
        coEvery { client.fetchRatesSince(any()) } returns Result.failure(RuntimeException("offline"))

        manager.syncIfNeeded()

        coVerify(exactly = 0) { exchangeRateRepository.store(any()) }
        // The attempt is still stamped: the throttle bounds attempts.
        coVerify(exactly = 1) { userPreferences.setLastRateSyncAttempt(any()) }
    }
}
