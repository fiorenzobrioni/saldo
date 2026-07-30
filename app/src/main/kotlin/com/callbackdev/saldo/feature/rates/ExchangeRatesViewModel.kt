package com.callbackdev.saldo.feature.rates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.ExchangeRateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/** Immutable UI state of the exchange-rates screen. */
data class ExchangeRatesUiState(
    val isLoading: Boolean = true,
    /** The currency every row is quoted against (1 base = X quoted). */
    val base: Currency = fallbackCurrency,
    /** Most recent publication day across the board; null with an empty cache. */
    val latestDay: LocalDate? = null,
    /** Currencies the ledger uses, in-use notice and section of their own. */
    val yourRows: List<RateRow> = emptyList(),
    /** The rest of the downloaded basket, alphabetical. */
    val otherRows: List<RateRow> = emptyList(),
    /** Whether the conversion preference is on (the screen works either way). */
    val conversionEnabled: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && yourRows.isEmpty() && otherRows.isEmpty()
}

/**
 * The exchange-rates board (ADR 40): every downloaded currency against the
 * app's primary currency, with its recent published history. A read-only
 * window on the same cache the converters use - the screen never fetches by
 * itself, the sync policy already runs on every foreground.
 */
@HiltViewModel
class ExchangeRatesViewModel @Inject constructor(
    accountRepository: AccountRepository,
    userPreferences: UserPreferencesRepository,
    exchangeRateRepository: ExchangeRateRepository,
    clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<ExchangeRatesUiState> = combine(
        accountRepository.observeAccountsWithBalance(),
        userPreferences.primaryCurrencyOverride,
        userPreferences.currencyConversionEnabled,
        exchangeRateRepository.observeLedgerCurrencies(),
        exchangeRateRepository.observeRatesSince(LocalDate.now(clock).minusDays(WINDOW_DAYS)),
    ) { accounts, override, enabled, ledgerCodes, rates ->
        val primary = primaryCurrency(accounts, override)
        // The board needs a base it can quote: the euro always works, any
        // basket currency works; a primary outside both falls back to the
        // euro so the screen never comes up blank for that reason alone.
        val quotable = primary.currencyCode == EUR_CODE ||
            rates.any { it.currency == primary.currencyCode }
        val base = if (quotable) primary else Currency.getInstance(EUR_CODE)
        val rows = RateBoard.build(
            rates = rates,
            base = base,
            ledgerCurrencies = ledgerCodes.toSet(),
        )
        ExchangeRatesUiState(
            isLoading = false,
            base = base,
            latestDay = rows.maxOfOrNull { it.day },
            yourRows = rows.filter { it.inUse },
            otherRows = rows.filterNot { it.inUse },
            conversionEnabled = enabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ExchangeRatesUiState(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val EUR_CODE = "EUR"

        /** Calendar window wide enough for seven published samples (two weekends inside). */
        const val WINDOW_DAYS = 21L
    }
}
