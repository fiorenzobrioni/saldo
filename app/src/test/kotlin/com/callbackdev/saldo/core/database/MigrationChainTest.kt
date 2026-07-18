package com.callbackdev.saldo.core.database

import com.callbackdev.saldo.core.database.migration.ALL_MIGRATIONS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The baseline every install starts from. */
private const val FIRST_VERSION = 1

/**
 * Cheap structural guard on the migration setup that runs in the JVM unit-test
 * gate (unlike the instrumented [MigrationsTest], which needs a device). It does
 * not execute any SQL; it catches the omissions that repeatedly broke updates:
 * bumping [SALDO_DATABASE_VERSION] without adding the matching migration, or a
 * gap/overlap in the chain. The instrumented test then validates the SQL itself.
 */
class MigrationChainTest {

    @Test
    fun `migrations form a contiguous chain reaching the current version`() {
        val steps = ALL_MIGRATIONS.sortedBy { it.startVersion }
        var expectedStart = FIRST_VERSION
        steps.forEach { migration ->
            assertEquals(
                expectedStart,
                migration.startVersion,
                "Migration chain has a gap or overlap before version ${migration.startVersion}",
            )
            assertEquals(
                migration.startVersion + 1,
                migration.endVersion,
                "Each migration must step exactly one version (${migration.startVersion} -> ${migration.endVersion})",
            )
            expectedStart = migration.endVersion
        }
        assertEquals(
            SALDO_DATABASE_VERSION,
            expectedStart,
            "ALL_MIGRATIONS must connect version $FIRST_VERSION to the current schema version; " +
                "bumping the version needs a matching Migration",
        )
    }
}
