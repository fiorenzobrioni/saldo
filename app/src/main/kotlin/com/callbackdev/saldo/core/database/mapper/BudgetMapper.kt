package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.BudgetEntity
import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.time.YearMonth
import java.util.Currency

private const val MONTHS_PER_YEAR = 12

/** Storage form of a [YearMonth]: the proleptic month (year * 12 + month - 1). */
fun YearMonth.toEpochMonth(): Long = year * MONTHS_PER_YEAR.toLong() + (monthValue - 1)

fun yearMonthOfEpochMonth(epochMonth: Long): YearMonth = YearMonth.of(
    Math.floorDiv(epochMonth, MONTHS_PER_YEAR.toLong()).toInt(),
    Math.floorMod(epochMonth, MONTHS_PER_YEAR.toLong()).toInt() + 1,
)

fun BudgetEntity.toDomain(): Budget {
    val currency = Currency.getInstance(currency)
    return Budget(
        id = id,
        categoryId = categoryId,
        amount = MoneyMapper.toAmount(amountMinor, currency),
        currency = currency,
        lastNotified80Month = lastNotified80EpochMonth?.let(::yearMonthOfEpochMonth),
        lastNotified100Month = lastNotified100EpochMonth?.let(::yearMonthOfEpochMonth),
    )
}

fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    categoryId = categoryId,
    amountMinor = MoneyMapper.toMinorUnits(amount, currency),
    currency = currency.currencyCode,
    lastNotified80EpochMonth = lastNotified80Month?.toEpochMonth(),
    lastNotified100EpochMonth = lastNotified100Month?.toEpochMonth(),
)
