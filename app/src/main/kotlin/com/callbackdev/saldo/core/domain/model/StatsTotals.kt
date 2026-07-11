package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
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
