package com.callbackdev.saldo.feature.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.glance.color.ColorProviders
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.designsystem.theme.BrandDarkColorScheme
import com.callbackdev.saldo.core.designsystem.theme.BrandLightColorScheme
import androidx.glance.material3.ColorProviders as GlanceColorProviders

/**
 * The palette a placed widget draws in.
 *
 * [background] is kept apart from [scheme] because it is the one color the user
 * can override per widget, opacity included; everything else - labels, the type
 * selector, the brand color of the action tile - still comes from the app's own
 * scheme, so a widget never stops looking like Saldo.
 */
data class QuickAddWidgetTheme(
    val scheme: ColorScheme,
    val providers: ColorProviders,
    val background: Color,
)

/**
 * Resolves the widget's palette from the app's theme settings and the widget's
 * own overrides.
 *
 * A forced light or dark theme is handed to Glance as the *same* scheme on both
 * branches: the launcher would otherwise pick by its own night mode and undo the
 * choice. With a custom background the scheme is chosen by the luminance of that
 * color rather than by any theme setting, which is what keeps the labels
 * readable when someone puts a light widget on a dark app or the reverse.
 */
fun resolveWidgetTheme(
    context: Context,
    preferences: ThemePreferences,
    config: QuickAddWidgetConfig,
): QuickAddWidgetTheme {
    val dark = when (config.appearance) {
        WidgetAppearance.LIGHT -> false
        WidgetAppearance.DARK -> true
        WidgetAppearance.CUSTOM -> !config.backgroundColor.asOpaqueColor().isLight()
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
    // The app's Dashboard sits on colorScheme.background, so a widget that is
    // meant to look like the app sits on it too.
    val base = when (config.appearance) {
        WidgetAppearance.CUSTOM -> config.backgroundColor.asOpaqueColor()
        else -> scheme.background
    }
    return QuickAddWidgetTheme(
        scheme = scheme,
        providers = GlanceColorProviders(scheme, scheme),
        background = base.copy(alpha = config.backgroundOpacity / PERCENT),
    )
}

private const val PERCENT = 100f

/** Above this the color takes dark ink; the same threshold the avatars use. */
private const val LIGHT_LUMINANCE_THRESHOLD = 0.35f

private fun Int.asOpaqueColor(): Color = Color(OPAQUE or (this and RGB_MASK))

private fun Color.isLight(): Boolean = luminance() > LIGHT_LUMINANCE_THRESHOLD

private const val OPAQUE = 0xFF000000.toInt()

private const val RGB_MASK = 0xFFFFFF

private fun Context.isSystemInDarkMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
