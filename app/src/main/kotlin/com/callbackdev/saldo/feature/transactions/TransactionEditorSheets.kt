package com.callbackdev.saldo.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.Tag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Bottom sheet listing the pickable accounts with their current balance.
 * [disabledAccountId] greys out the other leg of a transfer so the two legs
 * can never point at the same account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountPickerSheet(
    title: String,
    accounts: List<AccountWithBalance>,
    selectedAccountId: Long?,
    disabledAccountId: Long?,
    onSelect: (AccountWithBalance) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            items(accounts, key = { it.account.id }) { item ->
                val enabled = item.account.id != disabledAccountId
                AccountPickerRow(
                    item = item,
                    isSelected = item.account.id == selectedAccountId,
                    enabled = enabled,
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun AccountPickerRow(
    item: AccountWithBalance,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = item.account
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.4f)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            val avatarColor = AccountVisuals.color(account.color)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(AvatarShape)
                    .background(avatarColor),
            ) {
                Icon(
                    imageVector = AccountVisuals.icon(account.icon),
                    contentDescription = null,
                    tint = contentColorOn(avatarColor),
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Text(
                text = MoneyFormatter.format(item.balance, account.currency),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Bottom sheet to assign existing tags and create new ones inline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagPickerSheet(
    allTags: List<Tag>,
    selectedTagIds: Set<Long>,
    onToggle: (Tag) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newTagName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.transaction_editor_tags),
                style = MaterialTheme.typography.titleMedium,
            )
            if (allTags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in selectedTagIds,
                            onClick = { onToggle(tag) },
                            label = { Text(tag.name) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text(stringResource(R.string.transaction_editor_new_tag)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(12.dp))
                FilledTonalIconButton(
                    onClick = {
                        onCreate(newTagName)
                        newTagName = ""
                    },
                    enabled = newTagName.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.transaction_editor_add_tag),
                    )
                }
            }
        }
    }
}

/** Bottom sheet listing every category for the current type, opened from "All". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryPickerSheet(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.transaction_editor_category),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            CategoryGrid(
                categories = categories,
                selectedId = selectedId,
                onSelect = onSelect,
            )
        }
    }
}

/** Material date picker preset on the movement's current date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        // Calendar-only: the input/calendar mode toggle animates slowly and janky,
        // and typing a date adds little here.
        DatePicker(state = state, showModeToggle = false)
    }
}

/** Confirmation before permanently deleting the movement being edited. */
@Composable
internal fun DeleteTransactionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transaction_delete_title)) },
        text = { Text(stringResource(R.string.transaction_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.transaction_editor_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
