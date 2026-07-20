package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance

/**
 * One account-type section of the accounts list: the [type] and its accounts,
 * already sorted alphabetically by name.
 */
data class AccountTypeGroup(
    val type: AccountType,
    val accounts: List<AccountWithBalance>,
)

/**
 * Groups accounts by [AccountType] and orders the result:
 * - groups follow the enum declaration order, so CHECKING (conto corrente) comes first;
 * - accounts within a group are alphabetical by name, case-insensitive.
 *
 * Pure function, JVM-testable; mirrors [com.callbackdev.saldo.feature.transactions.buildDayGroups].
 */
internal fun buildAccountTypeGroups(
    items: List<AccountWithBalance>,
): List<AccountTypeGroup> = items
    .groupBy { it.account.type }
    .map { (type, group) -> AccountTypeGroup(type, group.sortedWith(accountOrder)) }
    .sortedBy { it.type.ordinal }

/**
 * The same ordering flattened into a single list (type declaration order, then
 * name). Used by the archived section, which stays a single collapsed card
 * without per-type headers.
 */
internal fun List<AccountWithBalance>.sortedByTypeThenName(): List<AccountWithBalance> =
    sortedWith(compareBy<AccountWithBalance> { it.account.type.ordinal }.then(accountOrder))

private val accountOrder: Comparator<AccountWithBalance> =
    compareBy<AccountWithBalance, String>(String.CASE_INSENSITIVE_ORDER) { it.account.name }
        .thenBy { it.account.id }
