package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DecimalFormatSymbols
import java.util.Locale

/** The separators the amount surfaces need: what the keypad prints and what groups thousands. */
@Immutable
data class AmountSymbols(val decimal: Char, val grouping: Char)

/**
 * Separators of the current locale, read observably so the keypad and the
 * amount display recompose when the user changes language.
 */
@Composable
fun rememberAmountSymbols(): AmountSymbols {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val locale = configuration.locales[0] ?: Locale.getDefault()
        val symbols = DecimalFormatSymbols.getInstance(locale)
        AmountSymbols(decimal = symbols.decimalSeparator, grouping = symbols.groupingSeparator)
    }
}
