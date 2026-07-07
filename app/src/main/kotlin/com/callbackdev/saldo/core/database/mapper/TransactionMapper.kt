package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.database.relation.CategoryTotalRow
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.time.Instant
import java.time.ZoneOffset
import java.util.Currency

fun TransactionEntity.toDomain(): Transaction {
    val currency = Currency.getInstance(currency)
    val transferCurrency = transferCurrency?.let(Currency::getInstance)
    return Transaction(
        id = id,
        type = type,
        amount = MoneyMapper.toAmount(amountMinor, currency),
        currency = currency,
        accountId = accountId,
        timestamp = Instant.ofEpochMilli(timestampEpochMilli),
        zoneOffset = ZoneOffset.ofTotalSeconds(zoneOffsetSeconds),
        transferAccountId = transferAccountId,
        transferAmount = transferAmountMinor?.let {
            MoneyMapper.toAmount(it, transferCurrency ?: currency)
        },
        transferCurrency = transferCurrency,
        categoryId = categoryId,
        description = description,
        note = note,
        isExcludedFromStats = isExcludedFromStats,
        isRefund = isRefund,
        recurringRuleId = recurringRuleId,
    )
}

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    type = type,
    amountMinor = MoneyMapper.toMinorUnits(amount, currency),
    currency = currency.currencyCode,
    accountId = accountId,
    timestampEpochMilli = timestamp.toEpochMilli(),
    zoneOffsetSeconds = zoneOffset.totalSeconds,
    transferAccountId = transferAccountId,
    transferAmountMinor = transferAmount?.let {
        MoneyMapper.toMinorUnits(it, transferCurrency ?: currency)
    },
    transferCurrency = transferCurrency?.currencyCode,
    categoryId = categoryId,
    description = description,
    note = note,
    isExcludedFromStats = isExcludedFromStats,
    isRefund = isRefund,
    recurringRuleId = recurringRuleId,
)

fun CategoryTotalRow.toDomain(currency: Currency): CategoryTotal = CategoryTotal(
    categoryId = categoryId,
    total = MoneyMapper.toAmount(totalMinor, currency),
    count = count,
)
