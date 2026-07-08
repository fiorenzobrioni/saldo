package com.callbackdev.saldo.feature.transactions

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.callbackdev.saldo.R
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The primary locale, read observably so composables recompose on change. */
@Composable
private fun currentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
}

/** The decimal separator of the current locale, for keypad and amount display. */
@Composable
fun rememberDecimalSeparator(): Char {
    val locale = currentLocale()
    return remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
}

/**
 * Human day label: "Today", "Yesterday", then a localized weekday + date
 * (with the year only when it differs from the current one).
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
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}
