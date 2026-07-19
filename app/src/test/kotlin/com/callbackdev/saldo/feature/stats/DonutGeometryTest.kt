package com.callbackdev.saldo.feature.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DonutGeometryTest {

    @Test
    fun `sweeps and gaps cover the full turn`() {
        val slices = DonutGeometry.sliceAngles(listOf(0.5f, 0.3f, 0.2f), gapDegrees = 3f)

        val total = slices.sumOf { it.sweepAngle.toDouble() } + 3.0 * slices.size
        assertEquals(360.0, total, EPSILON)
    }

    @Test
    fun `sweeps are proportional to the fractions`() {
        val slices = DonutGeometry.sliceAngles(listOf(0.75f, 0.25f), gapDegrees = 0f)

        assertEquals(270.0, slices[0].sweepAngle.toDouble(), EPSILON)
        assertEquals(90.0, slices[1].sweepAngle.toDouble(), EPSILON)
    }

    @Test
    fun `a single slice fills the whole ring with no gap`() {
        val slices = DonutGeometry.sliceAngles(listOf(1f), gapDegrees = 3f)

        assertEquals(1, slices.size)
        assertEquals(DonutGeometry.START_ANGLE, slices.single().startAngle)
        assertEquals(360f, slices.single().sweepAngle)
    }

    @Test
    fun `many thin slices clamp the gap and never sweep negative`() {
        val fractions = List(40) { 1f / 40f }

        val slices = DonutGeometry.sliceAngles(fractions, gapDegrees = 10f)

        assertTrue(slices.all { it.sweepAngle >= 0f })
        val total = slices.sumOf { it.sweepAngle.toDouble() }
        assertTrue(total >= 360.0 * 0.75 - 1.0)
    }

    @Test
    fun `the ring starts at twelve o'clock and runs clockwise`() {
        val slices = DonutGeometry.sliceAngles(listOf(0.5f, 0.5f), gapDegrees = 0f)

        assertEquals(DonutGeometry.START_ANGLE, slices[0].startAngle)
        assertEquals(DonutGeometry.START_ANGLE + 180f, slices[1].startAngle)
    }

    @Test
    fun `hit-test resolves angles inside each slice`() {
        val slices = DonutGeometry.sliceAngles(listOf(0.5f, 0.5f), gapDegrees = 0f)

        // Just after twelve o'clock, clockwise: first slice.
        assertEquals(0, DonutGeometry.sliceIndexAt(-45f, slices))
        // Bottom of the ring: second slice.
        assertEquals(1, DonutGeometry.sliceIndexAt(135f, slices))
    }

    @Test
    fun `hit-test normalizes angles beyond a full turn`() {
        val slices = DonutGeometry.sliceAngles(listOf(0.5f, 0.5f), gapDegrees = 0f)

        assertEquals(0, DonutGeometry.sliceIndexAt(-45f + 360f, slices))
        assertEquals(1, DonutGeometry.sliceIndexAt(135f - 360f, slices))
    }

    @Test
    fun `hit-test misses the gaps`() {
        val slices = DonutGeometry.sliceAngles(listOf(0.5f, 0.5f), gapDegrees = 20f)

        // The first slice ends at -90 + 160 = 70 degrees; 80 falls in the gap.
        assertNull(DonutGeometry.sliceIndexAt(80f, slices))
    }

    @Test
    fun `empty fractions yield no slices`() {
        assertTrue(DonutGeometry.sliceAngles(emptyList(), gapDegrees = 3f).isEmpty())
        assertNull(DonutGeometry.sliceIndexAt(0f, emptyList()))
    }

    private companion object {
        const val EPSILON = 0.01
    }
}
