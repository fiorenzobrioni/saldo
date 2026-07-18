package com.callbackdev.saldo.core.database.migration

import androidx.room.migration.Migration

/**
 * Explicit, tested Room migrations (PLANNING ADR: never `fallbackToDestructiveMigration`).
 *
 * The schema history is a single version-1 baseline: while the app is unpublished
 * a schema change may be folded into this baseline (regenerating `1.json`) rather
 * than shipped as a migration, since no published database exists to preserve.
 * That is a deliberate exception, and it forces a reinstall/clear-data on the test
 * device (Room rejects the resulting version downgrade), so it must be a conscious
 * choice, not a habit.
 *
 * From the next schema change onward, prefer a real migration: add a
 * `Migration(N, N+1)` here, append it to [ALL_MIGRATIONS], bump
 * [com.callbackdev.saldo.core.database.SALDO_DATABASE_VERSION], and export the new
 * `N.json`. `MigrationsTest` (instrumented) then validates the whole chain against
 * the exported schemas automatically, and `MigrationChainTest` (JVM) checks the
 * chain is contiguous and reaches the current version. Note that
 * `ALTER TABLE ADD COLUMN` cannot add a foreign key: a column carrying an FK needs
 * the create-copy-drop-rename table recreation, while a brand new table can carry
 * its FK directly in `CREATE TABLE`.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
