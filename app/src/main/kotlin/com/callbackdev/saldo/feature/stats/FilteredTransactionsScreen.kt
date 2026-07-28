package com.callbackdev.saldo.feature.stats

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.feature.transactions.FilteredTotalsBar
import com.callbackdev.saldo.feature.transactions.TransactionDayGroup
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import com.callbackdev.saldo.feature.transactions.TransactionRowContent
import com.callbackdev.saldo.feature.transactions.dayLabel
import com.callbackdev.saldo.navigation.FilteredTransactionsRoute
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Statistics drill-down: the movements behind a tapped chart element, grouped
 * by day like the ledger, read-only rows (tap opens the editor, no swipe) and
 * the filtered totals bar always in view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredTransactionsScreen(
    route: FilteredTransactionsRoute,
    onNavigateBack: () -> Unit,
    onNavigateToTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FilteredTransactionsViewModel =
        hiltViewModel<FilteredTransactionsViewModel, FilteredTransactionsViewModel.Factory>(
            creationCallback = { factory -> factory.create(route) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val periodLabel = drillDownPeriodLabel(route)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column {
                        Text(
                            uiState.title ?: stringResource(
                                when {
                                    uiState.isUncategorized -> R.string.transaction_uncategorized
                                    uiState.isOtherCurrencies -> R.string.stats_other_currencies_title
                                    else -> R.string.nav_transactions
                                },
                            ),
                        )
                        Text(
                            text = periodLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(innerPadding))

            uiState.isEmpty -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.filter_no_results_title),
                body = stringResource(R.string.stats_no_data_period),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                FilteredTotalsBar(totals = uiState.totals, count = uiState.count)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 24.dp,
                    ),
                ) {
                    items(uiState.days, key = { it.date }) { day ->
                        FilteredDayGroup(
                            day = day,
                            today = uiState.today,
                            onItemClick = { onNavigateToTransaction(it.id) },
                            modifier = Modifier.padding(bottom = SaldoDimens.cardSpacing),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilteredDayGroup(
    day: TransactionDayGroup,
    today: LocalDate,
    onItemClick: (TransactionListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp),
        ) {
            Text(
                text = dayLabel(day.date, today),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = day.totals.joinToString(separator = "  ") { total ->
                    MoneyFormatter.formatSigned(total.amount, total.currency)
                },
                style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        SaldoCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                day.items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    TransactionRowContent(
                        item = item,
                        modifier = Modifier
                            .clickable(onClick = { onItemClick(item) })
                            .padding(
                                horizontal = SaldoDimens.rowPaddingHorizontal,
                                vertical = SaldoDimens.rowPaddingVertical,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Human label of the route's window: the month or year name when the window
 * is exactly one, a short date range otherwise. Locale casing is kept. A route
 * without bounds (a counterparty's history) says so instead.
 */
@Composable
private fun drillDownPeriodLabel(route: FilteredTransactionsRoute): String {
    val locale = LocalConfiguration.current.locales[0]
    val start = route.startEpochDay
    val endExclusive = route.endEpochDayExclusive
    if (start == null || endExclusive == null) {
        return stringResource(R.string.filtered_all_time)
    }
    return remember(route, locale) {
        formatWindow(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(endExclusive - 1), locale)
    }
}

private fun formatWindow(start: LocalDate, endInclusive: LocalDate, locale: Locale): String {
    val month = YearMonth.from(start)
    val isFullMonth = start.dayOfMonth == 1 &&
        YearMonth.from(endInclusive) == month &&
        endInclusive == month.atEndOfMonth()
    val isFullYear = start.dayOfYear == 1 &&
        endInclusive.year == start.year &&
        endInclusive == start.withDayOfYear(start.lengthOfYear())
    return when {
        isFullMonth -> {
            val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
            start.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
        isFullYear -> start.year.toString()
        else -> {
            val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMy")
            val formatter = DateTimeFormatter.ofPattern(pattern, locale)
            "${start.format(formatter)} - ${endInclusive.format(formatter)}"
        }
    }.withLocaleDateCasing(locale)
}
