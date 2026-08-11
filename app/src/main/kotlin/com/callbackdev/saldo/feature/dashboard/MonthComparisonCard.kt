package com.callbackdev.saldo.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SsidChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.component.rememberMotionEnabled
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import java.math.BigDecimal
import java.util.Currency

/**
 * Monthly comparison card: the previous month's day-by-day net balance change
 * overlaid with the current month's, both leaving zero on day one (see
 * [MonthComparisonSeries] for why deltas, not raw balances). The current month
 * is the primary line and stops at today with a dot; the previous month runs
 * muted underneath to the end of its own month. No forecast tail on purpose:
 * the hero sparkline above already estimates the month's end, this card
 * compares facts with facts.
 *
 * The footer carries the two reference figures as label/amount rows (never a
 * wrapping sentence): what had been spent by this day last month, and the
 * signed spend difference against it. Tap opens the statistics tab.
 */
@Suppress("LongParameterList") // One argument per card ingredient, all owned by the ViewModel.
@Composable
internal fun MonthComparisonCard(
    comparison: MonthComparisonSeries?,
    previousSpend: BigDecimal,
    delta: BigDecimal?,
    currency: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            DashboardCardHeader(
                icon = Icons.Outlined.SsidChart,
                title = stringResource(R.string.dashboard_month_comparison_title),
            )
            if (comparison != null &&
                comparison.previous.isNotEmpty() && comparison.current.isNotEmpty()
            ) {
                Spacer(Modifier.height(CHART_TOP_GAP))
                MonthComparisonChart(
                    comparison = comparison,
                    currency = currency,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CHART_HEIGHT),
                )
                Spacer(Modifier.height(LEGEND_TOP_GAP))
                ComparisonLegend()
            }
            Spacer(Modifier.height(FOOTER_TOP_GAP))
            ComparisonStatRow(
                label = stringResource(R.string.dashboard_comparison_spent_last_month),
                value = MoneyFormatter.format(previousSpend, currency),
            )
            delta?.let {
                Spacer(Modifier.height(FOOTER_ROW_GAP))
                ComparisonStatRow(
                    label = stringResource(R.string.dashboard_comparison_delta_label),
                    value = MoneyFormatter.formatSigned(it, currency),
                )
            }
        }
    }
}

/**
 * The overlay chart: both series share one normalized vertical scale and one
 * horizontal day scale (the longer month sets the width), so day N of one
 * month sits exactly above day N of the other. A leading zero point anchors
 * both lines to the month's start. When the plotted range straddles zero a
 * dotted baseline marks it, like the hero sparkline. Geometry is purely
 * presentational; the canvas is mute for TalkBack and the whole drawing
 * carries one description with formatted amounts.
 */
