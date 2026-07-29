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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the tag management queries (Phase 16): the merge
 * transaction [TagDao.mergeInto] with its cross-ref dedup, the per-tag usage
 * counts, and the promise that deleting a tag never touches the movements.
 */
@RunWith(AndroidJUnit4::class)
class TagDaoTest {

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
    fun mergeIntoMovesEveryAssignmentToTheTarget() = runBlocking {
        val target = tagDao.insert(TagEntity(name = "spesa"))
        val source = tagDao.insert(TagEntity(name = "Spesa"))
        val first = transactionDao.insert(expense(-100L))
        val second = transactionDao.insert(expense(-200L))
        tagDao.insertCrossRef(TransactionTagCrossRef(first, source))
        tagDao.insertCrossRef(TransactionTagCrossRef(second, source))

        tagDao.mergeInto(target, listOf(source))

        assertEquals(
            setOf(first to target, second to target),
            tagDao.getAllCrossRefs().map { it.transactionId to it.tagId }.toSet(),
        )
    }

    @Test
    fun mergeIntoKeepsASingleAssignmentWhenAMovementCarriedBoth() = runBlocking {
        val target = tagDao.insert(TagEntity(name = "spesa"))
        val source = tagDao.insert(TagEntity(name = "Spesa"))
        val movement = transactionDao.insert(expense(-100L))
        tagDao.insertCrossRef(TransactionTagCrossRef(movement, target))
        tagDao.insertCrossRef(TransactionTagCrossRef(movement, source))

        tagDao.mergeInto(target, listOf(source))

        val refs = tagDao.getAllCrossRefs()
        assertEquals(listOf(TransactionTagCrossRef(movement, target)), refs)
    }

    @Test
    fun mergeIntoDeletesTheSourcesAndPreservesEveryMovement() = runBlocking {
        val target = tagDao.insert(TagEntity(name = "spesa"))
        val sourceA = tagDao.insert(TagEntity(name = "Spesa"))
        val sourceB = tagDao.insert(TagEntity(name = "groceries"))
        val movement = transactionDao.insert(expense(-100L))
        tagDao.insertCrossRef(TransactionTagCrossRef(movement, sourceA))
        tagDao.insertCrossRef(TransactionTagCrossRef(movement, sourceB))

        tagDao.mergeInto(target, listOf(sourceA, sourceB))

        assertEquals(listOf(target), tagDao.getAll().map { it.id })
        assertEquals(1, transactionDao.getAll().size)
        assertEquals(listOf(TransactionTagCrossRef(movement, target)), tagDao.getAllCrossRefs())
    }

    @Test
    fun mergeIntoLeavesUnrelatedAssignmentsAlone() = runBlocking {
        val target = tagDao.insert(TagEntity(name = "spesa"))
        val source = tagDao.insert(TagEntity(name = "Spesa"))
        val other = tagDao.insert(TagEntity(name = "viaggi"))
        val movement = transactionDao.insert(expense(-100L))
        tagDao.insertCrossRef(TransactionTagCrossRef(movement, source))
        tagDao.insertCrossRef(TransactionTagCrossRef(movement, other))

        tagDao.mergeInto(target, listOf(source))

        assertEquals(
            setOf(movement to target, movement to other),
            tagDao.getAllCrossRefs().map { it.transactionId to it.tagId }.toSet(),
        )
    }

    @Test
    fun usageCountsGroupByTagAndSkipUnusedTags() = runBlocking {
        val used = tagDao.insert(TagEntity(name = "spesa"))
        val once = tagDao.insert(TagEntity(name = "viaggi"))
        tagDao.insert(TagEntity(name = "mai-usato"))
        val first = transactionDao.insert(expense(-100L))
        val second = transactionDao.insert(expense(-200L))
        tagDao.insertCrossRef(TransactionTagCrossRef(first, used))
        tagDao.insertCrossRef(TransactionTagCrossRef(second, used))
        tagDao.insertCrossRef(TransactionTagCrossRef(second, once))

        val counts = tagDao.observeUsageCounts().first()

        assertEquals(mapOf(used to 2, once to 1), counts.associate { it.tagId to it.count })
    }

    @Test
    fun deletingATagRemovesItsAssignmentsButNeverTheMovements() = runBlocking {
        val tagId = tagDao.insert(TagEntity(name = "work"))
        val movement = transactionDao.insert(expense(-500L))
        tagDao.insertCrossRef(TransactionTagCrossRef(movement, tagId))

        tagDao.delete(TagEntity(id = tagId, name = "work"))

        assertEquals(0, tagDao.getAllCrossRefs().size)
        assertEquals(1, transactionDao.getAll().size)
    }
}
