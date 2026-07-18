package com.callbackdev.saldo.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.callbackdev.saldo.core.database.migration.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-1-2-test"

/**
 * Validates the savings goals migration (schema version 1 -> 2) against the
 * exported `2.json`. Because `savings_goals` is a brand new table,
 * [MIGRATION_1_2] creates it with its foreign key in one `CREATE TABLE`, so the
 * migrated schema must match the from-scratch schema exactly (the previous
 * recurring-transfer migration crashed precisely because `ALTER TABLE ADD
 * COLUMN` cannot add a foreign key; this one has no such limitation).
 *
 * Instrumented: needs a device or emulator, not run in the JVM unit-test gate.
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SaldoDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_createsSavingsGoalsTable() {
        helper.createDatabase(TEST_DB, 1).close()

        // runMigrationsAndValidate throws if the migrated schema diverges from 2.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT COUNT(*) FROM savings_goals").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }
}
