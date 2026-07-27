package com.callbackdev.saldo.feature.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Which layout a size gets is a silent decision: pick wrong and the widget is
 * not broken, only bad, which no build and no crash will ever report.
 *
 * The row arithmetic is the part worth pinning, and since the widget went
 * `SizeMode.Responsive` it is pinned twice: once as arithmetic, and once as
 * the bucket set the launcher receives - a bucket that drifted from the
 * arithmetic would silently pin the wrong layout at that size.
 */
class WidgetLayoutTest {

    private val plenty = 20

    @Test
    fun `one launcher row is two buttons, not a squeezed grid`() {
        assertEquals(WidgetStyle.ACTIONS, layoutFor(DpSize(120.dp, 50.dp), plenty).style)
    }

    @Test
    fun `a wide but short widget is still two buttons`() {
        // The buttons share the width, so five columns and two columns are the
        // same layout: only the height decides.
        assertEquals(WidgetStyle.ACTIONS, layoutFor(DpSize(400.dp, 50.dp), plenty).style)
        assertEquals(WidgetStyle.ACTIONS, layoutFor(DpSize(250.dp, 80.dp), plenty).style)
    }

    @Test
    fun `the single row keeps an even inset on all four sides`() {
        val layout = layoutFor(DpSize(250.dp, 50.dp), plenty)
        assertEquals(layout.paddingHorizontal, layout.paddingVertical)
    }

    @Test
    fun `two rows high is a grid again`() {
        assertEquals(WidgetStyle.GRID, layoutFor(DpSize(120.dp, 120.dp), plenty).style)
    }

    @Test
    fun `a narrow widget shows two bare icon columns`() {
        val layout = layoutFor(DpSize(120.dp, 120.dp), plenty)
        assertEquals(2, layout.columns)
        assertTrue(!layout.showHeader)
        assertTrue(!layout.showLabels)
    }

    @Test
    fun `a wide short-ish widget shows one labelled row under the selector`() {
        val layout = layoutFor(DpSize(250.dp, 120.dp), plenty)
        assertEquals(WidgetStyle.GRID, layout.style)
        assertEquals(1, layout.rows)
        assertTrue(layout.showHeader)
        assertTrue(layout.showLabels)
    }

    @Test
    fun `the rows grow with the height`() {
        val heights = listOf(190, 260, 330, 400).map { DpSize(250.dp, it.dp) }
        val rows = heights.map { layoutFor(it, plenty).rows }
        assertEquals(rows.sorted(), rows, "Taller must never mean fewer rows")
        assertTrue(rows.last() > rows.first(), "A much taller widget must show more rows")
        assertEquals(2, layoutFor(DpSize(250.dp, 190.dp), plenty).rows)
    }

    @Test
    fun `the rows stop at the categories there are, rather than growing empty ones`() {
        // Three categories plus the "open Saldo" tile is one row of four,
        // however tall the widget is dragged.
        val layout = layoutFor(DpSize(250.dp, 600.dp), availableCategories = 3)
        assertEquals(1, layout.rows)
    }

    @Test
    fun `a widget with no categories still draws the row holding the way out`() {
        val layout = layoutFor(DpSize(250.dp, 600.dp), availableCategories = 0)
        assertEquals(1, layout.rows)
    }

    @Test
    fun `a tall widget eventually shows every category it has`() {
        val categories = 11
        val layout = layoutFor(DpSize(250.dp, 600.dp), availableCategories = categories)
        assertTrue(
            layout.categorySlots >= categories,
            "A tall widget should have room for all $categories categories, had ${layout.categorySlots}",
        )
    }

    @Test
    fun `a short widget never claims more rows than it can draw`() {
        val layout = layoutFor(DpSize(250.dp, 120.dp), plenty)
        val used = layout.rows * layout.rowHeight + (layout.rows - 1) * 6
        val available = 120 - 2 * layout.paddingVertical - 34 - 6
        assertTrue(used <= available, "Rows take ${used}dp of ${available}dp")
    }

