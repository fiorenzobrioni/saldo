package com.callbackdev.saldo.feature.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Which layout a size gets is a silent decision: pick wrong and the widget is
 * not broken, only bad, which no build and no crash will ever report.
 */
class WidgetLayoutTest {

    @Test
    fun `one launcher row is two buttons, not a squeezed grid`() {
        assertEquals(WidgetStyle.ACTIONS, layoutFor(DpSize(120.dp, 50.dp)).style)
    }

    @Test
    fun `a wide but short widget is still two buttons`() {
        // The buttons share the width, so five columns and two columns are the
        // same layout: only the height decides.
        assertEquals(WidgetStyle.ACTIONS, layoutFor(DpSize(400.dp, 50.dp)).style)
        assertEquals(WidgetStyle.ACTIONS, layoutFor(DpSize(250.dp, 80.dp)).style)
    }

    @Test
    fun `two rows high is a grid again`() {
        assertEquals(WidgetStyle.GRID, layoutFor(DpSize(120.dp, 120.dp)).style)
    }

    @Test
    fun `a small square shows four icon-only tiles`() {
        val layout = layoutFor(DpSize(120.dp, 120.dp))
        assertEquals(4, layout.columns * layout.rows)
        assertTrue(!layout.showHeader)
        assertTrue(!layout.showLabels)
    }

    @Test
    fun `a wide short-ish widget shows one labelled row under the selector`() {
        val layout = layoutFor(DpSize(250.dp, 120.dp))
        assertEquals(WidgetStyle.GRID, layout.style)
        assertEquals(1, layout.rows)
        assertTrue(layout.showHeader)
        assertTrue(layout.showLabels)
    }

    @Test
    fun `the largest bucket shows seven categories plus the way out`() {
        val layout = layoutFor(DpSize(250.dp, 190.dp))
        assertEquals(2, layout.rows)
        assertEquals(SaldoQuickAddWidget.MaxCategorySlots, layout.categorySlots)
    }

    @Test
    fun `growing past a bucket never falls back to a smaller layout`() {
        // A widget dragged larger must not lose tiles on the way.
        val large = layoutFor(DpSize(400.dp, 300.dp))
        assertEquals(WidgetStyle.GRID, large.style)
        assertEquals(SaldoQuickAddWidget.MaxCategorySlots, large.categorySlots)
    }
}
