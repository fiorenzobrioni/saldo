package com.callbackdev.saldo.feature.widget

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.designsystem.theme.BrandDarkColorScheme
import com.callbackdev.saldo.core.designsystem.theme.BrandLightColorScheme
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor
import com.callbackdev.saldo.core.designsystem.theme.moneyColors

/**
 * What a placed widget is dressed in: the app's two schemes (brand or the
 * wallpaper's, as the app itself), the card the user chose for this widget, and
 * - only for a see-through card whose ink the user did not name - what the
 * wallpaper says about the ground under it.
 *
 * The card, its colours, its opacity and the ink rule are Chiaro's, carried over
 * as Passo carries them, so the family's widgets on one home screen read as one
 * set of furniture (ADR 52).
 */
data class QuickAddWidgetTheme(
    val dress: WidgetDress,
    val background: WidgetBackground,
    val cardColor: WidgetCardColor,
    val opacityPct: Int,
    val wallpaperCarriesDarkInk: Boolean = false,
) {

    /**
     * What the renderer hands the launcher: every colour as a day/night pair of
     * ARGB ints. Computed once per theme instance - the theme is shared across
     * every size of a render (see `QuickAddWidgetDataLoader.loadShared`), so a
     * `get()` here would rebuild the same palette a dozen times per refresh.
     */
    internal val palette: WidgetPalette by lazy(LazyThreadSafetyMode.NONE) {
        widgetPalette(dress, background, cardColor, opacityPct, wallpaperCarriesDarkInk)
    }
}

/** The app's two schemes and their money colours, as the app itself resolves them. */
fun widgetDressFor(context: Context, preferences: ThemePreferences): WidgetDress {
    val light = if (preferences.useDynamicColor) dynamicLightColorScheme(context) else BrandLightColorScheme
    val dark = if (preferences.useDynamicColor) dynamicDarkColorScheme(context) else BrandDarkColorScheme
    return WidgetDress(
        lightScheme = light,
        darkScheme = dark,
        lightMoney = moneyColors(light, darkTheme = false),
        darkMoney = moneyColors(dark, darkTheme = true),
    )
}

/**
 * Resolves the widget's theme from the app's colour settings and the widget's
 * own look.
 *
 * The app's light/dark setting is deliberately not read: a widget lives on the
 * wallpaper, not inside the app, and "Same as the phone" means the phone. The
 * wallpaper is read only when [inkAsksWallpaper] says the ink depends on it,
 * and only here, at render time: no listener is kept for it (ADR 37), so after a
 * wallpaper change a see-through card takes its new ink at its next redraw.
 */
fun resolveWidgetTheme(
    context: Context,
    preferences: ThemePreferences,
    config: QuickAddWidgetConfig,
): QuickAddWidgetTheme = QuickAddWidgetTheme(
    dress = widgetDressFor(context, preferences),
    background = config.background,
    cardColor = config.cardColor,
    opacityPct = config.opacityPct,
    wallpaperCarriesDarkInk = inkAsksWallpaper(config.background, config.opacityPct) &&
        wallpaperWantsDarkInk(context),
)

/**
 * Whether the wallpaper behind a see-through card can carry dark text, by the
 * system's own account ([WallpaperColors.HINT_SUPPORTS_DARK_TEXT]). Read as the
 * affirmative signal it is: dark ink only when the system says the ground is
 * bright, light ink whenever it says nothing (Chiaro's and Passo's rule).
 */
private fun wallpaperWantsDarkInk(context: Context): Boolean {
    val manager = runCatching { WallpaperManager.getInstance(context) }.getOrNull() ?: return false
    val colors = runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
        ?: return false
    return colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
}
