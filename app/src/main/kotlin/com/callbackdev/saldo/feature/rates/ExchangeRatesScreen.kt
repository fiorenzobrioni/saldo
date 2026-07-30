package com.callbackdev.saldo.feature.rates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.AmountKeypadHost
import com.callbackdev.saldo.core.designsystem.component.AmountTarget
import com.callbackdev.saldo.core.designsystem.component.HeroAmountField
import com.callbackdev.saldo.core.designsystem.component.InfoBanner
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The exchange-rates board (ADR 40): every downloaded ECB currency against
 * the primary currency, with the change since the previous publication and a
 * sparkline of the recent published samples. Read-only: the cache updates
 * itself through the sync policy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeRatesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExchangeRatesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = keypadOpen) { keypadOpen = false }
    val converterTarget = uiState.converter?.let { converter ->
        val inputCurrency = if (converter.reversed) uiState.base else converter.currency
        AmountTarget(
            value = converter.input,
            fractionDigits = MoneyMapper.fractionDigits(inputCurrency),
            allowNegative = false,
            onValueChange = viewModel::onConverterInputChanged,
        )
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.rates_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            AmountKeypadHost(
                target = converterTarget.takeIf { keypadOpen },
                onHide = { keypadOpen = false },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Spacer(Modifier.padding(innerPadding))

            uiState.isEmpty -> RatesEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
            )

            else -> RatesList(
                uiState = uiState,
                converterTarget = converterTarget,
                converterActive = keypadOpen,
                onActivateConverter = { keypadOpen = true },
                onConverterCurrencySelected = viewModel::onConverterCurrencySelected,
                onConverterSwapped = viewModel::onConverterSwapped,
                onRowClick = { viewModel.onDetailSelected(it.currency) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    uiState.detail?.let { detail ->
        RateDetailSheet(
            detail = detail,
            base = uiState.base,
            onDismiss = { viewModel.onDetailSelected(null) },
        )
    }
}

@Suppress("LongParameterList") // One callback per converter interaction.
@Composable
private fun RatesList(
    uiState: ExchangeRatesUiState,
    converterTarget: AmountTarget?,
    converterActive: Boolean,
    onActivateConverter: () -> Unit,
    onConverterCurrencySelected: (Currency) -> Unit,
    onConverterSwapped: () -> Unit,
    onRowClick: (RateRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        item(key = "header") {
            RatesHeaderCard(uiState)
        }
        val converter = uiState.converter
        if (converter != null && converterTarget != null) {
            item(key = "converter") {
                ConverterCard(
                    converter = converter,
                    base = uiState.base,
                    target = converterTarget,
                    isActive = converterActive,
                    onActivate = onActivateConverter,
                    onCurrencySelected = onConverterCurrencySelected,
                    onSwapped = onConverterSwapped,
                )
            }
        }
        if (!uiState.conversionEnabled) {
            item(key = "conversion-off") {
                InfoBanner(stringResource(R.string.rates_conversion_off_note))
            }
        }
        if (uiState.yourRows.isNotEmpty()) {
            item(key = "yours-header") {
                RatesSectionHeader(stringResource(R.string.rates_section_yours))
            }
            items(uiState.yourRows, key = { "y-${it.currency.currencyCode}" }) { row ->
                RateRowCard(row, onClick = { onRowClick(row) })
            }
        }
        if (uiState.otherRows.isNotEmpty()) {
            item(key = "others-header") {
                RatesSectionHeader(stringResource(R.string.rates_section_others))
            }
            items(uiState.otherRows, key = { "o-${it.currency.currencyCode}" }) { row ->
                RateRowCard(row, onClick = { onRowClick(row) })
            }
        }
        item(key = "footer") {
            Text(
                text = stringResource(R.string.rates_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * The quick converter (ADR 40): an amount in the selected currency and its
 * countervalue in the base at the latest known rate, with a swap for the
 * opposite direction. Default direction is the abroad use case: a price seen
 * in the local currency, read in yours.
 */
@Suppress("LongParameterList") // One callback per interaction.
@Composable
private fun ConverterCard(
    converter: ConverterUiState,
    base: Currency,
    target: AmountTarget,
    isActive: Boolean,
    onActivate: () -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    onSwapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val from = if (converter.reversed) base else converter.currency
    val to = if (converter.reversed) converter.currency else base
    SaldoCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPaddingLarge,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.rates_converter_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSwapped) {
                    Icon(
                        imageVector = Icons.Outlined.SwapVert,
                        contentDescription = stringResource(R.string.rates_converter_swap),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroAmountField(
                    target = target,
                    currencySymbol = from.currencyCode,
                    isError = false,
                    isActive = isActive,
                    onActivate = onActivate,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                // The dropdown always drives the foreign leg, whichever side
                // of the conversion it currently sits on.
                CurrencyChoiceChip(
                    selected = converter.currency,
                    options = converter.options,
                    onSelected = onCurrencySelected,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = converter.result?.let { MoneyFormatter.formatApprox(it, to, locale) }
                    ?: MoneyFormatter.formatApprox(BigDecimal.ZERO, to, locale),
                style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
                color = if (converter.result != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** The foreign-currency selector: current code plus a dropdown of the board. */
@Composable
private fun CurrencyChoiceChip(
    selected: Currency,
    options: List<Currency>,
    onSelected: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { open = true }
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = selected.currencyCode,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = stringResource(R.string.rates_converter_pick_currency),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val locale = LocalConfiguration.current.locales[0]
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { currency ->
                DropdownMenuItem(
                    text = {
                        Text("${currency.currencyCode} - ${currency.displayNameIn(locale)}")
                    },
                    onClick = {
                        onSelected(currency)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * Detail of one currency: a larger chart of the published samples over the
 * chosen period, with minimum, maximum, latest value and the change across
 * the shown window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RateDetailSheet(
    detail: RateDetailUiState,
    base: Currency,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    var months by rememberSaveable { mutableStateOf(1) }
    val lastDay = detail.points.last().day
    val shown = detail.points.filter { it.day >= lastDay.minusMonths(months.toLong()) }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = detail.currency.currencyCode,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail.currency.displayNameIn(locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.rates_detail_subtitle,
                    base.currencyCode,
                    rateValueText(shown.last().value, locale),
                    detail.currency.currencyCode,
                ),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            )
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(1, 3).forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = months == option,
                        onClick = { months = option },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    ) {
                        Text(
                            stringResource(
                                if (option == 1) R.string.rates_detail_1m else R.string.rates_detail_3m,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            RateSparkline(
                history = shown.map { it.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DETAIL_CHART_HEIGHT),
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = shown.first().day
                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = shown.last().day
                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            DetailStatRow(
                label = stringResource(R.string.rates_detail_min),
                value = rateValueText(shown.minOf { it.value }, locale),
            )
            DetailStatRow(
                label = stringResource(R.string.rates_detail_max),
                value = rateValueText(shown.maxOf { it.value }, locale),
            )
            val first = shown.first().value
            val change = shown.last().value.subtract(first)
                .takeIf { first.signum() != 0 }
                ?.divide(first, CHANGE_SCALE, RoundingMode.HALF_UP)
            change?.let {
                DetailStatRow(
                    label = stringResource(R.string.rates_detail_change),
                    value = changeText(it, locale),
                    valueColor = changeColor(it),
                )
            }
        }
    }
}

@Composable
private fun DetailStatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            color = valueColor,
        )
    }
}

/** Hero: what the rows are quoted against, and how fresh the board is. */
@Composable
private fun RatesHeaderCard(uiState: ExchangeRatesUiState, modifier: Modifier = Modifier) {
    SaldoCard(shape = MaterialTheme.shapes.extraLarge, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(SaldoDimens.cardPaddingLarge),
        ) {
            Icon(
                imageVector = Icons.Outlined.CurrencyExchange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.rates_header, uiState.base.currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                uiState.latestDay?.let { day ->
                    Text(
                        text = stringResource(
                            R.string.rates_updated,
                            day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RatesSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

/** One currency: code and name, sparkline of the published samples, value and change. */
@Composable
private fun RateRowCard(
    row: RateRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    SaldoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(
                    horizontal = SaldoDimens.cardPadding,
                    vertical = SaldoDimens.cardPaddingVertical,
                )
                .semantics(mergeDescendants = true) {},
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.currency.currencyCode,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = row.currency.displayNameIn(locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RateSparkline(
                history = row.history,
                modifier = Modifier.size(width = SPARKLINE_WIDTH, height = SPARKLINE_HEIGHT),
            )
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = rateValueText(row.perBase, locale),
                    style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
                    fontWeight = FontWeight.Medium,
                )
                row.changeFraction?.let { change ->
                    Text(
                        text = changeText(change, locale),
                        style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                        color = changeColor(change),
                    )
                }
            }
        }
    }
}

/**
 * Tiny polyline of the published samples, min-max normalized. Decorative: the
 * value and its signed change carry the information for screen readers, and
 * the trend is never conveyed by color alone (the change has an explicit sign).
 */
@Composable
private fun RateSparkline(history: List<BigDecimal>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (history.size < 2) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = SPARKLINE_STROKE.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }
        val values = history.map { it.toFloat() }
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0f }
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * step
            // A flat series draws as a mid line instead of dividing by zero.
            val y = if (span == null) {
                size.height / 2f
            } else {
                size.height - ((value - min) / span) * size.height
            }
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = SPARKLINE_STROKE.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun RatesEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.CurrencyExchange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.rates_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.rates_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "dollaro statunitense" with a leading capital, in the UI locale. */
private fun java.util.Currency.displayNameIn(locale: Locale): String =
    getDisplayName(locale).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(locale) else it.toString()
    }

/** The rate value with 2 to 4 decimals: enough for GBP, not drowning IDR. */
private fun rateValueText(value: BigDecimal, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 4
    }.format(value)

/** Signed percent change against the previous publication, e.g. "+0,12%". */
private fun changeText(change: BigDecimal, locale: Locale): String {
    val formatted = NumberFormat.getPercentInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(change)
    return if (change.signum() > 0) "+$formatted" else formatted
}

@Composable
private fun changeColor(change: BigDecimal): Color = when {
    change.signum() > 0 -> MaterialTheme.moneyColors.income
    change.signum() < 0 -> MaterialTheme.moneyColors.expense
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val SPARKLINE_WIDTH = 64.dp
private val SPARKLINE_HEIGHT = 28.dp
private val SPARKLINE_STROKE = 2.dp
private val DETAIL_CHART_HEIGHT = 160.dp

/** Same scale the board uses for its own change fraction. */
private const val CHANGE_SCALE = 6
