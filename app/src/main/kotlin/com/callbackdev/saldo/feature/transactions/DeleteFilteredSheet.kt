package com.callbackdev.saldo.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers

/**
 * Pre-flight of the bulk delete of the filtered view. States how many movements
 * go, lets the user choose whether balances recompute or stay put via a
 * carry-over adjustment (with a live per-account impact preview), and offers an
 * export as a safety net before the destructive step. The sheet itself is the
 * confirmation: the primary button is the error-tinted "Delete".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteFilteredSheet(
    count: Int,
    impacts: List<AccountBalanceImpact>,
    onExportFirst: () -> Unit,
    onConfirm: (preserveBalances: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var preserveBalances by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Open fully expanded, not half-height: the sheet is taller than the CSV one
        // and its Delete/Cancel buttons must be visible without dragging.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.transactions_delete_filtered_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = pluralStringResource(R.plurals.transactions_delete_filtered_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            ModeOption(
                selected = !preserveBalances,
                title = stringResource(R.string.transactions_delete_mode_recompute),
                hint = stringResource(R.string.transactions_delete_mode_recompute_hint),
                onSelect = { preserveBalances = false },
            )
            ModeOption(
                selected = preserveBalances,
                title = stringResource(R.string.transactions_delete_mode_preserve),
                hint = stringResource(R.string.transactions_delete_mode_preserve_hint),
                onSelect = { preserveBalances = true },
            )

            Spacer(Modifier.height(12.dp))
            ImpactPreview(preserveBalances = preserveBalances, impacts = impacts)

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onExportFirst) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.transactions_delete_export_first))
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onConfirm(preserveBalances) },
                enabled = count > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.transactions_delete_confirm))
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun ModeOption(
    selected: Boolean,
    title: String,
    hint: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImpactPreview(
    preserveBalances: Boolean,
    impacts: List<AccountBalanceImpact>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (preserveBalances) {
            Text(
                text = stringResource(R.string.transactions_delete_impact_preserve),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (impacts.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.transactions_delete_carryover_note,
                        impacts.size,
                        impacts.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (impacts.isNotEmpty()) {
            Text(
                text = stringResource(R.string.transactions_delete_impact_recompute),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            impacts.forEach { impact -> ImpactRow(impact) }
        }
    }
}

@Composable
private fun ImpactRow(impact: AccountBalanceImpact, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Text(
            text = impact.account.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = MoneyFormatter.formatSigned(impact.delta, impact.account.currency),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            color = when {
                impact.delta.signum() > 0 -> MaterialTheme.moneyColors.income
                impact.delta.signum() < 0 -> MaterialTheme.moneyColors.expense
                else -> MaterialTheme.moneyColors.neutral
            },
        )
    }
}
