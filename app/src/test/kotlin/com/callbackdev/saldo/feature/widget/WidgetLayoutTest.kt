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
 * The row arithmetic is the part worth pinning. It used to be a constant two,
 * because `SizeMode.Responsive` reports the matched bucket rather than the real
 * widget, so a widget dragged taller kept being told it was 190dp high.
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
}
