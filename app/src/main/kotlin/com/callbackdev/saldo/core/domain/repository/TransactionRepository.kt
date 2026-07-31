package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.AccountTotal
import com.callbackdev.saldo.core.domain.model.CategorySpendDayTotal
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.CounterpartyTotal
import com.callbackdev.saldo.core.domain.model.CurrencyMovementCount
import com.callbackdev.saldo.core.domain.model.DailyActivity
import com.callbackdev.saldo.core.domain.model.DailyNet
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.ForeignAccountDayTotal
import com.callbackdev.saldo.core.domain.model.ForeignCategoryDayTotal
import com.callbackdev.saldo.core.domain.model.ForeignDashboardDayFlows
import com.callbackdev.saldo.core.domain.model.ForeignMonthlyDayTotal
import com.callbackdev.saldo.core.domain.model.MonthlyNet
import com.callbackdev.saldo.core.domain.model.MonthlyTotal
import com.callbackdev.saldo.core.domain.model.SpendDayTotal
import com.callbackdev.saldo.core.domain.model.StatsPeriodTotals
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.quickentry.DescriptionUsage
import com.callbackdev.saldo.core.domain.recurrence.CandidateOccurrence
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceAmountGroup
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceDescriptionGroup
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

    /**
     * Confirmed movements dated on [day] or later (their own local day, ADR 7),
     * soonest first. With tomorrow as the argument, the movements that have not
     * happened yet: booked in the ledger, but outside every window closed on
     * today. Feeds the "Upcoming" list and the forecast tail (ADR 36).
     */
    fun observeTransactionsFrom(day: LocalDate): Flow<List<Transaction>>

    /**
     * Confirmed movements with a reminder due between [from] and [to]
     * (inclusive) that have not been reminded about yet for their own date.
     * One-shot: the caller is the daily worker.
     */
    suspend fun getDueReminders(from: LocalDate, to: LocalDate): List<Transaction>

    /** Records [date] as the movement date already reminded about. */
    suspend fun updateReminderWatermark(transactionId: Long, date: LocalDate)

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

    /**
     * Signed totals per counterparty and currency across the whole ledger
     * (ADR 34), unmerged: the same person written two ways is two rows here,
     * and merging them is the use case's job. Negative means the money is out.
     */
    fun observeCounterpartyTotals(): Flow<List<CounterpartyTotal>>

    /**
     * The counterparty names already used, most recently used first, for the
     * editor's autocompletion. Spellings are distinct as stored.
     */
    fun observeCounterpartyNames(): Flow<List<String>>

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

    /**
     * Ids of the categories used most often for movements of [type] since
     * [since], most used first. Powers the quick-entry sheet's preselected
     * category: it is a "what do I usually tap" shortcut, so unlike the
     * statistics queries it counts every currency, every account and even
     * movements excluded from statistics. Empty on a fresh install, where the
     * caller falls back to the user's own category order.
     */
    suspend fun mostUsedCategoryIds(
        type: TransactionType,
        since: Instant,
        limit: Int,
    ): List<Long>

    /**
     * Recent categorized movements of [type] since [since] whose description
     * contains [word] (byte-wise SQL prefilter on the typed and accent-folded
     * forms; the caller does the real whole-word match). Capped at [limit]
     * rows, most recent first: the quick text entry's category suggestion
     * reads habits, not the whole ledger (ADR 42).
     */
    suspend fun descriptionUsage(
        type: TransactionType,
        since: Instant,
        word: String,
        foldedWord: String,
        limit: Int,
    ): List<DescriptionUsage>

    /*
     * Recurrence-scan candidates (Fase 19, ADR 43). Aggregate selection runs
     * in SQL with declared caps; the per-group reads are capped too. All
     * one-shot on purpose: the scan runs only on the explicit user action in
     * the Recurrences hub, never from an observer.
     */

    /** Fixed-amount candidate groups since [since], at least [minOccurrences] rows each, capped at [limit]. */
    suspend fun recurrenceAmountGroups(
        since: Instant,
        minOccurrences: Int,
        limit: Int,
    ): List<RecurrenceAmountGroup>

    /** Description-keyed candidate groups; the variable-bill twin of [recurrenceAmountGroups]. */
    suspend fun recurrenceDescriptionGroups(
        since: Instant,
        minOccurrences: Int,
        limit: Int,
    ): List<RecurrenceDescriptionGroup>

    /** The movements of one fixed-amount candidate group, most recent first, capped at [limit]. */
    suspend fun recurrenceAmountGroupOccurrences(
        group: RecurrenceAmountGroup,
        since: Instant,
        limit: Int,
    ): List<CandidateOccurrence>

    /** The movements of one description-keyed candidate group, most recent first, capped at [limit]. */
    suspend fun recurrenceDescriptionGroupOccurrences(
        group: RecurrenceDescriptionGroup,
        since: Instant,
        limit: Int,
    ): List<CandidateOccurrence>

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

    /**
     * How many movements in `[start, end)` the statistics skip only because
     * they are not in [currency]. Feeds the notice that tells the user the
     * charts are not showing everything the period holds.
     */
    fun observeOtherCurrencyCount(start: Instant, end: Instant, currency: Currency): Flow<Int>

    /** Number of movements labelled with [categoryId]. */
    suspend fun countForCategory(categoryId: Long): Int

    /**
     * The movement types actually filed under [categoryId]; empty when the
     * category labels nothing. Tells a category deletion which reassignment
     * targets are compatible: only expenses were filed, only incomes, or both.
     */
    suspend fun transactionTypesForCategory(categoryId: Long): Set<TransactionType>

    /** Inserts a new movement (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(transaction: Transaction): Long

    /**
     * Inserts a new movement unless it collides with an already-generated
     * recurring occurrence (same rule and occurrence date); returns the new id,
     * or -1 when the movement already existed and nothing was written.
     */
    suspend fun insertIfAbsent(transaction: Transaction): Long

    suspend fun delete(transaction: Transaction)

    /** Deletes every movement whose id is in [ids] (tag links cascade). */
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * Atomically deletes the movements in [ids] and inserts [inserts] (new
     * movements, id == 0), returning the ids assigned to the inserts. Backs the
     * filtered delete that preserves account balances via carry-over adjustments:
     * the removals and the adjustments commit together.
     */
    suspend fun deleteAndInsert(ids: List<Long>, inserts: List<Transaction>): List<Long>

    /*
     * Foreign residue aggregates (ADR 40): what the single-currency queries
     * above leave out, per (currency, local day of the movement), so the
     * domain can convert each bucket at the rate of its own date. Rows whose
     * stored currency code is not a valid ISO 4217 code are dropped by the
     * implementation.
     */

    /** Foreign twin of [observeDashboardTotals], per (currency, local day). */
    fun observeForeignDashboardFlows(
        windows: DashboardWindows,
        currency: Currency,
    ): Flow<List<ForeignDashboardDayFlows>>

    /** Foreign twin of [observeCategoryTotals], per (category, currency, local day). */
    fun observeForeignCategoryTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<ForeignCategoryDayTotal>>

    /** Foreign twin of [observeAccountSpendTotals], per (account, currency, local day). */
    fun observeForeignAccountSpendTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<ForeignAccountDayTotal>>

    /** Foreign twin of [observeMonthlyTotals], per (currency, local day). */
    fun observeForeignMonthlyTotals(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<ForeignMonthlyDayTotal>>

    /** [observeOtherCurrencyCount] broken down per currency. */
    fun observeOtherCurrencyCounts(
        start: Instant,
        end: Instant,
        currency: Currency,
    ): Flow<List<CurrencyMovementCount>>

    /**
     * Budget-relevant spend per (currency, local day), every currency
     * included: one subscription serves every budget whatever its currency.
     * Same filters as [observeStatsSpendTotal].
     */
    fun observeSpendByCurrencyDay(start: Instant, end: Instant): Flow<List<SpendDayTotal>>

    /** One-shot variant of [observeSpendByCurrencyDay] for the budget threshold check. */
    suspend fun getSpendByCurrencyDay(start: Instant, end: Instant): List<SpendDayTotal>

    /** Per-category twin of [observeSpendByCurrencyDay]. */
    fun observeCategorySpendByCurrencyDay(
        start: Instant,
        end: Instant,
    ): Flow<List<CategorySpendDayTotal>>

    /** One-shot variant of [observeCategorySpendByCurrencyDay]. */
    suspend fun getCategorySpendByCurrencyDay(
        start: Instant,
        end: Instant,
    ): List<CategorySpendDayTotal>
}
