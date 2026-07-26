package com.callbackdev.saldo.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.AmountTarget
import com.callbackdev.saldo.core.designsystem.component.AmountTextField
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.Account

private const val MIN_DAY = 1
private const val MAX_DAY = 31

/**
 * Credit card configuration block of the account editor (shown only for a
 * credit card account): the debit-vs-credit guidance, the billing cycle days,
 * the linked account, the optional credit limit and the statement charge mode.
 */
@Suppress("LongParameterList")
@Composable
internal fun CreditCardSection(
    uiState: AccountEditorUiState,
    linkedCandidates: List<Account>,
    onStatementClosingDayChanged: (Int) -> Unit,
    onPaymentDueDayChanged: (Int) -> Unit,
    onLinkedAccountChanged: (Long?) -> Unit,
    creditLimitTarget: AmountTarget,
    onActivateCreditLimit: () -> Unit,
    onStatementAutoPostChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // The type description under the type chips already explains what a
        // credit card account is; this section is configuration only.
        CreditCardSectionLabel(stringResource(R.string.account_cc_section_title))
        DayStepper(
            title = stringResource(R.string.account_cc_closing_day),
            hint = stringResource(R.string.account_cc_closing_day_hint),
            value = uiState.statementClosingDay,
            onChange = onStatementClosingDayChanged,
        )
        Spacer(Modifier.height(12.dp))
        DayStepper(
            title = stringResource(R.string.account_cc_due_day),
            hint = stringResource(R.string.account_cc_due_day_hint),
            value = uiState.paymentDueDay,
            onChange = onPaymentDueDayChanged,
        )
        Spacer(Modifier.height(16.dp))
        LinkedAccountField(
            candidates = linkedCandidates,
            selectedId = uiState.linkedAccountId,
            onSelect = onLinkedAccountChanged,
        )
        Spacer(Modifier.height(16.dp))
        AmountTextField(
            target = creditLimitTarget,
            label = stringResource(R.string.account_cc_limit),
            onActivate = onActivateCreditLimit,
            suffix = uiState.currency.symbol,
            supportingText = stringResource(R.string.account_cc_limit_hint),
        )
        Spacer(Modifier.height(16.dp))
        StatementModeSelector(
            autoPost = uiState.statementAutoPost,
            onChange = onStatementAutoPostChanged,
        )
    }
}

@Composable
private fun CreditCardSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun DayStepper(
    title: String,
    hint: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onChange(value - 1) },
            enabled = value > MIN_DAY,
        ) {
            Icon(
                imageVector = Icons.Outlined.Remove,
                contentDescription = stringResource(R.string.account_cc_day_decrease),
            )
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium.tabularNumbers(),
            modifier = Modifier.widthForTwoDigits(),
        )
        IconButton(
            onClick = { onChange(value + 1) },
            enabled = value < MAX_DAY,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.account_cc_day_increase),
            )
        }
    }
}

/** Fixed width so the +/- buttons never shift between one- and two-digit days. */
private fun Modifier.widthForTwoDigits(): Modifier = this.then(Modifier.size(width = 28.dp, height = 24.dp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkedAccountField(
    candidates: List<Account>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = candidates.firstOrNull { it.id == selectedId }?.name
    val label = when {
        candidates.isEmpty() -> stringResource(R.string.account_cc_linked_account_empty)
        selectedName != null -> selectedName
        else -> stringResource(R.string.account_cc_linked_account_none)
    }
    ExposedDropdownMenuBox(
        expanded = expanded && candidates.isNotEmpty(),
        onExpandedChange = { if (candidates.isNotEmpty()) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = candidates.isNotEmpty(),
            label = { Text(stringResource(R.string.account_cc_linked_account)) },
            supportingText = { Text(stringResource(R.string.account_cc_linked_account_hint)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = candidates.isNotEmpty())
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            candidates.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        onSelect(account.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementModeSelector(
    autoPost: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.account_cc_mode_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !autoPost,
                onClick = { onChange(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.account_cc_mode_confirm))
            }
            SegmentedButton(
                selected = autoPost,
                onClick = { onChange(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.account_cc_mode_auto))
            }
        }
        Text(
            text = stringResource(
                if (autoPost) R.string.account_cc_mode_auto_hint else R.string.account_cc_mode_confirm_hint,
            ),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
