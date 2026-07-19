package com.callbackdev.saldo.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.dao.RecurringRuleDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.entity.RecurringRuleEntity
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Instrumented tests for the Phase 10.1 SQL: the daily balance feed of the
 * dashboard sparkline (per-local-day net changes, ADR 7) and the one-shot
 * recap queries (period totals, biggest expense, daily activity, recurring
 * spend). Written to run on a device; CI has no emulator.
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoRecapTest {

    private lateinit var database: SaldoDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var recurringRuleDao: RecurringRuleDao

    private var accountId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SaldoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountDao = database.accountDao()
        transactionDao = database.transactionDao()
        recurringRuleDao = database.recurringRuleDao()
        accountId = runBlocking { accountDao.insert(account()) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun account(
        included: Boolean = true,
        archived: Boolean = false,
        currency: String = "EUR",
    ) = AccountEntity(
        name = "acc",
        type = AccountType.CHECKING,
        currency = currency,
        initialBalanceMinor = 0L,
        isIncludedInTotal = included,
        isArchived = archived,
    )

    /** Noon UTC of the given day: comfortably inside the day at any offset used here. */
    private fun instantOf(year: Int, month: Int, day: Int): Instant =
        LocalDateTime.of(year, month, day, 12, 0).toInstant(ZoneOffset.UTC)

    @Suppress("LongParameterList")
    private fun movement(
        type: TransactionType,
        amountMinor: Long,
        timestamp: Instant = instantOf(2026, 6, 15),
        offsetSeconds: Int = 0,
        account: Long = accountId,
        categoryId: Long? = null,
        transferAccountId: Long? = null,
        transferAmountMinor: Long? = null,
        excluded: Boolean = false,
        refund: Boolean = false,
        pending: Boolean = false,
        currency: String = "EUR",
        description: String? = null,
        recurringRuleId: Long? = null,
        recurringOccurrenceEpochDay: Long? = null,
    ) = TransactionEntity(
        type = type,
        amountMinor = amountMinor,
        currency = currency,
        accountId = account,
        timestampEpochMilli = timestamp.toEpochMilli(),
        zoneOffsetSeconds = offsetSeconds,
        categoryId = categoryId,
        transferAccountId = transferAccountId,
        transferAmountMinor = transferAmountMinor,
        transferCurrency = transferAccountId?.let { currency },
        isExcludedFromStats = excluded,
        isRefund = refund,
        isPending = pending,
        description = description,
        recurringRuleId = recurringRuleId,
        recurringOccurrenceEpochDay = recurringOccurrenceEpochDay,
    )

    private fun epochDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).toEpochDay()

    private val monthStart = instantOf(2026, 6, 1).toEpochMilli()
    private val monthEnd = instantOf(2026, 7, 1).toEpochMilli()

    @Test
    fun dailyNetChangesGroupByLocalDayAndCountBothTransferLegs() = runBlocking {
        val other = accountDao.insert(account())
        // 23:00 UTC on the 14th with a +2h offset lands on the local 15th.
        transactionDao.insert(
            movement(
                TransactionType.EXPENSE,
                -10_00,
                timestamp = LocalDateTime.of(2026, 6, 14, 23, 0).toInstant(ZoneOffset.UTC),
                offsetSeconds = 2 * 3600,
            ),
        )
        // Transfer: -30 on the source leg, +30 on the destination leg, same day.
        transactionDao.insert(
            movement(
                TransactionType.TRANSFER,
                -30_00,
                timestamp = instantOf(2026, 6, 15),
                transferAccountId = other,
                transferAmountMinor = 30_00,
            ),
        )
        // Pending never counts; cash basis keeps excluded-from-stats in.
        transactionDao.insert(movement(TransactionType.EXPENSE, -99_00, pending = true))
        transactionDao.insert(movement(TransactionType.EXPENSE, -5_00, excluded = true))

        val rows = transactionDao.observeDailyNetChanges(
            startEpochDay = epochDay(2026, 6, 1),
            endEpochDayExclusive = epochDay(2026, 7, 1),
            currency = "EUR",
        ).first()

        // One row for the local 15th: -10 (offset move) -30 +30 (both legs) -5 (excluded).
        assertEquals(1, rows.size)
        assertEquals(epochDay(2026, 6, 15), rows.single().epochDay)
        assertEquals(-15_00L, rows.single().netMinor)
    }

    @Test
    fun dailyNetChangesSkipExcludedAccountsAndOtherCurrencies() = runBlocking {
        val excludedAccount = accountDao.insert(account(included = false))
        val usdAccount = accountDao.insert(account(currency = "USD"))
        transactionDao.insert(movement(TransactionType.EXPENSE, -10_00, account = excludedAccount))
        transactionDao.insert(
            movement(TransactionType.EXPENSE, -20_00, account = usdAccount, currency = "USD"),
        )
        transactionDao.insert(movement(TransactionType.EXPENSE, -1_00))

        val rows = transactionDao.observeDailyNetChanges(
            startEpochDay = epochDay(2026, 6, 1),
            endEpochDayExclusive = epochDay(2026, 7, 1),
            currency = "EUR",
        ).first()

        assertEquals(-1_00L, rows.single().netMinor)
    }

    @Test
    fun netChangeBeforeSumsEverythingBeforeTheWindow() = runBlocking {
        transactionDao.insert(movement(TransactionType.INCOME, 50_00, timestamp = instantOf(2026, 5, 20)))
        transactionDao.insert(movement(TransactionType.EXPENSE, -10_00, timestamp = instantOf(2026, 6, 2)))

        val before = transactionDao.observeNetChangeBefore(epochDay(2026, 6, 1), "EUR").first()

        assertEquals(50_00L, before)
    }

    @Test
    fun statsPeriodTotalsMatchTheObserveMonthlyTotalsFilters() = runBlocking {
        transactionDao.insert(movement(TransactionType.EXPENSE, -40_00, categoryId = null))
        transactionDao.insert(movement(TransactionType.INCOME, 100_00))
        // Refund nets the spend instead of counting as income.
        transactionDao.insert(movement(TransactionType.INCOME, 15_00, refund = true))
        transactionDao.insert(movement(TransactionType.EXPENSE, -9_00, excluded = true))
        transactionDao.insert(movement(TransactionType.EXPENSE, -8_00, pending = true))

        val row = transactionDao.getStatsPeriodTotals(monthStart, monthEnd, "EUR")
        val monthly = transactionDao.observeMonthlyTotals(monthStart, monthEnd, "EUR").first()

        assertEquals(-25_00L, row.expenseMinor)
        assertEquals(100_00L, row.incomeMinor)
        assertEquals(monthly.single().expenseMinor, row.expenseMinor)
        assertEquals(monthly.single().incomeMinor, row.incomeMinor)
    }

    @Test
    fun biggestExpenseSkipsExcludedPendingAndTransfers() = runBlocking {
        val other = accountDao.insert(account())
        transactionDao.insert(movement(TransactionType.EXPENSE, -60_00, description = "biggest"))
        transactionDao.insert(movement(TransactionType.EXPENSE, -20_00))
        transactionDao.insert(movement(TransactionType.EXPENSE, -500_00, excluded = true))
        transactionDao.insert(movement(TransactionType.EXPENSE, -400_00, pending = true))
        transactionDao.insert(
            movement(
                TransactionType.TRANSFER,
                -900_00,
                transferAccountId = other,
                transferAmountMinor = 900_00,
            ),
        )

        val biggest = transactionDao.getBiggestExpense(monthStart, monthEnd, "EUR")

        assertEquals("biggest", biggest?.description)
        assertEquals(-60_00L, biggest?.amountMinor)
    }

    @Test
    fun biggestExpenseIsNullWithoutExpenses() = runBlocking {
        transactionDao.insert(movement(TransactionType.INCOME, 10_00))

        assertNull(transactionDao.getBiggestExpense(monthStart, monthEnd, "EUR"))
    }

    @Test
    fun dailyActivityGroupsByLocalDayWithSpendNetting() = runBlocking {
        transactionDao.insert(movement(TransactionType.EXPENSE, -10_00, timestamp = instantOf(2026, 6, 3)))
        transactionDao.insert(movement(TransactionType.EXPENSE, -20_00, timestamp = instantOf(2026, 6, 3)))
        transactionDao.insert(
            movement(TransactionType.INCOME, 5_00, timestamp = instantOf(2026, 6, 3), refund = true),
        )
        transactionDao.insert(movement(TransactionType.INCOME, 100_00, timestamp = instantOf(2026, 6, 9)))

        val rows = transactionDao.getDailyActivity(monthStart, monthEnd, "EUR")

        assertEquals(2, rows.size)
        val busiest = rows.first { it.epochDay == epochDay(2026, 6, 3) }
        assertEquals(3, busiest.count)
        assertEquals(-25_00L, busiest.spendMinor)
        val incomeDay = rows.first { it.epochDay == epochDay(2026, 6, 9) }
        assertEquals(1, incomeDay.count)
        assertEquals(0L, incomeDay.spendMinor)
    }

    @Test
    fun recurringSpendCountsOnlyRuleGeneratedExpenses() = runBlocking {
        val ruleId = recurringRuleDao.insert(
            RecurringRuleEntity(
                name = "sub",
                type = TransactionType.EXPENSE,
                currency = "EUR",
                accountId = accountId,
                frequency = RecurrenceFrequency.MONTHLY,
                startDateEpochDay = epochDay(2026, 1, 1),
                amountMinor = -12_99,
            ),
        )
        transactionDao.insert(
            movement(
                TransactionType.EXPENSE,
                -12_99,
                recurringRuleId = ruleId,
                recurringOccurrenceEpochDay = epochDay(2026, 6, 7),
            ),
        )
        transactionDao.insert(movement(TransactionType.EXPENSE, -50_00))
        transactionDao.insert(
            movement(
                TransactionType.EXPENSE,
                -7_00,
                pending = true,
                recurringRuleId = ruleId,
                recurringOccurrenceEpochDay = epochDay(2026, 6, 20),
            ),
        )

        assertEquals(
            -12_99L,
            transactionDao.getRecurringSpendTotal(monthStart, monthEnd, "EUR"),
        )
    }
}
