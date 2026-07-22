package com.callbackdev.saldo.feature.transactions

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The primary locale, read observably so composables recompose on change. */
@Composable
private fun currentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
}

/**
 * Plain short date: "6 Jul", with the year appended only when it differs from
 * [today]'s (or always, with [forceYear]). No "Today"/"Yesterday" prefix: the
 * building block of the other short labels and of the date-filter labels,
 * where a prefix would read badly.
 */
@Composable
fun shortDayLabel(date: LocalDate, today: LocalDate, forceYear: Boolean = false): String {
    val locale = currentLocale()
    return remember(date, today, locale, forceYear) {
        val skeleton = if (!forceYear && date.year == today.year) "dMMM" else "dMMMy"
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
            .withLocaleDateCasing(locale)
    }
}

/**
 * Human label of a (possibly open-ended) period: "5 Jul – 10 Jul", "From
 * 5 Jul" or "Until 5 Jul"; null when both bounds are null. Shared by the
 * custom-period chip and the picker sheet's summary so the two always
 * describe the same filter the same way. A range spanning different years
 * shows the year on both bounds: "5 Jul 2024 – 10 Jul" would read as two
 * dates of the same year.
 */
@Composable
fun periodLabel(start: LocalDate?, end: LocalDate?, today: LocalDate): String? = when {
    start != null && end != null -> {
        val forceYear = start.year != end.year
        stringResource(
            R.string.filter_date_range_label,
            shortDayLabel(start, today, forceYear),
            shortDayLabel(end, today, forceYear),
        )
    }

    start != null -> stringResource(R.string.filter_date_from_label, shortDayLabel(start, today))
    end != null -> stringResource(R.string.filter_date_until_label, shortDayLabel(end, today))
    else -> null
}

/**
 * Compact day label for the editor's date chip: "Today, 6 Jul" / "Yesterday,
 * 5 Jul", otherwise a short date (with the year only when it differs).
 */
@Composable
fun chipDayLabel(date: LocalDate, today: LocalDate): String {
    val shortDate = shortDayLabel(date, today)
    return when (date) {
        today -> "${stringResource(R.string.date_today)}, $shortDate"
        today.minusDays(1) -> "${stringResource(R.string.date_yesterday)}, $shortDate"
        else -> shortDate
    }
}

/**
 * Very compact day label for a trailing slot (e.g. the dashboard's recent
 * movements): "Today" / "Yesterday", otherwise a short date "6 Jul" (with the
 * year only when it differs). Shorter than [chipDayLabel], which also appends
 * the date to the "Today"/"Yesterday" cases.
 */
@Composable
fun compactDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    else -> shortDayLabel(date, today)
}

/**
 * Locale-formatted exchange rate for the implied-rate line of a cross-currency
 * transfer. Not money (never shown with a currency symbol), so plain number
 * formatting with enough decimals to be a useful plausibility check.
 */
@Composable
fun rateLabel(rate: BigDecimal): String {
    val locale = currentLocale()
    return remember(rate, locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = RATE_MIN_DECIMALS
            maximumFractionDigits = RATE_MAX_DECIMALS
        }.format(rate)
    }
}

private const val RATE_MIN_DECIMALS = 2
private const val RATE_MAX_DECIMALS = 4

/**
 * Human day label: "Today", "Yesterday", then a localized weekday + date
 * (with the year only when it differs from the current one). Casing is
 * normalized per locale: lowercase in Italian even on OEM ICU builds that
 * titlecase standalone names, capitalized in English.
 */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    else -> {
        val locale = currentLocale()
        val skeleton = if (date.year == today.year) "EEEEdMMMM" else "EEEEdMMMMy"
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
            .withLocaleDateCasing(locale)
    }
}
