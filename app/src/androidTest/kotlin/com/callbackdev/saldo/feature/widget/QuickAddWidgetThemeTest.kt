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
 * The case worth protecting is the custom background: the scheme has to be
 * picked from the *chosen colour*, not from any theme setting, or a black widget
 * on a light app would draw dark labels on a black tile.
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
    fun pureBlackTakesTheDarkSchemeSoItsLabelsStayReadable() {
        val config = QuickAddWidgetConfig(
            appearance = WidgetAppearance.CUSTOM,
            backgroundColor = 0x000000,
        )
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertEquals(0f, theme.background.luminance(), 0.001f)
        assertTrue(
            "Ink on a black widget must be light",
            theme.scheme.onSurfaceVariant.luminance() > theme.background.luminance(),
        )
    }

    @Test
    fun pureWhiteTakesTheLightSchemeEvenWhenTheAppIsDark() {
        val config = QuickAddWidgetConfig(
            appearance = WidgetAppearance.CUSTOM,
            backgroundColor = 0xFFFFFF,
        )
        val theme = resolveWidgetTheme(
            context,
            ThemePreferences(mode = ThemeMode.DARK, useDynamicColor = false),
            config,
        )
        assertTrue(
            "Ink on a white widget must be dark",
            theme.scheme.onSurfaceVariant.luminance() < theme.background.luminance(),
        )
    }

    @Test
    fun opacityReachesTheBackgroundAndNothingElse() {
        val config = QuickAddWidgetConfig(backgroundOpacity = 40)
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertEquals(0.4f, theme.background.alpha, 0.01f)
        // The scheme is untouched: only the background is see-through, the
        // labels and the brand colour stay solid.
        assertEquals(1f, theme.scheme.onSurfaceVariant.alpha, 0.001f)
    }

    @Test
    fun aFullyTransparentBackgroundIsAllowedAndStillCarriesItsColour() {
        val config = QuickAddWidgetConfig(
            appearance = WidgetAppearance.CUSTOM,
            backgroundColor = 0xFFFFFF,
            backgroundOpacity = 0,
        )
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertEquals(0f, theme.background.alpha, 0.001f)
    }
}
