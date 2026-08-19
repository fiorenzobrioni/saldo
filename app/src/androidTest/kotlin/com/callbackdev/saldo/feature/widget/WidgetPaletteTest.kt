package com.callbackdev.saldo.feature.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.designsystem.theme.BrandDarkColorScheme
import com.callbackdev.saldo.core.designsystem.theme.BrandLightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The palette is where the widget's colours stop being Compose and become the two
 * plain ints `RemoteViews.setColorInt` takes.
 *
 * Instrumented for the same reason [QuickAddWidgetThemeTest] is: the container
 * colour is derived in HCT through `ColorUtils`, which is a framework stub off a
 * device. The blend itself is pure and asserted on the JVM in `WashOverTest`.
 *
 * What matters here is that the pair stays a pair. A single resolved value would
 * freeze a placed widget in whichever theme the app last rendered it under,
 * because the launcher can only re-resolve a branch it was actually handed - and
 * conversely a forced appearance has to arrive as the same value twice, or the
 * launcher's night mode would undo the user's choice.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPaletteTest {

    private val palette = widgetPalette(
        light = BrandLightColorScheme,
        dark = BrandDarkColorScheme,
        lightIncome = Color(0xFF3E6837),
        darkIncome = Color(0xFFA4D397),
    )

    @Test
    fun themeTokensHandTheLauncherTwoRealBranches() {
        assertNotEquals(palette.background.light, palette.background.dark)
        assertNotEquals(palette.onSurface.light, palette.onSurface.dark)
        assertNotEquals(palette.onSurfaceVariant.light, palette.onSurfaceVariant.dark)
        assertNotEquals(palette.pillFill.light, palette.pillFill.dark)
    }

    /**
     * A forced appearance is expressed by handing both branches the same scheme:
     * the launcher may flip all it likes, the user's choice wins.
     */
    @Test
    fun aForcedAppearanceCollapsesThePairToOneValue() {
        val forced = widgetPalette(
            light = BrandDarkColorScheme,
            dark = BrandDarkColorScheme,
            lightIncome = Color(0xFFA4D397),
            darkIncome = Color(0xFFA4D397),
        )
        assertEquals(forced.background.light, forced.background.dark)
        assertEquals(forced.onSurface.light, forced.onSurface.dark)
        assertEquals(forced.incomeWash.light, forced.incomeWash.dark)
    }

    /**
     * A category colour is the one its user picked, so unlike a theme token it is
     * the same on both branches; only the wash behind it moves, because the
     * surface it is composited over does.
     */
    @Test
    fun aCategoryKeepsItsOwnInkButNotItsOwnWash() {
        val accent = Color(0xFF66BB6A)
        val ink = palette.categoryInk(accent)
        val wash = palette.categoryWash(accent)

        assertEquals(ink.light, ink.dark)
        assertEquals(accent.toArgb(), ink.light)
        assertNotEquals(wash.light, wash.dark)
    }

    /** Every wash reaches the launcher opaque; see WashOverTest for why. */
    @Test
    fun everyWashIsOpaque() {
        listOf(
            palette.expenseWash,
            palette.incomeWash,
            palette.neutralWash,
            palette.moreWash,
            palette.categoryWash(Color(0xFF66BB6A)),
        ).forEach { wash ->
            assertEquals(1f, Color(wash.light).alpha, 0.001f)
            assertEquals(1f, Color(wash.dark).alpha, 0.001f)
        }
    }

    /**
     * The container is the Material 3 widgetBackground token, a deliberate step
     * off the app's own surfaces, not colorScheme.background.
     */
    @Test
    fun theContainerIsTheWidgetBackgroundToken() {
        assertEquals(
            widgetBackgroundColorOf(BrandLightColorScheme).toArgb(),
            palette.background.light,
        )
        assertNotEquals(BrandLightColorScheme.background.toArgb(), palette.background.light)
    }

    /**
     * Expense and income must not read as the same control. They are told apart by
     * icon as well as colour on the widget, but the colours still have to differ,
     * or the pair loses the fast distinction the single-row layout relies on.
     */
    @Test
    fun expenseAndIncomeAreDistinctOnBothBranches() {
        assertNotEquals(palette.expenseInk.light, palette.incomeInk.light)
        assertNotEquals(palette.expenseInk.dark, palette.incomeInk.dark)
        assertNotEquals(palette.expenseWash.light, palette.incomeWash.light)
        assertNotEquals(palette.expenseWash.dark, palette.incomeWash.dark)
    }
}
