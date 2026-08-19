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

    /**
     * The settings preview must show the same container color the launcher
     * draws: the Material 3 widgetBackground token, not the app's own
     * background surface.
     */
    @Test
    fun theWidgetSitsOnTheWidgetBackgroundTokenByDefault() {
        val theme = resolveWidgetTheme(context, brandPreferences, QuickAddWidgetConfig())
        assertEquals(widgetBackgroundColorOf(BrandLightColorScheme), theme.previewBackground)
        assertNotEquals(BrandLightColorScheme.background, theme.previewBackground)
    }

    @Test
    fun theWidgetCanBeDarkWhileTheAppIsLight() {
        val config = QuickAddWidgetConfig(appearance = WidgetAppearance.DARK)
        val theme = resolveWidgetTheme(context, brandPreferences, config)
        assertEquals(widgetBackgroundColorOf(BrandDarkColorScheme), theme.previewBackground)
    }

    /**
     * The token's whole point: a step apart from secondaryContainer, lighter
     * on the light side and darker on the dark side, exactly as
     * the Material 3 widget spec derives it.
     */
    @Test
    fun theWidgetBackgroundTokenStepsAwayFromSecondaryContainer() {
        assertTrue(
            "Light widget background must be lighter than secondaryContainer",
            widgetBackgroundColorOf(BrandLightColorScheme).luminance() >
                BrandLightColorScheme.secondaryContainer.luminance(),
        )
        assertTrue(
            "Dark widget background must be darker than secondaryContainer",
            widgetBackgroundColorOf(BrandDarkColorScheme).luminance() <
                BrandDarkColorScheme.secondaryContainer.luminance(),
        )
    }

    @Test
    fun aForcedLightWidgetKeepsDarkInkEvenWhenTheAppIsDark() {
        val config = QuickAddWidgetConfig(appearance = WidgetAppearance.LIGHT)
        val theme = resolveWidgetTheme(
            context,
            ThemePreferences(mode = ThemeMode.DARK, useDynamicColor = false),
            config,
        )
        assertEquals(widgetBackgroundColorOf(BrandLightColorScheme), theme.previewBackground)
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

    /**
     * The widget always sits on a solid app surface: the opacity slider and
     * the wallpaper-hint ink are gone, so a translucent background would mean
     * a regression, not a setting.
     */
    @Test
    fun theBackgroundIsAlwaysOpaqueSoTheWidgetNeverDisappears() {
        val theme = resolveWidgetTheme(context, brandPreferences, QuickAddWidgetConfig())
        assertEquals(1f, theme.previewBackground.alpha, 0.001f)
        assertEquals(1f, theme.previewScheme.onSurfaceVariant.alpha, 0.001f)
    }
}
