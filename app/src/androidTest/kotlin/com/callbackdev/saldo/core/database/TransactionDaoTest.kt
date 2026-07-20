package com.callbackdev.saldo.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.dao.TagDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.entity.TagEntity
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.database.entity.TransactionTagCrossRef
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the bulk delete used by the filtered-delete feature:
 * [TransactionDao.deleteByIds] (with tag cross-ref cascade) and the atomic
 * [TransactionDao.deleteAndInsert] that backs the balance-preserving mode.
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var database: SaldoDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var tagDao: TagDao

    private var accountId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SaldoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = database.transactionDao()
        accountDao = database.accountDao()
        tagDao = database.tagDao()
        accountId = runBlocking {
            accountDao.insert(
                AccountEntity(
                    name = "Checking",
                    type = AccountType.CHECKING,
                    currency = "EUR",
                    initialBalanceMinor = 0L,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun expense(amountMinor: Long) = TransactionEntity(
        type = TransactionType.EXPENSE,
        amountMinor = amountMinor,
        currency = "EUR",
        accountId = accountId,
        timestampEpochMilli = 1_700_000_000_000L,
        zoneOffsetSeconds = 7200,
    )

    @Test
    fun deleteByIdsRemovesOnlyTheGivenRows() = runBlocking {
        val keep = transactionDao.insert(expense(-100L))
        val goA = transactionDao.insert(expense(-200L))
        val goB = transactionDao.insert(expense(-300L))

        transactionDao.deleteByIds(listOf(goA, goB))

        val remaining = transactionDao.getAll()
        assertEquals(1, remaining.size)
        assertEquals(keep, remaining.single().id)
    }

    @Test
    fun deleteByIdsCascadesToTagCrossRefs() = runBlocking {
        val id = transactionDao.insert(expense(-500L))
        val tagId = tagDao.insert(TagEntity(name = "work"))
        tagDao.insertCrossRef(TransactionTagCrossRef(transactionId = id, tagId = tagId))

        transactionDao.deleteByIds(listOf(id))

        assertTrue(transactionDao.getAll().isEmpty())
        assertTrue(tagDao.getAllCrossRefs().isEmpty())
    }

    @Test
    fun deleteAndInsertReplacesInOneStep() = runBlocking {
        val a = transactionDao.insert(expense(-100L))
        val b = transactionDao.insert(expense(-250L))

        val carryOver = TransactionEntity(
            type = TransactionType.ADJUSTMENT,
            amountMinor = -350L,
            currency = "EUR",
            accountId = accountId,
            timestampEpochMilli = 1_700_000_000_000L,
            zoneOffsetSeconds = 7200,
            description = "Cleanup carry-over",
        )

        val newIds = transactionDao.deleteAndInsert(listOf(a, b), listOf(carryOver))

        assertEquals(1, newIds.size)
        val remaining = transactionDao.getAll()
        assertEquals(1, remaining.size)
        assertEquals(TransactionType.ADJUSTMENT, remaining.single().type)
        assertEquals(-350L, remaining.single().amountMinor)
    }
}
