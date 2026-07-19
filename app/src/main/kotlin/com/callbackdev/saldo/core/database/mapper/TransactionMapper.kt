package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.database.relation.AccountTotalRow
import com.callbackdev.saldo.core.database.relation.CategoryTotalRow
import com.callbackdev.saldo.core.database.relation.DailyActivityRow
import com.callbackdev.saldo.core.database.relation.DailyNetRow
import com.callbackdev.saldo.core.database.relation.DashboardTotalsRow
import com.callbackdev.saldo.core.database.relation.MonthlyNetRow
import com.callbackdev.saldo.core.database.relation.MonthlyTotalRow
import com.callbackdev.saldo.core.domain.model.AccountTotal
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.DailyActivity
import com.callbackdev.saldo.core.domain.model.DailyNet
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.MonthlyNet
import com.callbackdev.saldo.core.domain.model.MonthlyTotal
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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
        isPending = isPending,
        recurringOccurrenceDate = recurringOccurrenceEpochDay?.let(LocalDate::ofEpochDay),
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
    isPending = isPending,
    recurringOccurrenceEpochDay = recurringOccurrenceDate?.toEpochDay(),
)

fun CategoryTotalRow.toDomain(currency: Currency): CategoryTotal = CategoryTotal(
    categoryId = categoryId,
    total = MoneyMapper.toAmount(totalMinor, currency),
    count = count,
)

fun DashboardTotalsRow.toDomain(currency: Currency): DashboardTotals = DashboardTotals(
    today = PeriodTotals(
        spend = MoneyMapper.toAmount(todaySpendMinor ?: 0L, currency),
        income = MoneyMapper.toAmount(todayIncomeMinor ?: 0L, currency),
    ),
    month = PeriodTotals(
        spend = MoneyMapper.toAmount(monthSpendMinor ?: 0L, currency),
        income = MoneyMapper.toAmount(monthIncomeMinor ?: 0L, currency),
    ),
    // Expense sums are negative; the to-date figures are positive magnitudes.
    monthToDateSpend = MoneyMapper.toAmount(monthToDateSpendMinor ?: 0L, currency).negate(),
    monthToDateNonRecurringSpend =
        MoneyMapper.toAmount(monthToDateNonRecurringSpendMinor ?: 0L, currency).negate(),
    previousMonthToDateSpend = MoneyMapper.toAmount(previousToDateSpendMinor ?: 0L, currency).negate(),
)

fun MonthlyTotalRow.toDomain(currency: Currency): MonthlyTotal = MonthlyTotal(
    month = YearMonth.parse(month),
    expense = MoneyMapper.toAmount(expenseMinor, currency),
    income = MoneyMapper.toAmount(incomeMinor, currency),
)

fun AccountTotalRow.toDomain(currency: Currency): AccountTotal = AccountTotal(
    accountId = accountId,
    total = MoneyMapper.toAmount(totalMinor, currency),
    count = count,
)

fun MonthlyNetRow.toDomain(currency: Currency): MonthlyNet = MonthlyNet(
    month = YearMonth.parse(month),
    net = MoneyMapper.toAmount(netMinor, currency),
)

fun DailyNetRow.toDomain(currency: Currency): DailyNet = DailyNet(
    date = LocalDate.ofEpochDay(epochDay),
    net = MoneyMapper.toAmount(netMinor, currency),
)

fun DailyActivityRow.toDomain(currency: Currency): DailyActivity = DailyActivity(
    date = LocalDate.ofEpochDay(epochDay),
    count = count,
    spend = MoneyMapper.toAmount(spendMinor ?: 0L, currency),
)
