package com.callbackdev.saldo.core.database

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.callbackdev.saldo.core.database.migration.MIGRATION_1_2
import com.callbackdev.saldo.core.database.migration.MIGRATION_2_3
import com.callbackdev.saldo.core.database.migration.MIGRATION_3_4
import com.callbackdev.saldo.core.database.migration.MIGRATION_4_5
import com.callbackdev.saldo.core.database.migration.MIGRATION_5_6
import com.callbackdev.saldo.core.database.migration.MIGRATION_6_7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests for the schema history. Run on a device/emulator; each step
 * validates the migrated schema against the exported JSON.
 */
@RunWith(AndroidJUnit4::class)
class RecurringRuleMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SaldoDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_addsColorAndIcon_keepingExistingRows() {
        val dbName = "migration-test"

        helper.createDatabase(dbName, 1).use { db ->
            db.insert(
                "recurring_rules",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("name", "Netflix")
                    put("type", "EXPENSE")
                    put("currency", "EUR")
                    put("accountId", 1L)
                    put("frequency", "MONTHLY")
                    put("startDateEpochDay", 20_000L)
                    put("mode", "AUTOMATIC")
                    put("isVariableAmount", 0)
                },
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        db.query("SELECT name, color, icon FROM recurring_rules").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Netflix", cursor.getString(0))
            // The new columns exist and default to null for pre-existing rows.
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }
    }

    @Test
    fun migrate2To3_addsIsPending_defaultingToConfirmed() {
        val dbName = "migration-test-2-3"

        helper.createDatabase(dbName, 2).use { db ->
            db.insert(
                "transactions",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("type", "EXPENSE")
                    put("amountMinor", -1299L)
                    put("currency", "EUR")
                    put("accountId", 1L)
                    put("timestampEpochMilli", 1_700_000_000_000L)
                    put("zoneOffsetSeconds", 0)
                    put("isExcludedFromStats", 0)
                    put("isRefund", 0)
                },
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        db.query("SELECT isPending FROM transactions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            // Existing movements are confirmed (not pending).
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migrate3To4_backfillsOccurrenceDay_leavingDuplicatesAndManualRowsNull() {
        val dbName = "migration-test-3-4"
        val dayEpoch = 20_000L
        val noonMillis = dayEpoch * MILLIS_PER_DAY + MILLIS_PER_DAY / 2

        helper.createDatabase(dbName, 3).use { db ->
            db.insert(
                "recurring_rules",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("id", 1L)
                    put("name", "Netflix")
                    put("type", "EXPENSE")
                    put("currency", "EUR")
                    put("accountId", 1L)
                    put("frequency", "MONTHLY")
                    put("startDateEpochDay", dayEpoch)
                    put("mode", "AUTOMATIC")
                    put("isVariableAmount", 0)
                },
            )
            // Two duplicates of the same occurrence (the pre-fix bug) plus a manual movement.
            repeat(2) {
                db.insert(
                    "transactions",
                    android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                    ContentValues().apply {
                        put("type", "EXPENSE")
                        put("amountMinor", -1299L)
                        put("currency", "EUR")
                        put("accountId", 1L)
                        put("timestampEpochMilli", noonMillis)
                        put("zoneOffsetSeconds", 3600)
                        put("isExcludedFromStats", 0)
                        put("isRefund", 0)
                        put("isPending", 0)
                        put("recurringRuleId", 1L)
                    },
                )
            }
            db.insert(
                "transactions",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("type", "EXPENSE")
                    put("amountMinor", -500L)
                    put("currency", "EUR")
                    put("accountId", 1L)
                    put("timestampEpochMilli", noonMillis)
                    put("zoneOffsetSeconds", 3600)
                    put("isExcludedFromStats", 0)
                    put("isRefund", 0)
                    put("isPending", 0)
                },
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        db.query(
            "SELECT recurringRuleId, recurringOccurrenceEpochDay FROM transactions ORDER BY id",
        ).use { cursor ->
            // Oldest duplicate is backfilled with the occurrence day.
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(dayEpoch, cursor.getLong(1))
            // The newer duplicate keeps NULL so the unique index can be created without dropping it.
            assertTrue(cursor.moveToNext())
            assertEquals(1L, cursor.getLong(0))
            assertTrue(cursor.isNull(1))
            // Manual movements are untouched.
            assertTrue(cursor.moveToNext())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
        }
    }

    @Test
    fun migrate4To5_addsLastReminder_defaultingToNull() {
        val dbName = "migration-test-4-5"

        helper.createDatabase(dbName, 4).use { db ->
            db.insert(
                "recurring_rules",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("name", "Netflix")
                    put("type", "EXPENSE")
                    put("currency", "EUR")
                    put("accountId", 1L)
                    put("frequency", "MONTHLY")
                    put("startDateEpochDay", 20_000L)
                    put("mode", "AUTOMATIC")
                    put("isVariableAmount", 0)
                },
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)

        db.query("SELECT name, lastReminderEpochDay FROM recurring_rules").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Netflix", cursor.getString(0))
            // Existing rules have never been reminded.
            assertTrue(cursor.isNull(1))
        }
    }

    @Test
    fun migrate5To6_createsBudgetsTable_keepingExistingData() {
        val dbName = "migration-test-5-6"

        helper.createDatabase(dbName, 5).use { db ->
            db.insert(
                "categories",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("id", 10L)
                    put("name", "Groceries")
                    put("type", "EXPENSE")
                    put("color", 0x66BB6A)
                    put("icon", "shopping_cart")
                    put("sortOrder", 0)
                    put("isDefault", 0)
                },
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        // Pre-existing data is intact and the new table starts empty.
        db.query("SELECT name FROM categories").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Groceries", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM budgets").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        // The unique index on categoryId is in place.
        db.query(
            "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type = 'index' AND name = 'index_budgets_categoryId'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun migrate6To7_addsIncomeOrder_seededFromSortOrder() {
        val dbName = "migration-test-6-7"

        helper.createDatabase(dbName, 6).use { db ->
            db.insert(
                "categories",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("id", 10L)
                    put("name", "Gifts")
                    put("type", "BOTH")
                    put("color", 0xF06292)
                    put("icon", "redeem")
                    put("sortOrder", 4)
                    put("isDefault", 0)
                },
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        db.query("SELECT sortOrder, sortOrderIncome FROM categories").use { cursor ->
            assertTrue(cursor.moveToFirst())
            // The new income key is seeded from the old shared sortOrder.
            assertEquals(4, cursor.getInt(0))
            assertEquals(4, cursor.getInt(1))
        }
        // The income-order index is in place.
        db.query(
            "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type = 'index' AND name = 'index_categories_sortOrderIncome'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
