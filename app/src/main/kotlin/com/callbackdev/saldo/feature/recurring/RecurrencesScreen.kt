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
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
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
    modifier: Modifier = Modifier,
    viewModel: RecurrencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabType = if (selectedTab == 0) TransactionType.EXPENSE else TransactionType.INCOME
    val section = uiState.section(tabType)
    val newRuleLabel = stringResource(
        if (tabType == TransactionType.INCOME) R.string.incomes_new else R.string.subscriptions_new,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
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
                    onSortSelected = viewModel::onSortSelected,
                    onItemClick = onNavigateToEditRule,
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
    onSortSelected: (SubscriptionSort) -> Unit,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isIncome = type == TransactionType.INCOME
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
                countRes = if (isIncome) {
                    R.plurals.incomes_active_count
                } else {
                    R.plurals.subscriptions_active_count
                },
            )
        }
        item {
            AnnualProjectionCard(
                annual = section.annualProjection,
                currency = section.currency,
                icon = if (isIncome) Icons.AutoMirrored.Outlined.TrendingUp else Icons.Outlined.EventRepeat,
            )
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
                textRes = if (isIncome) {
                    R.string.incomes_prorated_note
                } else {
                    R.string.subscriptions_prorated_note
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
    @PluralsRes countRes: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(SaldoDimens.cardPaddingLarge)) {
            Text(
                text = stringResource(R.string.subscriptions_this_month),
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
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
    EmptyState(
        icon = if (isIncome) Icons.AutoMirrored.Outlined.TrendingUp else Icons.Outlined.Subscriptions,
        title = stringResource(
            if (isIncome) R.string.incomes_empty_title else R.string.subscriptions_empty_title,
        ),
        body = stringResource(
            if (isIncome) R.string.incomes_empty_body else R.string.subscriptions_empty_body,
        ),
        actionLabel = actionLabel,
        onAction = onCreate,
        modifier = modifier,
    )
}
