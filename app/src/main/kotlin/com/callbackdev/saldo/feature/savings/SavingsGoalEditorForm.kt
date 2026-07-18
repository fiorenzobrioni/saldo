package com.callbackdev.saldo.feature.savings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.domain.model.Account
import java.util.Currency

/** Goal name field with the savings-specific label and error. */
@Composable
internal fun SavingsGoalNameField(
    name: String,
    showError: Boolean,
    onNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.savings_editor_name)) },
        placeholder = { Text(stringResource(R.string.savings_editor_name_hint)) },
        singleLine = true,
        isError = showError,
        supportingText = if (showError) {
            { Text(stringResource(R.string.savings_editor_name_error)) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Target amount field for the goal. */
@Composable
internal fun SavingsTargetField(
    input: String,
    currency: Currency,
    showError: Boolean,
    onChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = input,
        onValueChange = onChanged,
        label = { Text(stringResource(R.string.savings_editor_target)) },
        placeholder = { Text(stringResource(R.string.editor_amount_placeholder)) },
        prefix = { Text(currency.symbol) },
        singleLine = true,
        isError = showError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.headlineSmall,
        supportingText = if (showError) {
            { Text(stringResource(R.string.savings_editor_target_error)) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Linked-savings-account picker. Lists the selectable savings accounts and, in
 * create mode, a shortcut to create a new savings account inline. In edit mode
 * the linked account is fixed, so the field is shown read-only without the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavingsAccountField(
    accounts: List<Account>,
    selectedId: Long?,
    isEditable: Boolean,
    showError: Boolean,
    onSelected: (Long) -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded && isEditable,
        onExpandedChange = { if (isEditable) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = isEditable,
            isError = showError,
            label = { Text(stringResource(R.string.savings_editor_account)) },
            leadingIcon = selected?.let { account ->
                {
                    Icon(
                        imageVector = AccountVisuals.icon(account.icon),
                        contentDescription = null,
                        tint = AccountVisuals.color(account.color),
                    )
                }
            },
            supportingText = if (showError) {
                { Text(stringResource(R.string.savings_editor_account_error)) }
            } else {
                null
            },
            trailingIcon = if (isEditable) {
                { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            } else {
                null
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = isEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && isEditable, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    leadingIcon = {
                        Icon(
                            imageVector = AccountVisuals.icon(account.icon),
                            contentDescription = null,
                            tint = AccountVisuals.color(account.color),
                        )
                    },
                    onClick = {
                        onSelected(account.id)
                        expanded = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.savings_editor_account_create)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.AddCircleOutline, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onCreateAccount()
                },
            )
        }
    }
}
