package com.callbackdev.saldo.feature.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The month widgets' forms and breakpoints (ADR 54), pinned like the quick-add widget's. */
class MonthWidgetLayoutTest {

    @Test
    fun `each provider stays within the platform's sixteen breakpoints, all distinct`() {
        listOf(RingSizes, WordsSizes).forEach { sizes ->
            assertTrue(sizes.size <= MaxBreakpoints)
            assertEquals(sizes.size, sizes.toSet().size)
        }
    }

    @Test
    fun `the ring's forms at the reference grants`() {
        assertEquals(RingForm.ROW, ringLayout(WidgetSize(159f, 85f)).form)
        assertEquals(RingForm.SQUARE, ringLayout(WidgetSize(159f, 189f)).form)
        assertEquals(RingForm.PANEL, ringLayout(WidgetSize(340f, 189f)).form)
    }

    @Test
    fun `a narrow row is the ring alone, a wider one carries its words`() {
        assertEquals(false, ringLayout(WidgetSize(110f, 85f)).showText)
        assertEquals(false, ringLayout(WidgetSize(159f, 85f)).showText)
        assertEquals(true, ringLayout(WidgetSize(250f, 85f)).showText)
    }

    @Test
    fun `a row's ring fills the row`() {
        // 85 dp less the row's two 10 dp insets, held at the row's largest ring.
        assertEquals(64, ringLayout(WidgetSize(150f, 85f)).ringSide)
        assertEquals(44, ringLayout(WidgetSize(150f, 64f)).ringSide)
    }

    @Test
    fun `the words' forms at the reference grants`() {
        assertEquals(WordsForm.SHORT, wordsForm(WidgetSize(159f, 85f)))
        assertEquals(WordsForm.LINE, wordsForm(WidgetSize(340f, 85f)))
        assertEquals(WordsForm.PANEL, wordsForm(WidgetSize(159f, 189f)))
        assertEquals(WordsForm.PANEL, wordsForm(WidgetSize(340f, 189f)))
    }
}
