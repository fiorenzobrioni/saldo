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
import androidx.glance.material3.ColorProviders as GlanceColorProviders

/** The palette a placed widget draws in. */
data class QuickAddWidgetTheme(
    val scheme: ColorScheme,
    val providers: ColorProviders,
    val background: Color,
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
        WidgetAppearance.SYSTEM -> when (preferences.mode) {
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
        background = scheme.background,
    )
}

private fun Context.isSystemInDarkMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
