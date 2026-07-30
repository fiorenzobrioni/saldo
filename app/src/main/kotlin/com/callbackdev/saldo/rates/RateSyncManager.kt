package com.callbackdev.saldo.rates

import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When and what to fetch from the ECB feed (ADR 40): on demand, in the
 * foreground, never at rest. A sync is attempted only when every gate opens:
 *
 * 1. the conversion preference is on (off means zero network, the promise the
 *    Settings switch makes);
 * 2. the ledger actually touches more than one currency (counting an explicit
 *    primary-currency override): a single-currency user never generates
 *    traffic, which is what makes the on-by-default choice cost nothing;
 * 3. the throttle window has passed since the last attempt, successful or
 *    not, so a feed outage cannot turn every app open into a request;
 * 4. the cache is actually behind: missing history older than its earliest
 *    row, or missing the most recent expected publication.
 *
 * Two triggers call it: the app coming to the foreground (a staleness check)
 * and [start]'s watcher on the ledger currencies (so the first foreign
 * account fetches history right away instead of on the next open). There is
 * no worker and no retry loop: the next foreground is the retry.
 */
@Singleton
class RateSyncManager @Inject constructor(
    private val userPreferences: UserPreferencesRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val client: EcbRateClient,
    private val clock: Clock,
) {

    private val syncLock = Mutex()

    /**
     * Reacts to the conversion preference and the set of ledger currencies,
     * so enabling the feature or saving the first foreign account starts the
     * backfill immediately. Called once from the Application, like the other
     * watchers.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                userPreferences.currencyConversionEnabled,
                exchangeRateRepository.observeLedgerCurrencies(),
                userPreferences.primaryCurrencyOverride,
            ) { enabled, codes, override ->
                enabled && ratesNeeded(codes, override?.currencyCode)
            }
                .distinctUntilChanged()
                .collect { needed -> if (needed) syncIfNeeded() }
        }
    }

    /** Staleness check on returning to the foreground; fire-and-forget. */
    fun onAppForeground(scope: CoroutineScope) {
        scope.launch { syncIfNeeded() }
    }

    /**
     * Runs one sync attempt if every gate opens; concurrent callers collapse
     * into the one already running.
     */
    suspend fun syncIfNeeded() {
        if (syncLock.isLocked) return
        syncLock.withLock {
            if (!userPreferences.currencyConversionEnabled.first()) return
            val codes = exchangeRateRepository.observeLedgerCurrencies().first()
            val override = userPreferences.primaryCurrencyOverride.first()?.currencyCode
            if (!ratesNeeded(codes, override)) return

            val now = clock.millis()
            val lastAttempt = userPreferences.lastRateSyncAttemptEpochMilli.first()
            if (lastAttempt != null && now - lastAttempt < THROTTLE_MILLIS) return

            val startDay = fetchWindowStart() ?: return
            // Stamped before the fetch, success or not: the throttle exists
            // to bound attempts, not successes.
            userPreferences.setLastRateSyncAttempt(now)
            client.fetchRatesSince(startDay)
                .onSuccess { rates -> exchangeRateRepository.store(rates) }
            // A failure is deliberately silent: the surfaces keep converting
            // with the cache they have, each estimate already carrying the
            // date of the rate it used (ADR 40, never a block).
        }
    }

    /**
     * More than one distinct currency in play means countervalues exist to
     * compute. The explicit primary override counts as "in play": every
     * ledger currency converts into it even when no account uses it.
     */
    private fun ratesNeeded(ledgerCodes: List<String>, overrideCode: String?): Boolean =
        buildSet {
            addAll(ledgerCodes)
            overrideCode?.let(::add)
        }.size >= 2

    /**
     * First day the fetch should cover, or null when the cache is already
     * complete: history back to the ledger's oldest movement (plus the stock
     * window the sparkline reads) and forward to the last expected
     * publication. A cache missing old history refetches from the floor in a
     * single request - a one-off that also refreshes the head.
     */
    private suspend fun fetchWindowStart(): LocalDate? {
        val today = LocalDate.now(clock)
        val floor = listOfNotNull(
            exchangeRateRepository.oldestMovementDay(),
            today.minusDays(STOCK_WINDOW_DAYS),
        ).min().coerceAtLeast(EcbRateFeed.FEED_START)

        val earliest = exchangeRateRepository.earliestCachedDay()
        val latest = exchangeRateRepository.latestCachedDay()
        val needsBackfill = earliest == null || earliest > floor
        val needsTopUp = latest == null || latest < expectedLatestPublicationDay()
        return when {
            needsBackfill -> floor
            needsTopUp -> latest?.plusDays(1) ?: floor
            else -> null
        }
    }

    /**
     * The most recent day whose rates should exist: today after the
     * publication hour on a weekday, otherwise the previous weekday. The ECB
     * publishes around 16:00 CET on TARGET working days; the hour carries a
     * margin and TARGET holidays are deliberately not modelled - on those few
     * days the throttle bounds the wasted attempts.
     */
    private fun expectedLatestPublicationDay(): LocalDate {
        val nowAtEcb = ZonedDateTime.now(clock).withZoneSameInstant(ECB_ZONE)
        var candidate =
            if (nowAtEcb.toLocalTime() >= PUBLICATION_TIME) {
                nowAtEcb.toLocalDate()
            } else {
                nowAtEcb.toLocalDate().minusDays(1)
            }
        while (candidate.dayOfWeek == DayOfWeek.SATURDAY || candidate.dayOfWeek == DayOfWeek.SUNDAY) {
            candidate = candidate.minusDays(1)
        }
        return candidate
    }

    private companion object {
        /** At most one attempt per window; rates change once a day anyway. */
        const val THROTTLE_MILLIS = 6 * 60 * 60 * 1000L

        /** How far back the stock conversions look (the sparkline window). */
        const val STOCK_WINDOW_DAYS = 30L

        /** 16:00 CET publication plus a margin for the concertation to settle. */
        val PUBLICATION_TIME: LocalTime = LocalTime.of(17, 0)

        /** CET/CEST; the ECB sits in Frankfurt, whose IANA zone is Berlin's. */
        val ECB_ZONE: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
