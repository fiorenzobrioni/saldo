package com.callbackdev.saldo.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.rememberMotionEnabled
import com.callbackdev.saldo.core.domain.model.DailyBalance
import java.util.Currency
import kotlin.math.sqrt

/**
 * The hero card's 30-day balance sparkline: a smooth monotone-cubic line with
 * a soft gradient fill underneath and a dot on today's point, drawn with a
 * plain Canvas (ADR 27: a decorative sparkline needs no axes, markers or
 * scrolling, so Vico stays confined to the statistics screen).
 *
 * Geometry is purely presentational: balances are projected to float fractions
 * of the drawing area only to place pixels, never to compute money. The canvas
 * is mute for TalkBack, so the whole drawing carries a single description
 * summarizing the trend with properly formatted amounts.
 */
@Composable
internal fun BalanceSparkline(
    history: List<DailyBalance>,
    currency: Currency,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    if (history.size < 2) return
    val locale = LocalConfiguration.current.locales[0]
    val first = history.first().balance
    val last = history.last().balance
    val trend = last.compareTo(first)
    val description = when {
        trend > 0 -> stringResource(
            R.string.dashboard_sparkline_a11y_up,
            MoneyFormatter.format(first, currency, locale),
            MoneyFormatter.format(last, currency, locale),
        )
        trend < 0 -> stringResource(
            R.string.dashboard_sparkline_a11y_down,
            MoneyFormatter.format(first, currency, locale),
            MoneyFormatter.format(last, currency, locale),
        )
        else -> stringResource(
            R.string.dashboard_sparkline_a11y_flat,
            MoneyFormatter.format(last, currency, locale),
        )
    }

    // Presentational projection to [0, 1] fractions of the drawing height; a
    // flat series (min == max) sits on the vertical midline.
    val fractions = remember(history) {
        val values = history.map { it.balance.toFloat() }
        val min = values.min()
        val max = values.max()
        val span = max - min
        values.map { if (span == 0f) MIDLINE else (it - min) / span }
    }
    val tangents = remember(fractions) { monotoneTangents(fractions) }

    // One entrance reveal per screen visit, not one per data emission: a new
    // movement must update the line in place, not replay the sweep.
    val motionEnabled = rememberMotionEnabled()
    val reveal = remember { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) {
            reveal.animateTo(1f, tween(durationMillis = REVEAL_MILLIS, easing = FastOutSlowInEasing))
        }
    }

    Canvas(modifier.clearAndSetSemantics { contentDescription = description }) {
        val inset = INSET.dp.toPx()
        val width = size.width - 2 * inset
        val height = size.height - 2 * inset
        if (width <= 0f || height <= 0f) return@Canvas

        val step = width / (fractions.size - 1)
        val points = fractions.mapIndexed { index, fraction ->
            Offset(x = inset + step * index, y = inset + height * (1f - fraction))
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                // Screen y grows downward, so a positive data tangent bends the
                // curve up: the fraction-space tangent is negated via height.
                cubicTo(
                    points[i].x + step / 3f,
                    points[i].y - tangents[i] * height / 3f,
                    points[i + 1].x - step / 3f,
                    points[i + 1].y + tangents[i + 1] * height / 3f,
                    points[i + 1].x,
                    points[i + 1].y,
                )
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }

        clipRect(right = size.width * reveal.value) {
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = FILL_ALPHA), Color.Transparent),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            drawPath(
                path = line,
                color = lineColor,
                style = Stroke(width = STROKE.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(color = lineColor, radius = DOT_RADIUS.dp.toPx(), center = points.last())
        }
    }
}

/**
 * Fritsch-Carlson monotone tangents for a uniformly spaced series, expressed
 * as delta per unit index step. The interpolated curve never overshoots the
 * data range: tangents are zeroed at local extrema and clamped where the
 * classic scheme would bulge, so flat runs stay flat and a spike never draws a
 * dip that does not exist in the data. Top-level and pure so the geometry is
 * unit-testable on the JVM.
 */
internal fun monotoneTangents(values: List<Float>): List<Float> {
    require(values.size >= 2) { "A sparkline needs at least two points" }
    val n = values.size
    val slopes = FloatArray(n - 1) { values[it + 1] - values[it] }
    val tangents = FloatArray(n)
    tangents[0] = slopes[0]
    tangents[n - 1] = slopes[n - 2]
    for (i in 1 until n - 1) {
        val prev = slopes[i - 1]
        val next = slopes[i]
        tangents[i] = if (prev * next <= 0f) 0f else (prev + next) / 2f
    }
    for (i in 0 until n - 1) {
        val slope = slopes[i]
        if (slope == 0f) {
            tangents[i] = 0f
            tangents[i + 1] = 0f
        } else {
            val a = tangents[i] / slope
            val b = tangents[i + 1] / slope
            val norm = a * a + b * b
            if (norm > MONOTONE_LIMIT) {
                val scale = MONOTONE_FACTOR / sqrt(norm)
                tangents[i] = scale * a * slope
                tangents[i + 1] = scale * b * slope
            }
        }
    }
    return tangents.toList()
}

private const val REVEAL_MILLIS = 600
private const val MIDLINE = 0.5f
private const val FILL_ALPHA = 0.20f
private const val INSET = 4
private const val STROKE = 2
private const val DOT_RADIUS = 3
private const val MONOTONE_LIMIT = 9f
private const val MONOTONE_FACTOR = 3f
