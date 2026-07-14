package com.callbackdev.saldo.core.common.money

import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats domain amounts ([BigDecimal]) into localized strings for the UI.
 *
 * This is the only place where money becomes a [String] (domain rule: `Long`
 * in the DB, `BigDecimal` in the domain, localized `String` only in the UI).
 * Formatting always goes through [NumberFormat], never manual concatenation.
 */
object MoneyFormatter {

    /** Localized currency string, e.g. `1.234,56 €` for EUR in an Italian locale. */
    fun format(
        amount: BigDecimal,
        currency: Currency,
        locale: Locale = Locale.getDefault(),
    ): String = currencyFormat(currency, locale).format(amount)

    /**
     * Like [format] but with an explicit leading `+` for positive amounts, for
     * places where the direction of the movement matters (e.g. adjustments).
     */
    fun formatSigned(
        amount: BigDecimal,
        currency: Currency,
        locale: Locale = Locale.getDefault(),
    ): String {
        val formatted = format(amount, currency, locale)
        return if (amount.signum() > 0) "+$formatted" else formatted
    }

    /**
     * Per-thread cache of configured formatters, keyed by currency and locale.
     * [NumberFormat] construction goes through ICU and is not free; money is
     * formatted once per row on every recomposition of a list, so rebuilding it
     * each time shows up while scrolling. [NumberFormat] is also not
     * thread-safe, hence a [ThreadLocal] map rather than a shared instance: each
     * thread reuses its own formatter with no locking on the format path.
     */
    private val cache = ThreadLocal.withInitial { HashMap<String, NumberFormat>() }

    private fun currencyFormat(currency: Currency, locale: Locale): NumberFormat {
        val key = "${currency.currencyCode}|${locale.toLanguageTag()}"
        return cache.get()!!.getOrPut(key) {
            NumberFormat.getCurrencyInstance(locale).apply {
                this.currency = currency
                val digits = MoneyMapper.fractionDigits(currency)
                minimumFractionDigits = digits
                maximumFractionDigits = digits
            }
        }
    }
}
