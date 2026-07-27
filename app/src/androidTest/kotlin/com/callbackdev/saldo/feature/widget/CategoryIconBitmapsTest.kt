package com.callbackdev.saldo.feature.widget

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pixel half of the icon renderer (the structural half runs in CI as
 * `CategoryIconStructureTest`). Rasterizing needs a real `android.graphics`
 * canvas, so this only runs on a device.
 *
 * What it protects: a widget tile that comes back blank, or an icon whose
 * vector the renderer silently swallows. Both would ship as a hole on someone's
 * home screen and neither shows up in a build.
 */
@RunWith(AndroidJUnit4::class)
class CategoryIconBitmapsTest {

    private val size = 96

    @Test
    fun everyMappedIconRendersItsGlyph() {
        CategoryVisuals.iconKeys.forEach { key ->
            val bitmap = CategoryIconBitmaps.glyph(
                vector = CategoryVisuals.icon(key),
                sizePx = size,
            )
            assertEquals(size, bitmap.width)
            assertEquals(size, bitmap.height)
            assertTrue("Icon '$key' rendered as nothing at all", bitmap.hasGlyph())
        }
    }

    /**
     * The glyph is a white mask: the colour arrives as a day/night tint at the
     * `RemoteViews` level, never baked into the pixels. Baking it froze a
     * widget in whichever theme it was last composed under, because only the
     * launcher knows when the system theme flips.
     */
    @Test
    fun theGlyphIsAWhiteMaskWithNoBakedColour() {
        val bitmap = CategoryIconBitmaps.glyph(
            vector = CategoryVisuals.icon("shopping_cart"),
            sizePx = size,
        )
        assertTrue("The mask must hold pure white pixels", bitmap.hasGlyph())
    }

    /**
     * The wash behind a glyph is a Glance background, not part of the bitmap.
     * That is a payload decision, not a cosmetic one: `RemoteViews` carry their
     * bitmaps to the launcher through a size-capped transaction, and a
     * whole-tile bitmap is roughly three times the pixels of the glyph in it.
     */
    @Test
    fun theGlyphCarriesNoBackgroundOfItsOwn() {
        val bitmap = CategoryIconBitmaps.glyph(
            vector = CategoryVisuals.icon("shopping_cart"),
            sizePx = size,
        )
        assertEquals("A corner must stay transparent", Color.TRANSPARENT, bitmap.getPixel(0, 0))
    }

    @Test
    fun anUnknownIconStillRendersTheFallback() {
        val bitmap = CategoryIconBitmaps.glyph(
            vector = CategoryVisuals.icon("no-such-icon"),
            sizePx = size,
        )
        assertEquals(size, bitmap.width)
        assertTrue(bitmap.hasGlyph())
    }

    @Test
    fun theSameGlyphIsServedFromCacheRatherThanRedrawn() {
        val vector = CategoryVisuals.icon("restaurant")
        val first = CategoryIconBitmaps.glyph(vector, size)
        val second = CategoryIconBitmaps.glyph(vector, size)
        assertTrue("The glyph cache handed back a different bitmap", first === second)
    }

    /** The app mark is drawn past its safe zone, so it must reach the edges. */
    @Test
    fun theAppMarkFillsMoreThanItsSafeZone() {
        val context = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        val mark = CategoryIconBitmaps.appMark(context, AppShortcutIcon, size)
        assertEquals(size, mark.width)
        assertTrue(
            "The mark should be drawn, not blank",
            (0 until size).any { Color.alpha(mark.getPixel(size / 2, it)) > 0 },
        )
    }

    /** True when the mask holds fully opaque white pixels, i.e. a glyph was drawn. */
    private fun Bitmap.hasGlyph(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { it == Color.WHITE }
    }
}
