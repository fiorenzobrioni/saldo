package com.callbackdev.saldo.feature.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.DashboardSkeleton
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.SpeedDialFab
import com.callbackdev.saldo.core.designsystem.component.SpeedDialScrim
import com.callbackdev.saldo.core.designsystem.component.rememberMotionEnabled
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.feature.transactions.movementSpeedDialActions
import com.callbackdev.saldo.navigation.FilteredTransactionsRoute
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

/**
 * The "Today" home screen: a single glance at total balance, today's and this
 * month's cash flow, and the latest movements, with a speed-dial FAB for the
 * three quick actions. All figures derive reactively from the database.
 *
 * The pinned top bar carries the greeting and the date; once the hero balance
 * scrolls out of sight the title swaps to a compact total balance, so the
 * screen's key figure never actually leaves the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // One callback per navigation target, all wired by SaldoApp.
@Composable
fun DashboardScreen(
    onNavigateToAccounts: () -> Unit,
    onNavigateToAccount: (Long) -> Unit,
    onCreateFirstAccount: () -> Unit,
    onNavigateToNewTransaction: (TransactionType) -> Unit,
    onNavigateToEditTransaction: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToRecurrences: () -> Unit,
    onNavigateToPending: () -> Unit,
    onNavigateToUpcoming: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToSavingsGoals: () -> Unit,
    onNavigateToCounterparties: () -> Unit,
    onNavigateToFiltered: (FilteredTransactionsRoute) -> Unit,
    onNavigateToRecap: (YearMonth) -> Unit,
    onNavigateToRates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Both breakdown expansions live in the ViewModel so they survive scrolling
    // the cards out of view and moving between screens, resetting to their
    // defaults only on a fresh app open.
    val accountsExpanded by viewModel.balanceAccountsExpanded.collectAsStateWithLifecycle()
    val safeToSpendExpanded by viewModel.safeToSpendExpanded.collectAsStateWithLifecycle()
    val monthComparisonExpanded by viewModel.monthComparisonExpanded.collectAsStateWithLifecycle()
    // Plain remember on purpose: an open speed dial should not survive
    // navigating away and back (the tab keeps its state now), nor a rotation.
    var fabExpanded by remember { mutableStateOf(false) }

    fun quickAction(type: TransactionType) {
        fabExpanded = false
        onNavigateToNewTransaction(type)
    }

    // The list state is hoisted here so the top bar can watch the hero balance
    // scroll away: past the threshold (the hero figure is under the bar) the
    // title swaps to the compact balance.
    val listState = rememberLazyListState()
    val collapseThresholdPx = with(LocalDensity.current) { BALANCE_COLLAPSE_THRESHOLD.toPx() }
    val heroScrolledAway by remember(listState, collapseThresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            DashboardTopBar(
                band = uiState.greetingBand,
                date = uiState.date,
                showBalance = heroScrolledAway && !uiState.isLoading && uiState.hasAccounts,
                totalBalance = uiState.totalBalance,
                currency = uiState.primaryCurrency,
                estimated = uiState.totalBalanceEstimated,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            when {
                uiState.isLoading -> Unit

                // Without an account there is nothing to record yet: the FAB
                // stays (it must never vanish) and leads where the empty state
                // does, to the first account.
                !uiState.hasAccounts -> FloatingActionButton(onClick = onCreateFirstAccount) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.dashboard_empty_cta),
                    )
                }

                else -> SpeedDialFab(
                    expanded = fabExpanded,
                    onToggle = { fabExpanded = !fabExpanded },
                    actions = movementSpeedDialActions(::quickAction),
                    toggleDescription = stringResource(R.string.dashboard_fab_add),
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
                    listState = listState,
                    accountsExpanded = accountsExpanded,
                    onToggleAccounts = viewModel::toggleBalanceAccountsExpanded,
                    safeToSpendExpanded = safeToSpendExpanded,
                    onToggleSafeToSpend = viewModel::toggleSafeToSpendExpanded,
                    monthComparisonExpanded = monthComparisonExpanded,
                    onToggleMonthComparison = viewModel::toggleMonthComparisonExpanded,
                    onManageAccounts = onNavigateToAccounts,
                    onAccountClick = onNavigateToAccount,
                    onSeeAllTransactions = onSeeAllTransactions,
                    onTransactionClick = onNavigateToEditTransaction,
                    onOpenStats = onNavigateToStats,
                    onRecurringClick = onNavigateToRecurrences,
                    onPendingClick = onNavigateToPending,
                    onUpcomingClick = onNavigateToUpcoming,
                    onBudgetsClick = onNavigateToBudgets,
                    onSavingsGoalsClick = onNavigateToSavingsGoals,
                    onCounterpartiesClick = onNavigateToCounterparties,
                    onNavigateToFiltered = onNavigateToFiltered,
                    onRecapClick = onNavigateToRecap,
                    onRecapDismiss = viewModel::dismissRecapTeaser,
                    onRatesClick = onNavigateToRates,
                )
            }

            SpeedDialScrim(
                visible = fabExpanded,
                onDismiss = { fabExpanded = false },
            )
        }
    }
}

/**
 * The dashboard's pinned top bar. Its title crossfades between the greeting +
 * date header and a compact total balance, following [showBalance]: while the
 * hero card is on screen the bar carries context, once the hero figure is gone
 * the bar carries the figure. The swap slides in the scroll's own direction on
 * the theme's motion; with system animations off it snaps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    band: GreetingBand,
    date: LocalDate,
    showBalance: Boolean,
    totalBalance: BigDecimal,
    currency: Currency,
    estimated: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = rememberMotionEnabled()
    TopAppBar(
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        title = {
            AnimatedContent(
                targetState = showBalance,
                transitionSpec = {
                    // The incoming line slides in the scroll's own direction
                    // (up while collapsing, down while expanding) and fades;
                    // with system animations off it snaps.
                    if (!motionEnabled) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        val direction = if (targetState) 1 else -1
                        val enter = fadeIn(tween(TITLE_SWAP_MILLIS)) +
                            slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) {
                                it / TITLE_SLIDE_DIVISOR * direction
                            }
                        val exit = fadeOut(tween(TITLE_SWAP_MILLIS)) +
                            slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) {
                                -it / TITLE_SLIDE_DIVISOR * direction
                            }
                        enter togetherWith exit
                    }
                },
                label = "dashboardTopBarTitle",
            ) { balance ->
                if (balance) {
                    CollapsedBalanceTitle(
                        totalBalance = totalBalance,
                        currency = currency,
                        estimated = estimated,
                    )
                } else {
                    DashboardHeader(band = band, date = date)
                }
            }
        },
    )
}

/** Compact total balance shown in the top bar while the hero card is scrolled away. */
@Composable
private fun CollapsedBalanceTitle(
    totalBalance: BigDecimal,
    currency: Currency,
    estimated: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dashboard_balance_total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            // The "≈" is the app-wide estimate marker (ADR 40).
            text = (if (estimated) "≈ " else "") + MoneyFormatter.format(totalBalance, currency),
            style = MaterialTheme.typography.titleLarge.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            color = if (totalBalance.signum() < 0) {
                MaterialTheme.moneyColors.negative
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Suppress("LongParameterList") // One callback per card, all owned by the screen.
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    listState: LazyListState,
    accountsExpanded: Boolean,
    onToggleAccounts: () -> Unit,
    safeToSpendExpanded: Boolean,
    onToggleSafeToSpend: () -> Unit,
    monthComparisonExpanded: Boolean,
    onToggleMonthComparison: () -> Unit,
    onManageAccounts: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onOpenStats: () -> Unit,
    onRecurringClick: () -> Unit,
    onPendingClick: () -> Unit,
    onUpcomingClick: () -> Unit,
    onBudgetsClick: () -> Unit,
    onSavingsGoalsClick: () -> Unit,
    onCounterpartiesClick: () -> Unit,
    onNavigateToFiltered: (FilteredTransactionsRoute) -> Unit,
    onRecapClick: (YearMonth) -> Unit,
    onRecapDismiss: () -> Unit,
    onRatesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        item {
            BalanceCard(
                totalBalance = uiState.totalBalance,
                balanceAsOfToday = uiState.balanceAsOfToday,
                currency = uiState.primaryCurrency,
                accounts = uiState.accounts,
                history = uiState.balanceHistory,
                forecast = uiState.balanceForecast,
                accountsExpanded = accountsExpanded,
                onToggleAccounts = onToggleAccounts,
                onManageAccounts = onManageAccounts,
                onAccountClick = onAccountClick,
                estimated = uiState.totalBalanceEstimated,
                rateDay = uiState.totalBalanceRateDay,
                countervalues = uiState.accountCountervalues,
                onOpenRates = onRatesClick,
            )
        }
        uiState.safeToSpend?.takeIf { uiState.cardPrefs.showSafeToSpend }?.let { safeToSpend ->
            item {
                SafeToSpendCard(
                    safeToSpend = safeToSpend,
                    currency = uiState.primaryCurrency,
                    expanded = safeToSpendExpanded,
                    onToggleExpanded = onToggleSafeToSpend,
                    onManageBudgets = onBudgetsClick,
                )
            }
        }
        item {
            PeriodCardsRow(
                date = uiState.date,
                today = uiState.today,
                month = uiState.month,
                currency = uiState.primaryCurrency,
                estimated = uiState.periodTotalsEstimated,
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
        if (uiState.cardPrefs.showMonthComparison) {
            uiState.previousMonthSpendToDate?.let { previousSpend ->
                item {
                    MonthComparisonCard(
                        comparison = uiState.monthComparison,
                        previousSpend = previousSpend,
                        delta = uiState.monthVsPreviousToDate,
                        currency = uiState.primaryCurrency,
                        expanded = monthComparisonExpanded,
                        onToggleExpanded = onToggleMonthComparison,
                        onClick = onOpenStats,
                    )
                }
            }
        }
        uiState.recapTeaserMonth?.let { teaserMonth ->
            item {
                RecapTeaserCard(
                    month = teaserMonth,
                    onClick = { onRecapClick(teaserMonth) },
                    onDismiss = onRecapDismiss,
                )
            }
        }
        if (uiState.cardPrefs.showBudget) {
            item { BudgetCard(budgets = uiState.budgets, onClick = onBudgetsClick) }
        }
        if (uiState.cardPrefs.showSavingsGoals) {
            item {
                SavingsGoalsCard(
                    goals = uiState.savingsGoals,
                    currency = uiState.primaryCurrency,
                    onClick = onSavingsGoalsClick,
                )
            }
        }
        // No invitation when nothing is open: a card about lent money would be
        // noise for the many users who never lend any (the Settings switch and
        // the Management section stay the way in).
        if (uiState.cardPrefs.showCounterparties && uiState.counterparties.hasOpenEntries) {
            item {
                CounterpartiesCard(
                    ledger = uiState.counterparties,
                    onClick = onCounterpartiesClick,
                )
            }
        }
        if (uiState.pendingCount > 0) {
            item { PendingConfirmationCard(count = uiState.pendingCount, onClick = onPendingClick) }
        }
        // Below the confirmation nudge: what still needs an answer comes before
        // what is merely scheduled.
        if (uiState.cardPrefs.showUpcoming && !uiState.upcoming.isEmpty) {
            item {
                UpcomingCard(
                    preview = uiState.upcoming,
                    today = uiState.date,
                    onClick = onUpcomingClick,
                )
            }
        }
        if (uiState.dueStatements.isNotEmpty()) {
            item {
                StatementDueCard(
                    statements = uiState.dueStatements,
                    onClick = onManageAccounts,
                )
            }
        }
        if (uiState.cardPrefs.showRecurring) {
            item {
                RecurringCard(
                    summary = uiState.recurring,
                    currency = uiState.primaryCurrency,
                    onClick = onRecurringClick,
                )
            }
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
                        today = uiState.date,
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

/**
 * How far the hero card must scroll under the bar before the title swaps to
 * the compact balance: roughly the hero figure's own offset within the card,
 * so the swap lands just as the number disappears.
 */
private val BALANCE_COLLAPSE_THRESHOLD = 96.dp

/** How far the swapping top-bar titles slide, as a fraction (1/N) of their height. */
private const val TITLE_SLIDE_DIVISOR = 2

/** Fade duration of the top-bar title swap (the slide runs on its own spring). */
private const val TITLE_SWAP_MILLIS = 200
