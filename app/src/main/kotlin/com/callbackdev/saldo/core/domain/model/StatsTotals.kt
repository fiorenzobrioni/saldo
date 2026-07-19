package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Statistics totals of one calendar month (the movement's own local month,
 * ADR 7). [expense] keeps the signed convention (<= 0 in a normal month;
 * refunds are netted in and can push it above zero); [income] excludes
 * refunds and is >= 0.
 */
data class MonthlyTotal(
    val month: YearMonth,
    val expense: BigDecimal,
    val income: BigDecimal,
)

/** Signed spend total of one account over a period (refunds netted). */
data class AccountTotal(
    val accountId: Long,
    val total: BigDecimal,
    val count: Int,
)

/** Net effect of one month's movements on the total balance, every type counted. */
data class MonthlyNet(
    val month: YearMonth,
    val net: BigDecimal,
)

/** The total balance of the included accounts at the end of [month]. */
data class MonthlyBalance(
    val month: YearMonth,
    val balance: BigDecimal,
)

/**
 * Statistics totals of one arbitrary period: [expense] keeps the signed
 * convention of [MonthlyTotal] (refunds netted in), [income] excludes refunds.
 */
data class StatsPeriodTotals(
    val expense: BigDecimal,
    val income: BigDecimal,
)

/**
 * One local day's statistics activity: movement count and signed spend total
 * (refunds netted in), for the recap's busiest-day figure.
 */
data class DailyActivity(
    val date: LocalDate,
    val count: Int,
    val spend: BigDecimal,
)

/** Net effect of one local day's movements on the total balance, every type counted. */
data class DailyNet(
    val date: LocalDate,
    val net: BigDecimal,
)

/** The total balance of the included accounts at the end of [date]. */
data class DailyBalance(
    val date: LocalDate,
    val balance: BigDecimal,
)
