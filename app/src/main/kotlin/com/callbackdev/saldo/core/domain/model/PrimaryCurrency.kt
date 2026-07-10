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
 */
fun List<AccountWithBalance>.primaryCurrency(): Currency = this
    .filter { !it.account.isArchived && it.account.isIncludedInTotal }
    .groupingBy { it.account.currency }
    .eachCount()
    .maxByOrNull { it.value }?.key
    ?: fallbackCurrency
