package com.callbackdev.saldo.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.prefs.SavedCsvMapping

/**
 * The saved CSV column mappings (Fase 39, F5): name and column count per row,
 * with a delete action. Mappings are created from the import flow, never here,
 * so the dialog only lists and removes.
 */
@Composable
internal fun CsvMappingsDialog(
    mappings: List<SavedCsvMapping>,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_csv_mappings)) },
        text = {
            if (mappings.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_csv_mappings_hint_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    mappings.forEach { mapping ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = mapping.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.settings_csv_mappings_columns,
                                        mapping.fields.size,
                                        mapping.fields.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDelete(mapping.name) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(
                                        R.string.settings_csv_mappings_delete,
                                        mapping.name,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
