package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.relation.AccountWithBalanceRow
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.CreditCardConfig
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

fun AccountEntity.toDomain(): Account {
    val currency = Currency.getInstance(currency)
    return Account(
        id = id,
        name = name,
        type = type,
        currency = currency,
        initialBalance = MoneyMapper.toAmount(initialBalanceMinor, currency),
        color = color,
        icon = icon,
        isIncludedInTotal = isIncludedInTotal,
        isIncludedInBudget = isIncludedInBudget,
        isArchived = isArchived,
        sortOrder = sortOrder,
        createdAt = Instant.ofEpochMilli(createdAtEpochMilli),
        creditCard = toCreditCardConfig(currency),
    )
}

/**
 * Builds the [CreditCardConfig] from the flat columns. Present only when both
 * cycle days are set (they are written together for a credit card); any other
 * account leaves them null and has no config.
 */
private fun AccountEntity.toCreditCardConfig(currency: Currency): CreditCardConfig? {
    val closing = statementClosingDay
    val due = paymentDueDay
    if (closing == null || due == null) return null
    return CreditCardConfig(
        statementClosingDay = closing,
        paymentDueDay = due,
        linkedAccountId = linkedAccountId,
        creditLimit = creditLimitMinor?.let { MoneyMapper.toAmount(it, currency) },
        autoPost = statementAutoPost,
        lastSettledClosing = lastSettledClosingEpochDay?.let(LocalDate::ofEpochDay),
    )
}

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type,
    currency = currency.currencyCode,
    initialBalanceMinor = MoneyMapper.toMinorUnits(initialBalance, currency),
    color = color,
    icon = icon,
    isIncludedInTotal = isIncludedInTotal,
    isIncludedInBudget = isIncludedInBudget,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAtEpochMilli = createdAt.toEpochMilli(),
    creditLimitMinor = creditCard?.creditLimit?.let { MoneyMapper.toMinorUnits(it, currency) },
    statementClosingDay = creditCard?.statementClosingDay,
    paymentDueDay = creditCard?.paymentDueDay,
    linkedAccountId = creditCard?.linkedAccountId,
    statementAutoPost = creditCard?.autoPost ?: false,
    lastSettledClosingEpochDay = creditCard?.lastSettledClosing?.toEpochDay(),
)

fun AccountWithBalanceRow.toDomain(): AccountWithBalance {
    val account = account.toDomain()
    return AccountWithBalance(
        account = account,
        balance = MoneyMapper.toAmount(balanceMinor, account.currency),
    )
}
