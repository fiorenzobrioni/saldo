package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.SavingsGoalEntity
import com.callbackdev.saldo.core.domain.model.SavingsGoal
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.time.LocalDate
import java.util.Currency

fun SavingsGoalEntity.toDomain(): SavingsGoal {
    val currency = Currency.getInstance(currency)
    return SavingsGoal(
        id = id,
        name = name,
        targetAmount = MoneyMapper.toAmount(targetAmountMinor, currency),
        currency = currency,
        accountId = accountId,
        targetDate = targetDateEpochDay?.let(LocalDate::ofEpochDay),
        color = color,
        icon = icon,
        sortOrder = sortOrder,
    )
}

fun SavingsGoal.toEntity(): SavingsGoalEntity = SavingsGoalEntity(
    id = id,
    name = name,
    targetAmountMinor = MoneyMapper.toMinorUnits(targetAmount, currency),
    currency = currency.currencyCode,
    accountId = accountId,
    targetDateEpochDay = targetDate?.toEpochDay(),
    color = color,
    icon = icon,
    sortOrder = sortOrder,
)
