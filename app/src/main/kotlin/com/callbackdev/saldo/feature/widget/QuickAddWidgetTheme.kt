package com.callbackdev.saldo.feature.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.designsystem.theme.BrandDarkColorScheme
import com.callbackdev.saldo.core.designsystem.theme.BrandLightColorScheme
import com.callbackdev.saldo.core.designsystem.theme.MoneyColors
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import androidx.glance.material3.ColorProviders as GlanceColorProviders

/** The palette a placed widget draws in. */
data class QuickAddWidgetTheme(
    val scheme: ColorScheme,
    val providers: ColorProviders,
    val background: Color,
    /**
     * The accents of the single-row layout's two buttons.
     *
     * A deliberate, narrow exception to [MoneyColors], which keeps `expense`
     * neutral on purpose: colouring every expense in a ledger would shout, and
     * there the minus sign and the icon carry the distinction. Two action
     * buttons alone on a widget are not a ledger - there is no other context to
     * read them by - so the colour does the fast work and the icons still do the
     * accessible work. `income` is the app's own green; the red is the scheme's
     * only one, worn as a 16% wash so it reads as soft rather than as an alarm.
     */
    val expenseAccent: Color,
    val incomeAccent: Color,
)

/**
 * Resolves the widget's palette from the app's theme settings and the widget's
 * own appearance override.
 *
 * A forced light or dark theme is handed to Glance as the *same* scheme on both
 * branches: the launcher would otherwise pick by its own night mode and undo the
 * choice made here.
 */
fun resolveWidgetTheme(
    context: Context,
    preferences: ThemePreferences,
    config: QuickAddWidgetConfig,
): QuickAddWidgetTheme {
    val dark = when (config.appearance) {
        WidgetAppearance.LIGHT -> false
        WidgetAppearance.DARK -> true
        // Transparent has no background of its own to take a side from, so its
        // ink follows the app the way SYSTEM does. What it sits on is the
        // wallpaper, and no code here can know how light that is.
        WidgetAppearance.SYSTEM, WidgetAppearance.TRANSPARENT -> when (preferences.mode) {
            ThemeMode.SYSTEM -> context.isSystemInDarkMode()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }
    val scheme = when {
        preferences.useDynamicColor && dark -> dynamicDarkColorScheme(context)
        preferences.useDynamicColor -> dynamicLightColorScheme(context)
        dark -> BrandDarkColorScheme
        else -> BrandLightColorScheme
    }
    return QuickAddWidgetTheme(
        scheme = scheme,
        providers = GlanceColorProviders(scheme, scheme),
        // The app's Dashboard sits on colorScheme.background, so a widget meant
        // to look like the app sits on it too.
        background = if (config.appearance == WidgetAppearance.TRANSPARENT) {
            Color.Transparent
        } else {
            scheme.background
        },
        expenseAccent = scheme.error,
        incomeAccent = moneyColors(scheme, dark).income,
    )
}

private fun Context.isSystemInDarkMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
