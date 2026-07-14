package com.callbackdev.saldo.feature.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.domain.model.Category

/** Renders the deletion dialog currently requested by the editor view model. */
@Composable
internal fun CategoryDeleteDialogHost(
    dialog: CategoryDeleteDialog?,
    categoryName: String,
    alsoRemovesBudget: Boolean,
    onTargetSelected: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        CategoryDeleteDialog.Confirm -> DeleteAlert(
            title = stringResource(R.string.category_delete_title),
            body = stringResource(R.string.category_delete_body, categoryName)
                .withBudgetNote(alsoRemovesBudget),
            confirmLabel = stringResource(R.string.category_delete_confirm),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )

        is CategoryDeleteDialog.ConfirmUncategorize -> DeleteAlert(
            title = stringResource(R.string.category_delete_title),
            body = pluralStringResource(
                R.plurals.category_delete_uncategorize_body,
                dialog.movementCount,
                categoryName,
                dialog.movementCount,
            ).withBudgetNote(alsoRemovesBudget),
            confirmLabel = stringResource(R.string.category_delete_confirm),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )

        is CategoryDeleteDialog.Reassign -> ReassignDialog(
            dialog = dialog,
            categoryName = categoryName,
            alsoRemovesBudget = alsoRemovesBudget,
            onTargetSelected = onTargetSelected,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )

        null -> Unit
    }
}

/** Appends the budget-cascade warning when the category has a monthly budget. */
@Composable
private fun String.withBudgetNote(alsoRemovesBudget: Boolean): String =
    if (alsoRemovesBudget) {
        this + " " + stringResource(R.string.category_delete_budget_note)
    } else {
        this
    }

@Composable
private fun DeleteAlert(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ReassignDialog(
    dialog: CategoryDeleteDialog.Reassign,
    categoryName: String,
    alsoRemovesBudget: Boolean,
    onTargetSelected: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.category_reassign_title)) },
        text = {
            Column {
                Text(
                    text = pluralStringResource(
                        R.plurals.category_reassign_body,
                        dialog.movementCount,
                        categoryName,
                        dialog.movementCount,
                    ).withBudgetNote(alsoRemovesBudget),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.category_reassign_target),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(4.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(dialog.candidates, key = { it.id }) { candidate ->
                        ReassignTargetRow(
                            category = candidate,
                            selected = candidate.id == dialog.selectedTargetId,
                            onSelect = { onTargetSelected(candidate.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.category_reassign_confirm),
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
}

@Composable
private fun ReassignTargetRow(
    category: Category,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(8.dp))
        CategoryAvatar(category = category, size = 32.dp)
        Spacer(Modifier.size(12.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
