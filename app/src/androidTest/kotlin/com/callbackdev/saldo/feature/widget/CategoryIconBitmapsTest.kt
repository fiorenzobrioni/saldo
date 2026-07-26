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
    fun everyMappedIconDrawsAGlyph() {
        CategoryVisuals.iconKeys.forEach { key ->
            val bitmap = CategoryIconBitmaps.categoryTile(iconKey = key, colorRgb = green, sizePx = size)
            assertEquals(size, bitmap.width)
            assertEquals(size, bitmap.height)
            assertTrue(
                "Icon '$key' rendered as a bare tile: no glyph pixels",
                bitmap.hasGlyph(background = CategoryVisuals.color(green).toArgbInt()),
            )
        }
    }

    @Test
    fun theTileIsRoundedRatherThanSquare() {
        val bitmap = CategoryIconBitmaps.categoryTile(iconKey = "shopping_cart", colorRgb = green, sizePx = size)
        // The squircle leaves the very corner transparent; a square fill would not.
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
        assertTrue(Color.alpha(bitmap.getPixel(size / 2, size / 2)) > 0)
    }

    @Test
    fun anUnknownIconStillProducesATile() {
        val bitmap = CategoryIconBitmaps.categoryTile(iconKey = "no-such-icon", colorRgb = green, sizePx = size)
        assertEquals(size, bitmap.width)
        assertTrue(Color.alpha(bitmap.getPixel(size / 2, size / 2)) > 0)
    }

    @Test
    fun aNullIconFallsBackWithoutCrashing() {
        val bitmap = CategoryIconBitmaps.categoryTile(iconKey = null, colorRgb = null, sizePx = size)
        assertEquals(size, bitmap.width)
    }

    @Test
    fun theSameTileIsServedFromCacheRatherThanRedrawn() {
        val first = CategoryIconBitmaps.categoryTile(iconKey = "restaurant", colorRgb = green, sizePx = size)
        val second = CategoryIconBitmaps.categoryTile(iconKey = "restaurant", colorRgb = green, sizePx = size)
        assertTrue("The tile cache handed back a different bitmap", first === second)
    }

    @Test
    fun theActionTileIsOutlinedRatherThanFilled() {
        val action = CategoryIconBitmaps.actionTile(
            vector = Icons.Outlined.MoreHoriz,
            stroke = ComposeColor(0xFF00696D),
            glyph = ComposeColor(0xFF00696D),
            sizePx = size,
        )
        // The point of the action tile is that it does not read as a category
        // avatar: the middle of a filled squircle is opaque, an outlined one is
        // empty between the border and the glyph.
        assertEquals(Color.TRANSPARENT, action.getPixel(size / 2, size / 4))
        // The border itself is drawn: a horizontal scan through the middle must
        // hit paint near both edges.
        assertTrue(Color.alpha(action.getPixel(1, size / 2)) > 0)
        assertTrue(Color.alpha(action.getPixel(size - 2, size / 2)) > 0)
    }

    @Test
    fun theActionTileStillDrawsItsGlyph() {
        val action = CategoryIconBitmaps.actionTile(
            vector = Icons.Outlined.MoreHoriz,
            stroke = ComposeColor(0xFF00696D),
            glyph = ComposeColor(0xFF00696D),
            sizePx = size,
        )
        // Dead centre of a "more" glyph is its middle dot.
        assertTrue(Color.alpha(action.getPixel(size / 2, size / 2)) > 0)
    }

    /** True when some pixel differs from the tile background, i.e. a glyph was drawn. */
    private fun Bitmap.hasGlyph(background: Int): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { it != background && Color.alpha(it) > 0 }
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
