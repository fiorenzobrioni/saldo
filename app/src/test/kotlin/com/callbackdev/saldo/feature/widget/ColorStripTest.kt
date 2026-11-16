package com.callbackdev.saldo.feature.widget

import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The widget settings' strip of card colours: every colour offered, and rows that never leave one alone. */
class ColorStripTest {

    @Test
    fun `every card colour has its swatch, in the palette's order`() {
        assertEquals(WidgetCardColor.entries.toList(), WidgetCardColorChoices.map { it.first })
    }

    @Test
    fun `seven stand in one row from a 384dp phone, four over three on a 360dp one`() {
        // The strip's width: the screen less the group's margins (16 + 16) and its own (10 + 2).
        assertEquals(7, swatchesPerRow(7, 384.dp - 32.dp - 12.dp))
        assertEquals(4, swatchesPerRow(7, 360.dp - 32.dp - 12.dp))
    }

    @Test
    fun `a narrow strip is balanced and never leaves one colour alone`() {
        // 7 in rows of 6 or 5 would leave one or two under the rest: four over three instead.
        assertEquals(4, swatchesPerRow(7, (6 * 48).dp))
        assertEquals(4, swatchesPerRow(7, (5 * 48).dp))
        // Never less than one per row, however narrow.
        assertEquals(1, swatchesPerRow(7, 20.dp))
    }
}
