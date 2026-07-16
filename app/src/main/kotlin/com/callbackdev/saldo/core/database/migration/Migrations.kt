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
 *
 * v7 -> v8: adds `isIncludedInBudget` to `accounts`, an axis independent of
 * `isIncludedInTotal`: an account can count toward the total balance yet be kept
 * out of the budget/safe-to-spend spend (e.g. a savings account you occasionally
 * pay from). NOT NULL DEFAULT 1, so every existing account keeps counting toward
 * the budget, matching today's behaviour.
 *
 * v8 -> v9: adds the credit card columns to `accounts` (`creditLimitMinor`,
 * `statementClosingDay`, `paymentDueDay`, `linkedAccountId`, `statementAutoPost`,
 * `lastSettledClosingEpochDay`). All meaningful only for AccountType.CREDIT_CARD;
 * every existing account keeps NULL (and statementAutoPost 0), so it stays a
 * plain account. No foreign key on `linkedAccountId` on purpose: a self-reference
 * added via ALTER TABLE would complicate the migration and Room schema check for
 * no gain, referential integrity is handled in application logic instead.
 *
 * v9 -> v10: data-only. The generic CARD account type is removed in favour of
 * the explicit DEBIT_CARD and PREPAID_CARD: existing CARD rows become
 * DEBIT_CARD (the closest semantics; a prepaid user can switch the type in the
 * editor). No schema change. The enum value is gone from the code, so this
 * rewrite is what keeps pre-split rows decodable.
 *
 * v10 -> v11: data-only. DEBIT_CARD is retired one release after its
 * introduction: a debit card spends straight from the bank account and has no
 * balance of its own, so it never was a money container (the checking type's
 * contextual description now explains where to record that spending). Existing
 * DEBIT_CARD rows become CHECKING; the SAVINGS type added in the same release
 * needs no migration. As with v10, the enum value is gone from the code.
 *
 * v11 -> v12: data-only. Backfills the "Prestiti & Finanziamenti" default
 * expense category (loan and financing instalments, tracked as recurring
 * expenses) into databases seeded before it joined the default set. The name
 * is Italian on purpose: the seed localizes on first launch, but the only
 * pre-v12 installs are the developer's Italian test devices. Guarded so it
 * never duplicates a category the user already created with that name.
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

@Suppress("MagicNumber") // Schema version numbers.
val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE accounts ADD COLUMN isIncludedInBudget INTEGER NOT NULL DEFAULT 1",
        )
    }
}

@Suppress("MagicNumber") // Schema version numbers.
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN creditLimitMinor INTEGER")
        db.execSQL("ALTER TABLE accounts ADD COLUMN statementClosingDay INTEGER")
        db.execSQL("ALTER TABLE accounts ADD COLUMN paymentDueDay INTEGER")
        db.execSQL("ALTER TABLE accounts ADD COLUMN linkedAccountId INTEGER")
        db.execSQL("ALTER TABLE accounts ADD COLUMN statementAutoPost INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE accounts ADD COLUMN lastSettledClosingEpochDay INTEGER")
    }
}

@Suppress("MagicNumber") // Schema version numbers.
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE accounts SET type = 'DEBIT_CARD' WHERE type = 'CARD'")
    }
}

@Suppress("MagicNumber") // Schema version numbers.
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE accounts SET type = 'CHECKING' WHERE type = 'DEBIT_CARD'")
    }
}

@Suppress("MagicNumber") // Schema version numbers.
val MIGRATION_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO categories (name, type, color, icon, sortOrder, sortOrderIncome, isDefault)
            SELECT 'Prestiti & Finanziamenti', 'EXPENSE', 2541274, 'request_quote',
                (SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories),
                (SELECT COALESCE(MAX(sortOrderIncome), -1) + 1 FROM categories),
                1
            WHERE NOT EXISTS (
                SELECT 1 FROM categories WHERE name = 'Prestiti & Finanziamenti'
            )
            """.trimIndent(),
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
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
    )
