package com.callbackdev.saldo.feature.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.glance.color.ColorProviders
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.designsystem.theme.BrandDarkColorScheme
import com.callbackdev.saldo.core.designsystem.theme.BrandLightColorScheme
import androidx.glance.material3.ColorProviders as GlanceColorProviders

/**
 * The palette a placed widget draws in, resolved from the same Settings the app
 * itself obeys: brand palette by default, dynamic color when opted in, and the
 * chosen light/dark mode rather than only the launcher's.
 *
 * The resolved [scheme] is handed out alongside the Glance [providers] because
 * the category tiles are rasterized bitmaps ([CategoryIconBitmaps]) and need
 * real color values, not providers. Both sides therefore come from one decision
 * and cannot disagree.
 */
data class QuickAddWidgetTheme(
    val scheme: ColorScheme,
    val providers: ColorProviders,
)

/**
 * A forced light or dark theme is passed to Glance as the *same* scheme on both
 * branches: the launcher would otherwise pick by its own night mode and undo
 * the choice made in Settings.
 */
fun resolveWidgetTheme(context: Context, preferences: ThemePreferences): QuickAddWidgetTheme {
    val dark = when (preferences.mode) {
        ThemeMode.SYSTEM -> context.isSystemInDarkMode()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = when {
        preferences.useDynamicColor && dark -> dynamicDarkColorScheme(context)
        preferences.useDynamicColor -> dynamicLightColorScheme(context)
        dark -> BrandDarkColorScheme
        else -> BrandLightColorScheme
    }
    return QuickAddWidgetTheme(scheme = scheme, providers = GlanceColorProviders(scheme, scheme))
}

private fun Context.isSystemInDarkMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
