package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.relation.AccountWithBalanceRow
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.time.Instant
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
        isArchived = isArchived,
        sortOrder = sortOrder,
        createdAt = Instant.ofEpochMilli(createdAtEpochMilli),
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
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAtEpochMilli = createdAt.toEpochMilli(),
)

fun AccountWithBalanceRow.toDomain(): AccountWithBalance {
    val account = account.toDomain()
    return AccountWithBalance(
        account = account,
        balance = MoneyMapper.toAmount(balanceMinor, account.currency),
    )
}
