package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.RecurringRuleEntity
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.time.LocalDate
import java.util.Currency

fun RecurringRuleEntity.toDomain(): RecurringRule {
    val currency = Currency.getInstance(currency)
    val transferCurrency = transferCurrency?.let(Currency::getInstance)
    return RecurringRule(
        id = id,
        name = name,
        type = type,
        currency = currency,
        accountId = accountId,
        frequency = frequency,
        startDate = LocalDate.ofEpochDay(startDateEpochDay),
        amount = amountMinor?.let { MoneyMapper.toAmount(it, currency) },
        categoryId = categoryId,
        dayOfReference = dayOfReference,
        endDate = endDateEpochDay?.let(LocalDate::ofEpochDay),
        mode = mode,
        isVariableAmount = isVariableAmount,
        lastGeneratedDate = lastGeneratedEpochDay?.let(LocalDate::ofEpochDay),
        color = color,
        icon = icon,
        note = note,
        lastReminderDate = lastReminderEpochDay?.let(LocalDate::ofEpochDay),
        transferAccountId = transferAccountId,
        transferAmount = transferAmountMinor?.let {
            MoneyMapper.toAmount(it, transferCurrency ?: currency)
        },
        transferCurrency = transferCurrency,
        isPaused = isPaused,
    )
}

fun RecurringRule.toEntity(): RecurringRuleEntity = RecurringRuleEntity(
    id = id,
    name = name,
    type = type,
    currency = currency.currencyCode,
    accountId = accountId,
    frequency = frequency,
    startDateEpochDay = startDate.toEpochDay(),
    amountMinor = amount?.let { MoneyMapper.toMinorUnits(it, currency) },
    categoryId = categoryId,
    dayOfReference = dayOfReference,
    endDateEpochDay = endDate?.toEpochDay(),
    mode = mode,
    isVariableAmount = isVariableAmount,
    lastGeneratedEpochDay = lastGeneratedDate?.toEpochDay(),
    color = color,
    icon = icon,
    note = note,
    lastReminderEpochDay = lastReminderDate?.toEpochDay(),
    transferAccountId = transferAccountId,
    transferAmountMinor = transferAmount?.let {
        MoneyMapper.toMinorUnits(it, transferCurrency ?: currency)
    },
    transferCurrency = transferCurrency?.currencyCode,
    isPaused = isPaused,
)
