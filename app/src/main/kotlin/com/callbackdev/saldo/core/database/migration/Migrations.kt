package com.callbackdev.saldo.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, tested Room migrations (PLANNING: never `fallbackToDestructiveMigration`).
 *
 * v1 -> v2: adds the `color` and `icon` columns to `recurring_rules` so a
 * subscription can carry its own avatar, like accounts and categories. Both are
 * nullable, so existing rows keep NULL and no data is lost.
 *
 * v2 -> v3: adds `isPending` to `transactions` (confirm-mode / variable-amount
 * recurring movements await confirmation). NOT NULL DEFAULT 0, so every existing
 * movement is a confirmed one.
 *
 * v3 -> v4: adds `recurringOccurrenceEpochDay` to `transactions` plus a unique
 * index on (recurringRuleId, recurringOccurrenceEpochDay), the database-level
 * backstop against generating the same recurring occurrence twice. Existing
 * generated movements are backfilled from their local date; if the pre-fix bug
 * already produced duplicates, only the oldest row per occurrence is backfilled
 * (the extras keep NULL, which the unique index permits) so no data is dropped.
 *
 * v4 -> v5: adds `lastReminderEpochDay` to `recurring_rules`, the watermark of
 * the pre-renewal reminder notification (the occurrence date last reminded, so
 * each upcoming charge is reminded once). Nullable: existing rules have never
 * been reminded.
 *
 * v5 -> v6: creates the `budgets` table (monthly budgets: one optional overall
 * row with NULL categoryId plus per-category caps, unique index on categoryId)
 * with the per-threshold notification watermarks. DDL copied verbatim from the
 * schema Room exports for version 6, so validation matches byte for byte.
 *
 * v6 -> v7: adds `sortOrderIncome` to `categories`, the income tab's own manual
 * order, so reordering the expense tab no longer disturbs BOTH categories in the
 * income tab. Seeded from the existing `sortOrder` so the current income
 * ordering is preserved, with a matching index.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recurring_rules ADD COLUMN color INTEGER")
        db.execSQL("ALTER TABLE recurring_rules ADD COLUMN icon TEXT")
    }
}

@Suppress("MagicNumber") // Schema version numbers.
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN isPending INTEGER NOT NULL DEFAULT 0")
    }
}

@Suppress("MagicNumber") // Schema version numbers.
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN recurringOccurrenceEpochDay INTEGER")
        db.execSQL(
            """
            UPDATE transactions SET recurringOccurrenceEpochDay =
                (timestampEpochMilli + zoneOffsetSeconds * 1000) / 86400000
            WHERE recurringRuleId IS NOT NULL AND id IN (
                SELECT MIN(id) FROM transactions
                WHERE recurringRuleId IS NOT NULL
                GROUP BY recurringRuleId,
                    (timestampEpochMilli + zoneOffsetSeconds * 1000) / 86400000
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_transactions_recurringRuleId_recurringOccurrenceEpochDay` " +
                "ON `transactions` (`recurringRuleId`, `recurringOccurrenceEpochDay`)",
        )
    }
}

@Suppress("MagicNumber") // Schema version numbers.
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recurring_rules ADD COLUMN lastReminderEpochDay INTEGER")
    }
}

@Suppress("MagicNumber") // Schema version numbers.
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`lastNotified80EpochMonth` INTEGER, " +
                "`lastNotified100EpochMonth` INTEGER, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId` " +
                "ON `budgets` (`categoryId`)",
        )
    }
}

@Suppress("MagicNumber") // Schema version numbers.
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE categories ADD COLUMN sortOrderIncome INTEGER NOT NULL DEFAULT 0",
        )
        // Preserve the current income ordering, which was driven by sortOrder.
        db.execSQL("UPDATE categories SET sortOrderIncome = sortOrder")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_categories_sortOrderIncome` " +
                "ON `categories` (`sortOrderIncome`)",
        )
    }
}

/** All migrations, applied in order by Room. */
val ALL_MIGRATIONS: Array<Migration> =
    arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
    )
