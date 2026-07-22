package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/** Read/write access to accounts and their computed balances. */
interface AccountRepository {

    /** All accounts (archived included) with their current balance, ordered for display. */
    fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>>

    /**
     * Like [observeAccountsWithBalance] but each account also carries its
     * balance as of today ([AccountWithBalance.balanceAsOfToday], non-null only
     * when it diverges from the total). [todayEpochDayExclusive] is today's
     * local day plus one; movements dated on or after it are left out of the
     * "as of today" figure.
     */
    fun observeAccountsWithBalanceAsOfToday(
        todayEpochDayExclusive: Long,
    ): Flow<List<AccountWithBalance>>

    /** A single account, or null if it does not exist. */
    fun observeAccount(id: Long): Flow<Account?>

    /** The current balance of a single account (`initialBalance + Σ movements`). */
    fun observeAccountBalance(id: Long): Flow<BigDecimal>

    /**
     * The total balance across accounts that are included in the total and not
     * archived, restricted to [currency].
     */
    fun observeTotalBalance(currency: Currency): Flow<BigDecimal>

    /**
     * Sum of the initial balances of the accounts included in the total, not
     * archived and denominated in [currency]; the starting point of the
     * balance-over-time statistic.
     */
    fun observeInitialBalanceTotal(currency: Currency): Flow<BigDecimal>

    suspend fun getAccount(id: Long): Account?

    /** Inserts a new account (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(account: Account): Long

    /**
     * Next free sort position within [type], so a freshly created account
     * appends to the end of its own type group instead of jumping into the
     * middle of a manually arranged list.
     */
    suspend fun nextSortOrder(type: AccountType): Int

    /**
     * Persists a manual reorder of the active accounts. Ordering is confined to
     * within each type group, so each account's [Account.sortOrder] is rewritten
     * to its index among the accounts of its own type in [orderedActive].
     */
    suspend fun reorder(orderedActive: List<Account>)

    /**
     * Advances the credit card settlement watermark to [closing] without
     * touching the rest of the account row (safe against a concurrent edit).
     */
    suspend fun updateSettlementWatermark(accountId: Long, closing: LocalDate)

    suspend fun delete(account: Account)
}
