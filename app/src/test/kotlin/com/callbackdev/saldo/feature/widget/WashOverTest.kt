package com.callbackdev.saldo.feature.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The tonal wash behind every glyph on the widget, and the only piece of the
 * widget's colour arithmetic that can be asserted without a device.
 *
 * Worth asserting because getting it wrong is a widget that looks broken rather
 * than one that crashes. The only tint a `RemoteViews` can apply is
 * `setColorFilter`, which an ImageView runs in SRC_ATOP: handed a colour that
 * still carries alpha it composites against the shape it is tinting instead of
 * against the widget behind it, and the app's 16% wash came back a chalky pastel.
 * Flattening it here, opaque, is what makes the launcher-side result match the
 * in-app `CategoryCell`.
 */
class WashOverTest {

    private val lightSurface = 0xFFE8DEF8.toInt()
    private val darkSurface = 0xFF332D41.toInt()
    private val green = 0xFF66BB6A.toInt()

    @Test
    fun `the result is always opaque, whatever went in`() {
        assertEquals(0xFF, alpha(washOver(lightSurface, green)))
        assertEquals(0xFF, alpha(washOver(darkSurface, green)))
        // Even handed an accent that carries its own alpha: the accent's alpha is
        // not the wash's, and a translucent result is the bug this guards.
        assertEquals(0xFF, alpha(washOver(lightSurface, 0x8066BB6A.toInt())))
    }

    @Test
    fun `a wash lands between its surface and its accent`() {
        val wash = washOver(lightSurface, green)
        assertNotEquals(lightSurface, wash)
        assertNotEquals(green, wash)
        listOf(RED, GREEN, BLUE).forEach { shift ->
            val value = channel(wash, shift)
            val bounds = listOf(channel(lightSurface, shift), channel(green, shift)).sorted()
            assertTrue(
                value >= bounds.first() && value <= bounds.last(),
                "Channel at $shift: $value outside ${bounds.first()}..${bounds.last()}",
            )
        }
    }

    /** 16%: much nearer the surface than the accent, or it stops being a wash. */
    @Test
    fun `a wash reads as a tint of the surface, not as the accent`() {
        val wash = washOver(lightSurface, green)
        assertTrue(
            distance(wash, lightSurface) < distance(wash, green),
            "The wash must stay close to the surface it sits on",
        )
    }

    /**
     * The same accent over the two branches gives two different washes, which is
     * exactly why the palette carries a pair and cannot collapse it: the launcher
     * can only re-resolve a branch it was actually handed.
     */
    @Test
    fun `the same accent washes differently on light and dark`() {
        assertNotEquals(washOver(lightSurface, green), washOver(darkSurface, green))
    }

    /** An accent equal to the surface has nothing to tint: the surface comes back. */
    @Test
    fun `washing a surface with itself changes nothing`() {
        assertEquals(lightSurface, washOver(lightSurface, lightSurface))
    }

    /** No channel may wrap: the extremes are where integer blending goes wrong. */
    @Test
    fun `the extremes stay inside the byte`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        listOf(
            washOver(white, black),
            washOver(black, white),
            washOver(white, white),
            washOver(black, black),
        ).forEach { result ->
            assertEquals(0xFF, alpha(result))
            listOf(RED, GREEN, BLUE).forEach { shift ->
                val value = channel(result, shift)
                assertTrue(value in 0..0xFF, "Channel at $shift out of range: $value")
            }
        }
    }

    private fun alpha(color: Int) = channel(color, ALPHA)

    private fun channel(color: Int, shift: Int) = (color ushr shift) and 0xFF

    private fun distance(first: Int, second: Int) = listOf(RED, GREEN, BLUE)
        .sumOf { shift -> kotlin.math.abs(channel(first, shift) - channel(second, shift)) }

    private companion object {
        const val ALPHA = 24
        const val RED = 16
        const val GREEN = 8
        const val BLUE = 0
    }
}
