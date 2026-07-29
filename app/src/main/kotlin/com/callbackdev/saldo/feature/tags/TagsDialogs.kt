package com.callbackdev.saldo.feature.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R

/** Quick actions for a tapped tag: rename, merge others into it, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagActionsSheet(
    item: TagListItem,
    canMerge: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            TagAvatar(name = item.tag.name)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = item.tag.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = movementCountLabel(item.movementCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SheetAction(
            icon = Icons.Outlined.Edit,
            label = stringResource(R.string.tags_action_rename),
            onClick = onRename,
        )
        if (canMerge) {
            SheetAction(
                icon = Icons.AutoMirrored.Outlined.CallMerge,
                label = stringResource(R.string.tags_action_merge),
                onClick = onMerge,
            )
        }
        SheetAction(
            icon = Icons.Outlined.Delete,
            label = stringResource(R.string.tags_action_delete),
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
internal fun TagsDialogHost(
    dialog: TagsDialog?,
    onRenameInputChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onMergeSourceToggled: (Long) -> Unit,
    onConfirmMerge: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        is TagsDialog.Rename -> RenameTagDialog(
            dialog = dialog,
            onInputChange = onRenameInputChange,
            onConfirm = onConfirmRename,
            onDismiss = onDismiss,
        )

        is TagsDialog.Merge -> MergeTagsDialog(
            dialog = dialog,
            onSourceToggled = onMergeSourceToggled,
            onConfirm = onConfirmMerge,
            onDismiss = onDismiss,
        )

        is TagsDialog.ConfirmDelete -> DeleteTagDialog(
            dialog = dialog,
            onConfirm = onConfirmDelete,
            onDismiss = onDismiss,
        )

        null -> Unit
    }
}

/**
 * Rename, with the collision surfaced in place: typing a name that already
 * exists turns the confirm into a merge and says so under the field, instead
 * of letting a near-duplicate through or failing after the fact.
 */
@Composable
private fun RenameTagDialog(
    dialog: TagsDialog.Rename,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        title = { Text(stringResource(R.string.tags_rename_title)) },
        text = {
            OutlinedTextField(
                value = dialog.input,
                onValueChange = onInputChange,
                label = { Text(stringResource(R.string.tags_rename_label)) },
                singleLine = true,
                supportingText = dialog.collision?.let { collision ->
                    { Text(stringResource(R.string.tags_rename_collision, collision.name)) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = dialog.canConfirm) {
                Text(
                    stringResource(
                        if (dialog.collision != null) {
                            R.string.tags_merge_confirm
                        } else {
                            R.string.action_save
                        },
                    ),
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

/** Pick the tags to absorb into the target; their movements move, they disappear. */
@Composable
private fun MergeTagsDialog(
    dialog: TagsDialog.Merge,
    onSourceToggled: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.CallMerge, contentDescription = null) },
        title = { Text(stringResource(R.string.tags_merge_title, dialog.target.tag.name)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.tags_merge_body, dialog.target.tag.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(dialog.candidates, key = { it.tag.id }) { candidate ->
                        MergeSourceRow(
                            item = candidate,
                            checked = candidate.tag.id in dialog.selectedIds,
                            onToggle = { onSourceToggled(candidate.tag.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = dialog.selectedIds.isNotEmpty()) {
                Text(stringResource(R.string.tags_merge_confirm))
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
private fun MergeSourceRow(
    item: TagListItem,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.size(8.dp))
        TagAvatar(name = item.tag.name, size = 32.dp)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = item.tag.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = movementCountLabel(item.movementCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Delete with an explicit confirmation, never an undo: the removal touches
 * every cross-ref of the tag and a partial restore would lie. The body spells
 * out that the movements themselves stay.
 */
@Composable
private fun DeleteTagDialog(
    dialog: TagsDialog.ConfirmDelete,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.tags_delete_title)) },
        text = {
            Text(
                if (dialog.movementCount == 0) {
                    stringResource(R.string.tags_delete_body_unused, dialog.tag.name)
                } else {
                    pluralStringResource(
                        R.plurals.tags_delete_body,
                        dialog.movementCount,
                        dialog.tag.name,
                        dialog.movementCount,
                    )
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.tags_delete_confirm),
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
