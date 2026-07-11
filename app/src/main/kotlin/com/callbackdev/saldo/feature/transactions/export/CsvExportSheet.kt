package com.callbackdev.saldo.feature.transactions.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.prefs.CsvSeparator

/**
 * Pre-flight of the CSV export: says what will be exported (the current view,
 * filters applied), lets the user pick the column separator (persisted), and
 * hands off to the Share Sheet. The separator choice explains its decimal
 * convention, so the "why two options" is answered where it is asked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvExportSheet(
    count: Int,
    separator: CsvSeparator,
    onSeparatorSelected: (CsvSeparator) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.csv_export_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = pluralStringResource(R.plurals.csv_export_body, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.csv_separator),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            SeparatorSelector(selected = separator, onSelected = onSeparatorSelected)
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    when (separator) {
                        CsvSeparator.SEMICOLON -> R.string.csv_separator_hint_semicolon
                        CsvSeparator.COMMA -> R.string.csv_separator_hint_comma
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onExport,
                enabled = count > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.csv_export_action))
            }
        }
    }
}

@Composable
private fun SeparatorSelector(
    selected: CsvSeparator,
    onSelected: (CsvSeparator) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        CsvSeparator.SEMICOLON to R.string.csv_separator_semicolon,
        CsvSeparator.COMMA to R.string.csv_separator_comma,
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, labelRes) ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}
