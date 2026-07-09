package com.callbackdev.saldo.core.database

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.callbackdev.saldo.core.database.migration.MIGRATION_1_2
import com.callbackdev.saldo.core.database.migration.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test for v1 -> v2 (adds `color`/`icon` to `recurring_rules`). Runs on
 * a device/emulator; validates the migrated schema against the exported v2 JSON.
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
}
