package com.callbackdev.saldo.feature.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.rememberMotionEnabled
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Vico 3.x chart wrappers for the statistics screen. Series carry amounts in
 * minor units (Long, exact in Double far beyond any realistic figure): the
 * Double values position pixels, every visible label goes back through
 * [MoneyMapper]/[MoneyFormatter], so money math never happens in
 * floating point.
 */

/**
 * Grouped monthly columns (one or two series) with month initials on the x
 * axis. [onSelectedIndexChange] reports the x index under the tap marker,
 * so the card can offer a drill-down for that month; the last index survives
 * the marker hiding on touch-up, keeping the drill-down actionable.
 */
@Composable
internal fun MonthlyBarsChart(
    series: List<BarSeries>,
    monthLabels: List<String>,
    currency: Currency,
    chartDescription: String,
    modifier: Modifier = Modifier,
    onSelectedIndexChange: ((Int?) -> Unit)? = null,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(series) {
        modelProducer.runTransaction {
            columnModel {
                series.forEach { series(it.valuesMinor) }
            }
        }
    }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        series.map { barSeries ->
                            // Pill columns: fully rounded caps read softer than
                            // the near-square corners of the default shape.
                            rememberLineComponent(
                                fill = Fill(barSeries.color),
                                thickness = COLUMN_THICKNESS,
                                shape = CircleShape,
                            )
                        },
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = compactMoneyAxisFormatter(currency),
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = indexLabelFormatter(monthLabels),
                    guideline = null,
                ),
                marker = moneyMarker(currency),
                markerVisibilityListener = onSelectedIndexChange?.let { callback ->
                    remember(callback) { markerIndexListener(callback) }
                },
            ),
            modelProducer = modelProducer,
            // The series ends on the current month: open there, not 12 months back.
            scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
            animateIn = rememberMotionEnabled(),
            modifier = modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                // The chart draws on a silent canvas: give TalkBack a summary.
                .semantics { contentDescription = chartDescription },
        )
    }
}

/**
 * Adapts marker visibility events to a plain "selected x index" callback.
 * The selection is deliberately retained on hide: Vico hides the marker as
 * soon as the finger lifts, and clearing there would dismiss the drill-down
 * button before it could ever be tapped.
 */
private fun markerIndexListener(
    onSelectedIndexChange: (Int?) -> Unit,
): CartesianMarkerVisibilityListener = object : CartesianMarkerVisibilityListener {
    override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
        onSelectedIndexChange(targets.firstOrNull()?.x?.toInt())
    }

    override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
        onSelectedIndexChange(targets.firstOrNull()?.x?.toInt())
    }

    override fun onHidden(marker: CartesianMarker) {
        // Keep the last selection: the month stays selected after touch-up.
    }
}

/** The end-of-month balance as a line with a soft area fill; can go below zero. */
@Composable
internal fun BalanceLineChart(
    valuesMinor: List<Long>,
    monthLabels: List<String>,
    currency: Currency,
    chartDescription: String,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(valuesMinor) {
        modelProducer.runTransaction {
            lineModel { series(valuesMinor) }
        }
    }
    val lineColor = MaterialTheme.colorScheme.primary
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
                            // Gradient area: strong under the line, fading out
                            // toward the bottom, matching the hero sparkline.
                            areaFill = LineCartesianLayer.AreaFill.single(
                                fill = Fill(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            lineColor.copy(alpha = AREA_ALPHA),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = compactMoneyAxisFormatter(currency),
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = indexLabelFormatter(monthLabels),
                    guideline = null,
                ),
                marker = moneyMarker(currency),
            ),
            modelProducer = modelProducer,
            // The series ends on the current month: open there, not 12 months back.
            scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
            animateIn = rememberMotionEnabled(),
            modifier = modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                // The chart draws on a silent canvas: give TalkBack a summary.
                .semantics { contentDescription = chartDescription },
        )
    }
}

/**
 * Category donut drawn with a plain Canvas (ADR 29): rounded slices separated
 * by small gaps, a clockwise sweep-in on entry, the period total as a Compose
 * overlay in the hole, and slice taps that open the same drill-down as the
 * share rows below. Replaces the experimental Vico 3.x pie API; the geometry
 * (angles, hit-testing) is pure and JVM-tested in DonutGeometry.
 */
