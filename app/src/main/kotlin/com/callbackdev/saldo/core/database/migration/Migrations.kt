package com.callbackdev.saldo.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, tested Room migrations (PLANNING ADR: never `fallbackToDestructiveMigration`).
 *
 * The schema history was collapsed to a single version-1 baseline while the app
 * was still unpublished (no database exists in the wild to preserve). The policy
 * applies from every schema change onward: add a `Migration(N, N+1)` here, append
 * it to [ALL_MIGRATIONS], bump the database version, and cover it with an
 * instrumented test that validates against the exported schema JSON.
 */

/**
 * Adds the destination leg to `recurring_rules` so a rule can generate transfers
 * (PLANNING ADR 24), mirroring the transfer columns on `transactions`. All three
 * columns are nullable and left null for the existing expense/income rules.
 */
val MIGRATION_1_2 = Migration(1, 2) { db: SupportSQLiteDatabase ->
    db.execSQL("ALTER TABLE recurring_rules ADD COLUMN transferAccountId INTEGER")
    db.execSQL("ALTER TABLE recurring_rules ADD COLUMN transferAmountMinor INTEGER")
    db.execSQL("ALTER TABLE recurring_rules ADD COLUMN transferCurrency TEXT")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_recurring_rules_transferAccountId " +
            "ON recurring_rules (transferAccountId)",
    )
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
