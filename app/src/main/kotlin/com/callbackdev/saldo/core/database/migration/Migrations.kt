package com.callbackdev.saldo.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Explicit, tested Room migrations (PLANNING ADR: never `fallbackToDestructiveMigration`).
 *
 * Version 1 is the baseline every install starts from. Folding a schema change
 * into that baseline (regenerating `1.json`) was a deliberate exception allowed
 * only while the app was unpublished; the 1.0.0 release closed it for good, so
 * from here on every schema change is a real migration.
 *
 * The recipe: add a `Migration(N, N+1)` here, append it to [ALL_MIGRATIONS], bump
 * [com.callbackdev.saldo.core.database.SALDO_DATABASE_VERSION], and export the new
 * `N.json`. `MigrationsTest` (instrumented) then validates the whole chain against
 * the exported schemas automatically, and `MigrationChainTest` (JVM) checks the
 * chain is contiguous and reaches the current version. Note that
 * `ALTER TABLE ADD COLUMN` cannot add a foreign key: a column carrying an FK needs
 * the create-copy-drop-rename table recreation, while a brand new table can carry
 * its FK directly in `CREATE TABLE`.
 */

/**
 * Adds `transactions.counterparty` (the person on the other side of a loan
 * between people, ADR 34) and its index. Purely additive: a nullable TEXT
 * column carries no foreign key and needs no default, so `ALTER TABLE ADD
 * COLUMN` is enough and every existing movement simply reads back as having no
 * counterparty. The index name matches what Room generates for
 * `Index("counterparty")`, which is what the schema validation compares against.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `counterparty` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_counterparty` " +
                "ON `transactions` (`counterparty`)",
        )
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
