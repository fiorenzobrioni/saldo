@file:Suppress("TooManyFunctions") // One small composable per card/section/state.

package com.callbackdev.saldo.feature.upcoming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.ListSkeleton
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.UpcomingLedger
import com.callbackdev.saldo.navigation.UpcomingRoute
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/**
 * What is coming (ADR 36): confirmed movements dated ahead and occurrences
 * still to confirm, in one list grouped by day. A hero keeps the two totals
 * apart, "going out" and "coming in", for the same reason the credits screen
 * does: netting them would hide two different situations behind one number.
 *
 * The confirmation queue is a filter of this list, not a screen of its own.
 * The two were always the same movements, and showing them in two places -
 * reached from two cards of the same dashboard - is how an app grows a second
 * answer to a question it had already answered.
 *
 * Tapping a pending row opens the confirmation sheet; tapping anything else
 * opens the movement in the editor, where a date or a reminder is changed like
 * on any other movement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(
    route: UpcomingRoute,
    onNavigateBack: () -> Unit,
    onNavigateToTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpcomingViewModel = hiltViewModel<UpcomingViewModel, UpcomingViewModel.Factory>(
        creationCallback = { factory -> factory.create(route) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmTarget by remember { mutableStateOf<UpcomingItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                UpcomingEvent.WriteFailed ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.editor_write_failed))
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.upcoming_title)) },
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
            uiState.isLoading -> ListSkeleton(modifier = Modifier.padding(innerPadding))

            uiState.isEmpty -> EmptyState(
                icon = Icons.Outlined.EventAvailable,
                title = stringResource(R.string.upcoming_empty_title),
                body = stringResource(R.string.upcoming_empty_body),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> UpcomingContent(
                uiState = uiState,
                onFilterSelected = viewModel::onFilterSelected,
                onItemClick = { item ->
                    if (item.isPending) confirmTarget = item else onNavigateToTransaction(item.id)
                },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }

    confirmTarget?.let { target ->
        ConfirmSheet(
            item = target,
            today = uiState.today,
            onConfirm = { magnitude ->
                viewModel.confirm(target.transaction, magnitude)
                confirmTarget = null
            },
            onSkip = {
                viewModel.skip(target.transaction)
                confirmTarget = null
            },
            onDismiss = { confirmTarget = null },
        )
    }
}

@Composable
private fun UpcomingContent(
    uiState: UpcomingUiState,
    onFilterSelected: (UpcomingFilter) -> Unit,
    onItemClick: (UpcomingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(uiState.items) { uiState.items.groupBy { it.date }.toList() }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        item(key = "summary") { UpcomingSummaryCard(ledger = uiState.ledger) }
        if (uiState.showFilters) {
            item(key = "filters") {
                UpcomingFilterRow(
                    selected = uiState.filter,
                    pendingCount = uiState.ledger.pendingCount,
                    onSelected = onFilterSelected,
                )
            }
        }
        if (uiState.isFilteredEmpty) {
            item(key = "filtered-empty") {
                AllConfirmedState(modifier = Modifier.fillMaxWidth().padding(top = 32.dp))
            }
        }
        groups.forEach { (date, items) ->
            item(key = "header-${date.toEpochDay()}") {
                DayHeader(date = date, today = uiState.today)
            }
            item(key = "group-${date.toEpochDay()}") {
                UpcomingGroupCard(items = items, onItemClick = onItemClick)
            }
        }
    }
}

/**
 * The two totals side by side, never netted, plus the line that says how much
 * of the list is still waiting for an answer. Transfers are in neither figure:
 * moving money between one's own accounts is not something coming in or going
 * out.
 */
@Composable
private fun UpcomingSummaryCard(ledger: UpcomingLedger, modifier: Modifier = Modifier) {
    SaldoCard(shape = MaterialTheme.shapes.extraLarge, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SaldoDimens.cardPaddingLarge)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SummaryHalf(
                    label = stringResource(R.string.upcoming_outgoing),
                    amount = ledger.outgoing,
                    currency = ledger.currency,
                    color = MaterialTheme.moneyColors.expense,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.height(SummaryDividerHeight),
                )
                SummaryHalf(
                    label = stringResource(R.string.upcoming_incoming),
                    amount = ledger.incoming,
                    currency = ledger.currency,
                    color = MaterialTheme.moneyColors.income,
                    modifier = Modifier.weight(1f),
                )
            }
            // A figure still to confirm is not a promise: say so, instead of
            // letting the totals above read as settled.
            if (ledger.pendingCount > 0) {
                SummaryNote(
                    text = pluralStringResource(
                        R.plurals.upcoming_pending_note,
                        ledger.pendingCount,
                        ledger.pendingCount,
                    ),
                )
            }
            if (ledger.hasOtherCurrencies) {
                SummaryNote(text = stringResource(R.string.upcoming_other_currencies))
            }
        }
    }
}

@Composable
private fun SummaryHalf(
    label: String,
    amount: BigDecimal,
    currency: Currency,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = MoneyFormatter.format(amount, currency),
            style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            // A zero total is not news: it stays quiet instead of shouting a color.
            color = if (amount.signum() > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SummaryNote(text: String, modifier: Modifier = Modifier) {
    Spacer(Modifier.height(10.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun UpcomingFilterRow(
    selected: UpcomingFilter,
    pendingCount: Int,
    onSelected: (UpcomingFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selected == UpcomingFilter.ALL,
            onClick = { onSelected(UpcomingFilter.ALL) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text(stringResource(R.string.upcoming_filter_all))
        }
        SegmentedButton(
            selected = selected == UpcomingFilter.PENDING,
            onClick = { onSelected(UpcomingFilter.PENDING) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text(stringResource(R.string.upcoming_filter_pending, pendingCount))
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, modifier: Modifier = Modifier) {
    Text(
        text = upcomingDayLabel(date, today),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun UpcomingGroupCard(
    items: List<UpcomingItem>,
    onItemClick: (UpcomingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(modifier = modifier.fillMaxWidth()) {
        Column {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = SaldoDimens.rowPaddingHorizontal),
                    )
                }
                Surface(onClick = { onItemClick(item) }, color = Color.Transparent) {
                    UpcomingRowContent(
                        item = item,
                        modifier = Modifier.padding(
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
 * Nothing left to confirm: reached by clearing the queue, not by never having
 * had one, so it keeps the totals above it instead of taking over the screen.
 */
@Composable
private fun AllConfirmedState(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.TaskAlt,
        title = stringResource(R.string.pending_empty_title),
        body = stringResource(R.string.pending_empty_body),
        modifier = modifier,
    )
}

private val SummaryDividerHeight = 40.dp
