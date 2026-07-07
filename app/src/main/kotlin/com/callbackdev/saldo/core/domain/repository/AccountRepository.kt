package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.Currency

/** Read/write access to accounts and their computed balances. */
interface AccountRepository {

    /** All accounts (archived included) with their current balance, ordered for display. */
    fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>>

    /** A single account, or null if it does not exist. */
    fun observeAccount(id: Long): Flow<Account?>

    /** The current balance of a single account (`initialBalance + Σ movements`). */
    fun observeAccountBalance(id: Long): Flow<BigDecimal>

    /**
     * The total balance across accounts that are included in the total and not
     * archived, restricted to [currency].
     */
    fun observeTotalBalance(currency: Currency): Flow<BigDecimal>

    suspend fun getAccount(id: Long): Account?

    /** Inserts a new account (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(account: Account): Long

    suspend fun delete(account: Account)
}