@Composable
private fun MonthComparisonChart(
    comparison: MonthComparisonSeries,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val currentColor = MaterialTheme.colorScheme.primary
    val previousColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PREVIOUS_ALPHA)
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant

    // "By this day last month" for TalkBack: the previous month's change at
    // today's day, clamped to its own length (a 31st has no twin in a 30-day
    // month).
    val previousAtToday = comparison.previous[
        (comparison.current.size - 1).coerceAtMost(comparison.previous.lastIndex),
    ]
    val description = stringResource(
        R.string.dashboard_comparison_a11y,
        MoneyFormatter.formatSigned(comparison.current.last(), currency),
        MoneyFormatter.formatSigned(previousAtToday, currency),
    )

    // Both series prefixed with the zero they leave from, projected together
    // to [0, 1] fractions of the drawing height so they share one scale.
    val geometry = remember(comparison) {
        val previous = listOf(0f) + comparison.previous.map { it.toFloat() }
        val current = listOf(0f) + comparison.current.map { it.toFloat() }
        val values = previous + current
        val min = values.min()
        val max = values.max()
        val span = max - min
        fun normalize(series: List<Float>): List<Float> =
            series.map { if (span == 0f) MIDLINE else (it - min) / span }
        ComparisonGeometry(
            previous = normalize(previous),
            current = normalize(current),
            zeroFraction = zeroLineFraction(min, max),
            // The longer month sets the horizontal day scale.
            daySteps = maxOf(previous.size, current.size) - 1,
        )
    }

    // One entrance reveal per screen visit, like the hero sparkline.
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
        if (width <= 0f || height <= 0f || geometry.daySteps < 1) return@Canvas

        val step = width / geometry.daySteps
        fun points(series: List<Float>): List<Offset> = series.mapIndexed { index, fraction ->
            Offset(x = inset + step * index, y = inset + height * (1f - fraction))
        }

        val zeroY = geometry.zeroFraction?.let { inset + height * (1f - it) }

        clipRect(right = size.width * reveal.value) {
            if (zeroY != null) {
                drawLine(
                    color = zeroLineColor,
                    start = Offset(inset, zeroY),
                    end = Offset(inset + width, zeroY),
                    strokeWidth = ZERO_STROKE.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(ZERO_DOT_ON.dp.toPx(), ZERO_DOT_OFF.dp.toPx()),
                    ),
                )
            }
            // Previous month first, so the current line always covers it.
            if (geometry.previous.size > 1) {
                val previousPoints = points(geometry.previous)
                val tangents = monotoneTangents(geometry.previous).map { it * height }
                drawPath(
                    path = segmentPath(previousPoints, tangents, from = 0, to = previousPoints.lastIndex),
                    color = previousColor,
                    style = Stroke(width = PREVIOUS_STROKE.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            if (geometry.current.size > 1) {
                val currentPoints = points(geometry.current)
                val tangents = monotoneTangents(geometry.current).map { it * height }
                drawPath(
                    path = segmentPath(currentPoints, tangents, from = 0, to = currentPoints.lastIndex),
                    color = currentColor,
                    style = Stroke(width = CURRENT_STROKE.dp.toPx(), cap = StrokeCap.Round),
                )
                drawCircle(
                    color = currentColor,
                    radius = DOT_RADIUS.dp.toPx(),
                    center = currentPoints.last(),
                )
            }
        }
    }
}

/** The two series' normalized fractions plus the shared axes facts. */
private data class ComparisonGeometry(
    val previous: List<Float>,
    val current: List<Float>,
    val zeroFraction: Float?,
    val daySteps: Int,
)

/** Color-key legend under the chart, plus what the lines plot. */
@Composable
private fun ComparisonLegend(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LEGEND_ENTRY_GAP),
        modifier = modifier.fillMaxWidth(),
    ) {
        LegendEntry(
            color = MaterialTheme.colorScheme.primary,
            label = stringResource(R.string.dashboard_comparison_this_month),
        )
        LegendEntry(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PREVIOUS_ALPHA),
            label = stringResource(R.string.dashboard_comparison_previous_month),
        )
        Text(
            text = stringResource(R.string.dashboard_comparison_caption),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            // Takes only the leftover width and truncates with an ellipsis:
            // the color keys always win the space fight.
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LegendEntry(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LEGEND_DOT_GAP),
        modifier = modifier,
    ) {
        Spacer(
            Modifier
                .size(LEGEND_DOT_SIZE)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * One reference row of the card's footer: muted label taking the leftover
 * width, tabular amount pushed to the row's end (the same idiom as the
 * Today/month stat lines), so the figure never wraps to its own line.
 */
@Composable
private fun ComparisonStatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(FOOTER_VALUE_GAP))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private val CHART_HEIGHT = 64.dp
private val CHART_TOP_GAP = 10.dp
private val LEGEND_TOP_GAP = 4.dp
private val FOOTER_TOP_GAP = 8.dp
private val FOOTER_ROW_GAP = 2.dp
private val FOOTER_VALUE_GAP = 6.dp
private val LEGEND_ENTRY_GAP = 12.dp
private val LEGEND_DOT_GAP = 4.dp
private val LEGEND_DOT_SIZE = 8.dp

private const val PREVIOUS_ALPHA = 0.45f
private const val PREVIOUS_STROKE = 1.5f
private const val CURRENT_STROKE = 2
private const val DOT_RADIUS = 3
private const val INSET = 4
private const val MIDLINE = 0.5f
private const val REVEAL_MILLIS = 600
private const val ZERO_STROKE = 1
private const val ZERO_DOT_ON = 1
private const val ZERO_DOT_OFF = 3
