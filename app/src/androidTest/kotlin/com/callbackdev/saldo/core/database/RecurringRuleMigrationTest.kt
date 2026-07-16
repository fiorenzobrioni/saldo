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
import com.callbackdev.saldo.core.database.migration.MIGRATION_7_8
import com.callbackdev.saldo.core.database.migration.MIGRATION_8_9
import com.callbackdev.saldo.core.database.migration.MIGRATION_10_11
import com.callbackdev.saldo.core.database.migration.MIGRATION_11_12
import com.callbackdev.saldo.core.database.migration.MIGRATION_9_10
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

    @Test
    fun migrate7To8_addsIsIncludedInBudget_defaultingToIncluded() {
        val dbName = "migration-test-7-8"

        helper.createDatabase(dbName, 7).use { db ->
            db.insert(
                "accounts",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("name", "Savings")
                    put("type", "CHECKING")
                    put("currency", "EUR")
                    put("initialBalanceMinor", 0L)
                    put("isIncludedInTotal", 1)
                    put("isArchived", 0)
                    put("sortOrder", 0)
                    put("createdAtEpochMilli", 0L)
                },
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT name, isIncludedInBudget FROM accounts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Savings", cursor.getString(0))
            // Existing accounts keep counting toward the budget.
            assertEquals(1, cursor.getInt(1))
        }
    }

    @Test
    fun migrate8To9_addsCreditCardColumns_defaultingToPlainAccount() {
        val dbName = "migration-test-8-9"

        helper.createDatabase(dbName, 8).use { db ->
            db.insert(
                "accounts",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("name", "Checking")
                    put("type", "CHECKING")
                    put("currency", "EUR")
                    put("initialBalanceMinor", 0L)
                    put("isIncludedInTotal", 1)
                    put("isIncludedInBudget", 1)
                    put("isArchived", 0)
                    put("sortOrder", 0)
                    put("createdAtEpochMilli", 0L)
                },
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 9, true, MIGRATION_8_9)

        db.query(
            "SELECT name, creditLimitMinor, statementClosingDay, paymentDueDay, " +
                "linkedAccountId, statementAutoPost, lastSettledClosingEpochDay FROM accounts",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Checking", cursor.getString(0))
            // Every credit card column is null for a pre-existing plain account,
            // except the NOT NULL auto-post flag, which defaults to 0 (confirm).
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
            assertEquals(0, cursor.getInt(5))
            assertTrue(cursor.isNull(6))
        }
    }

    @Test
    fun migrate9To10_rewritesGenericCardToDebitCard_leavingOtherTypesAlone() {
        val dbName = "migration-test-9-10"

        helper.createDatabase(dbName, 9).use { db ->
            listOf("CARD" to "Postepay", "CHECKING" to "Checking").forEach { (type, name) ->
                db.insert(
                    "accounts",
                    android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                    ContentValues().apply {
                        put("name", name)
                        put("type", type)
                        put("currency", "EUR")
                        put("initialBalanceMinor", 0L)
                        put("isIncludedInTotal", 1)
                        put("isIncludedInBudget", 1)
                        put("isArchived", 0)
                        put("sortOrder", 0)
                        put("createdAtEpochMilli", 0L)
                        put("statementAutoPost", 0)
                    },
                )
            }
        }

        val db = helper.runMigrationsAndValidate(dbName, 10, true, MIGRATION_9_10)

        db.query("SELECT name, type FROM accounts ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Postepay", cursor.getString(0))
            assertEquals("DEBIT_CARD", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals("Checking", cursor.getString(0))
            assertEquals("CHECKING", cursor.getString(1))
        }
    }

    @Test
    fun migrate10To11_rewritesDebitCardToChecking_leavingOtherTypesAlone() {
        val dbName = "migration-test-10-11"

        helper.createDatabase(dbName, 10).use { db ->
            listOf("DEBIT_CARD" to "Bancomat", "PREPAID_CARD" to "Postepay").forEach { (type, name) ->
                db.insert(
                    "accounts",
                    android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                    ContentValues().apply {
                        put("name", name)
                        put("type", type)
                        put("currency", "EUR")
                        put("initialBalanceMinor", 0L)
                        put("isIncludedInTotal", 1)
                        put("isIncludedInBudget", 1)
                        put("isArchived", 0)
                        put("sortOrder", 0)
                        put("createdAtEpochMilli", 0L)
                        put("statementAutoPost", 0)
                    },
                )
            }
        }

        val db = helper.runMigrationsAndValidate(dbName, 11, true, MIGRATION_10_11)

        db.query("SELECT name, type FROM accounts ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Bancomat", cursor.getString(0))
            assertEquals("CHECKING", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals("Postepay", cursor.getString(0))
            assertEquals("PREPAID_CARD", cursor.getString(1))
        }
    }

    @Test
    fun migrate11To12_backfillsLoansCategory_afterExistingOnes() {
        val dbName = "migration-test-11-12"

        helper.createDatabase(dbName, 11).use { db ->
            db.insert(
                "categories",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("name", "Tasse")
                    put("type", "EXPENSE")
                    put("color", 0xFF7043)
                    put("icon", "account_balance")
                    put("sortOrder", 7)
                    put("sortOrderIncome", 7)
                    put("isDefault", 1)
                },
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 12, true, MIGRATION_11_12)

        db.query(
            "SELECT name, type, icon, sortOrder, isDefault FROM categories " +
                "WHERE name = 'Prestiti & Finanziamenti'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("EXPENSE", cursor.getString(1))
            assertEquals("request_quote", cursor.getString(2))
            // Appended after the existing categories.
            assertEquals(8, cursor.getInt(3))
            assertEquals(1, cursor.getInt(4))
        }
    }

    @Test
    fun migrate11To12_skipsBackfill_whenTheCategoryAlreadyExists() {
        val dbName = "migration-test-11-12-existing"

        helper.createDatabase(dbName, 11).use { db ->
            db.insert(
                "categories",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("name", "Prestiti & Finanziamenti")
                    put("type", "EXPENSE")
                    put("color", 0x26C6DA)
                    put("icon", "request_quote")
                    put("sortOrder", 0)
                    put("sortOrderIncome", 0)
                    put("isDefault", 0)
                },
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 12, true, MIGRATION_11_12)

        db.query(
            "SELECT COUNT(*) FROM categories WHERE name = 'Prestiti & Finanziamenti'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
