package com.callbackdev.saldo.core.database.relation

import androidx.room.Embedded
import com.callbackdev.saldo.core.database.entity.AccountEntity

/** An account row plus its computed balance in minor units, from a DAO query. */
data class AccountWithBalanceRow(
    @Embedded
    val account: AccountEntity,
    val balanceMinor: Long,
)
