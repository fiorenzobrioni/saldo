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
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    @ColumnInfo(name = "createdAtEpochMilli")
    val createdAtEpochMilli: Long = 0L,
)
