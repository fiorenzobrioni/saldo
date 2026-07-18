package com.callbackdev.saldo.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.callbackdev.saldo.core.database.migration.ALL_MIGRATIONS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migrations-test"

/** The baseline every install starts from and every migration builds upon. */
private const val FIRST_VERSION = 1

/**
 * One generic migration test that covers the whole chain, not one test per
 * migration: it creates the baseline schema and runs every migration in
 * [ALL_MIGRATIONS] up to [SALDO_DATABASE_VERSION], validating the result against
 * the exported schema JSON at each step. Any future migration is exercised
 * automatically once it is added to [ALL_MIGRATIONS] and the version is bumped;
 * a migration whose result diverges from the entities (a wrong `CREATE TABLE`, a
 * missing default, an FK that `ALTER TABLE` cannot add) fails here.
 *
 * Instrumented: it needs a real SQLite, so it runs on a device or emulator, not
 * in the JVM unit-test gate. Run it before releasing any schema change. The
 * cheap structural half of the guard (chain contiguity, version reached) lives
 * in the JVM `MigrationChainTest` and does run in CI.
 */
@RunWith(AndroidJUnit4::class)
class MigrationsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SaldoDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateFromBaselineToCurrent_matchesTheExportedSchema() {
        // createDatabase already validates the baseline schema against 1.json.
        helper.createDatabase(TEST_DB, FIRST_VERSION).close()
        if (SALDO_DATABASE_VERSION > FIRST_VERSION) {
            // runMigrationsAndValidate throws if the migrated schema diverges from
            // the exported <version>.json (the class of bug this test exists for).
            helper.runMigrationsAndValidate(
                TEST_DB,
                SALDO_DATABASE_VERSION,
                true,
                *ALL_MIGRATIONS,
            ).close()
        }
    }

    @Test
    fun openMigratedDatabaseWithRoom_succeeds() {
        // Opening through Room applies the same migrations and validates the final
        // schema against the entities, mirroring the runtime open on a device.
        val db = Room.databaseBuilder(context, SaldoDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        db.openHelper.writableDatabase
        db.close()
    }
}
