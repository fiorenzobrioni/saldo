package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.designsystem.visuals.labelRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance

/**
 * Account list: active accounts with their computed balance, a collapsible
 * archived section, quick actions per account (edit, adjust balance, archive,
 * delete) and an empty state for the first launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToNewAccount: () -> Unit,
    onNavigateToEditAccount: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

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

                AccountsEvent.AccountDeleted -> snackbarHostState.showSnackbar(
                    resources.getString(R.string.accounts_snackbar_deleted),
                )
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isEmpty && !uiState.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToNewAccount,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.accounts_new)) },
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(innerPadding))

            uiState.isEmpty -> AccountsEmptyState(
                onCreateAccount = onNavigateToNewAccount,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> AccountsList(
                uiState = uiState,
                onAccountClick = { viewModel.onAccountSelected(it.account.id) },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }

    uiState.selected?.let { selected ->
        AccountActionsSheet(
            item = selected,
            onDismiss = { viewModel.onAccountSelected(null) },
            onEdit = {
                viewModel.onAccountSelected(null)
                onNavigateToEditAccount(selected.account.id)
            },
            onAdjustBalance = { viewModel.openAdjustBalance(selected) },
            onArchive = { viewModel.archive(selected.account) },
            onUnarchive = { viewModel.unarchive(selected.account) },
            onDelete = { viewModel.requestDelete(selected.account) },
        )
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

@Composable
private fun AccountsList(
    uiState: AccountsUiState,
    onAccountClick: (AccountWithBalance) -> Unit,
    modifier: Modifier = Modifier,
) {
    var archivedExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        if (uiState.active.isNotEmpty()) {
            item(key = "active") {
                AccountsCard(items = uiState.active, onAccountClick = onAccountClick)
            }
        }

        if (uiState.archived.isNotEmpty()) {
            item(key = "archived-header") {
                ArchivedHeader(
                    count = uiState.archived.size,
                    expanded = archivedExpanded,
                    onToggle = { archivedExpanded = !archivedExpanded },
                )
            }
            if (archivedExpanded) {
                item(key = "archived") {
                    AccountsCard(items = uiState.archived, onAccountClick = onAccountClick)
                }
            }
        }
    }
}

/** All accounts of a section in one grouped card, split by hairline dividers. */
@Composable
private fun AccountsCard(
    items: List<AccountWithBalance>,
    onAccountClick: (AccountWithBalance) -> Unit,
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
                        modifier = Modifier.padding(horizontal = SaldoDimens.rowPaddingHorizontal),
                    )
                }
                Surface(
                    onClick = { onAccountClick(item) },
                    color = Color.Transparent,
                ) {
                    AccountRowContent(
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

@Composable
private fun ArchivedHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.accounts_archived_header, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Flat account row (avatar, name/detail, balance) for the grouped list card. */
@Composable
internal fun AccountRowContent(
    item: AccountWithBalance,
    modifier: Modifier = Modifier,
) {
    val account = item.account
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (account.isArchived) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(account = account)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = accountSupportingText(account),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = MoneyFormatter.format(item.balance, account.currency),
            style = MaterialTheme.typography.titleMedium.tabularNumbers(),
            color = if (item.balance.signum() < 0) {
                MaterialTheme.moneyColors.negative
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
internal fun AccountAvatar(
    account: Account,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(AvatarShape)
            .background(AccountVisuals.color(account.color)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AccountVisuals.icon(account.icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun accountSupportingText(account: Account): String {
    val parts = buildList {
        add(stringResource(account.type.labelRes()))
        if (account.isArchived) add(stringResource(R.string.accounts_archived_label))
        if (!account.isIncludedInTotal) {
            add(stringResource(R.string.accounts_excluded_from_total))
        }
    }
    return parts.joinToString(separator = " · ")
}

@Composable
private fun AccountsEmptyState(
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = AccountVisuals.icon(null),
        title = stringResource(R.string.accounts_empty_title),
        body = stringResource(R.string.accounts_empty_body),
        actionLabel = stringResource(R.string.accounts_create_first),
        onAction = onCreateAccount,
        modifier = modifier,
    )
}
