package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.designsystem.visuals.labelRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.HeroAmountField
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance

/** Quick actions for a tapped account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountActionsSheet(
    item: AccountWithBalance,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAdjustBalance: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = item.account
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
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
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(account.type.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = MoneyFormatter.format(item.balance, account.currency),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SheetAction(
            icon = Icons.Outlined.Edit,
            label = stringResource(R.string.account_action_edit),
            onClick = onEdit,
        )
        if (!account.isArchived) {
            SheetAction(
                icon = Icons.Outlined.Balance,
                label = stringResource(R.string.account_action_adjust_balance),
                onClick = onAdjustBalance,
            )
            SheetAction(
                icon = Icons.Outlined.Archive,
                label = stringResource(R.string.account_action_archive),
                onClick = onArchive,
            )
        } else {
            SheetAction(
                icon = Icons.Outlined.Unarchive,
                label = stringResource(R.string.account_action_unarchive),
                onClick = onUnarchive,
            )
        }
        SheetAction(
            icon = Icons.Outlined.Delete,
            label = stringResource(R.string.account_action_delete),
            onClick = onDelete,
            contentColor = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = contentColor,
            leadingIconColor = contentColor,
        ),
        modifier = modifier.clickable(onClick = onClick),
    )
}

/** Renders the dialog currently requested by the view model, if any. */
@Composable
internal fun AccountsDialogHost(
    dialog: AccountsDialog?,
    onAdjustInputChanged: (String) -> Unit,
    onConfirmAdjust: () -> Unit,
    onConfirmDelete: () -> Unit,
    onArchiveInstead: (Account) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        is AccountsDialog.AdjustBalance -> AdjustBalanceDialog(
            dialog = dialog,
            onInputChanged = onAdjustInputChanged,
            onConfirm = onConfirmAdjust,
            onDismiss = onDismiss,
        )

        is AccountsDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.accounts_delete_title)) },
            text = {
                Text(stringResource(R.string.accounts_delete_body, dialog.account.name))
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(
                        text = stringResource(R.string.account_action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

        is AccountsDialog.ArchiveInstead -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
            title = { Text(stringResource(R.string.accounts_cannot_delete_title)) },
            text = {
                Text(
                    if (dialog.movementCount > 0) {
                        pluralStringResource(
                            R.plurals.accounts_cannot_delete_body,
                            dialog.movementCount,
                            dialog.account.name,
                            dialog.movementCount,
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.accounts_cannot_delete_rules_body,
                            dialog.ruleCount,
                            dialog.account.name,
                            dialog.ruleCount,
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { onArchiveInstead(dialog.account) }) {
                    Text(stringResource(R.string.account_action_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

        null -> Unit
    }
}

/**
 * Balance adjustment: the user types the real balance and the dialog previews
 * the ADJUSTMENT movement that will be recorded.
 */
@Composable
private fun AdjustBalanceDialog(
    dialog: AccountsDialog.AdjustBalance,
    onInputChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currency = dialog.account.currency
    val delta = dialog.delta
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Balance, contentDescription = null) },
        title = { Text(stringResource(R.string.accounts_adjust_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.accounts_adjust_current,
                        MoneyFormatter.format(dialog.currentBalance, currency),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                HeroAmountField(
                    input = dialog.input,
                    currencySymbol = currency.symbol,
                    isError = false,
                    onValueChange = onInputChanged,
                    showSignToggle = true,
                    label = stringResource(R.string.accounts_adjust_real_balance),
                    compact = true,
                    focusRequester = focusRequester,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    delta == null -> Unit
                    delta.signum() == 0 -> Text(
                        text = stringResource(R.string.accounts_adjust_no_change),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> Text(
                        text = stringResource(
                            R.string.accounts_adjust_preview,
                            MoneyFormatter.formatSigned(delta, currency),
                        ),
                        style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = delta != null && delta.signum() != 0,
            ) {
                Text(stringResource(R.string.accounts_adjust_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
