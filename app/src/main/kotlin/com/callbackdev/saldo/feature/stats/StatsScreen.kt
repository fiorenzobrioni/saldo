package com.callbackdev.saldo.feature.stats

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.StatsSkeleton
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.feature.transactions.FilterDateRangeSheet
import com.callbackdev.saldo.feature.transactions.periodLabel as customPeriodLabel
import com.callbackdev.saldo.navigation.FilteredTransactionsRoute
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency

/**
 * Statistics: category ring, per-account spend and 12-month trends over the
 * primary currency (Phase 7). Aggregates are computed in SQL and exclude
 * transfers, adjustments, pending and excluded-from-stats movements; refunds
 * net the spend they refund.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateToFiltered: (FilteredTransactionsRoute) -> Unit,
    onNavigateToRecap: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.nav_stats)) },
                actions = {
                    IconButton(onClick = { onNavigateToRecap(recapMonthFor(uiState)) }) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = stringResource(R.string.stats_recap_action),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> StatsSkeleton(Modifier.padding(innerPadding))

            uiState.isEmpty -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                EmptyState(
                    icon = Icons.Outlined.Insights,
                    title = stringResource(R.string.stats_empty_title),
                    body = stringResource(R.string.stats_empty_body),
                    modifier = Modifier.weight(1f),
                )
                // The one empty screen that is not really empty: without this,
                // a period holding only foreign movements reads as "you
                // recorded nothing this month".
                if (uiState.showsOtherCurrencyNotice) {
                    OtherCurrencyNotice(
                        count = uiState.otherCurrencyCount,
                        currency = uiState.currency,
                        onClick = {
                            onNavigateToFiltered(
                                periodRoute(
                                    uiState.period,
                                    uiState.today,
                                    otherCurrenciesOnly = true,
                                ),
                            )
                        },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    )
                }
            }

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
                // Directly under the period selector: it qualifies the period
                // the whole screen is about, before any figure is read.
                if (uiState.showsOtherCurrencyNotice) {
                    item {
                        OtherCurrencyNotice(
                            count = uiState.otherCurrencyCount,
                            currency = uiState.currency,
                            onClick = {
                                onNavigateToFiltered(
                                    periodRoute(
                                        uiState.period,
                                        uiState.today,
                                        otherCurrenciesOnly = true,
                                    ),
                                )
                            },
                        )
                    }
                }
                item {
                    CategorySharesCard(
                        slices = uiState.slices,
                        total = uiState.periodSpendTotal,
                        currency = uiState.currency,
                        chart = {
                            CategoryDonut(
                                slices = uiState.slices,
                                centerAmount = MoneyFormatter.format(
                                    uiState.periodSpendTotal,
                                    uiState.currency,
                                ),
                                centerLabel = stringResource(R.string.stats_total_spent_label),
                                chartDescription = stringResource(R.string.stats_chart_ring_a11y),
                                // Same drill-down as the share rows below.
                                onSliceClick = { slice ->
                                    onNavigateToFiltered(
                                        periodRoute(
                                            period = uiState.period,
                                            today = uiState.today,
                                            categoryId = slice.category?.id,
                                            uncategorizedOnly = slice.category == null,
                                        ),
                                    )
                                },
                            )
                        },
                        onSliceClick = { slice ->
                            onNavigateToFiltered(
                                periodRoute(
                                    period = uiState.period,
                                    today = uiState.today,
                                    categoryId = slice.category?.id,
                                    uncategorizedOnly = slice.category == null,
                                ),
                            )
                        },
                    )
                }
                // Right under the donut, so the two period-driven cards sit together
                // before the fixed 12-month charts.
                item {
                    AccountSpendsCard(
                        spends = uiState.accountSpends,
                        currency = uiState.currency,
                        onAccountClick = { spend ->
                            onNavigateToFiltered(
                                periodRoute(uiState.period, uiState.today, accountId = spend.account.id),
                            )
                        },
                    )
                }
                item { ExpenseTrendCard(uiState, onNavigateToFiltered) }
                item { IncomeExpenseCard(uiState, onNavigateToFiltered) }
                item { BalanceHistoryCard(uiState) }
            }
        }
    }

    if (showRangePicker) {
        val currentRange = (uiState.period as? StatsPeriod.Custom)
        FilterDateRangeSheet(
            initialStart = currentRange?.start,
            initialEnd = currentRange?.end,
            today = uiState.today,
            showClear = uiState.period is StatsPeriod.Custom,
            onApply = { start, end ->
                viewModel.selectCustomRange(start, end)
                showRangePicker = false
            },
            // Stats has no "no period": clearing a custom range returns to the
            // default month view.
            onClear = {
                viewModel.selectMonthMode()
                showRangePicker = false
            },
            onDismiss = { showRangePicker = false },
        )
    }
}

/** Column chart of the last 12 months' spend, with a per-month drill-down. */
@Composable
private fun ExpenseTrendCard(
    uiState: StatsUiState,
    onNavigateToFiltered: (FilteredTransactionsRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsCard(title = stringResource(R.string.stats_trend_title), modifier = modifier) {
        if (uiState.isTrendEmpty) {
            NoPeriodData()
            return@StatsCard
        }
        var selectedIndex by remember { mutableStateOf<Int?>(null) }
        MonthlyBarsChart(
            series = listOf(
                BarSeries(
                    valuesMinor = uiState.monthlyTotals.map {
                        MoneyMapper.toMinorUnits(it.expense, uiState.currency)
                    },
                    color = MaterialTheme.colorScheme.primary,
                ),
            ),
            monthLabels = monthLabels(uiState),
            currency = uiState.currency,
            chartDescription = stringResource(R.string.stats_chart_trend_a11y),
            onSelectedIndexChange = { selectedIndex = it },
        )
        MonthDrillDownButton(
            month = selectedIndex?.let { uiState.monthlyTotals.getOrNull(it)?.month },
            onNavigateToFiltered = onNavigateToFiltered,
        )
    }
}

/** Grouped columns comparing monthly income and expenses, with a legend. */
@Composable
private fun IncomeExpenseCard(
    uiState: StatsUiState,
    onNavigateToFiltered: (FilteredTransactionsRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsCard(title = stringResource(R.string.stats_income_expense_title), modifier = modifier) {
        if (uiState.isTrendEmpty) {
            NoPeriodData()
            return@StatsCard
        }
        val incomeColor = MaterialTheme.moneyColors.income
        val expenseColor = MaterialTheme.moneyColors.expense
        var selectedIndex by remember { mutableStateOf<Int?>(null) }
        MonthlyBarsChart(
            series = listOf(
                BarSeries(
                    valuesMinor = uiState.monthlyTotals.map {
                        MoneyMapper.toMinorUnits(it.income, uiState.currency)
                    },
                    color = incomeColor,
                ),
                BarSeries(
                    valuesMinor = uiState.monthlyTotals.map {
                        MoneyMapper.toMinorUnits(it.expense, uiState.currency)
                    },
                    color = expenseColor,
                ),
            ),
            monthLabels = monthLabels(uiState),
            currency = uiState.currency,
            chartDescription = stringResource(R.string.stats_chart_income_expense_a11y),
            onSelectedIndexChange = { selectedIndex = it },
        )
        Spacer(Modifier.height(8.dp))
        ChartLegend(
            entries = listOf(
                incomeColor to stringResource(R.string.dashboard_stat_incomes),
                expenseColor to stringResource(R.string.dashboard_stat_expenses),
            ),
        )
        MonthDrillDownButton(
            month = selectedIndex?.let { uiState.monthlyTotals.getOrNull(it)?.month },
            onNavigateToFiltered = onNavigateToFiltered,
        )
    }
}

/**
 * The month the recap toolbar action opens: the displayed month when the
 * period selector sits on a completed month, otherwise the last completed
 * one. The current month is never offered (its figures still change daily),
 * so any past month is reachable by paging the selector first.
 */
private fun recapMonthFor(uiState: StatsUiState): YearMonth {
    val currentMonth = YearMonth.from(uiState.today)
    val selected = (uiState.period as? StatsPeriod.Month)?.month
    return if (selected != null && selected < currentMonth) selected else currentMonth.minusMonths(1)
}

/**
 * "View transactions for <month>" under a column chart, visible from the
 * first tap on a month onward: the selection outlives the transient tap
 * marker, which hides on touch-up. An explicit button rather than navigating
 * on tap, so scrubbing the chart never leaves the screen by accident.
 */
@Composable
private fun MonthDrillDownButton(
    month: YearMonth?,
    onNavigateToFiltered: (FilteredTransactionsRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (month == null) return
    val locale = LocalConfiguration.current.locales[0]
    val label = remember(month, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
        month.atDay(1).format(DateTimeFormatter.ofPattern(pattern, locale))
            .withLocaleDateCasing(locale)
    }
    TextButton(
        onClick = {
            onNavigateToFiltered(
                FilteredTransactionsRoute(
                    startEpochDay = month.atDay(1).toEpochDay(),
                    endEpochDayExclusive = month.plusMonths(1).atDay(1).toEpochDay(),
                    statsScope = true,
                ),
            )
        },
        modifier = modifier,
    ) {
        Text(stringResource(R.string.stats_view_movements, label))
    }
}

/** The route covering the selected period, optionally narrowed to one entity. */
private fun periodRoute(
    period: StatsPeriod,
    today: LocalDate,
    categoryId: Long? = null,
    accountId: Long? = null,
    uncategorizedOnly: Boolean = false,
    otherCurrenciesOnly: Boolean = false,
): FilteredTransactionsRoute {
    val range = period.dateRange(today)
    return FilteredTransactionsRoute(
        startEpochDay = range.start.toEpochDay(),
        endEpochDayExclusive = range.endInclusive.plusDays(1).toEpochDay(),
        categoryId = categoryId,
        accountId = accountId,
        statsScope = true,
        uncategorizedOnly = uncategorizedOnly,
        otherCurrenciesOnly = otherCurrenciesOnly,
    )
}

/**
 * Every figure on this screen is scoped to one currency, so a period that also
 * holds foreign movements is being under-reported. This says so in one line and
 * hands over the list of what is missing, rather than leaving the user to work
 * out why the totals feel short.
 *
 * Deliberately a quiet informational row, not a warning: nothing is wrong, the
 * charts simply cannot add up two currencies until conversion exists.
 */
@Composable
private fun OtherCurrencyNotice(
    count: Int,
    currency: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CurrencyExchange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.stats_other_currencies_notice,
                    count,
                    count,
                    currency.currencyCode,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Line chart of the end-of-month total balance over the last 12 months. */
@Composable
private fun BalanceHistoryCard(uiState: StatsUiState, modifier: Modifier = Modifier) {
    StatsCard(title = stringResource(R.string.stats_balance_title), modifier = modifier) {
        if (uiState.balanceHistory.isEmpty()) {
            NoPeriodData()
        } else {
            val locale = LocalConfiguration.current.locales[0]
            BalanceLineChart(
                valuesMinor = uiState.balanceHistory.map {
                    MoneyMapper.toMinorUnits(it.balance, uiState.currency)
                },
                monthLabels = remember(uiState.balanceHistory, locale) {
                    uiState.balanceHistory.map { monthInitial(it.month, locale) }
                },
                currency = uiState.currency,
                chartDescription = stringResource(R.string.stats_chart_balance_a11y),
            )
        }
    }
}

/** Localized single-letter month labels for the 12-month x axes. */
@Composable
private fun monthLabels(uiState: StatsUiState): List<String> {
    val locale = LocalConfiguration.current.locales[0]
    return remember(uiState.monthlyTotals, locale) {
        uiState.monthlyTotals.map { monthInitial(it.month, locale) }
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
                text = periodLabel(period, today),
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

/**
 * Localized label of the selected period, normalized to the locale's own
 * casing. Custom ranges reuse the movements filter's label so an open-ended
 * period reads the same way there and here ("Dal 5 lug", "Fino al 5 lug").
 */
@Composable
private fun periodLabel(period: StatsPeriod, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (period) {
        is StatsPeriod.Month -> remember(period, locale) {
            val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
            period.month.atDay(1).format(DateTimeFormatter.ofPattern(pattern, locale))
                .withLocaleDateCasing(locale)
        }
        is StatsPeriod.Year -> period.year.toString()
        is StatsPeriod.Custom ->
            customPeriodLabel(period.start, period.end, today)
                ?: stringResource(R.string.stats_period_custom)
    }
}
