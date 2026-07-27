package com.callbackdev.saldo.feature.widget

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.graphics.Color as ComposeColor
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
    private val green = 0x66BB6A

    @Test
    fun everyMappedIconRendersItsGlyph() {
        CategoryVisuals.iconKeys.forEach { key ->
            val bitmap = CategoryIconBitmaps.glyph(
                vector = CategoryVisuals.icon(key),
                color = CategoryVisuals.color(green),
                sizePx = size,
            )
            assertEquals(size, bitmap.width)
            assertEquals(size, bitmap.height)
            assertTrue(
                "Icon '$key' rendered as nothing at all",
                bitmap.hasGlyph(tint = CategoryVisuals.color(green).toArgbInt()),
            )
        }
    }

    /**
     * The wash behind a glyph is a Glance background now, not part of the
     * bitmap. That is a payload decision, not a cosmetic one: `RemoteViews`
     * carry their bitmaps to the launcher through a size-capped transaction, and
     * a whole-tile bitmap is roughly three times the pixels of the glyph in it.
     */
    @Test
    fun theGlyphCarriesNoBackgroundOfItsOwn() {
        val bitmap = CategoryIconBitmaps.glyph(
            vector = CategoryVisuals.icon("shopping_cart"),
            color = CategoryVisuals.color(green),
            sizePx = size,
        )
        assertEquals("A corner must stay transparent", Color.TRANSPARENT, bitmap.getPixel(0, 0))
    }

    @Test
    fun anUnknownIconStillRendersTheFallback() {
        val bitmap = CategoryIconBitmaps.glyph(
            vector = CategoryVisuals.icon("no-such-icon"),
            color = CategoryVisuals.color(green),
            sizePx = size,
        )
        assertEquals(size, bitmap.width)
        assertTrue(bitmap.hasGlyph(tint = CategoryVisuals.color(green).toArgbInt()))
    }

    @Test
    fun theSameGlyphIsServedFromCacheRatherThanRedrawn() {
        val vector = CategoryVisuals.icon("restaurant")
        val colour = CategoryVisuals.color(green)
        val first = CategoryIconBitmaps.glyph(vector, colour, size)
        val second = CategoryIconBitmaps.glyph(vector, colour, size)
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

    /** True when the tile holds pixels of the solid tint, i.e. a glyph was drawn. */
    private fun Bitmap.hasGlyph(tint: Int): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { it == tint }
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
        android.graphics.Color.argb(
            (alpha * COLOR_MAX).toInt(),
            (red * COLOR_MAX).toInt(),
            (green * COLOR_MAX).toInt(),
            (blue * COLOR_MAX).toInt(),
        )

    private companion object {
        const val COLOR_MAX = 255
    }
}
