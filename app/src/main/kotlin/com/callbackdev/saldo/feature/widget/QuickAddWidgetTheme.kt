package com.callbackdev.saldo.feature.widget

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
 * (light or dark), both sides of the pair are simply the same scheme: the
 * launcher can flip all it wants, the choice made here wins.
 *
 * The background is always the solid app surface: the widget never sits
 * directly on the wallpaper anymore, so no code here needs to know what the
 * wallpaper looks like.
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
     * What Glance hands the launcher: both branches, resolved there. Computed
     * once per theme instance - the theme is shared across every size bucket
     * of a render (see `QuickAddWidgetDataLoader.loadShared`), so a `get()`
     * here would rebuild the same providers over a dozen times per refresh.
     */
    val providers: ColorProviders by lazy(LazyThreadSafetyMode.NONE) {
        GlanceColorProviders(lightScheme, darkScheme)
    }

    val background: ColorProvider
        get() = DayNightColorProvider(
            day = lightScheme.background,
            night = darkScheme.background,
        )

    /** A full-strength ink that still flips with the launcher's night mode. */
    fun ink(day: Color, night: Color): ColorProvider = DayNightColorProvider(day = day, night = night)

    /** The wash behind glyphs: the app's own category wash, on both branches. */
    fun wash(day: Color, night: Color): ColorProvider = DayNightColorProvider(
        day = day.copy(alpha = WashAlpha),
        night = night.copy(alpha = WashAlpha),
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
    val previewBackground: Color get() = previewScheme.background
}

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

/** Matches `CategoryCell`: the app's unselected category wash. */
internal const val WashAlpha = 0.16f
