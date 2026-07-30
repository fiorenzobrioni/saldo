package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.rates.ConversionState
import com.callbackdev.saldo.core.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The one place that collapses the conversion preference and the rate cache
 * into a [ConversionState] (ADR 40). With the preference off the rate flow is
 * never even subscribed: disabling the feature really returns the app to its
 * pre-conversion behavior, Room subscriptions included.
 */
class ObserveConversionStateUseCase @Inject constructor(
    private val userPreferences: UserPreferencesRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<ConversionState> =
        userPreferences.currencyConversionEnabled.flatMapLatest { enabled ->
            if (enabled) {
                exchangeRateRepository.observeRateTable()
                    .map { rates -> ConversionState(enabled = true, rates = rates) }
            } else {
                flowOf(ConversionState.INACTIVE)
            }
        }
}
