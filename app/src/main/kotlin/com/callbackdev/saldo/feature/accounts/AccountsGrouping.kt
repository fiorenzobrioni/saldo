package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.core.domain.rates.RateTable
import java.math.BigDecimal
import java.util.Currency

/**
 * Groups accounts by [AccountType] and orders the result:
 * - groups follow the enum declaration order, so CHECKING (conto corrente) comes first;
 * - accounts within a group follow their manual position ([Account.sortOrder]),
 *   with name (case-insensitive) as the tie-break so an untouched group stays
 *   alphabetical.
 *
 * Each group carries its balance subtotal for the section header. A
 * single-currency group sums exactly in its own currency; a mixed group,
 * which used to show no figure at all, now sums in [primary] with the foreign
 * balances converted at the latest known rate (ADR 40) and says so with the
 * estimated flag - unless some balance has no usable rate, in which case the
 * header falls back to the label alone rather than a partial figure.
 *
 * Pure function, JVM-testable; mirrors [com.callbackdev.saldo.feature.transactions.buildDayGroups].
 */
internal fun buildAccountTypeGroups(
    items: List<AccountWithBalance>,
    primary: Currency? = null,
    rates: RateTable = RateTable.EMPTY,
): List<AccountTypeGroup> = items
    .groupBy { it.account.type }
    .map { (type, group) ->
        val currency = group.map { it.account.currency }.distinct().singleOrNull()
        when {
            currency != null -> AccountTypeGroup(
                type = type,
                accounts = group.sortedWith(accountOrder),
                subtotal = group.sumOf { it.balance },
                currency = currency,
                subtotalAsOfToday = group
                    .sumOf { it.balanceAsOfToday ?: it.balance }
                    .takeIf { today -> today.compareTo(group.sumOf { it.balance }) != 0 },
            )

            else -> convertedGroup(type, group, primary, rates)
        }
    }
    .sortedBy { it.type.ordinal }

/** A mixed-currency group: converted subtotal when every balance has a rate, bare label otherwise. */
private fun convertedGroup(
    type: AccountType,
    group: List<AccountWithBalance>,
    primary: Currency?,
    rates: RateTable,
): AccountTypeGroup {
    val sorted = group.sortedWith(accountOrder)
    if (primary == null) return AccountTypeGroup(type = type, accounts = sorted)
    val subtotal = group.convertedSum(primary, rates) { it.balance }
        ?: return AccountTypeGroup(type = type, accounts = sorted)
    return AccountTypeGroup(
        type = type,
        accounts = sorted,
        subtotal = subtotal,
        currency = primary,
        subtotalEstimated = true,
        subtotalAsOfToday = group
            .convertedSum(primary, rates) { it.balanceAsOfToday ?: it.balance }
            ?.takeIf { today -> today.compareTo(subtotal) != 0 },
    )
}

/**
 * Sum of one balance per account converted into [primary] at the latest known
 * rate; null as soon as one balance cannot be converted (a partial subtotal
 * would silently under-report, the exact failure the notices exist to avoid).
 */
private inline fun List<AccountWithBalance>.convertedSum(
    primary: Currency,
    rates: RateTable,
    balanceOf: (AccountWithBalance) -> BigDecimal,
): BigDecimal? {
    var total = BigDecimal.ZERO
    forEach { item ->
        val balance = balanceOf(item)
        val converted = if (item.account.currency == primary) {
            balance
        } else {
            CurrencyConverter.convertAtLatest(balance, item.account.currency, primary, rates)
                ?.amount
                ?: return null
        }
        total = total.add(converted)
    }
    return total
}

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