@Composable
internal fun CategoryDonut(
    slices: List<CategorySlice>,
    centerAmount: String,
    centerLabel: String,
    chartDescription: String,
    modifier: Modifier = Modifier,
    onSliceClick: ((CategorySlice) -> Unit)? = null,
) {
    val arcs = remember(slices) {
        DonutGeometry.sliceAngles(slices.map { it.fraction }, gapDegrees = DONUT_GAP_DEGREES)
    }
    val colors = remember(slices) { slices.map { CategoryVisuals.color(it.category?.color) } }

    // One clockwise sweep-in per screen visit; a data change (period paging)
    // replays it, which reads as the chart redrawing for the new period.
    val motionEnabled = rememberMotionEnabled()
    val reveal = remember(slices) { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(slices) {
        if (reveal.value < 1f) {
            reveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = DONUT_REVEAL_MILLIS, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(DONUT_HEIGHT),
    ) {
        Canvas(
            modifier = Modifier
                .size(DONUT_HEIGHT)
                // The ring draws on a silent canvas: point TalkBack to the list.
                .semantics { contentDescription = chartDescription }
                .pointerInput(arcs, onSliceClick) {
                    if (onSliceClick == null) return@pointerInput
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val distance = (offset - center).getDistance()
                        val outer = minOf(center.x, center.y)
                        val inner = outer - DONUT_STROKE.toPx() - DONUT_TOUCH_SLACK.toPx()
                        if (distance in inner..(outer + DONUT_TOUCH_SLACK.toPx())) {
                            val angle = Math.toDegrees(
                                kotlin.math.atan2(
                                    (offset.y - center.y).toDouble(),
                                    (offset.x - center.x).toDouble(),
                                ),
                            ).toFloat()
                            DonutGeometry.sliceIndexAt(angle, arcs)?.let { index ->
                                slices.getOrNull(index)?.let(onSliceClick)
                            }
                        }
                    }
                },
        ) {
            val stroke = DONUT_STROKE.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val revealedBudget = reveal.value * FULL_TURN_DEGREES
            arcs.forEachIndexed { index, arc ->
                // Sequential reveal: a slice draws only the part of its sweep
                // that the clockwise budget has already reached.
                val offsetFromStart = arc.startAngle - DonutGeometry.START_ANGLE
                val drawnSweep = (revealedBudget - offsetFromStart).coerceIn(0f, arc.sweepAngle)
                if (drawnSweep > 0f) {
                    drawArc(
                        color = colors[index],
                        startAngle = arc.startAngle,
                        sweepAngle = drawnSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerAmount,
                style = MaterialTheme.typography.titleLarge.tabularNumbers(),
            )
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Maps x indexes (0-based) to the corresponding month label. */
@Composable
private fun indexLabelFormatter(labels: List<String>): CartesianValueFormatter =
    remember(labels) {
        CartesianValueFormatter { _, value, _ ->
            labels.getOrElse(value.toInt()) { "" }
        }
    }

/**
 * Axis labels for minor-unit values: localized, no decimals, thousands
 * compacted ("1.2k"). BigDecimal only; the Double is the exact minor amount.
 */
@Composable
private fun compactMoneyAxisFormatter(currency: Currency): CartesianValueFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(currency, locale) {
        CartesianValueFormatter { _, value, _ ->
            val amount = MoneyMapper.toAmount(value.toLong(), currency)
            val format = NumberFormat.getNumberInstance(locale)
            if (amount.abs() >= ONE_THOUSAND) {
                format.maximumFractionDigits = KILO_FRACTION_DIGITS
                format.format(
                    amount.divide(ONE_THOUSAND, KILO_FRACTION_DIGITS, RoundingMode.HALF_UP),
                ) + "k"
            } else {
                format.maximumFractionDigits = 0
                format.format(amount)
            }
        }
    }
}

/** Tap marker showing the tapped month's values as localized money. */
@Composable
private fun moneyMarker(currency: Currency): DefaultCartesianMarker {
    val formatter = remember(currency) { markerMoneyFormatter(currency) }
    return rememberDefaultCartesianMarker(
        label = rememberAxisLabelComponent(),
        valueFormatter = formatter,
    )
}

private fun markerMoneyFormatter(currency: Currency): DefaultCartesianMarker.ValueFormatter =
    DefaultCartesianMarker.ValueFormatter { _, targets ->
        targets.joinToString(separator = "  ") { target ->
            when (target) {
                is ColumnCartesianLayerMarkerTarget -> target.columns.joinToString(separator = "  ") {
                    formatMinor(it.entry.y, currency)
                }
                is LineCartesianLayerMarkerTarget -> target.points.joinToString(separator = "  ") {
                    formatMinor(it.entry.y, currency)
                }
                else -> ""
            }
        }
    }

private fun formatMinor(value: Double, currency: Currency): String =
    MoneyFormatter.format(MoneyMapper.toAmount(value.toLong(), currency), currency)

/** Month initial (localized, uppercase) for the x axis of the 12-month charts. */
internal fun monthInitial(month: java.time.YearMonth, locale: Locale): String =
    month.month.getDisplayName(java.time.format.TextStyle.NARROW, locale)

private val CHART_HEIGHT = 220.dp
private val DONUT_HEIGHT = 240.dp
private val DONUT_STROKE = 24.dp
private val DONUT_TOUCH_SLACK = 8.dp
private const val DONUT_GAP_DEGREES = 3f
private const val DONUT_REVEAL_MILLIS = 700
private const val FULL_TURN_DEGREES = 360f
private val COLUMN_THICKNESS = 16.dp
private const val AREA_ALPHA = 0.30f
private const val KILO_FRACTION_DIGITS = 1
private const val KILO = 1000L
private val ONE_THOUSAND = BigDecimal(KILO)

