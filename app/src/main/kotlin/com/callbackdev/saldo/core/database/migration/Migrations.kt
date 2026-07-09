package com.callbackdev.saldo.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, tested Room migrations (PLANNING: never `fallbackToDestructiveMigration`).
 *
 * v1 -> v2: adds the `color` and `icon` columns to `recurring_rules` so a
 * subscription can carry its own avatar, like accounts and categories. Both are
 * nullable, so existing rows keep NULL and no data is lost.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recurring_rules ADD COLUMN color INTEGER")
        db.execSQL("ALTER TABLE recurring_rules ADD COLUMN icon TEXT")
    }
}

/** All migrations, applied in order by Room. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
