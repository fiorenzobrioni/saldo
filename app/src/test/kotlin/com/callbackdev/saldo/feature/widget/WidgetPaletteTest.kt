package com.callbackdev.saldo.feature.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.callbackdev.saldo.core.designsystem.theme.BrandDarkColorScheme
import com.callbackdev.saldo.core.designsystem.theme.BrandLightColorScheme
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.widgetCardContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The palette is where the widget's colours stop being Compose and become the
 * day/night int pairs `RemoteViews.setColorInt` takes. What matters is that the
 * pair is a pair only where it should be: the phone's card has two real
 * branches, every card the user named arrives as the same value twice, or the
 * launcher's night mode would undo the choice.
 */
class WidgetPaletteTest {

    private val dress = WidgetDress(
        lightScheme = BrandLightColorScheme,
        darkScheme = BrandDarkColorScheme,
        lightMoney = moneyColors(BrandLightColorScheme, darkTheme = false),
        darkMoney = moneyColors(BrandDarkColorScheme, darkTheme = true),
    )

    private fun palette(background: WidgetBackground, pct: Int = 100, bright: Boolean = false) =
        widgetPalette(dress, background, WidgetCardColor.BLUE, pct, wallpaperCarriesDarkInk = bright)

    @Test
    fun `the phone's card hands the launcher two real branches`() {
        val system = palette(WidgetBackground.SYSTEM)
        assertEquals(BrandLightColorScheme.surface.toArgb(), system.background.light)
        assertEquals(BrandDarkColorScheme.surface.toArgb(), system.background.dark)
        assertNotEquals(system.onSurface.light, system.onSurface.dark)
    }

    @Test
    fun `a named card is the same on both branches`() {
        listOf(WidgetBackground.LIGHT, WidgetBackground.DARK, WidgetBackground.COLOR).forEach { background ->
            val named = palette(background)
            assertEquals(named.background.light, named.background.dark, "$background ground")
            assertEquals(named.onSurface.light, named.onSurface.dark, "$background ink")
            assertEquals(named.expenseInk.light, named.expenseInk.dark, "$background expense")
        }
    }

    @Test
    fun `a colour card writes in white, with the card's own blue on the white pill`() {
        val blue = palette(WidgetBackground.COLOR)
        val card = widgetCardContainer(WidgetCardColor.BLUE).toArgb()
        assertEquals(card, blue.background.light)
        assertEquals(Color.White.toArgb(), blue.onSurface.light)
        assertEquals(Color.White.toArgb(), blue.pillFill.light)
        assertEquals(card, blue.pillInk.light)
        // The dark theme's money colours: the set made for a dark ground.
        assertEquals(BrandDarkColorScheme.error.toArgb(), blue.expenseInk.light)
    }

    @Test
    fun `the opacity becomes the card's image alpha, never the ink's`() {
        assertEquals(255, palette(WidgetBackground.COLOR, 100).backgroundAlpha)
        assertEquals(0, palette(WidgetBackground.COLOR, 0).backgroundAlpha)
        val half = palette(WidgetBackground.COLOR, 50)
        assertEquals(128, half.backgroundAlpha)
        assertEquals(0xFF, half.onSurface.light ushr 24)
    }

    @Test
    fun `a see-through colour card over a bright wallpaper writes in dark ink`() {
        val clear = palette(WidgetBackground.COLOR, pct = 20, bright = true)
        assertEquals(BrandLightColorScheme.onSurface.toArgb(), clear.onSurface.light)
        assertEquals(BrandLightColorScheme.onSurface.toArgb(), clear.onSurface.dark)
    }

    /**
     * The seed's mid-tone categories sink into a dark card; lifted towards white
     * they keep their hue and clear 3:1, the floor for a graphic, on all six.
     */
    @Test
    fun `category glyphs are lifted on a colour card and readable on all six`() {
        val seed = listOf(0x5C6BC0, 0x7E57C2, 0x66BB6A, 0xEF5350, 0x42A5F5, 0x26A69A, 0xEC407A, 0x8D6E63)
        WidgetCardColor.entries.forEach { color ->
            val palette = widgetPalette(dress, WidgetBackground.COLOR, color, 100, false)
            val ground = widgetCardContainer(color).toArgb()
            seed.forEach { rgb ->
                val ink = palette.categoryInk(Color(0xFF000000L or rgb.toLong())).light
                assertTrue(contrast(ink, ground) >= 3.0, "#%06X on $color: %.2f".format(rgb, contrast(ink, ground)))
            }
        }
        val light = palette(WidgetBackground.LIGHT)
        val indigo = Color(0xFF5C6BC0)
        assertEquals(indigo.toArgb(), light.categoryInk(indigo).light)
    }

    @Test
    fun `mix is opaque and exact at its ends`() {
        val green = 0xFF66BB6A.toInt()
        assertEquals(green, mix(green, -1, 0f))
        assertEquals(-1, mix(green, -1, 1f))
        assertEquals(0xFF, mix(0x8066BB6A.toInt(), -1, 0.5f) ushr 24)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb ushr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
