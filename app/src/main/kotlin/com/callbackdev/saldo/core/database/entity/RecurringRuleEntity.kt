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
 * ([java.time.LocalDate.toEpochDay]); [amountMinor] is null for variable-amount rules.
 * The generation engine is Phase 6.
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
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId"), Index("categoryId")],
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
    val note: String? = null,
)
