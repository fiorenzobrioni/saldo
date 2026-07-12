package com.callbackdev.saldo.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a monthly budget (schema version 6). A row with a null
 * [categoryId] is the overall monthly budget; rows with a category id cap a
 * single expense category. The unique index keeps one budget per category;
 * SQLite treats NULLs as distinct, so the single-overall-budget rule is
 * enforced by the repository (every overall write goes through one
 * transactional upsert).
 *
 * [amountMinor] is the monthly limit as a positive magnitude. The two
 * watermarks record the last month (proleptic month: year * 12 + month - 1)
 * each threshold notification was posted for, so every threshold fires at
 * most once per month per budget (same pattern as
 * [RecurringRuleEntity.lastReminderEpochDay]).
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId", unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val categoryId: Long?,
    val amountMinor: Long,
    val currency: String,
    val lastNotified80EpochMonth: Long? = null,
    val lastNotified100EpochMonth: Long? = null,
)
