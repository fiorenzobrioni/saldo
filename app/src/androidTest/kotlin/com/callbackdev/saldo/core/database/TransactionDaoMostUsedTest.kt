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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [TransactionDao.mostUsedCategories] backs the quick-add widget's adaptive
 * grid. It is deliberately not a statistics query: it counts what the user
 * actually taps, so movements excluded from statistics and foreign currencies
 * still count, and only pending (unconfirmed) ones do not.
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoMostUsedTest {

    private lateinit var database: SaldoDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao

    private var accountId = 0L
    private var groceries = 0L
    private var transport = 0L
    private var leisure = 0L

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SaldoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = database.transactionDao()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        runBlocking {
            accountId = accountDao.insert(
                AccountEntity(
                    name = "Checking",
                    type = AccountType.CHECKING,
                    currency = "EUR",
                    initialBalanceMinor = 0L,
                ),
            )
            groceries = categoryDao.insert(category("Groceries"))
            transport = categoryDao.insert(category("Transport"))
            leisure = categoryDao.insert(category("Leisure"))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun category(name: String) = CategoryEntity(
        name = name,
        type = CategoryType.EXPENSE,
        color = 0x66BB6A,
        icon = "shopping_cart",
    )

    @Suppress("LongParameterList")
    private fun movement(
        categoryId: Long?,
        atMillis: Long,
        type: TransactionType = TransactionType.EXPENSE,
        isPending: Boolean = false,
        excludedFromStats: Boolean = false,
        currency: String = "EUR",
    ) = TransactionEntity(
        type = type,
        amountMinor = -1_000L,
        currency = currency,
        accountId = accountId,
        timestampEpochMilli = atMillis,
        zoneOffsetSeconds = 7200,
        categoryId = categoryId,
        isPending = isPending,
        isExcludedFromStats = excludedFromStats,
    )

    private suspend fun mostUsed(limit: Int = 10, sinceMillis: Long = now - 60 * day): List<Long> =
        transactionDao.mostUsedCategories(TransactionType.EXPENSE.name, sinceMillis, limit)
            .mapNotNull { it.categoryId }

    @Test
    fun ordersByUseCount() = runBlocking {
        repeat(3) { transactionDao.insert(movement(transport, now - it * day)) }
        repeat(5) { transactionDao.insert(movement(groceries, now - it * day)) }
        transactionDao.insert(movement(leisure, now - day))

        assertEquals(listOf(groceries, transport, leisure), mostUsed())
    }

    @Test
    fun breaksTiesOnTheMostRecentUse() = runBlocking {
        transactionDao.insert(movement(transport, now - 10 * day))
        transactionDao.insert(movement(groceries, now - day))

        assertEquals(listOf(groceries, transport), mostUsed())
    }

    @Test
    fun respectsTheLimit() = runBlocking {
        transactionDao.insert(movement(groceries, now))
        transactionDao.insert(movement(transport, now))
        transactionDao.insert(movement(leisure, now))

        assertEquals(2, mostUsed(limit = 2).size)
    }

    @Test
    fun ignoresMovementsOlderThanTheWindow() = runBlocking {
        repeat(5) { transactionDao.insert(movement(groceries, now - (90 + it) * day)) }
        transactionDao.insert(movement(transport, now - day))

        assertEquals(listOf(transport), mostUsed())
    }

    @Test
    fun countsMovementsExcludedFromStatisticsAndForeignCurrencies() = runBlocking {
        transactionDao.insert(movement(groceries, now, excludedFromStats = true))
        transactionDao.insert(movement(groceries, now - day, currency = "USD"))
        transactionDao.insert(movement(transport, now))

        // Two taps on groceries, one on transport: what the user tapped is the
        // point, not what the charts add up.
        assertEquals(listOf(groceries, transport), mostUsed())
    }

    @Test
    fun ignoresPendingMovements() = runBlocking {
        repeat(4) { transactionDao.insert(movement(groceries, now - it * day, isPending = true)) }
        transactionDao.insert(movement(transport, now))

        assertEquals(listOf(transport), mostUsed())
    }

    @Test
    fun ignoresTheOtherMovementType() = runBlocking {
        repeat(4) { transactionDao.insert(movement(groceries, now - it * day, type = TransactionType.INCOME)) }
        transactionDao.insert(movement(transport, now))

        assertEquals(listOf(transport), mostUsed())
    }

    @Test
    fun neverReturnsTheUncategorizedBucket() = runBlocking {
        repeat(4) { transactionDao.insert(movement(categoryId = null, atMillis = now - it * day)) }
        transactionDao.insert(movement(transport, now))

        assertEquals(listOf(transport), mostUsed())
    }

    @Test
    fun comesBackEmptyOnAFreshInstall() = runBlocking {
        assertTrue(mostUsed().isEmpty())
    }
}
