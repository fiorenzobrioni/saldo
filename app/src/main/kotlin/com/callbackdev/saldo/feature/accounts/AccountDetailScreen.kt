package com.callbackdev.saldo.feature.accounts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.AsOfTodayAmount
import com.callbackdev.saldo.core.designsystem.component.ListSkeleton
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.component.ThresholdProgressBar
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.DailyBalance
import com.callbackdev.saldo.core.domain.model.SavingsGoalProgress
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.feature.dashboard.BalanceSparkline
import com.callbackdev.saldo.feature.transactions.FilteredTotal
import com.callbackdev.saldo.feature.transactions.SwipeableTransactionRow
import com.callbackdev.saldo.feature.transactions.TransactionDayGroup
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import com.callbackdev.saldo.feature.transactions.dayLabel
import com.callbackdev.saldo.navigation.AccountDetailRoute
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency

/**
 * One account in full (Fase 39, F1): the balance with its own 30-day
 * sparkline, the type-specific block (statement, loan, goal), one month of
 * movements at a time and every account action. Reached by tapping an account
 * wherever one is listed; the editor is one tap away in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    route: AccountDetailRoute,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToNewTransaction: (Long) -> Unit,
    onNavigateToTransaction: (Long) -> Unit,
    onNavigateToDuplicate: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountDetailViewModel =
        hiltViewModel<AccountDetailViewModel, AccountDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(route) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // The account is gone (deleted here or elsewhere): nothing left to show.
    LaunchedEffect(uiState.isMissing) {
        if (uiState.isMissing) onNavigateBack()
    }

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountsEvent.AccountArchived -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(
                            R.string.accounts_snackbar_archived,
                            event.account.name,
                        ),
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.unarchive(event.account)
                    }
                }

                is AccountsEvent.BalanceAdjusted -> snackbarHostState.showSnackbar(
                    resources.getString(
                        R.string.accounts_snackbar_adjusted,
                        MoneyFormatter.formatSigned(event.delta, event.currency),
                    ),
                )

                is AccountsEvent.StatementSettled -> snackbarHostState.showSnackbar(
                    resources.getString(
                        R.string.accounts_snackbar_statement_settled,
                        MoneyFormatter.format(event.amount, event.currency),
                    ),
                )

                AccountsEvent.AccountDeleted -> onNavigateBack()

                AccountsEvent.WriteFailed -> snackbarHostState.showSnackbar(
                    resources.getString(R.string.editor_write_failed),
                )
            }
        }
    }

    val item = uiState.item
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = item?.account?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(onClick = { onNavigateToEdit(item.account.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.account_action_edit),
                            )
                        }
                        AccountOverflowMenu(
                            item = item,
                            onAdjustBalance = viewModel::openAdjustBalance,
                            onArchive = { viewModel.archive(item.account) },
                            onUnarchive = { viewModel.unarchive(item.account) },
                            onDelete = { viewModel.requestDelete(item.account) },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (item != null && !item.account.isArchived) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToNewTransaction(item.account.id) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.account_detail_new_movement)) },
                )
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading || item == null) {
            ListSkeleton(Modifier.padding(innerPadding))
        } else {
            AccountDetailContent(
                uiState = uiState,
                item = item,
                onSettleStatement = viewModel::settleStatement,
                onPreviousMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onMovementClick = { onNavigateToTransaction(it.id) },
                onMovementDelete = viewModel::deleteMovement,
                onMovementDuplicate = { onNavigateToDuplicate(it.id) },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }

    AccountsDialogHost(
        dialog = uiState.dialog,
        onAdjustInputChanged = viewModel::onAdjustInputChanged,
        onConfirmAdjust = viewModel::confirmAdjustBalance,
        onConfirmDelete = viewModel::confirmDelete,
        onArchiveInstead = { viewModel.archive(it) },
        onDismiss = viewModel::dismissDialog,
    )
}

/** The account actions that are not the edit shortcut: a quiet overflow. */
@Composable
private fun AccountOverflowMenu(
    item: AccountWithBalance,
    onAdjustBalance: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = stringResource(R.string.account_detail_more_actions),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (!item.account.isArchived) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.account_action_adjust_balance)) },
                leadingIcon = { Icon(Icons.Outlined.Balance, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAdjustBalance()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.account_action_archive)) },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                onClick = {
                    expanded = false
                    onArchive()
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.account_action_unarchive)) },
                leadingIcon = { Icon(Icons.Outlined.Unarchive, contentDescription = null) },
                onClick = {
                    expanded = false
                    onUnarchive()
                },
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.account_action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                expanded = false
                onDelete()
            },
        )
    }
}

