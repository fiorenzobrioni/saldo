package com.callbackdev.saldo.feature.widget

import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.designsystem.theme.BrandDarkColorScheme
import com.callbackdev.saldo.core.designsystem.theme.BrandLightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented because resolving the theme reads the device configuration and,
 * for dynamic color, the platform palette.
 *
 * Two behaviours are worth pinning. The per-widget override: a widget lives on
 * the wallpaper, so it has to be able to disagree with the app's own theme
 * without ending up with ink the same shade as its background. And the shape of
 * the pair: a forced appearance must hand the launcher the same scheme on both
 * branches (or the launcher's night mode undoes the choice), while a
 * system-following widget must hand it two real ones (or it freezes in the
 * theme it was last composed under).
 */
@RunWith(AndroidJUnit4::class)
class QuickAddWidgetThemeTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val brandPreferences = ThemePreferences(mode = ThemeMode.LIGHT, useDynamicColor = false)

    @Test
    fun theWidgetSitsOnTheAppBackgroundByDefault() {
        val theme = resolveWidgetTheme(context, brandPreferences, QuickAddWidgetConfig())
        assertEquals(BrandLightColorScheme.background, theme.previewBackground)
    }

    @Test
    fun theWidgetCanBeDarkWhileTheAppIsLight() {
        val config = QuickAddWidgetConfig(appearance = WidgetAppearance.DARK)
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertEquals(BrandDarkColorScheme.background, theme.previewBackground)
    }

    @Test
    fun aForcedLightWidgetKeepsDarkInkEvenWhenTheAppIsDark() {
        val config = QuickAddWidgetConfig(appearance = WidgetAppearance.LIGHT)
        val theme = resolveWidgetTheme(
            context,
            ThemePreferences(mode = ThemeMode.DARK, useDynamicColor = false),
            config,
        )
        assertEquals(BrandLightColorScheme.background, theme.previewBackground)
        assertTrue(
            "Ink on a light widget must be dark",
            theme.previewScheme.onSurfaceVariant.luminance() < theme.previewBackground.luminance(),
        )
    }

    @Test
    fun aForcedDarkWidgetKeepsLightInk() {
        val config = QuickAddWidgetConfig(appearance = WidgetAppearance.DARK)
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertTrue(
            "Ink on a dark widget must be light",
            theme.previewScheme.onSurfaceVariant.luminance() > theme.previewBackground.luminance(),
        )
    }

    /**
     * A forced appearance hands the launcher the *same* scheme on both
     * branches: the launcher resolves day/night on its own, and two real
     * branches would let its night mode undo the user's explicit choice.
     */
    @Test
    fun aForcedAppearanceGivesTheLauncherNoRoomToFlip() {
        val config = QuickAddWidgetConfig(appearance = WidgetAppearance.DARK)
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertEquals(theme.lightScheme, theme.darkScheme)
    }

    /**
     * The inverse, and the fix for a real bug: a widget following the system
     * used to receive a single resolved scheme, so a theme toggle left it
     * frozen in the old palette until the next data refresh. Both branches
     * must be real for the launcher to flip them by itself.
     */
    @Test
    fun aSystemWidgetCarriesBothBranchesForTheLauncher() {
        val config = QuickAddWidgetConfig(appearance = WidgetAppearance.SYSTEM)
        val theme = resolveWidgetTheme(
            context,
            ThemePreferences(mode = ThemeMode.SYSTEM, useDynamicColor = false),
            config,
        )
        assertNotEquals(theme.lightScheme.background, theme.darkScheme.background)
    }

    @Test
    fun theOpacityCarriesIntoTheBackgroundAndOnlyTheBackground() {
        val config = QuickAddWidgetConfig(backgroundOpacity = 0f)
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertEquals(0f, theme.previewBackground.alpha, 0.001f)
        assertEquals(1f, theme.previewScheme.onSurfaceVariant.alpha, 0.001f)
    }

    @Test
    fun aHalfOpacityBackgroundIsHalfOpaque() {
        val config = QuickAddWidgetConfig(backgroundOpacity = 0.6f)
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertEquals(0.6f, theme.previewBackground.alpha, 0.001f)
    }

    /**
     * As the background fades, the tile washes densify: they become the only
     * local contrast the glyphs and labels get over an arbitrary wallpaper.
     */
    @Test
    fun theWashDensifiesAsTheBackgroundFades() {
        val opaque = resolveWidgetTheme(context, brandPreferences, QuickAddWidgetConfig())
        val transparent = resolveWidgetTheme(
            context,
            brandPreferences,
            QuickAddWidgetConfig(backgroundOpacity = 0f),
        )
        assertTrue(
            "Wash at opacity 0 (${transparent.washAlpha}) must be denser than at 1 (${opaque.washAlpha})",
            transparent.washAlpha > opaque.washAlpha,
        )
    }

    @Test
    fun theBackgroundIsOpaqueByDefaultSoTheWidgetNeverDisappears() {
        val theme = resolveWidgetTheme(context, brandPreferences, QuickAddWidgetConfig())
        assertEquals(1f, theme.previewBackground.alpha, 0.001f)
    }
}
