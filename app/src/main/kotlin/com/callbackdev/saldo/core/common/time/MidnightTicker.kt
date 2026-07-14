package com.callbackdev.saldo.core.common.time

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * Emits today's local date immediately, then again right after each local
 * midnight, so date-anchored pipelines (the dashboard's "Today" card, the
 * stats period windows) recompute when the day changes while the screen
 * stays open. Collection typically lives inside a `WhileSubscribed` stateIn,
 * so the ticker only runs while someone is actually looking.
 */
fun midnightTicker(clock: Clock): Flow<LocalDate> = flow {
    while (currentCoroutineContext().isActive) {
        emit(LocalDate.now(clock))
        val nextMidnight = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant()
        // A small cushion keeps a coarse timer from firing a hair early.
        delay(Duration.between(clock.instant(), nextMidnight).toMillis().coerceAtLeast(MIN_DELAY_MILLIS))
    }
}

private const val MIN_DELAY_MILLIS = 1_000L
