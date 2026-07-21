package com.callbackdev.saldo.feature.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SparklineGeometryTest {

    @Test
    fun `flat input yields zero tangents everywhere`() {
        val tangents = monotoneTangents(listOf(0.5f, 0.5f, 0.5f, 0.5f))

        assertTrue(tangents.all { it == 0f })
    }

    @Test
    fun `two points share the straight-line slope`() {
        val tangents = monotoneTangents(listOf(0f, 1f))

        assertEquals(listOf(1f, 1f), tangents)
    }

    @Test
    fun `local extrema get a zero tangent`() {
        // Rise then fall: the peak at index 1 must be flat or the curve
        // would overshoot above the data maximum.
        val tangents = monotoneTangents(listOf(0f, 1f, 0f))

        assertEquals(0f, tangents[1])
    }

    @Test
    fun `flat runs stay flat`() {
        val tangents = monotoneTangents(listOf(0f, 1f, 1f, 1f, 0.5f))

        // Both endpoints of each flat segment are zeroed.
        assertEquals(0f, tangents[1])
        assertEquals(0f, tangents[2])
        assertEquals(0f, tangents[3])
    }

    @Test
    fun `interpolated curve never overshoots the data range`() {
        val values = listOf(0f, 0.05f, 0.1f, 1f, 0.9f, 0.2f, 0.2f, 0.6f)
        val tangents = monotoneTangents(values)

        // Sample each cubic Hermite segment densely; monotone tangents must
        // keep every sample within the segment's own [min, max].
        for (i in 0 until values.size - 1) {
            val y0 = values[i]
            val y1 = values[i + 1]
            val m0 = tangents[i]
            val m1 = tangents[i + 1]
            val low = minOf(y0, y1) - EPSILON
            val high = maxOf(y0, y1) + EPSILON
            for (step in 0..SAMPLES) {
                val t = step.toFloat() / SAMPLES
                val t2 = t * t
                val t3 = t2 * t
                val value = (2 * t3 - 3 * t2 + 1) * y0 + (t3 - 2 * t2 + t) * m0 +
                    (-2 * t3 + 3 * t2) * y1 + (t3 - t2) * m1
                assertTrue(
                    value in low..high,
                    "segment $i overshoots at t=$t: $value not in [$low, $high]",
                )
            }
        }
    }

    @Test
    fun `steep tangents are clamped`() {
        val values = listOf(0f, 0.5f, 0.51f, 1f)
        val tangents = monotoneTangents(values)

        // No tangent may exceed three times its segment slope (Fritsch-Carlson bound).
        for (i in 0 until values.size - 1) {
            val slope = values[i + 1] - values[i]
            if (slope != 0f) {
                assertTrue(abs(tangents[i] / slope) <= LIMIT + EPSILON)
                assertTrue(abs(tangents[i + 1] / slope) <= LIMIT + EPSILON)
            }
        }
    }

    @Test
    fun `fewer than two points is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { monotoneTangents(listOf(1f)) }
    }

    @Test
    fun `zero baseline sits at its fraction of a straddling range`() {
        assertEquals(0.25f, zeroLineFraction(min = -50f, max = 150f))
    }

    @Test
    fun `zero baseline is centered on a symmetric range`() {
        assertEquals(0.5f, zeroLineFraction(min = -100f, max = 100f))
    }

    @Test
    fun `all-positive range draws no zero baseline`() {
        assertNull(zeroLineFraction(min = 10f, max = 200f))
    }

    @Test
    fun `all-negative range draws no zero baseline`() {
        assertNull(zeroLineFraction(min = -200f, max = -10f))
    }

    @Test
    fun `range merely touching zero draws no baseline`() {
        // At either edge the line would coincide with the curve's own
        // extreme and read as a stray underline.
        assertNull(zeroLineFraction(min = 0f, max = 100f))
        assertNull(zeroLineFraction(min = -100f, max = 0f))
    }

    private companion object {
        const val SAMPLES = 50
        const val EPSILON = 1e-4f
        const val LIMIT = 3f
    }
}
