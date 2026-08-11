package com.callbackdev.saldo.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.rememberMotionEnabled
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.DailyBalance
import java.util.Currency
import kotlin.math.sqrt

/**
 * The hero card's balance sparkline: a smooth monotone-cubic line over the
 * last 30 days with a soft gradient fill underneath and a dot on today's
 * point, drawn with a plain Canvas (ADR 27: a decorative sparkline needs no
 * axes, markers or scrolling, so Vico stays confined to the statistics
 * screen).
 *
 * When a [forecast] is provided, the line continues past today as a dashed
 * tail to the end of the month (same curve, same per-day step, no fill), with
 * a hollow ring on the projected end-of-month point and a small pill carrying
 * the estimated figure (on the error container pairing when the estimate is
 * negative, matching the card-wide "red only when negative" rule). The
 * 30-day history window is fixed, so the tail can take at most about half
 * the width (a 31-day month seen from day 1): the solid, factual part
 * always dominates.
 *
 * When the plotted range straddles zero, a faint dotted baseline marks where
 * zero sits ([zeroLineFraction]): with min-max normalization a balance just
 * above zero would otherwise be indistinguishable from a comfortable one. The
 * baseline is drawn behind everything else in the hairline color, and the
 * gradient fill then anchors to it instead of the canvas bottom, so only the
 * area above zero carries the tint and the below-zero stretch reads as such.
 *
 * Geometry is purely presentational: balances are projected to float
 * fractions of the drawing area only to place pixels, never to compute money.
 * The canvas is mute for TalkBack, so the whole drawing carries a single
 * description summarizing the trend (and the estimate) with properly
 * formatted amounts.
 */
@Composable
internal fun BalanceSparkline(
    history: List<DailyBalance>,
    forecast: List<DailyBalance>,
    currency: Currency,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    if (history.size < 2) return
    val locale = LocalConfiguration.current.locales[0]
    val projectedText = forecast.lastOrNull()?.let { MoneyFormatter.format(it.balance, currency, locale) }
    val description = sparklineDescription(history, projectedText, currency)

    // Presentational projection to [0, 1] fractions of the drawing height,
    // normalized over history and forecast together so the tail fits; a flat
    // series (min == max) sits on the vertical midline. The zero baseline, when
    // the range straddles it, lives in the same normalized space.
    val (fractions, zeroFraction) = remember(history, forecast) {
        val values = (history + forecast).map { it.balance.toFloat() }
        val min = values.min()
        val max = values.max()
        val span = max - min
        val fractions = values.map { if (span == 0f) MIDLINE else (it - min) / span }
        fractions to zeroLineFraction(min, max)
    }
    val tangents = remember(fractions) { monotoneTangents(fractions) }
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant

    // The estimate pill: text measured up front, drawn inside the canvas next
    // to the projected end-of-month point.
    val textMeasurer = rememberTextMeasurer()
    val pillStyle = MaterialTheme.typography.labelSmall.tabularNumbers()
    val pillLayout = projectedText?.let { text ->
        val pillText = stringResource(R.string.dashboard_sparkline_forecast_pill, text)
        remember(pillText, pillStyle) { textMeasurer.measure(AnnotatedString(pillText), pillStyle) }
    }
    // The pill flips to the error pairing when the projection lands below
    // zero, in line with the card-wide "red only when negative" rule: the
    // "≈" and the dashed tail already say "estimate", the color carries the
    // sign. The end-of-month ring stays neutral: the warning lives in the
    // pill, not in the geometry.
    val projectedNegative = (forecast.lastOrNull()?.balance?.signum() ?: 0) < 0
    val pillContainer = if (projectedNegative) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val pillContent = if (projectedNegative) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

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
        val todayIndex = history.size - 1
        val scaledTangents = tangents.map { it * height }
        val line = segmentPath(points, scaledTangents, from = 0, to = todayIndex)
        // The gradient fill belongs to the factual part only: it stops under
        // today's point, so the dashed tail reads as tentative.
        val fill = Path().apply {
            addPath(line)
            lineTo(points[todayIndex].x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        val tail = if (todayIndex < points.size - 1) {
            segmentPath(points, scaledTangents, from = todayIndex, to = points.size - 1)
        } else {
            null
        }

        // With a visible zero baseline the tinted area stops at zero: below it
        // only the bare curve remains, so negative stretches carry no mass.
        val zeroY = zeroFraction?.let { inset + height * (1f - it) }
        val fillBottom = zeroY ?: size.height

        clipRect(right = size.width * reveal.value) {
            if (zeroY != null) {
                // Fine dots (round caps on hair-length dashes) in the hairline
                // color: a quiet reference clearly distinct from the long-dash
                // forecast tail. Drawn first, so the data always covers it.
                drawLine(
                    color = zeroLineColor,
                    start = Offset(points.first().x, zeroY),
                    end = Offset(points.last().x, zeroY),
                    strokeWidth = ZERO_STROKE.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(ZERO_DOT_ON.dp.toPx(), ZERO_DOT_OFF.dp.toPx()),
                    ),
                )
            }
            clipRect(bottom = fillBottom) {
                drawPath(
                    path = fill,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = FILL_ALPHA), Color.Transparent),
                        startY = 0f,
                        endY = fillBottom,
                    ),
                )
            }
            drawPath(
                path = line,
                color = lineColor,
                style = Stroke(width = STROKE.dp.toPx(), cap = StrokeCap.Round),
            )
            if (tail != null) {
                drawPath(
                    path = tail,
                    color = lineColor.copy(alpha = FORECAST_ALPHA),
                    style = Stroke(
                        width = STROKE.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(DASH_ON.dp.toPx(), DASH_OFF.dp.toPx()),
                        ),
                    ),
                )
                drawCircle(
                    color = lineColor.copy(alpha = FORECAST_ALPHA),
                    radius = DOT_RADIUS.dp.toPx(),
                    center = points.last(),
                    style = Stroke(width = RING_STROKE.dp.toPx()),
                )
            }
            drawCircle(color = lineColor, radius = DOT_RADIUS.dp.toPx(), center = points[todayIndex])
        }
        // A non-null pill implies a forecast, hence a tail to annotate.
        pillLayout?.let { layout ->
            drawEstimatePill(
                layout = layout,
                anchor = points.last(),
                container = pillContainer,
                content = pillContent,
                reveal = reveal.value,
            )
        }
    }
}

