package com.callbackdev.saldo.feature.recurring

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Upcoming
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.TransactionType
import java.math.BigDecimal
import java.util.Currency

/**
 * The recurrences hub: a Subscriptions tab (recurring expenses) and an Incomes
 * tab (salary, rent received...), each with this month's total, the annual
 * projection and the active rules sorted by next charge (or by amount or name).
 * Non-monthly amounts are amortized to a monthly figure in the totals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrencesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToNewRule: (TransactionType) -> Unit,
    onNavigateToEditRule: (Long) -> Unit,
    onNavigateToUpcoming: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurrencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabType = when (selectedTab) {
        0 -> TransactionType.EXPENSE
        1 -> TransactionType.INCOME
        else -> TransactionType.TRANSFER
    }
    val section = uiState.section(tabType)
    val newRuleLabel = stringResource(
        when (tabType) {
            TransactionType.INCOME -> R.string.incomes_new
            TransactionType.TRANSFER -> R.string.transfers_new
            else -> R.string.subscriptions_new
        },
    )

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.recurrences_title)) },
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
        floatingActionButton = {
            if (!uiState.isLoading && !section.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToNewRule(tabType) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(newRuleLabel) },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.recurrences_tab_subscriptions)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.recurrences_tab_incomes)) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.recurrences_tab_transfers)) },
                )
            }

            when {
                uiState.isLoading -> LoadingState()

                section.isEmpty -> RecurrencesEmptyState(
                    type = tabType,
                    actionLabel = newRuleLabel,
                    onCreate = { onNavigateToNewRule(tabType) },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> RecurrencesContent(
                    section = section,
                    type = tabType,
                    sort = uiState.sort,
                    today = uiState.today,
                    plannedSavings = uiState.plannedMonthlySavings,
                    savingsCurrency = uiState.savingsCurrency,
                    onSortSelected = viewModel::onSortSelected,
                    onItemClick = onNavigateToEditRule,
                    onUpcomingClick = onNavigateToUpcoming,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun RecurrencesContent(
    section: RecurrenceSection,
    type: TransactionType,
    sort: SubscriptionSort,
    today: java.time.LocalDate,
    plannedSavings: BigDecimal,
    savingsCurrency: Currency,
    onSortSelected: (SubscriptionSort) -> Unit,
    onItemClick: (Long) -> Unit,
    onUpcomingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isIncome = type == TransactionType.INCOME
    val isTransfer = type == TransactionType.TRANSFER
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        item {
            MonthlyTotalCard(
                total = section.monthlyTotal,
                currency = section.currency,
                activeCount = section.activeCount,
                titleRes = when {
                    isTransfer -> R.string.transfers_this_month
                    else -> R.string.subscriptions_this_month
                },
                countRes = when {
                    isTransfer -> R.plurals.transfers_active_count
                    isIncome -> R.plurals.incomes_active_count
                    else -> R.plurals.subscriptions_active_count
                },
            )
        }
        if (isTransfer) {
            // Highlight the portion flowing into savings accounts: the seed of
            // Savings Goals (v2.0). Only shown when there is planned saving.
            if (plannedSavings.signum() > 0) {
                item {
                    PlannedSavingsCard(amount = plannedSavings, currency = savingsCurrency)
                }
            }
        } else {
            item {
                AnnualProjectionCard(
                    annual = section.annualProjection,
                    currency = section.currency,
                    // The trending pair mirrors the tabs: down for expenses, up for
                    // incomes (same visual language as the dashboard comparison).
                    icon = if (isIncome) {
                        Icons.AutoMirrored.Outlined.TrendingUp
                    } else {
                        Icons.AutoMirrored.Outlined.TrendingDown
                    },
                )
            }
        }
        item {
            UpcomingLinkRow(onClick = onUpcomingClick)
        }
        item {
            SortHeader(
                sort = sort,
                type = type,
                onSortSelected = onSortSelected,
            )
        }
        item {
            RecurrencesListCard(
                items = section.items,
                type = type,
                today = today,
                onItemClick = onItemClick,
            )
        }
        item {
            FooterNote(
                textRes = when {
                    isTransfer -> R.string.transfers_prorated_note
                    isIncome -> R.string.incomes_prorated_note
                    else -> R.string.subscriptions_prorated_note
                },
            )
        }
    }
}

/** Neutral hero card: the normalized monthly amount and the active count. */
@Composable
private fun MonthlyTotalCard(
    total: BigDecimal,
    currency: Currency,
    activeCount: Int,
    @StringRes titleRes: Int,
    @PluralsRes countRes: Int,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(SaldoDimens.cardPaddingLarge)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = MoneyFormatter.format(total, currency),
                    style = MaterialTheme.typography.displaySmall.tabularNumbers(),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = pluralStringResource(countRes, activeCount, activeCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}

/** Tinted card: the monthly run-rate projected over a year. */
@Composable
private fun AnnualProjectionCard(
    annual: BigDecimal,
    currency: Currency,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(SaldoDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.subscriptions_annual_projection),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = MoneyFormatter.format(annual, currency),
                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * Highlighted card summing the recurring transfers that land in a savings
 * account: "you are setting aside X/month". The honest seed of Savings Goals.
 */
@Composable
private fun PlannedSavingsCard(
    amount: BigDecimal,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(SaldoDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Savings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.transfers_planned_savings),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(
                        R.string.transfers_planned_savings_amount,
                        MoneyFormatter.format(amount, currency),
                    ),
                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/**
 * The way from the rules to their next occurrences. The hub administers
 * schedules, "Upcoming" lists what those schedules (and every dated movement)
 * are about to produce: two different questions, one link between them, so
 * neither screen is a dead end.
 */
@Composable
private fun UpcomingLinkRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SaldoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Upcoming,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.upcoming_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.recurrences_upcoming_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SortHeader(
    sort: SubscriptionSort,
    type: TransactionType,
    onSortSelected: (SubscriptionSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(sort.labelRes(type)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Outlined.SwapVert,
                    contentDescription = stringResource(R.string.subscriptions_sort),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SubscriptionSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes(type))) },
                        onClick = {
                            onSortSelected(option)
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurrencesListCard(
    items: List<SubscriptionItem>,
    type: TransactionType,
    today: java.time.LocalDate,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Surface(
                    onClick = { onItemClick(item.id) },
                    color = Color.Transparent,
                ) {
                    SubscriptionRowContent(
                        item = item,
                        type = type,
                        today = today,
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

@Composable
private fun FooterNote(@StringRes textRes: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecurrencesEmptyState(
    type: TransactionType,
    actionLabel: String,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isIncome = type == TransactionType.INCOME
    val isTransfer = type == TransactionType.TRANSFER
    EmptyState(
        icon = when {
            isTransfer -> Icons.Outlined.SwapHoriz
            isIncome -> Icons.AutoMirrored.Outlined.TrendingUp
            else -> Icons.AutoMirrored.Outlined.TrendingDown
        },
        title = stringResource(
            when {
                isTransfer -> R.string.transfers_empty_title
                isIncome -> R.string.incomes_empty_title
                else -> R.string.subscriptions_empty_title
            },
        ),
        body = stringResource(
            when {
                isTransfer -> R.string.transfers_empty_body
                isIncome -> R.string.incomes_empty_body
                else -> R.string.subscriptions_empty_body
            },
        ),
        actionLabel = actionLabel,
        onAction = onCreate,
        modifier = modifier,
    )
}
