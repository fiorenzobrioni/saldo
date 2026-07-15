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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Instrumented tests for the budget spend SQL (total and per-category): the
 * refund-netted spend filter, the exclusion of pure incomes (a BOTH category's
 * incomes must not offset its budget), pending/excluded movements and the
 * currency restriction.
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoBudgetSpendTest {

    private lateinit var database: SaldoDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao

    private var accountId = 0L
    private var categoryId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SaldoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        transactionDao = database.transactionDao()
        runBlocking {
            accountId = accountDao.insert(
                AccountEntity(
                    name = "acc",
                    type = AccountType.CHECKING,
                    currency = "EUR",
                    initialBalanceMinor = 0L,
                ),
            )
            categoryId = categoryDao.insert(
                CategoryEntity(name = "Food", type = CategoryType.BOTH, color = 0x66BB6A, icon = "cart"),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun instantOf(year: Int, month: Int, day: Int): Instant =
        LocalDateTime.of(year, month, day, 12, 0).toInstant(ZoneOffset.UTC)

    @Suppress("LongParameterList")
    private fun movement(
        type: TransactionType,
        amountMinor: Long,
        category: Long? = categoryId,
        excluded: Boolean = false,
        refund: Boolean = false,
        pending: Boolean = false,
        currency: String = "EUR",
        account: Long = accountId,
        timestamp: Instant = instantOf(2026, 7, 10),
    ) = TransactionEntity(
        type = type,
        amountMinor = amountMinor,
        currency = currency,
        accountId = account,
        timestampEpochMilli = timestamp.toEpochMilli(),
        zoneOffsetSeconds = 0,
        categoryId = category,
        isExcludedFromStats = excluded,
        isRefund = refund,
        isPending = pending,
    )

    private val monthStart = instantOf(2026, 7, 1).toEpochMilli()
    private val monthEnd = instantOf(2026, 7, 31).toEpochMilli()

    @Test
    fun spendTotalNetsRefundsAndSkipsExcludedPendingAndForeignCurrency() = runBlocking {
        transactionDao.insert(movement(TransactionType.EXPENSE, -100_00))
        transactionDao.insert(movement(TransactionType.EXPENSE, -30_00))
        // A refund nets the spend; a pure income never does.
        transactionDao.insert(movement(TransactionType.INCOME, 20_00, refund = true))
        transactionDao.insert(movement(TransactionType.INCOME, 999_00))
        transactionDao.insert(movement(TransactionType.EXPENSE, -50_00, excluded = true))
        transactionDao.insert(movement(TransactionType.EXPENSE, -40_00, pending = true))
        transactionDao.insert(movement(TransactionType.EXPENSE, -25_00, currency = "USD"))
        transactionDao.insert(movement(TransactionType.ADJUSTMENT, -77_00))

        val total = transactionDao.observeStatsSpendTotal(monthStart, monthEnd, "EUR").first()

        // -100 - 30 + 20 = -110
        assertEquals(-110_00L, total)
        assertEquals(total, transactionDao.getStatsSpendTotal(monthStart, monthEnd, "EUR"))
    }

    @Test
    fun spendTotalIsNullWhenNothingMatches() = runBlocking {
        transactionDao.insert(movement(TransactionType.INCOME, 999_00))

        assertNull(transactionDao.observeStatsSpendTotal(monthStart, monthEnd, "EUR").first())
    }

    @Test
    fun spendTotalRespectsTheWindow() = runBlocking {
        transactionDao.insert(movement(TransactionType.EXPENSE, -10_00))
        transactionDao.insert(
            movement(TransactionType.EXPENSE, -99_00, timestamp = instantOf(2026, 6, 15)),
        )

        assertEquals(
            -10_00L,
            transactionDao.observeStatsSpendTotal(monthStart, monthEnd, "EUR").first(),
        )
    }

    @Test
    fun spendSkipsAccountsExcludedFromBudget() = runBlocking {
        val excludedAccount = accountDao.insert(
            AccountEntity(
                name = "savings",
                type = AccountType.CHECKING,
                currency = "EUR",
                initialBalanceMinor = 0L,
                isIncludedInBudget = false,
            ),
        )
        transactionDao.insert(movement(TransactionType.EXPENSE, -100_00))
        // Spend on a budget-excluded account must not count, in either query.
        transactionDao.insert(movement(TransactionType.EXPENSE, -70_00, account = excludedAccount))

        assertEquals(
            -100_00L,
            transactionDao.observeStatsSpendTotal(monthStart, monthEnd, "EUR").first(),
        )
        assertEquals(
            -100_00L,
            transactionDao.observeCategorySpendTotals(monthStart, monthEnd, "EUR").first()
                .single().totalMinor,
        )
    }

    @Test
    fun categorySpendKeepsPureIncomesOutOfABothCategory() = runBlocking {
        val other = categoryDao.insert(
            CategoryEntity(name = "Transport", type = CategoryType.EXPENSE, color = 0x111111, icon = "bus"),
        )
        transactionDao.insert(movement(TransactionType.EXPENSE, -60_00))
        transactionDao.insert(movement(TransactionType.INCOME, 15_00, refund = true))
        // A pure income in the same BOTH category must not offset its budget.
        transactionDao.insert(movement(TransactionType.INCOME, 500_00))
        transactionDao.insert(movement(TransactionType.EXPENSE, -12_00, category = other))
        transactionDao.insert(movement(TransactionType.EXPENSE, -5_00, category = null))

        val rows = transactionDao.observeCategorySpendTotals(monthStart, monthEnd, "EUR").first()
            .associateBy { it.categoryId }

        assertEquals(2, rows.size)
        assertEquals(-45_00L, rows.getValue(categoryId).totalMinor)
        assertEquals(-12_00L, rows.getValue(other).totalMinor)
        assertEquals(
            rows.values.map { it.totalMinor }.toSet(),
            transactionDao.getCategorySpendTotals(monthStart, monthEnd, "EUR")
                .map { it.totalMinor }
                .toSet(),
        )
    }
}
