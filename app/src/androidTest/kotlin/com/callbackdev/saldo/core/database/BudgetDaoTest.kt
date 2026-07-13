package com.callbackdev.saldo.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.database.dao.BudgetDao
import com.callbackdev.saldo.core.database.dao.CategoryDao
import com.callbackdev.saldo.core.database.entity.BudgetEntity
import com.callbackdev.saldo.core.database.entity.CategoryEntity
import com.callbackdev.saldo.core.domain.model.CategoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the budgets table: the unique index on categoryId,
 * the CASCADE on category deletion and the targeted watermark updates.
 */
@RunWith(AndroidJUnit4::class)
class BudgetDaoTest {

    private lateinit var database: SaldoDatabase
    private lateinit var budgetDao: BudgetDao
    private lateinit var categoryDao: CategoryDao

    private var categoryId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SaldoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        budgetDao = database.budgetDao()
        categoryDao = database.categoryDao()
        categoryId = runBlocking {
            categoryDao.insert(
                CategoryEntity(name = "Groceries", type = CategoryType.EXPENSE, color = 0x66BB6A, icon = "cart"),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun budget(category: Long? = null, amountMinor: Long = 50_000L) =
        BudgetEntity(categoryId = category, amountMinor = amountMinor, currency = "EUR")

    @Test
    fun uniqueIndexRejectsASecondBudgetForTheSameCategory() = runBlocking {
        budgetDao.insert(budget(category = categoryId))

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { budgetDao.insert(budget(category = categoryId)) }
        }
    }

    @Test
    fun deletingTheCategoryCascadesToItsBudget() = runBlocking {
        budgetDao.insert(budget(category = categoryId))
        budgetDao.insert(budget(category = null))

        categoryDao.deleteById(categoryId)

        val remaining = budgetDao.getAll()
        assertEquals(1, remaining.size)
        assertNull(remaining.single().categoryId)
    }

    @Test
    fun overallReadFindsOnlyTheNullCategoryRow() = runBlocking {
        budgetDao.insert(budget(category = categoryId, amountMinor = 20_000L))
        assertNull(budgetDao.getOverall())

        budgetDao.insert(budget(category = null, amountMinor = 80_000L))

        assertEquals(80_000L, budgetDao.getOverall()?.amountMinor)
    }

    @Test
    fun observeAllListsTheOverallBudgetFirst() = runBlocking {
        budgetDao.insert(budget(category = categoryId))
        budgetDao.insert(budget(category = null))

        val rows = budgetDao.observeAll().first()

        assertNull(rows.first().categoryId)
        assertEquals(categoryId, rows.last().categoryId)
    }

    @Test
    fun watermarkUpdatesAreTargetedAndMark100ImpliesBoth() = runBlocking {
        val id = budgetDao.insert(budget(category = null, amountMinor = 123L))

        budgetDao.markNotified80(id, 24_318L)
        var row = budgetDao.getById(id)!!
        assertEquals(24_318L, row.lastNotified80EpochMonth)
        assertNull(row.lastNotified100EpochMonth)
        // The targeted UPDATE leaves the rest of the row untouched.
        assertEquals(123L, row.amountMinor)

        budgetDao.markNotified100(id, 24_319L)
        row = budgetDao.getById(id)!!
        assertEquals(24_319L, row.lastNotified80EpochMonth)
        assertEquals(24_319L, row.lastNotified100EpochMonth)
    }

    @Test
    fun deleteByIdRemovesOnlyThatBudget() = runBlocking {
        val overall = budgetDao.insert(budget(category = null))
        budgetDao.insert(budget(category = categoryId))

        budgetDao.deleteById(overall)

        val remaining = budgetDao.getAll()
        assertEquals(1, remaining.size)
        assertTrue(remaining.single().categoryId == categoryId)
    }
}
