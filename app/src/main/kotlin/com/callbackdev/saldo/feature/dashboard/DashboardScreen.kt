package com.callbackdev.saldo.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.DashboardSkeleton
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.navigation.FilteredTransactionsRoute

/**
 * The "Today" home screen: a single glance at total balance, today's and this
 * month's cash flow, and the latest movements, with a speed-dial FAB for the
 * three quick actions. All figures derive reactively from the database.
 */
@Composable
fun DashboardScreen(
    onNavigateToAccounts: () -> Unit,
    onCreateFirstAccount: () -> Unit,
    onNavigateToNewTransaction: (TransactionType) -> Unit,
    onNavigateToEditTransaction: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onNavigateToRecurrences: () -> Unit,
    onNavigateToPending: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToFiltered: (FilteredTransactionsRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Plain remember on purpose: an open speed dial should not survive
    // navigating away and back (the tab keeps its state now), nor a rotation.
    var fabExpanded by remember { mutableStateOf(false) }

    fun quickAction(type: TransactionType) {
        fabExpanded = false
        onNavigateToNewTransaction(type)
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (!uiState.isLoading && uiState.hasAccounts) {
                DashboardSpeedDial(
                    expanded = fabExpanded,
                    onToggle = { fabExpanded = !fabExpanded },
                    onAddExpense = { quickAction(TransactionType.EXPENSE) },
                    onAddIncome = { quickAction(TransactionType.INCOME) },
                    onAddTransfer = { quickAction(TransactionType.TRANSFER) },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isLoading -> DashboardSkeleton()

                !uiState.hasAccounts -> DashboardEmptyState(
                    onCreateFirstAccount = onCreateFirstAccount,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> DashboardContent(
                    uiState = uiState,
                    onManageAccounts = onNavigateToAccounts,
                    onSeeAllTransactions = onSeeAllTransactions,
                    onTransactionClick = onNavigateToEditTransaction,
                    onRecurringClick = onNavigateToRecurrences,
                    onPendingClick = onNavigateToPending,
                    onBudgetsClick = onNavigateToBudgets,
                    onNavigateToFiltered = onNavigateToFiltered,
                )
            }

            AnimatedVisibility(
                visible = fabExpanded,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { fabExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onManageAccounts: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onRecurringClick: () -> Unit,
    onPendingClick: () -> Unit,
    onBudgetsClick: () -> Unit,
    onNavigateToFiltered: (FilteredTransactionsRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        item {
            DashboardHeader(
                band = uiState.greetingBand,
                roll = uiState.greetingRoll,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        item {
            BalanceCard(
                totalBalance = uiState.totalBalance,
                currency = uiState.primaryCurrency,
                accounts = uiState.accounts,
                date = uiState.date,
                onManageAccounts = onManageAccounts,
            )
        }
        uiState.safeToSpend?.takeIf { uiState.cardPrefs.showSafeToSpend }?.let { safeToSpend ->
            item {
                SafeToSpendCard(
                    safeToSpend = safeToSpend,
                    currency = uiState.primaryCurrency,
                    onClick = onBudgetsClick,
                )
            }
        }
        item {
            PeriodCardsRow(
                date = uiState.date,
                today = uiState.today,
                month = uiState.month,
                currency = uiState.primaryCurrency,
                onTodayClick = {
                    onNavigateToFiltered(
                        FilteredTransactionsRoute(
                            startEpochDay = uiState.date.toEpochDay(),
                            endEpochDayExclusive = uiState.date.plusDays(1).toEpochDay(),
                        ),
                    )
                },
                onMonthClick = {
                    val firstOfMonth = uiState.date.withDayOfMonth(1)
                    onNavigateToFiltered(
                        FilteredTransactionsRoute(
                            startEpochDay = firstOfMonth.toEpochDay(),
                            endEpochDayExclusive = firstOfMonth.plusMonths(1).toEpochDay(),
                        ),
                    )
                },
            )
        }
        uiState.previousMonthSpendToDate?.let { previousSpend ->
            item {
                MonthComparisonRow(
                    previousSpend = previousSpend,
                    spentMore = uiState.spentMoreThanLastMonth,
                    currency = uiState.primaryCurrency,
                )
            }
        }
        if (uiState.cardPrefs.showBudget) {
            item { BudgetCard(budgets = uiState.budgets, onClick = onBudgetsClick) }
        }
        if (uiState.pendingCount > 0) {
            item { PendingConfirmationCard(count = uiState.pendingCount, onClick = onPendingClick) }
        }
        if (uiState.dueStatements.isNotEmpty()) {
            item {
                StatementDueCard(
                    statements = uiState.dueStatements,
                    onClick = onManageAccounts,
                )
            }
        }
        item {
            RecurringCard(
                summary = uiState.recurring,
                currency = uiState.primaryCurrency,
                onClick = onRecurringClick,
            )
        }
        if (uiState.cardPrefs.showRecentTransactions) {
            item { RecentHeader(onSeeAll = onSeeAllTransactions) }
            if (uiState.recent.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_recent_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            } else {
                item {
                    RecentMovementsCard(
                        items = uiState.recent,
                        onItemClick = onTransactionClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentHeader(onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.dashboard_recent_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        TextButton(onClick = onSeeAll) {
            Text(stringResource(R.string.dashboard_recent_see_all))
        }
    }
}

@Composable
private fun DashboardEmptyState(
    onCreateFirstAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.Outlined.AccountBalanceWallet,
        title = stringResource(R.string.dashboard_empty_title),
        body = stringResource(R.string.dashboard_empty_body),
        actionLabel = stringResource(R.string.dashboard_empty_cta),
        onAction = onCreateFirstAccount,
        modifier = modifier,
    )
}

private const val SCRIM_ALPHA = 0.32f
