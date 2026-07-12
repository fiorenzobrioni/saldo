package com.callbackdev.saldo.core.domain.model

import java.util.Currency

/**
 * The currencies offered by every currency picker (account editor, Settings,
 * onboarding): the device locale's currency first, then common ones.
 */
object CurrencyCatalog {

    private val COMMON_CURRENCY_CODES = listOf(
        "EUR", "USD", "GBP", "CHF", "JPY", "CAD", "AUD", "NZD",
        "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "RON", "BGN",
        "TRY", "UAH", "CNY", "HKD", "SGD", "KRW", "INR", "AED",
        "ILS", "THB", "MYR", "IDR", "PHP", "VND", "BRL", "MXN",
        "ARS", "CLP", "COP", "ZAR",
    )

    val supportedCurrencies: List<Currency> = buildList {
        add(fallbackCurrency)
        COMMON_CURRENCY_CODES.forEach { code ->
            val currency = Currency.getInstance(code)
            if (currency != fallbackCurrency) add(currency)
        }
    }
}
