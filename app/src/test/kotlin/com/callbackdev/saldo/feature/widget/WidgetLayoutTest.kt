package com.callbackdev.saldo.feature.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Which layout a size gets is a silent decision: pick wrong and the widget is
 * not broken, only bad, which no build and no crash will ever report.
 *
 * The row arithmetic is the part worth pinning, and it is pinned twice: once as
 * arithmetic, and once as the set of sizes the launcher receives - a breakpoint
 * that drifted from the arithmetic would either clip the last row or waste the
 * room it was meant to fill.
 */
class WidgetLayoutTest {

    private val plenty = 20

    /** dp, the unit every breakpoint is declared in. */
    private fun size(width: Int, height: Int) = WidgetSize(width.toFloat(), height.toFloat())

    @Test
    fun `one launcher row is two buttons, not a squeezed grid`() {
        assertEquals(WidgetStyle.ACTIONS, layoutFor(size(120, 50), plenty).style)
    }

    @Test
    fun `a wide but short widget is still two buttons`() {
        // The buttons share the width, so five columns and two columns are the
        // same layout: only the height decides.
        assertEquals(WidgetStyle.ACTIONS, layoutFor(size(400, 50), plenty).style)
        assertEquals(WidgetStyle.ACTIONS, layoutFor(size(250, 80), plenty).style)
    }

    @Test
    fun `the single row keeps an even inset on all four sides`() {
        val layout = layoutFor(size(250, 50), plenty)
        assertEquals(layout.paddingHorizontal, layout.paddingVertical)
    }

    @Test
    fun `two rows high is a grid again`() {
        assertEquals(WidgetStyle.GRID, layoutFor(size(120, 120), plenty).style)
    }

    @Test
    fun `a narrow widget shows two bare icon columns`() {
        val layout = layoutFor(size(120, 120), plenty)
        assertEquals(2, layout.columns)
        assertTrue(!layout.showHeader)
        assertTrue(!layout.showLabels)
    }

    @Test
    fun `a wide short-ish widget shows one labelled row under the selector`() {
        val layout = layoutFor(size(250, 120), plenty)
        assertEquals(WidgetStyle.GRID, layout.style)
        assertEquals(1, layout.rows)
        assertTrue(layout.showHeader)
        assertTrue(layout.showLabels)
    }

    @Test
    fun `the rows grow with the height`() {
        val heights = listOf(190, 260, 330, 400).map { size(250, it) }
        val rows = heights.map { layoutFor(it, plenty).rows }
        assertEquals(rows.sorted(), rows, "Taller must never mean fewer rows")
        assertTrue(rows.last() > rows.first(), "A much taller widget must show more rows")
        assertEquals(2, layoutFor(size(250, 190), plenty).rows)
    }

    @Test
    fun `the rows stop at the categories there are, rather than growing empty ones`() {
        // Three categories plus the "open Saldo" tile is one row of four,
        // however tall the widget is dragged.
        val layout = layoutFor(size(250, 600), availableCategories = 3)
        assertEquals(1, layout.rows)
    }

    @Test
    fun `a widget with no categories still draws the row holding the way out`() {
        val layout = layoutFor(size(250, 600), availableCategories = 0)
        assertEquals(1, layout.rows)
    }

    @Test
    fun `a tall widget eventually shows every category it has`() {
        val categories = 11
        val layout = layoutFor(size(250, 600), availableCategories = categories)
        assertTrue(
            layout.categorySlots >= categories,
            "A tall widget should have room for all $categories categories, had ${layout.categorySlots}",
        )
    }

    @Test
    fun `a short widget never claims more rows than it can draw`() {
        val layout = layoutFor(size(250, 120), plenty)
        val used = layout.rows * layout.rowHeight + (layout.rows - 1) * 6
        val available = 120 - 2 * layout.paddingVertical - 34 - 6
        assertTrue(used <= available, "Rows take ${used}dp of ${available}dp")
    }

    /**
     * The sizes-map contract: the launcher only ever shows one of these
     * pre-rendered layouts, so each breakpoint must land exactly on the layout
     * step it was computed for - and the set must cover every step there is.
     */
    @Test
    fun `every breakpoint resolves to the layout it was designed for`() {
        val designed = mapOf(
            size(110, 40) to (WidgetStyle.ACTIONS to 0),
            size(110, 64) to (WidgetStyle.ACTIONS to 0),
            size(110, 88) to (WidgetStyle.ACTIONS to 0),
            size(110, 112) to (WidgetStyle.ACTIONS to 0),
            size(110, 120) to (WidgetStyle.GRID to 1),
            size(110, 126) to (WidgetStyle.GRID to 2),
            size(110, 184) to (WidgetStyle.GRID to 3),
            size(110, 242) to (WidgetStyle.GRID to 4),
            size(110, 300) to (WidgetStyle.GRID to 5),
            size(250, 120) to (WidgetStyle.GRID to 1),
            size(250, 190) to (WidgetStyle.GRID to 2),
            size(250, 260) to (WidgetStyle.GRID to 3),
            size(250, 330) to (WidgetStyle.GRID to 4),
            size(250, 400) to (WidgetStyle.GRID to 5),
        )
        assertEquals(designed.keys, GridWidgetSizes.toSet(), "The size set must match the designed steps")
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
        val base = layoutFor(size(250, 260), plenty)
        val scaled = layoutFor(size(250, 260), plenty, fontScale = 1.5f)
        assertTrue(scaled.rowHeight > base.rowHeight, "The label line must grow with the font")
        assertTrue(scaled.rows <= base.rows, "Taller rows cannot keep the same count")
        val used = scaled.rows * scaled.rowHeight + (scaled.rows - 1) * 6
        val available = 260 - 2 * scaled.paddingVertical - 34 - 6
        assertTrue(used <= available, "Rows take ${used}dp of ${available}dp at font scale 1.5")
    }

    @Test
    fun `the bare-icon narrow grid ignores the font scale`() {
        assertEquals(
            layoutFor(size(110, 184), plenty),
            layoutFor(size(110, 184), plenty, fontScale = 2f),
        )
    }

    /**
     * The bar provider composes only [ActionSizes], and its provider info
     * caps the resize below the grid threshold: every one of its sizes must
     * therefore resolve to the two-button layout, or the bar would try to
     * draw a grid it has no room for.
     */
    @Test
    fun `every bar size is the two-button layout`() {
        ActionSizes.forEach { size ->
            assertEquals(WidgetStyle.ACTIONS, layoutFor(size, plenty).style, "Style at $size")
        }
    }

    /**
     * The app-shortcut square: its side is the declared height minus the even
     * inset, which is also the button height, floored so the smallest size
     * keeps a usable target. This is what makes the shortcut read as a square
     * instead of a fixed-width rectangle on launchers with taller rows.
     */
    @Test
    fun `the single-row sizes derive the shortcut square from their height`() {
        assertEquals(60, layoutFor(size(110, 88), plenty).shortcutSide)
        assertEquals(84, layoutFor(size(110, 112), plenty).shortcutSide)
        assertEquals(40, layoutFor(size(110, 40), plenty).shortcutSide, "Floored, never a sliver")
    }
}
