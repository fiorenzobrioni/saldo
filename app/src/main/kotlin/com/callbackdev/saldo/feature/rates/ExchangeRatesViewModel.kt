package com.callbackdev.saldo.feature.rates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.core.domain.rates.ExchangeRate
import com.callbackdev.saldo.core.domain.rates.RateTable
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.ExchangeRateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/**
 * The quick converter at the top of the exchange-rates screen: an amount in
 * [currency] (or in the base, when [reversed]) and its countervalue at the
 * latest known rate. [reversed] false is the abroad use case: type the price
 * seen in the local currency, read it in yours.
 */
data class ConverterUiState(
    val input: String = "",
    /** The foreign leg; the other leg is always the screen's base currency. */
    val currency: Currency,
    /** False: [currency] -> base; true: base -> [currency]. */
    val reversed: Boolean = false,
    /** Countervalue of the parsed input in the target currency; null while unparsable. */
    val result: BigDecimal? = null,
    /** Currencies offered by the selector, in-use first (the board's own order). */
    val options: List<Currency> = emptyList(),
)

/** One published sample of a currency's value against the base. */
data class RatePoint(
    val day: LocalDate,
    val value: BigDecimal,
)

/** The tapped currency's full history over the screen's window, for the detail sheet. */
data class RateDetailUiState(
    val currency: Currency,
    /** Published samples, oldest first, as perBase values. */
    val points: List<RatePoint>,
)

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
    /** The quick converter; null while there is nothing to convert with. */
    val converter: ConverterUiState? = null,
    /** The tapped currency's detail, driving the bottom sheet; null when closed. */
    val detail: RateDetailUiState? = null,
) {
    val isEmpty: Boolean get() = !isLoading && yourRows.isEmpty() && otherRows.isEmpty()
}

/**
 * The exchange-rates board (ADR 40): every downloaded currency against the
 * app's primary currency, with its recent published history, a quick
 * converter on top and a per-currency detail. A read-only window on the same
 * cache the converters use - the screen never fetches by itself, the sync
 * policy already runs on every foreground.
 */
@HiltViewModel
class ExchangeRatesViewModel @Inject constructor(
    accountRepository: AccountRepository,
    userPreferences: UserPreferencesRepository,
    exchangeRateRepository: ExchangeRateRepository,
    clock: Clock,
) : ViewModel() {

    /** Everything read from the repositories, collapsed for combine arity. */
    private data class Sources(
        val accounts: List<AccountWithBalance>,
        val override: Currency?,
        val enabled: Boolean,
        val ledgerCodes: List<String>,
        val rates: List<ExchangeRate>,
    )

    /** The screen's own interaction state, collapsed for combine arity. */
    private data class Locals(
        val converterInput: String,
        val converterCode: String?,
        val converterReversed: Boolean,
        val detailCode: String?,
    )

    private val converterInput = MutableStateFlow("")
    private val converterCode = MutableStateFlow<String?>(null)
    private val converterReversed = MutableStateFlow(false)
    private val detailCode = MutableStateFlow<String?>(null)

    private val sources = combine(
        accountRepository.observeAccountsWithBalance(),
        userPreferences.primaryCurrencyOverride,
        userPreferences.currencyConversionEnabled,
        exchangeRateRepository.observeLedgerCurrencies(),
        exchangeRateRepository.observeRatesSince(LocalDate.now(clock).minusDays(WINDOW_DAYS)),
        ::Sources,
    )

    private val locals = combine(
        converterInput,
        converterCode,
        converterReversed,
        detailCode,
        ::Locals,
    )

    val uiState: StateFlow<ExchangeRatesUiState> = combine(sources, locals) { s, l ->
        buildState(s, l)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ExchangeRatesUiState(),
    )

    /** The keypad's edited value, stored as-is (the keypad already sanitizes). */
    fun onConverterInputChanged(value: String) {
        converterInput.value = value
    }

    fun onConverterCurrencySelected(currency: Currency) {
        converterCode.value = currency.currencyCode
    }

    /** Flips the direction: prices abroad in yours, or yours abroad. */
    fun onConverterSwapped() {
        converterReversed.update { !it }
    }

    /** Opens (or closes, with null) the detail sheet for a board row. */
    fun onDetailSelected(currency: Currency?) {
        detailCode.value = currency?.currencyCode
    }

    private fun buildState(s: Sources, l: Locals): ExchangeRatesUiState {
        val primary = primaryCurrency(s.accounts, s.override)
        // The board needs a base it can quote: the euro always works, any
        // basket currency works; a primary outside both falls back to the
        // euro so the screen never comes up blank for that reason alone.
        val quotable = primary.currencyCode == EUR_CODE ||
            s.rates.any { it.currency == primary.currencyCode }
        val base = if (quotable) primary else Currency.getInstance(EUR_CODE)
        val rows = RateBoard.build(
            rates = s.rates,
            base = base,
            ledgerCurrencies = s.ledgerCodes.toSet(),
        )
        return ExchangeRatesUiState(
            isLoading = false,
            base = base,
            latestDay = rows.maxOfOrNull { it.day },
            yourRows = rows.filter { it.inUse },
            otherRows = rows.filterNot { it.inUse },
            conversionEnabled = s.enabled,
            converter = converterOf(rows, base, s.rates, l),
            detail = detailOf(base, s.rates, l.detailCode),
        )
    }

    /** The converter, anchored to the selected (or first) board currency. */
    private fun converterOf(
        rows: List<RateRow>,
        base: Currency,
        rates: List<ExchangeRate>,
        l: Locals,
    ): ConverterUiState? {
        val options = rows.map { it.currency }
        val foreign = l.converterCode
            ?.let { code -> options.firstOrNull { it.currencyCode == code } }
            ?: options.firstOrNull()
            ?: return null
        val from = if (l.converterReversed) base else foreign
        val to = if (l.converterReversed) foreign else base
        val table = RateTable.of(rates)
        return ConverterUiState(
            input = l.converterInput,
            currency = foreign,
            reversed = l.converterReversed,
            result = MoneyInput.parse(l.converterInput)?.let { amount ->
                CurrencyConverter.convertAtLatest(amount, from, to, table)?.amount
            },
            options = options,
        )
    }

    private fun detailOf(
        base: Currency,
        rates: List<ExchangeRate>,
        code: String?,
    ): RateDetailUiState? {
        if (code == null) return null
        val points = RateBoard.detailSeries(rates, base, code)
        if (points.isEmpty()) return null
        return RateDetailUiState(Currency.getInstance(code), points)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val EUR_CODE = "EUR"

        /**
         * Calendar window of the whole screen: three months back, enough for
         * the detail chart's longest period and far more than the seven
         * published samples the board rows show.
         */
        const val WINDOW_DAYS = 92L
    }
}
