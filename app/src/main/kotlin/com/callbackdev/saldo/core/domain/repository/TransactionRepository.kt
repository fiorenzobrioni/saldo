package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.AccountTotal
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.DailyActivity
import com.callbackdev.saldo.core.domain.model.DailyNet
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.MonthlyNet
import com.callbackdev.saldo.core.domain.model.MonthlyTotal
import com.callbackdev.saldo.core.domain.model.StatsPeriodTotals
import com.callbackdev.saldo.core.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/** Read/write access to movements and their statistical aggregates. */
@Suppress("TooManyFunctions") // A data-access interface naturally has many queries.
interface TransactionRepository {

    /** Confirmed movements, most recent first (pending recurring movements excluded). */
    fun observeTransactions(): Flow<List<Transaction>>

    /** Recurring movements awaiting confirmation, oldest first. */
    fun observePendingTransactions(): Flow<List<Transaction>>

    /** Movements whose instant is in `[start, end)`, most recent first. */
    fun observeTransactionsBetween(start: Instant, end: Instant): Flow<List<Transaction>>

    /** Movements that touch [accountId] as source or transfer destination. */
    fun observeTransactionsForAccount(accountId: Long): Flow<List<Transaction>>

    /**
     * Per-category totals for expenses and incomes in `[start, end)`, restricted
     * to [currency]. Transfers and adjustments are excluded at query level
     * (PLANNING ADR 8), as are movements flagged out of statistics.
     */
    fun observeCategoryTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<CategoryTotal>>

    /**
     * Per-month expense/income totals in `[start, end)` (the movement's own
     * local month, ADR 7), restricted to [currency]. Statistics rules: refunds
     * net the spend instead of counting as income; transfers, adjustments,
     * excluded-from-stats and pending movements never count.
     */
    fun observeMonthlyTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<MonthlyTotal>>

    /**
     * Per-account signed spend totals in `[start, end)`, restricted to
     * [currency], with the same statistics rules as [observeMonthlyTotals].
     */
    fun observeAccountSpendTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<AccountTotal>>

    /**
     * Total signed spend in `[start, end)`, restricted to [currency], with the
     * same statistics rules as [observeMonthlyTotals] (expenses negative,
     * refunds netting them). Zero when nothing matches. Drives overall budget
     * progress; unlike [observeDashboardTotals] this is not a cash figure.
     */
    fun observeStatsSpendTotal(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<BigDecimal>

    /** One-shot variant of [observeStatsSpendTotal] for the budget threshold check. */
    suspend fun getStatsSpendTotal(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): BigDecimal

    /**
     * Per-category signed spend totals in `[start, end)`, restricted to
     * [currency]: expenses plus their refunds only, so pure incomes in a BOTH
     * category never offset its budget. Drives category budget progress.
     */
    fun observeCategorySpendTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<CategoryTotal>>

    /** One-shot variant of [observeCategorySpendTotals] for the budget threshold check. */
    suspend fun getCategorySpendTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): List<CategoryTotal>

    /**
     * Net effect per local month on the balance of the accounts included in
     * the total (every type, transfer legs included; cash figure, so
     * excluded-from-stats movements still count), across the whole ledger.
     */
    fun observeMonthlyNetChanges(currency: Currency): Flow<List<MonthlyNet>>

    /**
     * Net effect per local day (ADR 7) on the balance of the included accounts,
     * limited to days in `[start, endExclusive)`. Same rules as
     * [observeMonthlyNetChanges]; days without movements are absent.
     */
    fun observeDailyNetChanges(
        currency: Currency,
        start: LocalDate,
        endExclusive: LocalDate,
    ): Flow<List<DailyNet>>

    /**
     * Net effect of every movement whose local day precedes [start] on the
     * balance of the included accounts, same rules as
     * [observeMonthlyNetChanges]. Zero when nothing matches.
     */
    fun observeNetChangeBefore(currency: Currency, start: LocalDate): Flow<BigDecimal>

    /**
     * One-shot statistics totals of `[start, end)` in [currency]: same rules
     * as [observeMonthlyTotals], without the per-month grouping. Zeros when
     * nothing matches. Feeds the monthly recap.
     */
    suspend fun getStatsPeriodTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): StatsPeriodTotals

    /** One-shot twin of [observeCategoryTotals], for the monthly recap. */
    suspend fun getCategoryTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): List<CategoryTotal>

    /**
     * The single biggest expense of `[start, end)` in [currency] under
     * statistics rules; null when the period has no expenses.
     */
    suspend fun getBiggestExpense(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Transaction?

    /**
     * Per-local-day movement count and signed spend (statistics rules) in
     * `[start, end)`, days without movements absent. Feeds the recap's
     * busiest-day figure.
     */
    suspend fun getDailyActivity(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): List<DailyActivity>

    /**
     * Signed total of the period's rule-generated expenses under statistics
     * rules; zero when none. Feeds the monthly recap.
     */
    suspend fun getRecurringSpendTotal(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): BigDecimal

    /** The latest confirmed movements, capped in SQL. */
    fun observeRecentTransactions(limit: Int): Flow<List<Transaction>>

    /**
     * The dashboard's aggregate figures for [windows], restricted to [currency],
     * computed by the database in a single query. Cash figures: movements
     * flagged out of statistics still count (unlike [observeCategoryTotals]).
     */
    fun observeDashboardTotals(
        windows: DashboardWindows,
        currency: Currency,
    ): Flow<DashboardTotals>

    suspend fun getTransaction(id: Long): Transaction?

    /** Number of movements that touch [accountId] as source or transfer destination. */
    suspend fun countForAccount(accountId: Long): Int

    /**
     * Signed sum of an account's own movements (source side only) in
     * `[start, end)`, in [currency], confirmed movements only. The credit card
     * statement amount owed for a closed cycle is the negation of this sum.
     */
    suspend fun sumOwnMovements(
        accountId: Long,
        start: Instant,
        end: Instant,
        currency: Currency,
    ): BigDecimal

    /** Number of movements labelled with [categoryId]. */
    suspend fun countForCategory(categoryId: Long): Int

    /** Inserts a new movement (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(transaction: Transaction): Long

    /**
     * Inserts a new movement unless it collides with an already-generated
     * recurring occurrence (same rule and occurrence date); returns the new id,
     * or -1 when the movement already existed and nothing was written.
     */
    suspend fun insertIfAbsent(transaction: Transaction): Long

    suspend fun delete(transaction: Transaction)
}
