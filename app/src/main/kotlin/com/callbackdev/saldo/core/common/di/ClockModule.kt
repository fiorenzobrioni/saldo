package com.callbackdev.saldo.core.common.di

import com.callbackdev.saldo.core.common.time.SystemZoneClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Provides the system clock. Injecting [Clock] instead of calling
 * `Instant.now()` keeps time-dependent logic (adjustments, recurrences)
 * deterministic under test. Its zone is the phone's of the moment
 * ([SystemZoneClock]), never the one the process started in.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemZoneClock
}
