package com.callbackdev.saldo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * Room row for a recurring rule. Dates are stored as epoch days
 * ([java.time.LocalDate.toEpochDay]); [amountMinor] is null for variable-amount
 * rules. [color] (0xRRGGBB) and [icon] (Material Symbols key) drive the
 * subscription avatar and were added in schema version 2 (both nullable).
 * [lastReminderEpochDay] (schema version 5) is the occurrence date the last
 * pre-renewal reminder was posted for, so each occurrence is reminded once.
 * The [transferAccountId]/[transferAmountMinor]/[transferCurrency] columns
 * (schema version 2 of the collapsed baseline) mirror [TransactionEntity] and
 * carry the destination leg of a TRANSFER rule; null for expense/income rules.
 */
@Entity(
    tableName = "recurring_rules",
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
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("transferAccountId")],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: TransactionType,
    val currency: String,
    val accountId: Long,
    val frequency: RecurrenceFrequency,
    @ColumnInfo(name = "startDateEpochDay")
    val startDateEpochDay: Long,
    @ColumnInfo(name = "amountMinor")
    val amountMinor: Long? = null,
    val categoryId: Long? = null,
    val dayOfReference: Int? = null,
    @ColumnInfo(name = "endDateEpochDay")
    val endDateEpochDay: Long? = null,
    val mode: RecurrenceMode = RecurrenceMode.AUTOMATIC,
    val isVariableAmount: Boolean = false,
    @ColumnInfo(name = "lastGeneratedEpochDay")
    val lastGeneratedEpochDay: Long? = null,
    val color: Int? = null,
    val icon: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "lastReminderEpochDay")
    val lastReminderEpochDay: Long? = null,
    val transferAccountId: Long? = null,
    @ColumnInfo(name = "transferAmountMinor")
    val transferAmountMinor: Long? = null,
    val transferCurrency: String? = null,
    /**
     * A paused rule generates nothing and is priced at zero until resumed
     * (Fase 39, F3). Added in schema v5 with a DEFAULT so existing rows stay
     * active.
     */
    @ColumnInfo(name = "isPaused", defaultValue = "0")
    val isPaused: Boolean = false,
)
