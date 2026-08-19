package com.callbackdev.saldo.feature.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.designsystem.theme.BrandDarkColorScheme
import com.callbackdev.saldo.core.designsystem.theme.BrandLightColorScheme
import com.callbackdev.saldo.core.designsystem.theme.MoneyColors
import com.callbackdev.saldo.core.designsystem.theme.moneyColors

/**
 * The palette a placed widget draws in, carried as a light/dark *pair* rather
 * than a single resolved scheme.
 *
 * The pair is the fix for a real bug: a widget renders in the launcher's
 * process, and the launcher re-resolves day/night on its own when the system
 * theme flips - but only if it was handed both branches. A single resolved
 * scheme meant a widget froze in whichever theme it was last rendered in until
 * the next data refresh happened along. When an appearance is forced (light or
 * dark), both sides of the pair are simply the same scheme: the launcher can
 * flip all it wants, the choice made here wins.
 *
 * The background is always solid: the widget never sits directly on the
 * wallpaper anymore, so no code here needs to know what the wallpaper looks
 * like. The container colour is the Material 3 `widgetBackground` token (see
 * [widgetBackgroundColorOf]), the role the platform reserves for widget
 * containers.
 */
data class QuickAddWidgetTheme(
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme,
    val lightMoney: MoneyColors,
    val darkMoney: MoneyColors,
    /**
     * The side the in-app settings preview shows. A regular Compose screen has
     * no launcher to resolve day/night for it, so the preview picks one side
     * and this is which.
     */
    val previewDark: Boolean,
) {

    /**
     * What the renderer hands the launcher: every colour as a day/night pair of
     * ARGB ints. Computed once per theme instance - the theme is shared across
     * every size of a render (see `QuickAddWidgetDataLoader.loadShared`), so a
     * `get()` here would rebuild the same palette a dozen times per refresh.
     */
    internal val palette: WidgetPalette by lazy(LazyThreadSafetyMode.NONE) {
        widgetPalette(
            light = lightScheme,
            dark = darkScheme,
            lightIncome = lightMoney.income,
            darkIncome = darkMoney.income,
        )
    }

    /** The side the settings preview renders, since it cannot do day/night. */
    val previewScheme: ColorScheme get() = if (previewDark) darkScheme else lightScheme

    /** The money colours that go with [previewScheme]. */
    val previewMoney: MoneyColors get() = if (previewDark) darkMoney else lightMoney

    /**
     * The widget container colour the settings preview shows: the same
     * `widgetBackground` token the placed widget wears.
     */
    val previewBackground: Color get() = widgetBackgroundColorOf(previewScheme)

    /** The in-app preview's own copy of the tonal wash behind a glyph. */
    fun previewWash(accent: Color): Color =
        Color(washOver(previewBackground.toArgb(), accent.toArgb()))
}

/**
 * The Material 3 `widgetBackground` token for [scheme]: the scheme's
 * secondaryContainer nudged in HCT tone (+5 above mid tone, -10 below), so the
 * widget container reads a step apart from in-app surfaces while staying in the
 * dynamic palette.
 *
 * The derivation is the one glance-material3 applied in `ColorProviders(light,
 * dark)`. It is spelled out here rather than imported because the widget no
 * longer goes through Glance at all, and because the in-app settings preview
 * has to show the same container colour the launcher draws.
 */
internal fun widgetBackgroundColorOf(scheme: ColorScheme): Color {
    val hct = FloatArray(HctComponents)
    ColorUtils.colorToM3HCT(scheme.secondaryContainer.toArgb(), hct)
    val adjustment = if (hct[2] > MidTone) WidgetBackgroundToneLight else WidgetBackgroundToneDark
    val tone = (hct[2] + adjustment).coerceIn(0f, MaxTone)
    return Color(ColorUtils.M3HCTToColor(hct[0], hct[1], tone))
}

private const val HctComponents = 3
private const val MidTone = 50f
private const val MaxTone = 100f

/** The WIDGET_BG_TONE_ADJUSTMENT_LIGHT/_DARK values of the Material 3 widget spec. */
private const val WidgetBackgroundToneLight = 5f
private const val WidgetBackgroundToneDark = -10f

/**
 * Resolves the widget's palette from the app's theme settings and the widget's
 * own appearance override. Pure function of its inputs: no wallpaper reads, no
 * binder calls, so the result can be cached per render (see
 * `QuickAddWidgetDataLoader.loadShared`) and re-resolved only when the theme
 * settings or the widget configuration actually change.
 */
fun resolveWidgetTheme(
    context: Context,
    preferences: ThemePreferences,
    config: QuickAddWidgetConfig,
): QuickAddWidgetTheme {
    val forcedDark = forcedDark(preferences, config)
    val lightBase = if (preferences.useDynamicColor) dynamicLightColorScheme(context) else BrandLightColorScheme
    val darkBase = if (preferences.useDynamicColor) dynamicDarkColorScheme(context) else BrandDarkColorScheme
    val light = if (forcedDark == true) darkBase else lightBase
    val dark = if (forcedDark == false) lightBase else darkBase
    return QuickAddWidgetTheme(
        lightScheme = light,
        darkScheme = dark,
        lightMoney = moneyColors(light, darkTheme = forcedDark ?: false),
        darkMoney = moneyColors(dark, darkTheme = forcedDark ?: true),
        previewDark = forcedDark ?: context.isSystemInDarkMode(),
    )
}

/**
 * True or false when the widget must not follow the launcher's night mode,
 * null when both branches are real and the launcher decides.
 */
private fun forcedDark(
    preferences: ThemePreferences,
    config: QuickAddWidgetConfig,
): Boolean? = when (config.appearance) {
    WidgetAppearance.LIGHT -> false
    WidgetAppearance.DARK -> true
    WidgetAppearance.SYSTEM, WidgetAppearance.TRANSPARENT -> preferences.mode.forcedDark()
}

private fun ThemeMode.forcedDark(): Boolean? = when (this) {
    ThemeMode.SYSTEM -> null
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private fun Context.isSystemInDarkMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