/**
 * The TalkBack summary of the whole drawing: the 30-day trend with formatted
 * amounts, plus the end-of-month estimate when the dashed tail is shown.
 */
@Composable
private fun sparklineDescription(
    history: List<DailyBalance>,
    projectedText: String?,
    currency: Currency,
): String {
    val locale = LocalConfiguration.current.locales[0]
    val first = history.first().balance
    val last = history.last().balance
    val trend = last.compareTo(first)
    val trendDescription = when {
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
    return if (projectedText != null) {
        "$trendDescription. ${stringResource(R.string.dashboard_sparkline_a11y_forecast, projectedText)}"
    } else {
        trendDescription
    }
}

/**
 * The monotone-cubic path through [points] between indices [from] and [to]
 * (inclusive). [scaledTangents] are the series tangents already multiplied by
 * the drawing height; sharing them across segments keeps the dashed tail
 * continuing the solid line without a kink at today's point. Internal because
 * the month-comparison chart draws its two lines through the same geometry.
 */
internal fun segmentPath(
    points: List<Offset>,
    scaledTangents: List<Float>,
    from: Int,
    to: Int,
): Path = Path().apply {
    moveTo(points[from].x, points[from].y)
    for (i in from until to) {
        // Screen y grows downward, so a positive data tangent bends the
        // curve up: the fraction-space tangent is negated via the scaling.
        val third = (points[i + 1].x - points[i].x) / BEZIER_THIRD
        cubicTo(
            points[i].x + third,
            points[i].y - scaledTangents[i] / BEZIER_THIRD,
            points[i + 1].x - third,
            points[i + 1].y + scaledTangents[i + 1] / BEZIER_THIRD,
            points[i + 1].x,
            points[i + 1].y,
        )
    }
}

/**
 * The estimate pill next to the projected end-of-month point: a rounded
 * container with the "≈ amount" label, placed to the left of [anchor] (the
 * point sits on the right edge) and above or below it depending on where the
 * line ends, clamped inside the canvas. It fades in on the last stretch of
 * [reveal], once the sweep has uncovered the tail it annotates.
 */
@Suppress("MagicNumber") // Dp literals for padding/margins, idiomatic like in composables.
private fun DrawScope.drawEstimatePill(
    layout: TextLayoutResult,
    anchor: Offset,
    container: Color,
    content: Color,
    reveal: Float,
) {
    val alpha = ((reveal - PILL_REVEAL_START) / (1f - PILL_REVEAL_START)).coerceIn(0f, 1f)
    if (alpha == 0f) return
    val paddingX = 6.dp.toPx()
    val paddingY = 2.dp.toPx()
    val margin = 6.dp.toPx()
    val pillWidth = layout.size.width + 2 * paddingX
    val pillHeight = layout.size.height + 2 * paddingY
    val x = (anchor.x - pillWidth - margin).coerceAtLeast(0f)
    val y = (if (anchor.y > size.height / 2f) anchor.y - pillHeight - margin else anchor.y + margin)
        .coerceIn(0f, (size.height - pillHeight).coerceAtLeast(0f))
    drawRoundRect(
        color = container.copy(alpha = container.alpha * alpha),
        topLeft = Offset(x, y),
        size = Size(pillWidth, pillHeight),
        cornerRadius = CornerRadius(pillHeight / 2f),
    )
    drawText(
        textLayoutResult = layout,
        color = content.copy(alpha = content.alpha * alpha),
        topLeft = Offset(x + paddingX, y + paddingY),
    )
}

/**
 * Where the zero baseline sits within the plotted [min, max] range, as a
 * [0, 1] fraction of the value span, or null when the line should not be
 * drawn. Only a strict crossing counts: a series that merely touches zero at
 * its minimum (or maximum) would put the line right along the curve's own
 * edge, where it reads as a stray underline rather than a reference. Top-level
 * and pure so the placement rule is unit-testable on the JVM.
 */
internal fun zeroLineFraction(min: Float, max: Float): Float? =
    if (min < 0f && max > 0f) -min / (max - min) else null

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

/** Hermite-to-Bezier control points sit at one third of the step. */
private const val BEZIER_THIRD = 3f
private const val REVEAL_MILLIS = 600
private const val MIDLINE = 0.5f
private const val FILL_ALPHA = 0.20f
private const val FORECAST_ALPHA = 0.65f
private const val PILL_REVEAL_START = 0.6f
private const val INSET = 4
private const val STROKE = 2
private const val DOT_RADIUS = 3
private const val RING_STROKE = 1.5f
private const val DASH_ON = 5
private const val DASH_OFF = 6

// The zero baseline: a 1dp stroke whose hair-length dashes render as fine
// dots under the round cap, spaced tighter than the forecast's long dashes.
private const val ZERO_STROKE = 1
private const val ZERO_DOT_ON = 1
private const val ZERO_DOT_OFF = 3
private const val MONOTONE_LIMIT = 9f
private const val MONOTONE_FACTOR = 3f
