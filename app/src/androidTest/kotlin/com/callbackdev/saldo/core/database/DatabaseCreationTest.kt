package com.callbackdev.saldo.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.database.seed.DatabaseSeedCallback
import com.callbackdev.saldo.core.database.seed.DefaultCategories
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the fresh-create path (Room `onCreate` + [DatabaseSeedCallback]),
 * the one a clean install or "clear data" takes. The migration tests only cover
 * ALTER TABLE upgrades, where added columns carry a SQL default; a from-scratch
 * schema has none, so a seed insert that omits a NOT NULL column aborts onCreate
 * and the app cannot open. This is the regression guard for that class of bug.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseCreationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val database: SaldoDatabase = Room
        .inMemoryDatabaseBuilder(context, SaldoDatabase::class.java)
        .addCallback(DatabaseSeedCallback(context))
        .build()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun freshDatabase_seedsEveryDefaultCategory() = runBlocking {
        // Reaching the DAO forces Room to open the connection, running onCreate
        // and the seed. Before the fix this threw a NOT NULL constraint failure.
        assertEquals(DefaultCategories.count, database.categoryDao().count())
    }

    @Test
    fun freshDatabase_seedsIncomeSortOrder() = runBlocking {
        // Every seeded row must carry sortOrderIncome, the column whose omission
        // caused the crash. The income tab reads it, so a zero-everywhere column
        // would also silently break income ordering.
        val incomeOrders = database.categoryDao().getAll().map { it.sortOrderIncome }
        assertEquals(DefaultCategories.count, incomeOrders.size)
        assertTrue(incomeOrders.any { it > 0 })
    }
}
