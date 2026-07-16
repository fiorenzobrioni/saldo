package com.callbackdev.saldo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.callbackdev.saldo.core.domain.model.AccountType

/**
 * Room row for an account. The balance is never stored: it is computed from
 * [initialBalanceMinor] plus the movements (see the balance queries in the DAO).
 *
 * @property currency ISO 4217 code (e.g. "EUR").
 * @property color RGB colour (0xRRGGBB) or null.
 */
@Entity(
    tableName = "accounts",
    indices = [Index("isArchived"), Index("sortOrder")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: AccountType,
    val currency: String,
    @ColumnInfo(name = "initialBalanceMinor")
    val initialBalanceMinor: Long,
    val color: Int? = null,
    val icon: String? = null,
    val isIncludedInTotal: Boolean = true,
    val isIncludedInBudget: Boolean = true,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    @ColumnInfo(name = "createdAtEpochMilli")
    val createdAtEpochMilli: Long = 0L,
    // --- Credit card fields (schema version 9). Meaningful only for
    // AccountType.CREDIT_CARD; null/false for every other account. ---
    /** Card limit (fido) in minor units, for the utilisation indicator; null = untracked. */
    @ColumnInfo(name = "creditLimitMinor")
    val creditLimitMinor: Long? = null,
    /** Day of month the billing cycle closes, 1..31 (beyond month length = last day). */
    val statementClosingDay: Int? = null,
    /** Day of the following month the statement is charged, 1..31. */
    val paymentDueDay: Int? = null,
    /** Account charged for the statement (transfer destination), null until chosen. */
    val linkedAccountId: Long? = null,
    /** True posts the statement transfer automatically; false waits for confirmation. */
    @ColumnInfo(name = "statementAutoPost", defaultValue = "0")
    val statementAutoPost: Boolean = false,
    /** Closing date (epoch day) of the last settled cycle, the settlement watermark. */
    @ColumnInfo(name = "lastSettledClosingEpochDay")
    val lastSettledClosingEpochDay: Long? = null,
)
