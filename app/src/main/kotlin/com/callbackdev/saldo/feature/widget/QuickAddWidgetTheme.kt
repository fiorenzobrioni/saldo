package com.callbackdev.saldo.feature.widget

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.unit.ColorProvider
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.designsystem.theme.BrandDarkColorScheme
import com.callbackdev.saldo.core.designsystem.theme.BrandLightColorScheme
import com.callbackdev.saldo.core.designsystem.theme.MoneyColors
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import androidx.glance.material3.ColorProviders as GlanceColorProviders

/**
 * The palette a placed widget draws in, carried as a light/dark *pair* rather
 * than a single resolved scheme.
 *
 * The pair is the fix for a real bug: a widget renders in the launcher's
 * process, and the launcher re-resolves day/night resources on its own when the
 * system theme flips - but only if it was handed both branches. The old single
 * resolved scheme meant a widget froze in whichever theme it was last composed
 * in until the next data refresh happened along. When an appearance is forced
 * (light, dark, or the wallpaper's pick for a mostly transparent widget), both
 * sides of the pair are simply the same scheme: the launcher can flip all it
 * wants, the choice made here wins.
 */
data class QuickAddWidgetTheme(
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme,
    val lightMoney: MoneyColors,
    val darkMoney: MoneyColors,
    /** The background alpha the user chose in the widget settings, 0..1. */
    val backgroundOpacity: Float,
    /**
     * The side the in-app settings preview shows. A regular Compose screen has
     * no launcher to resolve day/night for it, so the preview picks one side
     * and this is which.
     */
    val previewDark: Boolean,
) {

    /** What Glance hands the launcher: both branches, resolved there. */
    val providers: ColorProviders get() = GlanceColorProviders(lightScheme, darkScheme)

    val background: ColorProvider
        get() = DayNightColorProvider(
            day = lightScheme.background.copy(alpha = backgroundOpacity),
            night = darkScheme.background.copy(alpha = backgroundOpacity),
        )

    /**
     * The wash behind glyphs, denser as the background fades. At full opacity
     * it is the app's own 16% category wash; on a mostly transparent widget
     * that wash all but disappears into the wallpaper, and the tiles are the
     * only local contrast the glyphs and labels get.
     */
    val washAlpha: Float get() = BaseWashAlpha + (1f - backgroundOpacity) * WashBoost

    /** A full-strength ink that still flips with the launcher's night mode. */
    fun ink(day: Color, night: Color): ColorProvider = DayNightColorProvider(day = day, night = night)

    fun wash(day: Color, night: Color): ColorProvider = DayNightColorProvider(
        day = day.copy(alpha = washAlpha),
        night = night.copy(alpha = washAlpha),
    )

    /**
     * The accents of the single-row layout's two buttons.
     *
     * A deliberate, narrow exception to [MoneyColors], which keeps `expense`
     * neutral on purpose: colouring every expense in a ledger would shout, and
     * there the minus sign and the icon carry the distinction. Two action
     * buttons alone on a widget are not a ledger - there is no other context to
     * read them by - so the colour does the fast work and the icons still do the
     * accessible work. `income` is the app's own green; the red is the scheme's
     * only one, worn as a wash so it reads as soft rather than as an alarm.
     */
    val expenseAccent: ColorProvider get() = ink(lightScheme.error, darkScheme.error)
    val expenseWash: ColorProvider get() = wash(lightScheme.error, darkScheme.error)
    val incomeAccent: ColorProvider get() = ink(lightMoney.income, darkMoney.income)
    val incomeWash: ColorProvider get() = wash(lightMoney.income, darkMoney.income)

    /** The quiet tonal fill of the app-shortcut button: present, not competing. */
    val neutralWash: ColorProvider get() = wash(lightScheme.onSurfaceVariant, darkScheme.onSurfaceVariant)

    /** The side the settings preview renders, since it cannot do day/night. */
    val previewScheme: ColorScheme get() = if (previewDark) darkScheme else lightScheme
    val previewBackground: Color get() = previewScheme.background.copy(alpha = backgroundOpacity)
}

/**
 * Resolves the widget's palette from the app's theme settings and the widget's
 * own appearance override.
 *
 * A mostly transparent widget sits on the wallpaper, not on any surface this
 * code controls, so below [InkFromWallpaperBelow] of opacity the ink side comes
 * from the wallpaper's own [WallpaperColors.HINT_SUPPORTS_DARK_TEXT] - the same
 * hint the system clock uses to stay readable. The hint is global rather than
 * local to where the widget happens to sit, which is why the tiles also wear a
 * denser wash there ([QuickAddWidgetTheme.washAlpha]): the hint picks the side,
 * the wash guarantees the glyphs a floor of local contrast.
 */
fun resolveWidgetTheme(
    context: Context,
    preferences: ThemePreferences,
    config: QuickAddWidgetConfig,
): QuickAddWidgetTheme {
    val forcedDark = forcedDark(context, preferences, config)
    val lightBase = if (preferences.useDynamicColor) dynamicLightColorScheme(context) else BrandLightColorScheme
    val darkBase = if (preferences.useDynamicColor) dynamicDarkColorScheme(context) else BrandDarkColorScheme
    val light = if (forcedDark == true) darkBase else lightBase
    val dark = if (forcedDark == false) lightBase else darkBase
    return QuickAddWidgetTheme(
        lightScheme = light,
        darkScheme = dark,
        lightMoney = moneyColors(light, darkTheme = forcedDark ?: false),
        darkMoney = moneyColors(dark, darkTheme = forcedDark ?: true),
        backgroundOpacity = config.backgroundOpacity,
        previewDark = forcedDark ?: context.isSystemInDarkMode(),
    )
}

/**
 * True or false when the widget must not follow the launcher's night mode,
 * null when both branches are real and the launcher decides.
 */
private fun forcedDark(
    context: Context,
    preferences: ThemePreferences,
    config: QuickAddWidgetConfig,
): Boolean? = when (config.appearance) {
    WidgetAppearance.LIGHT -> false
    WidgetAppearance.DARK -> true
    WidgetAppearance.SYSTEM, WidgetAppearance.TRANSPARENT ->
        if (config.backgroundOpacity < InkFromWallpaperBelow) {
            // A light wallpaper asks for dark text, which is the light palette.
            wallpaperSupportsDarkText(context)?.not() ?: preferences.mode.forcedDark()
        } else {
            preferences.mode.forcedDark()
        }
}

private fun ThemeMode.forcedDark(): Boolean? = when (this) {
    ThemeMode.SYSTEM -> null
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Whether the home wallpaper is light enough for dark text, or null when the
 * wallpaper offers no hint (some live wallpapers) and the theme should decide.
 * Needs no permission: the hint is public precisely so surfaces drawn over the
 * wallpaper can stay readable.
 */
private fun wallpaperSupportsDarkText(context: Context): Boolean? = runCatching {
    WallpaperManager.getInstance(context)
        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        ?.let { colors -> colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0 }
}.getOrNull()

private fun Context.isSystemInDarkMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

/** Below this opacity the widget reads against the wallpaper, not its own background. */
private const val InkFromWallpaperBelow = 0.5f

/** Matches `CategoryCell`: the app's unselected category wash. */
private const val BaseWashAlpha = 0.16f

/** How much the wash densifies on the way to a fully transparent background. */
private const val WashBoost = 0.22f
