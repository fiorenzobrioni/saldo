package com.callbackdev.saldo.feature.stats

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.feature.transactions.FilterDateRangePickerDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Statistics: category ring, per-account spend and 12-month trends over the
 * primary currency (Phase 7). Aggregates are computed in SQL and exclude
 * transfers, adjustments, pending and excluded-from-stats movements; refunds
 * net the spend they refund.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_stats)) })
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(innerPadding))

            uiState.isEmpty -> EmptyState(
                icon = Icons.Outlined.Insights,
                title = stringResource(R.string.stats_empty_title),
                body = stringResource(R.string.stats_empty_body),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
            ) {
                item {
                    PeriodSelector(
                        period = uiState.period,
                        today = uiState.today,
                        onSelectMonthMode = viewModel::selectMonthMode,
                        onSelectYearMode = viewModel::selectYearMode,
                        onRequestCustomRange = { showRangePicker = true },
                        onPrevious = viewModel::previousPeriod,
                        onNext = viewModel::nextPeriod,
                    )
                }
                item {
                    CategorySharesCard(
                        slices = uiState.slices,
                        total = uiState.periodSpendTotal,
                        currency = uiState.currency,
                    )
                }
                item {
                    AccountSpendsCard(
                        spends = uiState.accountSpends,
                        currency = uiState.currency,
                    )
                }
            }
        }
    }

    if (showRangePicker) {
        val currentRange = (uiState.period as? StatsPeriod.Custom)
        FilterDateRangePickerDialog(
            initialStart = currentRange?.start,
            initialEnd = currentRange?.end,
            onConfirm = { start, end ->
                viewModel.selectCustomRange(start, end)
                showRangePicker = false
            },
            onDismiss = { showRangePicker = false },
        )
    }
}

/**
 * Mode toggle (month/year/custom) plus previous/next stepping around the
 * period label. Custom ranges have no natural neighbours, so the chevrons
 * only step months and years; the forward chevron stops at the present.
 */
@Composable
private fun PeriodSelector(
    period: StatsPeriod,
    today: LocalDate,
    onSelectMonthMode: () -> Unit,
    onSelectYearMode: () -> Unit,
    onRequestCustomRange: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = period is StatsPeriod.Month,
                onClick = onSelectMonthMode,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = MODE_COUNT),
            ) {
                Text(stringResource(R.string.stats_period_month))
            }
            SegmentedButton(
                selected = period is StatsPeriod.Year,
                onClick = onSelectYearMode,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = MODE_COUNT),
            ) {
                Text(stringResource(R.string.stats_period_year))
            }
            SegmentedButton(
                selected = period is StatsPeriod.Custom,
                onClick = onRequestCustomRange,
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = MODE_COUNT),
            ) {
                Text(stringResource(R.string.stats_period_custom))
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onPrevious, enabled = period !is StatsPeriod.Custom) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.stats_previous_period),
                )
            }
            Text(
                text = periodLabel(period),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onNext,
                enabled = period !is StatsPeriod.Custom && !period.isAtPresent(today),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.stats_next_period),
                )
            }
        }
    }
}

private const val MODE_COUNT = 3

/** Localized label of the selected period, keeping the locale's own casing. */
@Composable
private fun periodLabel(period: StatsPeriod): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(period, locale) {
        when (period) {
            is StatsPeriod.Month -> {
                val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
                period.month.atDay(1).format(DateTimeFormatter.ofPattern(pattern, locale))
            }
            is StatsPeriod.Year -> period.year.toString()
            is StatsPeriod.Custom -> {
                val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMy")
                val formatter = DateTimeFormatter.ofPattern(pattern, locale)
                "${period.start.format(formatter)} - ${period.end.format(formatter)}"
            }
        }
    }
}
