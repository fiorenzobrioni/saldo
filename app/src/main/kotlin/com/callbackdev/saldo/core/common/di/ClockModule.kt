package com.callbackdev.saldo.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Provides the system clock. Injecting [Clock] instead of calling
 * `Instant.now()` keeps time-dependent logic (adjustments, recurrences)
 * deterministic under test.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
