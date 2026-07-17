package com.callbackdev.saldo.feature.transactions

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.ListSkeleton
import com.callbackdev.saldo.core.designsystem.component.SaldoCardDefaults
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.feature.transactions.export.CsvExportSheet
import java.time.LocalDate

/**
 * Movement ledger: all movements grouped by day (per-movement offset, ADR 7)
 * with daily net totals, swipe-to-delete with undo, tap-to-edit, plus search
 * and combinable filters with an always-visible filtered total (Phase 7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onNavigateToNewTransaction: () -> Unit,
    onNavigateToEditTransaction: (Long) -> Unit,
    onNavigateToAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val csvSeparator by viewModel.csvSeparator.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel, resources, context) {
        viewModel.events.collect { event ->
            handleTransactionsEvent(event, viewModel, snackbarHostState, resources, context)
        }
    }

    val closeSearch = {
        viewModel.setQuery("")
        isSearching = false
    }
    BackHandler(enabled = isSearching, onBack = closeSearch)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            // A brief crossfade instead of a hard swap: the only bar change
            // in the app that would otherwise cut without a transition.
            AnimatedContent(
                targetState = isSearching,
                transitionSpec = {
                    fadeIn(tween(durationMillis = SEARCH_SWAP_MILLIS)) togetherWith
                        fadeOut(tween(durationMillis = SEARCH_SWAP_MILLIS))
                },
                label = "searchTopBarSwap",
            ) { searching ->
                if (searching) {
                    SearchTopBar(
                        query = searchQuery,
                        onQueryChange = viewModel::setQuery,
                        onClose = closeSearch,
                    )
                } else {
                    TopAppBar(
                        scrollBehavior = scrollBehavior,
                        title = { Text(stringResource(R.string.nav_transactions)) },
                        actions = {
                            if (uiState.hasAnyTransactions) {
                                IconButton(onClick = { isSearching = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = stringResource(R.string.transactions_search),
                                    )
                                }
                                FilterButton(
                                    activeCount = uiState.filters.activeCount,
                                    onClick = { showFilterSheet = true },
                                )
                                IconButton(onClick = { showExportSheet = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.IosShare,
                                        contentDescription = stringResource(R.string.csv_export_title),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isLoading && !uiState.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToNewTransaction,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.transactions_new)) },
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> ListSkeleton(Modifier.padding(innerPadding))

            uiState.isEmpty -> TransactionsEmptyState(
                hasAccounts = uiState.hasAccounts,
                onAddTransaction = onNavigateToNewTransaction,
                onCreateAccount = onNavigateToAccounts,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                TransactionsFilterBar(
                    filters = uiState.filters,
                    categories = uiState.filterCategories,
                    accounts = uiState.filterAccounts,
                    tags = uiState.filterTags,
                    onSetPreset = viewModel::setDatePreset,
                    onRequestCustomRange = { showRangePicker = true },
                    onFiltersChange = viewModel::applyFilters,
                )
                if (uiState.filters.isActive) {
                    Spacer(Modifier.height(4.dp))
                    FilteredTotalsBar(
                        totals = uiState.filteredTotals,
                        count = uiState.filteredCount,
                    )
                }
                if (uiState.isNoResults) {
                    NoResultsState(
                        onClearFilters = {
                            viewModel.clearFilters()
                            isSearching = false
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    TransactionsList(
                        days = uiState.days,
                        today = uiState.today,
                        onItemClick = { onNavigateToEditTransaction(it.id) },
                        onItemDelete = viewModel::delete,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        TransactionFilterSheet(
            filters = uiState.filters,
            categories = uiState.filterCategories,
            accounts = uiState.filterAccounts,
            tags = uiState.filterTags,
            onApply = viewModel::applyFilters,
            onDismiss = { showFilterSheet = false },
        )
    }

    if (showExportSheet) {
        CsvExportSheet(
            count = uiState.filteredCount,
            separator = csvSeparator,
            onSeparatorSelected = viewModel::setCsvSeparator,
            onExport = {
                showExportSheet = false
                viewModel.exportCsv()
            },
            onDismiss = { showExportSheet = false },
        )
    }

    if (showRangePicker) {
        FilterDateRangePickerDialog(
            initialStart = uiState.filters.customStart,
            initialEnd = uiState.filters.customEnd,
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showRangePicker = false
            },
            onDismiss = { showRangePicker = false },
        )
    }
}

/**
 * One-shot event reactions: undoable delete snackbar, CSV hand-off to the
 * system Share Sheet, export failure notice. Extracted so the screen function
 * stays a readable layout.
 */