@Suppress("LongParameterList") // One callback per action the content hosts.
@Composable
private fun AccountDetailContent(
    uiState: AccountDetailUiState,
    item: AccountWithBalance,
    onSettleStatement: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMovementClick: (TransactionListItem) -> Unit,
    onMovementDelete: (TransactionListItem) -> Unit,
    onMovementDuplicate: (TransactionListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        item(key = "header", contentType = "header") {
            AccountHeaderCard(
                item = item,
                countervalue = uiState.countervalue,
                countervalueCurrency = uiState.primaryCurrency,
                history = uiState.history,
            )
        }
        if (uiState.hasTypeExtras) {
            item(key = "extras", contentType = "extras") {
                SaldoCard(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(SaldoDimens.rowPaddingVertical))
                    CreditCardRowExtras(
                        item = item,
                        dueStatement = uiState.dueStatement,
                        onSettleStatement = onSettleStatement,
                    )
                    LoanRowExtras(item = item, progress = uiState.loanProgress)
                    uiState.savingsGoal?.let { goal ->
                        SavingsGoalExtras(progress = goal, currency = item.account.currency)
                    }
                }
            }
        }
        item(key = "month", contentType = "month") {
            MonthSelector(
                month = uiState.month,
                canGoBack = uiState.canGoToPreviousMonth,
                canGoForward = uiState.canGoToNextMonth,
                totals = uiState.monthTotals,
                count = uiState.monthMovementCount,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (uiState.days.isEmpty()) {
            item(key = "empty-month", contentType = "empty") {
                Text(
                    text = stringResource(R.string.account_detail_month_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            }
        }
        uiState.days.forEach { day ->
            item(key = "day-${day.date}", contentType = "day") {
                Column {
                    DayHeader(day = day, today = uiState.today)
                    Spacer(Modifier.height(6.dp))
                    SaldoCard(modifier = Modifier.fillMaxWidth()) {
                        day.items.forEachIndexed { index, movement ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            SwipeableTransactionRow(
                                item = movement,
                                onClick = { onMovementClick(movement) },
                                onDelete = { onMovementDelete(movement) },
                                onDuplicate = if (movement.transaction.type == TransactionType.ADJUSTMENT) {
                                    null
                                } else {
                                    { onMovementDuplicate(movement) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The hero of the detail: who the account is, what it holds, and how that
 * moved over the last 30 days. The sparkline is the dashboard's, without the
 * forecast tail: a single account has no budget-like estimate to project.
 */
@Composable
private fun AccountHeaderCard(
    item: AccountWithBalance,
    countervalue: CurrencyConverter.Estimate?,
    countervalueCurrency: Currency?,
    history: List<DailyBalance>,
    modifier: Modifier = Modifier,
) {
    val account = item.account
    SaldoCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SaldoDimens.cardPaddingLarge)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(account = account)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val supporting = accountSupportingText(account, showType = true)
                    if (supporting.isNotEmpty()) {
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = MoneyFormatter.format(item.balance, account.currency),
                style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
                color = if (item.balance.signum() < 0) {
                    MaterialTheme.moneyColors.negative
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            item.balanceAsOfToday?.let { today ->
                AsOfTodayAmount(amount = today, currency = account.currency)
            }
            if (countervalue != null && countervalueCurrency != null) {
                Text(
                    text = MoneyFormatter.formatApprox(countervalue.amount, countervalueCurrency),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (history.size > 1) {
                Spacer(Modifier.height(12.dp))
                BalanceSparkline(
                    history = history,
                    forecast = emptyList(),
                    currency = account.currency,
                    lineColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SPARKLINE_HEIGHT),
                )
                Text(
                    text = stringResource(R.string.account_detail_history_caption),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** The goal laid over a savings account: name, bar and the saved-of-target line. */
@Composable
private fun SavingsGoalExtras(
    progress: SavingsGoalProgress,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SaldoDimens.rowPaddingHorizontal,
                end = SaldoDimens.rowPaddingHorizontal,
                bottom = SaldoDimens.rowPaddingVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = progress.goal.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.account_loan_percent,
                    (progress.fraction.coerceIn(0f, 1f) * PERCENT).toInt(),
                ),
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ThresholdProgressBar(
            fraction = progress.fraction,
            color = if (progress.isReached) MaterialTheme.moneyColors.income else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (progress.isReached) {
                stringResource(R.string.account_detail_goal_reached)
            } else {
                stringResource(
                    R.string.account_detail_goal_progress,
                    MoneyFormatter.format(progress.saved, currency),
                    MoneyFormatter.format(progress.goal.targetAmount, currency),
                )
            },
            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Which month of movements is shown, with the month's own expense and income
 * totals underneath: the same per-currency figures the ledger's filtered
 * summary shows, so the two never disagree about a month.
 */
@Suppress("LongParameterList") // One argument per selector ingredient.
@Composable
private fun MonthSelector(
    month: YearMonth,
    canGoBack: Boolean,
    canGoForward: Boolean,
    totals: List<FilteredTotal>,
    count: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = canGoBack) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = stringResource(R.string.account_detail_previous_month),
                )
            }
            Text(
                text = monthLabel(month),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext, enabled = canGoForward) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.account_detail_next_month),
                )
            }
        }
        if (count > 0) {
            val expensesLabel = stringResource(R.string.dashboard_stat_expenses)
            val incomesLabel = stringResource(R.string.dashboard_stat_incomes)
            val countText = pluralStringResource(R.plurals.account_detail_month_count, count, count)
            Text(
                text = buildString {
                    append(countText)
                    totals.forEach { total ->
                        append(" · ")
                        append(expensesLabel)
                        append(' ')
                        append(MoneyFormatter.formatSigned(total.expenses, total.currency))
                        append(" · ")
                        append(incomesLabel)
                        append(' ')
                        append(MoneyFormatter.formatSigned(total.incomes, total.currency))
                    }
                },
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
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
private fun monthLabel(month: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(month, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
        month.atDay(1).format(DateTimeFormatter.ofPattern(pattern, locale)).withLocaleDateCasing(locale)
    }
}

private val SPARKLINE_HEIGHT = 72.dp
private const val PERCENT = 100
