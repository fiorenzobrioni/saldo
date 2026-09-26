package com.callbackdev.saldo.feature.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Chiaro's ink rule, as Passo pins it (`WidgetInkTest` in both): which inks a
 * card writes with, from its ground, its solidity and, for a see-through card
 * whose ink nobody named, the wallpaper. Getting it wrong is dark text on a dark
 * card, which reads as a broken widget rather than as a wrong colour.
 */
class WidgetInkTest {

    @Test
    fun `a solid colour card writes in white, day and night`() {
        listOf(false, true).forEach { night ->
            assertEquals(WidgetInk.OVER_COLOR, widgetInk(WidgetBackground.COLOR, 100, night, false))
            assertEquals(WidgetInk.OVER_COLOR, widgetInk(WidgetBackground.COLOR, InkTrustFloorPct, night, true))
        }
    }

    @Test
    fun `a named light or dark card keeps its ink at any opacity`() {
        listOf(0, 30, 50, 100).forEach { pct ->
            assertEquals(WidgetInk.ON_LIGHT, widgetInk(WidgetBackground.LIGHT, pct, night = true, false))
            assertEquals(WidgetInk.ON_DARK, widgetInk(WidgetBackground.DARK, pct, night = false, true))
        }
    }

    @Test
    fun `the phone's solid card follows the branch being resolved`() {
        assertEquals(WidgetInk.ON_LIGHT, widgetInk(WidgetBackground.SYSTEM, 100, night = false, false))
        assertEquals(WidgetInk.ON_DARK, widgetInk(WidgetBackground.SYSTEM, 100, night = true, false))
    }

    /**
     * Below the floor the ground is the wallpaper, and only its own hint may ask
     * for dark ink: a silent wallpaper gets light ink, the safe answer on the
     * dark-to-mid photos most home screens carry.
     */
    @Test
    fun `a see-through card asks the wallpaper, and trusts only a yes`() {
        listOf(WidgetBackground.SYSTEM, WidgetBackground.COLOR).forEach { background ->
            listOf(false, true).forEach { night ->
                assertEquals(WidgetInk.ON_LIGHT, widgetInk(background, 45, night, wallpaperCarriesDarkInk = true))
                assertEquals(WidgetInk.ON_DARK, widgetInk(background, 45, night, wallpaperCarriesDarkInk = false))
            }
        }
    }

    @Test
    fun `the wallpaper is read only when the rule needs it`() {
        assertTrue(inkAsksWallpaper(WidgetBackground.COLOR, 45))
        assertTrue(inkAsksWallpaper(WidgetBackground.SYSTEM, 0))
        assertFalse(inkAsksWallpaper(WidgetBackground.COLOR, InkTrustFloorPct))
        assertFalse(inkAsksWallpaper(WidgetBackground.LIGHT, 0))
        assertFalse(inkAsksWallpaper(WidgetBackground.DARK, 10))
    }

    @Test
    fun `every ground but light counts as dark for the money colours`() {
        assertTrue(WidgetInk.OVER_COLOR.darkGround)
        assertTrue(WidgetInk.ON_DARK.darkGround)
        assertFalse(WidgetInk.ON_LIGHT.darkGround)
    }
}
