package com.callbackdev.saldo.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.dao.CategoryDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.entity.CategoryEntity
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the balance and aggregate SQL. They run on a device or
 * emulator (Room needs SQLite); the money mapping is covered by JVM unit tests.
 */
@RunWith(AndroidJUnit4::class)
class SaldoDatabaseTest {

    private lateinit var database: SaldoDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SaldoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        transactionDao = database.transactionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun account(
        initialMinor: Long,
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

    private fun movement(
        type: TransactionType,
        amountMinor: Long,
        accountId: Long,
        categoryId: Long? = null,
        transferAccountId: Long? = null,
        transferAmountMinor: Long? = null,
        excluded: Boolean = false,
        refund: Boolean = false,
    ) = TransactionEntity(
        type = type,
        amountMinor = amountMinor,
        currency = "EUR",
        accountId = accountId,
        timestampEpochMilli = 1_700_000_000_000L,
        zoneOffsetSeconds = 0,
        categoryId = categoryId,
        transferAccountId = transferAccountId,
        transferAmountMinor = transferAmountMinor,
        transferCurrency = transferAccountId?.let { "EUR" },
        isExcludedFromStats = excluded,
        isRefund = refund,
    )

    @Test
    fun accountBalanceSumsAllMovementTypes() = runBlocking {
        val a = accountDao.insert(account(initialMinor = 10_000L))
        val b = accountDao.insert(account(initialMinor = 0L))

        transactionDao.insert(movement(TransactionType.EXPENSE, -4_500L, a))
        transactionDao.insert(movement(TransactionType.INCOME, 2_000L, a))
        transactionDao.insert(movement(TransactionType.ADJUSTMENT, 500L, a))
        transactionDao.insert(
            movement(TransactionType.TRANSFER, -3_000L, a, transferAccountId = b, transferAmountMinor = 3_000L),
        )

        assertEquals(5_000L, accountDao.observeBalance(a).first())
        assertEquals(3_000L, accountDao.observeBalance(b).first())
    }

    @Test
    fun totalBalanceExcludesArchivedAndNotIncludedAccounts() = runBlocking {
        val a = accountDao.insert(account(initialMinor = 10_000L))
        val b = accountDao.insert(account(initialMinor = 0L))
        accountDao.insert(account(initialMinor = 100_000L, archived = true))
        accountDao.insert(account(initialMinor = 50_000L, included = false))

        transactionDao.insert(movement(TransactionType.EXPENSE, -4_500L, a))
        transactionDao.insert(movement(TransactionType.INCOME, 2_000L, a))
        transactionDao.insert(movement(TransactionType.ADJUSTMENT, 500L, a))
        transactionDao.insert(
            movement(TransactionType.TRANSFER, -3_000L, a, transferAccountId = b, transferAmountMinor = 3_000L),
        )

        assertEquals(8_000L, accountDao.observeTotalBalance("EUR").first())
    }

    @Test
    fun balancesAsOfExcludeMovementsDatedAfterCutoff() = runBlocking {
        val a = accountDao.insert(account(initialMinor = 10_000L))
        val b = accountDao.insert(account(initialMinor = 0L))

        // Local day (offset 0): 1_700_000_000_000 ms -> epochDay 19675 ("today"),
        // 1_702_080_000_000 ms -> epochDay 19700 (in the future).
        val todayMillis = 1_700_000_000_000L
        val futureMillis = 1_702_080_000_000L
        transactionDao.insert(movement(TransactionType.EXPENSE, -1_000L, a).copy(timestampEpochMilli = todayMillis))
        transactionDao.insert(movement(TransactionType.INCOME, 5_000L, a).copy(timestampEpochMilli = futureMillis))
        transactionDao.insert(
            movement(TransactionType.TRANSFER, -2_000L, a, transferAccountId = b, transferAmountMinor = 2_000L)
                .copy(timestampEpochMilli = futureMillis),
        )

        val cutoff = 19_676L // today (19675) + 1
        val total = accountDao.observeAllWithBalance().first().associate { it.account.id to it.balanceMinor }
        val asOf = accountDao.observeAllBalancesAsOf(cutoff).first().associate { it.accountId to it.balanceMinor }

        // The total counts every confirmed movement, future ones included.
        assertEquals(12_000L, total[a])
        assertEquals(2_000L, total[b])
        // As of today only the today-dated expense counts; future income and transfer are left out.
        assertEquals(9_000L, asOf[a])
        assertEquals(0L, asOf[b])
    }

    @Test
    fun categoryTotalsNetRefundsAndExcludeTransfersAdjustments() = runBlocking {
        val a = accountDao.insert(account(initialMinor = 100_000L))
        val b = accountDao.insert(account(initialMinor = 0L))
        val category = categoryDao.insert(
            CategoryEntity(name = "Dining", type = CategoryType.EXPENSE, color = 0xEF5350, icon = "restaurant"),
        )

        transactionDao.insert(movement(TransactionType.EXPENSE, -6_000L, a, categoryId = category))
        transactionDao.insert(movement(TransactionType.INCOME, 4_000L, a, categoryId = category, refund = true))
        transactionDao.insert(movement(TransactionType.EXPENSE, -1_000L, a, categoryId = category, excluded = true))
        transactionDao.insert(movement(TransactionType.ADJUSTMENT, 999L, a))
        transactionDao.insert(
            movement(TransactionType.TRANSFER, -2_000L, a, transferAccountId = b, transferAmountMinor = 2_000L),
        )

        val totals = transactionDao.observeCategoryTotals(
            startMillis = 0L,
            endMillis = Long.MAX_VALUE,
            currency = "EUR",
        ).first()

        assertEquals(1, totals.size)
        val row = totals.single()
        assertEquals(category, row.categoryId)
        assertEquals(-2_000L, row.totalMinor)
        assertEquals(2, row.count)
    }

    @Test
    fun categoryTotalsIgnoreOrdinaryIncomesOfACategoryUsedForBoth() = runBlocking {
        // A BOTH category: 100.00 of gifts given, 80.00 of gifts received. The
        // income is not a refund, so the slice must read the full 100.00 of
        // spend, like the trend bars do; netting it to 20.00 would understate
        // the category and the ring's total (the bug the query once had).
        val a = accountDao.insert(account(initialMinor = 100_000L))
        val gifts = categoryDao.insert(
            CategoryEntity(name = "Gifts", type = CategoryType.BOTH, color = 0xEF5350, icon = "gift"),
        )
        transactionDao.insert(movement(TransactionType.EXPENSE, -10_000L, a, categoryId = gifts))
        transactionDao.insert(movement(TransactionType.INCOME, 8_000L, a, categoryId = gifts))

        val observed = transactionDao.observeCategoryTotals(0L, Long.MAX_VALUE, "EUR").first().single()
        val oneShot = transactionDao.getCategoryTotals(0L, Long.MAX_VALUE, "EUR").single()

        assertEquals(-10_000L, observed.totalMinor)
        assertEquals(1, observed.count)
        assertEquals(-10_000L, oneShot.totalMinor)
        assertEquals(1, oneShot.count)
    }

    @Test
    fun betweenFiltersByInstantRange() = runBlocking {
        val a = accountDao.insert(account(initialMinor = 0L))
        transactionDao.insert(movement(TransactionType.EXPENSE, -100L, a).copy(timestampEpochMilli = 1_000L))
        transactionDao.insert(movement(TransactionType.EXPENSE, -200L, a).copy(timestampEpochMilli = 2_000L))
        transactionDao.insert(movement(TransactionType.EXPENSE, -300L, a).copy(timestampEpochMilli = 3_000L))

        val inRange = transactionDao.observeBetween(startMillis = 2_000L, endMillis = 3_000L).first()

        assertEquals(1, inRange.size)
        assertEquals(-200L, inRange.single().amountMinor)
    }
}
