package com.callbackdev.saldo.core.domain.model

import java.util.Currency
import java.util.Locale

/**
 * The default currency when no account gives a signal: the device locale's,
 * falling back to EUR for locales without one.
 */
val fallbackCurrency: Currency =
    runCatching { Currency.getInstance(Locale.getDefault()) }.getOrNull()
        ?: Currency.getInstance("EUR")

/**
 * The app's primary currency: the one shared by most non-archived accounts
 * included in the total. All single-currency aggregates (dashboard,
 * statistics) are restricted to it; multi-currency conversion is a later
 * feature (VISION).
 *
 * Ties are broken deterministically ([fallbackCurrency] first, then the ISO
 * code alphabetically) instead of by account order: with two currencies at
 * the same count, an arbitrary winner would let an unrelated edit flip every
 * aggregate on the screen.
 */
fun List<AccountWithBalance>.primaryCurrency(): Currency = this
    .filter { !it.account.isArchived && it.account.isIncludedInTotal }
    .groupingBy { it.account.currency }
    .eachCount()
    .entries
    .sortedWith(
        compareByDescending<Map.Entry<Currency, Int>> { it.value }
            .thenByDescending { it.key == fallbackCurrency }
            .thenBy { it.key.currencyCode },
    )
    .firstOrNull()?.key
    ?: fallbackCurrency

/**
 * The primary currency honoring an explicit user choice (Settings or
 * onboarding) over the automatic account-plurality rule. A null [override]
 * means "automatic", which is also what existing installs get until they
 * pick one.
 */
fun primaryCurrency(accounts: List<AccountWithBalance>, override: Currency?): Currency =
    override ?: accounts.primaryCurrency()
