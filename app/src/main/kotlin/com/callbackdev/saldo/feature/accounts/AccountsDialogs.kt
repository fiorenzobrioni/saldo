package com.callbackdev.saldo.feature.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.AmountKeypadHost
import com.callbackdev.saldo.core.designsystem.component.AmountTarget
import com.callbackdev.saldo.core.designsystem.component.HeroAmountField
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.money.MoneyMapper

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
    val amountTarget = AmountTarget(
        value = dialog.input,
        fractionDigits = MoneyMapper.fractionDigits(currency),
        // A balance can be restated downwards: the adjustment carries the sign.
        allowNegative = true,
        onValueChange = onInputChanged,
    )

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
                    target = amountTarget,
                    currencySymbol = currency.symbol,
                    isError = false,
                    // The dialog exists to type a balance: the keypad is always up.
                    isActive = true,
                    onActivate = {},
                    showSignToggle = true,
                    label = stringResource(R.string.accounts_adjust_real_balance),
                    compact = true,
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
                Spacer(Modifier.height(8.dp))
                // Inside the dialog rather than docked: there is no bottom bar
                // here, and the keypad is the only way in for these digits.
                AmountKeypadHost(target = amountTarget, compact = true)
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
}
