package com.callbackdev.saldo.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Instrumented tests for the Phase 7 statistics SQL: monthly totals,
 * per-account spend and monthly net changes. Verifies at query level the
 * exclusion of TRANSFER/ADJUSTMENT, pending and excluded-from-stats movements,
 * the refund netting, the currency restriction, and the per-row-offset month
 * grouping (ADR 7).
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoStatsTest {

    private lateinit var database: SaldoDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var transactionDao: TransactionDao

    private var accountId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SaldoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountDao = database.accountDao()
        transactionDao = database.transactionDao()
        accountId = runBlocking { accountDao.insert(account()) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun account(
        initialMinor: Long = 0L,
        included: Boolean = true,
        archived: Boolean = false,
        currency: String = "EUR",
    ) = AccountEntity(
        name = "acc",
        type = AccountType.CHECKING,
        currency = currency,
        initialBalanceMinor = initialMinor,
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
    )

    private val windowStart = instantOf(2026, 1, 1).toEpochMilli()
    private val windowEnd = instantOf(2026, 12, 31).toEpochMilli()

    @Test
    fun monthlyTotalsExcludeTransfersAdjustmentsPendingAndFlagged() = runBlocking {
        val other = accountDao.insert(account())
        transactionDao.insert(movement(TransactionType.EXPENSE, -10_00))
        transactionDao.insert(movement(TransactionType.INCOME, 20_00))
        transactionDao.insert(movement(TransactionType.EXPENSE, -99_00, excluded = true))
        transactionDao.insert(movement(TransactionType.EXPENSE, -77_00, pending = true))
        transactionDao.insert(movement(TransactionType.ADJUSTMENT, 55_00))
        transactionDao.insert(
            movement(
                TransactionType.TRANSFER,
                -44_00,
                transferAccountId = other,
                transferAmountMinor = 44_00,
            ),
        )
        transactionDao.insert(movement(TransactionType.EXPENSE, -66_00, currency = "USD"))

        val rows = transactionDao.observeMonthlyTotals(windowStart, windowEnd, "EUR").first()

        assertEquals(1, rows.size)
        assertEquals("2026-06", rows[0].month)
        assertEquals(-10_00L, rows[0].expenseMinor)
        assertEquals(20_00L, rows[0].incomeMinor)
    }

    @Test
    fun monthlyTotalsCountRefundsAsNegativeSpendNotIncome() = runBlocking {
        transactionDao.insert(movement(TransactionType.EXPENSE, -50_00))
        transactionDao.insert(movement(TransactionType.INCOME, 15_00, refund = true))
        transactionDao.insert(movement(TransactionType.INCOME, 100_00))

        val rows = transactionDao.observeMonthlyTotals(windowStart, windowEnd, "EUR").first()

        assertEquals(-35_00L, rows[0].expenseMinor)
        assertEquals(100_00L, rows[0].incomeMinor)
    }

    @Test
    fun monthlyTotalsGroupByTheMovementOwnOffset() = runBlocking {
        // 23:30 UTC on June 30th is already July 1st at UTC+2, but still
        // June 30th at UTC-1 (ADR 7: the saved offset decides the month).
        val lateJune = LocalDateTime.of(2026, 6, 30, 23, 30).toInstant(ZoneOffset.UTC)
        transactionDao.insert(
            movement(TransactionType.EXPENSE, -10_00, timestamp = lateJune, offsetSeconds = 7_200),
        )
        transactionDao.insert(
            movement(TransactionType.EXPENSE, -5_00, timestamp = lateJune, offsetSeconds = -3_600),
        )

        val rows = transactionDao.observeMonthlyTotals(windowStart, windowEnd, "EUR").first()

        assertEquals(listOf("2026-06", "2026-07"), rows.map { it.month })
        assertEquals(-5_00L, rows[0].expenseMinor)
        assertEquals(-10_00L, rows[1].expenseMinor)
    }

    @Test
    fun accountSpendTotalsGroupSpendPerAccountAndNetRefunds() = runBlocking {
        val other = accountDao.insert(account())
        transactionDao.insert(movement(TransactionType.EXPENSE, -30_00))
        transactionDao.insert(movement(TransactionType.INCOME, 10_00, refund = true))
        transactionDao.insert(movement(TransactionType.EXPENSE, -7_00, account = other))
        transactionDao.insert(movement(TransactionType.INCOME, 500_00)) // salary, not spend
        transactionDao.insert(
            movement(
                TransactionType.TRANSFER,
                -99_00,
                transferAccountId = other,
                transferAmountMinor = 99_00,
            ),
        )

        val rows = transactionDao.observeAccountSpendTotals(windowStart, windowEnd, "EUR")
            .first()
            .sortedBy { it.accountId }

        // The salary INCOME on the first account still groups there: the row is
        // (spend -30 + refund 10 + 0), the salary is excluded by the type filter.
        assertEquals(listOf(accountId, other), rows.map { it.accountId })
        assertEquals(-20_00L, rows[0].totalMinor)
        assertEquals(-7_00L, rows[1].totalMinor)
    }

    @Test
    fun monthlyNetChangesCountEveryTypeAndBothTransferLegs() = runBlocking {
        val other = accountDao.insert(account())
        val outside = accountDao.insert(account(included = false))
        transactionDao.insert(movement(TransactionType.EXPENSE, -10_00))
        transactionDao.insert(movement(TransactionType.INCOME, 40_00))
        transactionDao.insert(movement(TransactionType.ADJUSTMENT, 5_00))
        // Cash figure: excluded-from-stats still moves the balance.
        transactionDao.insert(movement(TransactionType.EXPENSE, -1_00, excluded = true))
        // Pending never counts.
        transactionDao.insert(movement(TransactionType.EXPENSE, -77_00, pending = true))
        // Transfer between two included accounts nets to zero.
        transactionDao.insert(
            movement(
                TransactionType.TRANSFER,
                -20_00,
                transferAccountId = other,
                transferAmountMinor = 20_00,
            ),
        )
        // Transfer towards an account outside the total only counts the out leg.
        transactionDao.insert(
            movement(
                TransactionType.TRANSFER,
                -3_00,
                transferAccountId = outside,
                transferAmountMinor = 3_00,
            ),
        )

        val rows = transactionDao.observeMonthlyNetChanges("EUR").first()

        assertEquals(1, rows.size)
        assertEquals("2026-06", rows[0].month)
        // -10 + 40 + 5 - 1 - 20 + 20 - 3 = 31
        assertEquals(31_00L, rows[0].netMinor)
    }

    @Test
    fun monthlyNetChangesIgnoreArchivedAndForeignCurrencyAccounts() = runBlocking {
        val archived = accountDao.insert(account(archived = true))
        val dollars = accountDao.insert(account(currency = "USD"))
        transactionDao.insert(movement(TransactionType.EXPENSE, -10_00))
        transactionDao.insert(movement(TransactionType.EXPENSE, -50_00, account = archived))
        transactionDao.insert(
            movement(TransactionType.EXPENSE, -70_00, account = dollars, currency = "USD"),
        )

        val rows = transactionDao.observeMonthlyNetChanges("EUR").first()

        assertEquals(-10_00L, rows.single().netMinor)
    }

    @Test
    fun initialBalanceTotalSumsOnlyIncludedActiveAccountsInCurrency() = runBlocking {
        accountDao.insert(account(initialMinor = 100_00))
        accountDao.insert(account(initialMinor = 50_00, included = false))
        accountDao.insert(account(initialMinor = 25_00, archived = true))
        accountDao.insert(account(initialMinor = 33_00, currency = "USD"))

        assertEquals(100_00L, accountDao.observeInitialBalanceTotal("EUR").first())
    }
}
