package com.callbackdev.saldo.core.domain.account

import com.callbackdev.saldo.core.domain.model.Account

/**
 * Picks the account a new movement should start on: the explicit Settings
 * default when it points to an active account, otherwise the last used one,
 * otherwise the first active one. An archived or deleted default is silently
 * skipped rather than surfacing an error.
 *
 * Pure so both the editor and the widget's quick entry resolve it the same way.
 */
object DefaultAccountResolver {

    /** [accounts] is expected to already exclude archived accounts. */
    fun resolve(
        accounts: List<Account>,
        defaultAccountId: Long?,
        lastUsedAccountId: Long?,
    ): Account? =
        accounts.firstOrNull { it.id == defaultAccountId }
            ?: accounts.firstOrNull { it.id == lastUsedAccountId }
            ?: accounts.firstOrNull()
}
