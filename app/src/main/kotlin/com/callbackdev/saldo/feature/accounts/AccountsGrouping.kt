package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import java.math.BigDecimal

/**
 * Groups accounts by [AccountType] and orders the result:
 * - groups follow the enum declaration order, so CHECKING (conto corrente) comes first;
 * - accounts within a group follow their manual position ([Account.sortOrder]),
 *   with name (case-insensitive) as the tie-break so an untouched group stays
 *   alphabetical.
 *
 * Each group carries its balance subtotal for the section header, or null when
 * the group mixes currencies (see [AccountTypeGroup]).
 *
 * Pure function, JVM-testable; mirrors [com.callbackdev.saldo.feature.transactions.buildDayGroups].
 */
internal fun buildAccountTypeGroups(
    items: List<AccountWithBalance>,
): List<AccountTypeGroup> = items
    .groupBy { it.account.type }
    .map { (type, group) ->
        val currency = group.map { it.account.currency }.distinct().singleOrNull()
        AccountTypeGroup(
            type = type,
            accounts = group.sortedWith(accountOrder),
            // Only a single-currency group has a meaningful sum; otherwise the
            // header shows the type label alone.
            subtotal = currency?.let { group.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.balance) } },
            currency = currency,
        )
    }
    .sortedBy { it.type.ordinal }

/**
 * The same ordering flattened into a single list (type declaration order, then
 * manual position, then name). Used by the archived section and by the
 * dashboard balance breakdown, which stay flat without per-type headers.
 */
internal fun List<AccountWithBalance>.sortedByTypeThenName(): List<AccountWithBalance> =
    sortedWith(compareBy<AccountWithBalance> { it.account.type.ordinal }.then(accountOrder))

private val accountOrder: Comparator<AccountWithBalance> =
    compareBy<AccountWithBalance> { it.account.sortOrder }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.account.name }
        .thenBy { it.account.id }
