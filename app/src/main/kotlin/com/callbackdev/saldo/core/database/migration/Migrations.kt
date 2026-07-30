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

/**
 * Adds the per-movement reminder (ADR 36): `hasReminder`, the user's request to
 * be warned before a future-dated movement falls due, and
 * `lastReminderEpochDay`, the watermark that keeps a daily run from notifying
 * twice about the same date. Both purely additive - the flag needs a NOT NULL
 * default, the watermark is nullable - so `ALTER TABLE ADD COLUMN` suffices and
 * every existing movement reads back as having no reminder, which is what it
 * had. No index: the reminder scan is a one-shot over the handful of
 * future-dated rows, already served by the timestamp index.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `hasReminder` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `lastReminderEpochDay` INTEGER")
    }
}

/**
 * Adds the `exchange_rates` cache of ECB reference rates (ADR 40): one row per
 * (day, currency) with the rate kept as the exact decimal string the feed
 * published. A brand new table, so `CREATE TABLE` carries the composite
 * primary key directly; the index matches what Room generates for
 * `Index("currency", "dateEpochDay")`, the shape of the "most recent rate on
 * or before day X" lookup. No data to move: the cache starts empty and fills
 * on the first sync.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (" +
                "`dateEpochDay` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`rate` TEXT NOT NULL, " +
                "PRIMARY KEY(`dateEpochDay`, `currency`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exchange_rates_currency_dateEpochDay` " +
                "ON `exchange_rates` (`currency`, `dateEpochDay`)",
        )
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
