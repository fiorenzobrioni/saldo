package com.callbackdev.saldo.core.domain.rates

/**
 * What a screen needs to know to show countervalues: whether the user keeps
 * the conversion on, and the rate history to convert with. With [active]
 * false every surface behaves exactly like the app before the feature
 * existed - single currency, informative rows, never a blank or a zero
 * (ADR 40, clean degradation).
 */
data class ConversionState(
    val enabled: Boolean,
    val rates: RateTable,
) {

    /** True when estimates can actually be produced: on, with at least one rate. */
    val active: Boolean get() = enabled && !rates.isEmpty

    companion object {
        val INACTIVE = ConversionState(enabled = false, rates = RateTable.EMPTY)
    }
}
