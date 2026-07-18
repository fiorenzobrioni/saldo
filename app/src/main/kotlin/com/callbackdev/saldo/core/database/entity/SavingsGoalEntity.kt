package com.callbackdev.saldo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a savings goal (schema version 2). The amount saved is never
 * stored: it is the linked account's computed balance (see the balance queries
 * in [com.callbackdev.saldo.core.database.dao.AccountDao]). [accountId]
 * references the dedicated savings account through a unique index, enforcing one
 * goal per account (the pot/vault model); deleting the account cascades the goal
 * away.
 *
 * [targetAmountMinor] is a positive magnitude; [targetDateEpochDay] is optional
 * ([java.time.LocalDate.toEpochDay]). [color] (0xRRGGBB) and [icon] (Material
 * Symbols key) drive the goal avatar, mirroring accounts and categories.
 */
@Entity(
    tableName = "savings_goals",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId", unique = true)],
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "targetAmountMinor")
    val targetAmountMinor: Long,
    val currency: String,
    val accountId: Long,
    @ColumnInfo(name = "targetDateEpochDay")
    val targetDateEpochDay: Long? = null,
    val color: Int? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
)
