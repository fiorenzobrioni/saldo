package com.callbackdev.saldo.feature.transactions.importer

import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/**
 * Builds the key used to tell whether two movements are "the same" for
 * duplicate detection: the local day, the type, the amount rounded to the
 * currency's minor units, the currency, and the normalized account name and
 * description. The same function keys both the existing ledger and the imported
 * rows, so the two are always compared on identical terms.
 */
object MovementSignature {

    fun of(
        date: LocalDate,
        type: TransactionType,
        amount: BigDecimal,
        currency: Currency,
        accountName: String,
        description: String?,
    ): String {
        val minorUnits = MoneyMapper.toMinorUnits(amount, currency)
        return listOf(
            date.toString(),
            type.name,
            minorUnits.toString(),
            currency.currencyCode,
            normalize(accountName),
            normalize(description.orEmpty()),
        ).joinToString("|")
    }

    /** Trims, lowercases and collapses internal whitespace for a stable match. */
    private fun normalize(text: String): String =
        text.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

    private val WHITESPACE = "\\s+".toRegex()
}
