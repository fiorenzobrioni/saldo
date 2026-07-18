package com.callbackdev.saldo.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, tested Room migrations (PLANNING ADR: never `fallbackToDestructiveMigration`).
 *
 * The schema history was collapsed to a single version-1 baseline while the app
 * was still unpublished; migrations resume from there. Each schema change adds a
 * `Migration(N, N+1)` here, appends it to [ALL_MIGRATIONS], bumps the database
 * version, and is covered by an instrumented test against the exported schema
 * JSON.
 */

/**
 * Adds the `savings_goals` table (savings goals feature, schema version 2).
 *
 * This is a brand new table, so `CREATE TABLE` carries the foreign key to
 * `accounts` directly. That is why this migration is a clean addition rather
 * than a baseline collapse: unlike `ALTER TABLE ADD COLUMN` (which cannot add a
 * foreign key, and had forced the recurring-transfer columns into the baseline),
 * a `CREATE TABLE` produces a schema identical to the one Room builds from
 * scratch. The statements mirror Room's generated SQL exactly (verified against
 * the exported `2.json`).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `savings_goals` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`targetAmountMinor` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`targetDateEpochDay` INTEGER, " +
                "`color` INTEGER, " +
                "`icon` TEXT, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_savings_goals_accountId` " +
                "ON `savings_goals` (`accountId`)",
        )
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