private suspend fun handleTransactionsEvent(
    event: TransactionsEvent,
    viewModel: TransactionsViewModel,
    snackbarHostState: SnackbarHostState,
    resources: Resources,
    context: Context,
) {
    when (event) {
        is TransactionsEvent.TransactionDeleted -> {
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.transactions_snackbar_deleted),
                actionLabel = resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(event)
            }
        }

        is TransactionsEvent.CsvExported -> {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, event.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, resources.getString(R.string.csv_share_title)),
            )
        }

        TransactionsEvent.CsvExportFailed -> {
            snackbarHostState.showSnackbar(
                message = resources.getString(R.string.csv_export_failed),
                duration = SnackbarDuration.Short,
            )
        }

        TransactionsEvent.WriteFailed -> {
            snackbarHostState.showSnackbar(
                message = resources.getString(R.string.editor_write_failed),
                duration = SnackbarDuration.Short,
            )
        }
    }
}

/**
 * The register as flat lazy items (one per row, not one per day): with
 * thousands of movements only the visible rows compose and recycle, and the
 * day headers, rows and spacers each get a stable key and contentType. The
 * per-row segment shapes recompose the grouped-card look of the old
 * one-card-per-day layout.
 */
@Composable
private fun TransactionsList(
    days: List<TransactionDayGroup>,
    today: LocalDate,
    onItemClick: (TransactionListItem) -> Unit,
    onItemDelete: (TransactionListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    ) {
        days.forEach { day ->
            item(key = "header-${day.date}", contentType = "day-header") {
                Column(modifier = Modifier.animateItem()) {
                    DayHeader(day = day, today = today)
                    Spacer(Modifier.height(6.dp))
                }
            }
            itemsIndexed(
                items = day.items,
                key = { _, item -> item.id },
                contentType = { _, _ -> "transaction" },
            ) { index, item ->
                TransactionSegment(
                    item = item,
                    isFirst = index == 0,
                    isLast = index == day.items.lastIndex,
                    onClick = { onItemClick(item) },
                    onDelete = { onItemDelete(item) },
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "spacer-${day.date}", contentType = "day-spacer") {
                Spacer(Modifier.height(SaldoDimens.cardSpacing))
            }
        }
    }
}

/**
 * One movement row drawn as a segment of its day's card: the first row rounds
 * the top corners, the last the bottom ones, and a hairline divider separates
 * consecutive rows. The clip also bounds the swipe-delete background.
 */
@Composable
private fun TransactionSegment(
    item: TransactionListItem,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = MaterialTheme.shapes.large
    val square = CornerSize(0.dp)
    val shape = when {
        isFirst && isLast -> cardShape
        isFirst -> cardShape.copy(bottomStart = square, bottomEnd = square)
        isLast -> cardShape.copy(topStart = square, topEnd = square)
        else -> RectangleShape
    }
    // White fill on the grey canvas with a hairline frame, matching SaldoCard.
    // The per-segment border also draws the line between consecutive rows, so no
    // separate divider is needed; the flat per-row lazy layout (kept for
    // recycling) still reads as one grouped card per day.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SaldoCardDefaults.containerColor)
            .border(SaldoCardDefaults.BorderWidth, MaterialTheme.saldoSurfaces.cardBorder, shape),
    ) {
        SwipeableTransactionRow(
            item = item,
            onClick = onClick,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun DayHeader(
    day: TransactionDayGroup,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
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
}

@Composable
private fun TransactionsEmptyState(
    hasAccounts: Boolean,
    onAddTransaction: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        title = stringResource(R.string.transactions_empty_title),
        body = stringResource(
            if (hasAccounts) {
                R.string.transactions_empty_body
            } else {
                R.string.transactions_empty_no_accounts_body
            },
        ),
        actionLabel = stringResource(
            if (hasAccounts) R.string.transactions_add_first else R.string.transactions_create_account,
        ),
        onAction = if (hasAccounts) onAddTransaction else onCreateAccount,
        modifier = modifier,
    )
}

/** Duration of the top-bar/search crossfade; brief on purpose, it is chrome. */
private const val SEARCH_SWAP_MILLIS = 180
