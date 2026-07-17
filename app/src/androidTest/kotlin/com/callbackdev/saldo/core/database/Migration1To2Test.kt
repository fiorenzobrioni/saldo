package com.callbackdev.saldo.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.callbackdev.saldo.core.database.migration.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the 1 -> 2 upgrade that adds the transfer destination leg to
 * `recurring_rules` (PLANNING ADR 24). A pre-existing expense rule must survive
 * with null transfer columns, matching the exported v2 schema.
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    private val dbName = "migration-1-2-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SaldoDatabase::class.java,
    )

    @Test
    fun migrate1To2_addsNullTransferColumnsAndKeepsExistingRules() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                INSERT INTO recurring_rules
                    (id, name, type, currency, accountId, frequency, startDateEpochDay, mode, isVariableAmount)
                VALUES (1, 'Netflix', 'EXPENSE', 'EUR', 1, 'MONTHLY', 20000, 'AUTOMATIC', 0)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        db.query(
            "SELECT name, transferAccountId, transferAmountMinor, transferCurrency " +
                "FROM recurring_rules WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Netflix", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }
    }
}
