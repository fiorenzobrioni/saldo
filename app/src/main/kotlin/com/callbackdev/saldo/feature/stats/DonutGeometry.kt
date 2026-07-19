package com.callbackdev.saldo.feature.stats

/** One slice's arc: where it starts and how far it sweeps, in degrees. */
data class DonutSlice(val startAngle: Float, val sweepAngle: Float)

/**
 * Pure geometry of the category donut: slice angles and tap hit-testing,
 * kept free of Compose so the math is unit-testable on the JVM. Angles are
 * degrees in the drawArc convention (0 at 3 o'clock, growing clockwise);
 * the ring starts at 12 o'clock ([START_ANGLE]) and runs clockwise.
 */
object DonutGeometry {

    /** 12 o'clock in the drawArc convention. */
    const val START_ANGLE = -90f

    /**
     * Turns share fractions (0..1, summing to ~1) into slice arcs separated
     * by [gapDegrees]. A single slice takes the full ring with no gap; with
     * many thin slices the gap is clamped so no slice ever sweeps below zero.
     */
    fun sliceAngles(fractions: List<Float>, gapDegrees: Float): List<DonutSlice> = when {
        fractions.isEmpty() -> emptyList()
        fractions.size == 1 -> listOf(DonutSlice(START_ANGLE, FULL_TURN))
        else -> {
            // Clamp the gap so the gaps never eat more than a quarter of the ring.
            val gap = minOf(gapDegrees, FULL_TURN * MAX_TOTAL_GAP_FRACTION / fractions.size)
            val available = FULL_TURN - gap * fractions.size
            val total = fractions.sum()
            var cursor = START_ANGLE
            fractions.map { fraction ->
                val sweep = if (total <= 0f) 0f else (fraction / total * available).coerceAtLeast(0f)
                val slice = DonutSlice(startAngle = cursor, sweepAngle = sweep)
                cursor += sweep + gap
                slice
            }
        }
    }

    /**
     * The slice under [angleDegrees] (any winding; normalized internally), or
     * null when the angle falls in a gap. The caller has already checked that
     * the touch radius is within the ring.
     */
    fun sliceIndexAt(angleDegrees: Float, slices: List<DonutSlice>): Int? {
        slices.forEachIndexed { index, slice ->
            val delta = normalizeDegrees(angleDegrees - slice.startAngle)
            if (delta <= slice.sweepAngle) return index
        }
        return null
    }

    /** Normalizes to [0, 360). */
    private fun normalizeDegrees(degrees: Float): Float {
        val remainder = degrees % FULL_TURN
        return if (remainder < 0f) remainder + FULL_TURN else remainder
    }

    private const val FULL_TURN = 360f
    private const val MAX_TOTAL_GAP_FRACTION = 0.25f
}
