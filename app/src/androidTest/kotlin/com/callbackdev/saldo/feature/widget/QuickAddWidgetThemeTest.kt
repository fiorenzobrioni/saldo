package com.callbackdev.saldo.feature.widget

import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.designsystem.theme.BrandDarkColorScheme
import com.callbackdev.saldo.core.designsystem.theme.BrandLightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented because resolving the theme reads the device configuration and,
 * for dynamic color, the platform palette.
 *
 * The case worth protecting is the per-widget override: a widget lives on the
 * wallpaper, so it has to be able to disagree with the app's own theme without
 * ending up with ink the same shade as its background.
 */
@RunWith(AndroidJUnit4::class)
class QuickAddWidgetThemeTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val brandPreferences = ThemePreferences(mode = ThemeMode.LIGHT, useDynamicColor = false)

    @Test
    fun theWidgetSitsOnTheAppBackgroundByDefault() {
        val theme = resolveWidgetTheme(context, brandPreferences, QuickAddWidgetConfig())
        assertEquals(BrandLightColorScheme.background, theme.background)
    }

    @Test
    fun theWidgetCanBeDarkWhileTheAppIsLight() {
        val config = QuickAddWidgetConfig(appearance = WidgetAppearance.DARK)
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertEquals(BrandDarkColorScheme.background, theme.background)
    }

    @Test
    fun aForcedLightWidgetKeepsDarkInkEvenWhenTheAppIsDark() {
        val config = QuickAddWidgetConfig(appearance = WidgetAppearance.LIGHT)
        val theme = resolveWidgetTheme(
            context,
            ThemePreferences(mode = ThemeMode.DARK, useDynamicColor = false),
            config,
        )
        assertEquals(BrandLightColorScheme.background, theme.background)
        assertTrue(
            "Ink on a light widget must be dark",
            theme.scheme.onSurfaceVariant.luminance() < theme.background.luminance(),
        )
    }

    @Test
    fun aForcedDarkWidgetKeepsLightInk() {
        val config = QuickAddWidgetConfig(appearance = WidgetAppearance.DARK)
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertTrue(
            "Ink on a dark widget must be light",
            theme.scheme.onSurfaceVariant.luminance() > theme.background.luminance(),
        )
    }

    /**
     * Transparent has no background to take a side from, so the ink follows the
     * app the way SYSTEM does. What it sits on is the wallpaper, and nothing
     * here can know how light that is.
     */
    @Test
    fun theTransparentAppearanceKeepsItsInkAndDropsOnlyTheBackground() {
        val config = QuickAddWidgetConfig(appearance = WidgetAppearance.TRANSPARENT)
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertEquals(0f, theme.background.alpha, 0.001f)
        assertEquals(1f, theme.scheme.onSurfaceVariant.alpha, 0.001f)
        assertEquals(BrandLightColorScheme.onSurfaceVariant, theme.scheme.onSurfaceVariant)
    }

    @Test
    fun theBackgroundIsOpaqueSoTheWidgetNeverDisappears() {
        val theme = resolveWidgetTheme(context, brandPreferences, QuickAddWidgetConfig())
        assertEquals(1f, theme.background.alpha, 0.001f)
    }
}
