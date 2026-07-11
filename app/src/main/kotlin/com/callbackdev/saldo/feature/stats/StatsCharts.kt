package com.callbackdev.saldo.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
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
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
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
 * axis. [onSelectedIndexChange] reports the x index under the tap marker
 * (null when it hides), so the card can offer a drill-down for that month.
 */
@Composable
internal fun MonthlyBarsChart(
    series: List<BarSeries>,
    monthLabels: List<String>,
    currency: Currency,
    modifier: Modifier = Modifier,
    onSelectedIndexChange: ((Int?) -> Unit)? = null,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(series) {
        modelProducer.runTransaction {
            columnSeries {
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
                            rememberLineComponent(
                                fill = Fill(barSeries.color),
                                thickness = COLUMN_THICKNESS,
                                shape = MaterialTheme.shapes.extraSmall,
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
            modifier = modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT),
        )
    }
}

/** Adapts marker visibility events to a plain "selected x index" callback. */
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
        onSelectedIndexChange(null)
    }
}

/** The end-of-month balance as a line with a soft area fill; can go below zero. */
@Composable
internal fun BalanceLineChart(
    valuesMinor: List<Long>,
    monthLabels: List<String>,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(valuesMinor) {
        modelProducer.runTransaction {
            lineSeries { series(valuesMinor) }
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
                            areaFill = LineCartesianLayer.AreaFill.single(
                                fill = Fill(lineColor.copy(alpha = AREA_ALPHA)),
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
            modifier = modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT),
        )
    }
}

/**
 * Category donut: Vico pie (experimental in 3.x) with the period total as a
 * Compose overlay in the hole. Slice taps are not exposed by the API, so the
 * drill-down lives on the share list rows below the chart.
 */
@Composable
internal fun CategoryDonut(
    slices: List<CategorySlice>,
    centerAmount: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { PieChartModelProducer() }
    LaunchedEffect(slices) {
        modelProducer.runTransaction {
            pieSeries { series(slices.map { it.amount }) }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(DONUT_HEIGHT),
    ) {
        PieChartHost(
            chart = rememberPieChart(
                sliceProvider = PieChart.SliceProvider.series(
                    slices.map { slice ->
                        PieChart.Slice(fill = Fill(CategoryVisuals.color(slice.category.color)))
                    },
                ),
                innerSize = PieSize.Inner.fixed(DONUT_HOLE),
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(DONUT_HEIGHT),
        )
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
private val DONUT_HOLE = 76.dp
private val COLUMN_THICKNESS = 12.dp
private const val AREA_ALPHA = 0.25f
private const val KILO_FRACTION_DIGITS = 1
private const val KILO = 1000L
private val ONE_THOUSAND = BigDecimal(KILO)

