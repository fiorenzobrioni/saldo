package com.callbackdev.saldo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * Room row for a movement. See [com.callbackdev.saldo.core.domain.model.Transaction]
 * for the amount-sign and transfer conventions.
 *
 * @property amountMinor signed effect on [accountId], in minor units of [currency].
 * @property transferAmountMinor for transfers, the positive effect on
 *   [transferAccountId] in minor units of [transferCurrency]; null otherwise.
 * @property timestampEpochMilli UTC instant of the movement.
 * @property zoneOffsetSeconds device UTC offset when recorded, for correct local grouping.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["transferAccountId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringRuleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("transferAccountId"),
        Index("categoryId"),
        Index("recurringRuleId"),
        Index("timestampEpochMilli"),
        Index("type"),
        Index("currency"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: TransactionType,
    @ColumnInfo(name = "amountMinor")
    val amountMinor: Long,
    val currency: String,
    val accountId: Long,
    @ColumnInfo(name = "timestampEpochMilli")
    val timestampEpochMilli: Long,
    val zoneOffsetSeconds: Int,
    val transferAccountId: Long? = null,
    @ColumnInfo(name = "transferAmountMinor")
    val transferAmountMinor: Long? = null,
    val transferCurrency: String? = null,
    val categoryId: Long? = null,
    val description: String? = null,
    val note: String? = null,
    val isExcludedFromStats: Boolean = false,
    val isRefund: Boolean = false,
    val recurringRuleId: Long? = null,
    /**
     * A recurring movement awaiting confirmation (confirm mode / variable amount).
     * Pending movements are excluded from balances and statistics until confirmed.
     * Added in schema version 3.
     */
    @ColumnInfo(name = "isPending", defaultValue = "0")
    val isPending: Boolean = false,
)
