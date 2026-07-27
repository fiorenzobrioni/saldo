package com.callbackdev.saldo.core.domain.transaction

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.money.TransactionSign
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Builds the expense/income movement of the quick-entry paths (today the home
 * screen widget), applying the same rules the full editor applies: amount
 * rescaled to the account currency, sign from [TransactionSign], and the zone
 * offset resolved at the movement's own date and time rather than "now" (so a
 * movement dated across a DST boundary keeps the offset that was in force then).
 *
 * Transfers and adjustments are deliberately out of scope here: they need the
 * destination leg and the balance arithmetic that only the full editor and
 * [com.callbackdev.saldo.core.domain.usecase] offer.
 */
object QuickTransactionFactory {

    fun create(
        type: TransactionType,
        amount: BigDecimal,
        account: Account,
        categoryId: Long?,
        dateTime: LocalDateTime,
        zone: ZoneId,
        description: String? = null,
    ): Transaction {
        val digits = MoneyMapper.fractionDigits(account.currency)
        val scaled = amount.setScale(digits, RoundingMode.HALF_UP)
        val offset = zone.rules.getOffset(dateTime)
        return Transaction(
            type = type,
            amount = TransactionSign.signed(type, scaled),
            currency = account.currency,
            accountId = account.id,
            timestamp = dateTime.toInstant(offset),
            zoneOffset = offset,
            categoryId = categoryId,
            description = description?.trim()?.ifEmpty { null },
        )
    }
}
