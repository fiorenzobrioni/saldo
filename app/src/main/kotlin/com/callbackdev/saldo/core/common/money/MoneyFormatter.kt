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

    private fun currencyFormat(currency: Currency, locale: Locale): NumberFormat =
        NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            val digits = MoneyMapper.fractionDigits(currency)
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
}