    /**
     * The Responsive contract: the launcher only ever shows one of these
     * pre-rendered buckets, so each must land exactly on the layout step it was
     * computed for - and the set must cover every step there is.
     */
    @Test
    fun `every responsive bucket resolves to the layout it was designed for`() {
        val designed = mapOf(
            DpSize(110.dp, 40.dp) to (WidgetStyle.ACTIONS to 0),
            DpSize(110.dp, 64.dp) to (WidgetStyle.ACTIONS to 0),
            DpSize(110.dp, 88.dp) to (WidgetStyle.ACTIONS to 0),
            DpSize(110.dp, 112.dp) to (WidgetStyle.ACTIONS to 0),
            DpSize(110.dp, 120.dp) to (WidgetStyle.GRID to 1),
            DpSize(110.dp, 126.dp) to (WidgetStyle.GRID to 2),
            DpSize(110.dp, 184.dp) to (WidgetStyle.GRID to 3),
            DpSize(110.dp, 242.dp) to (WidgetStyle.GRID to 4),
            DpSize(110.dp, 300.dp) to (WidgetStyle.GRID to 5),
            DpSize(250.dp, 120.dp) to (WidgetStyle.GRID to 1),
            DpSize(250.dp, 190.dp) to (WidgetStyle.GRID to 2),
            DpSize(250.dp, 260.dp) to (WidgetStyle.GRID to 3),
            DpSize(250.dp, 330.dp) to (WidgetStyle.GRID to 4),
            DpSize(250.dp, 400.dp) to (WidgetStyle.GRID to 5),
        )
        assertEquals(designed.keys, WidgetBuckets, "The bucket set must match the designed steps")
        designed.forEach { (size, spec) ->
            val (style, rows) = spec
            val layout = layoutFor(size, plenty)
            assertEquals(style, layout.style, "Style at $size")
            if (style == WidgetStyle.GRID) {
                assertEquals(rows, layout.rows, "Rows at $size")
            }
        }
    }

    @Test
    fun `a larger system font costs rows rather than clipping labels`() {
        val base = layoutFor(DpSize(250.dp, 260.dp), plenty)
        val scaled = layoutFor(DpSize(250.dp, 260.dp), plenty, fontScale = 1.5f)
        assertTrue(scaled.rowHeight > base.rowHeight, "The label line must grow with the font")
        assertTrue(scaled.rows <= base.rows, "Taller rows cannot keep the same count")
        val used = scaled.rows * scaled.rowHeight + (scaled.rows - 1) * 6
        val available = 260 - 2 * scaled.paddingVertical - 34 - 6
        assertTrue(used <= available, "Rows take ${used}dp of ${available}dp at font scale 1.5")
    }

    @Test
    fun `the bare-icon narrow grid ignores the font scale`() {
        assertEquals(
            layoutFor(DpSize(110.dp, 184.dp), plenty),
            layoutFor(DpSize(110.dp, 184.dp), plenty, fontScale = 2f),
        )
    }

    /**
     * The bar provider composes only [ActionBuckets], and its provider info
     * caps the resize below the grid threshold: every one of its buckets must
     * therefore resolve to the two-button layout, or the bar would try to
     * draw a grid it has no room for.
     */
    @Test
    fun `every bar bucket is the two-button layout`() {
        ActionBuckets.forEach { size ->
            assertEquals(WidgetStyle.ACTIONS, layoutFor(size, plenty).style, "Style at $size")
        }
    }

    /**
     * The app-shortcut square: its side is the bucket height minus the even
     * inset, which is also the button height, floored so the smallest bucket
     * keeps a usable target. This is what makes the shortcut read as a square
     * instead of a fixed-width rectangle on launchers with taller rows.
     */
    @Test
    fun `the single-row buckets size the shortcut square from their height`() {
        assertEquals(60, layoutFor(DpSize(110.dp, 88.dp), plenty).shortcutSide)
        assertEquals(84, layoutFor(DpSize(110.dp, 112.dp), plenty).shortcutSide)
        assertEquals(40, layoutFor(DpSize(110.dp, 40.dp), plenty).shortcutSide, "Floored, never a sliver")
    }
}
