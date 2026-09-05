package com.callbackdev.saldo.feature.transactions.importer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R

/** The fields in the order they are offered: the two required ones first, then by how common they are. */
private val MAPPING_FIELD_ORDER: List<CsvField> = listOf(
    CsvField.DATE,
    CsvField.AMOUNT,
    CsvField.TYPE,
    CsvField.DESCRIPTION,
    CsvField.ACCOUNT,
    CsvField.CATEGORY,
    CsvField.CURRENCY,
    CsvField.TO_ACCOUNT,
    CsvField.RECEIVED_AMOUNT,
    CsvField.RECEIVED_CURRENCY,
    CsvField.TAGS,
    CsvField.NOTE,
    CsvField.COUNTERPARTY,
    CsvField.EXCLUDED_FROM_STATS,
    CsvField.REFUND,
)

/** The fields shown in the preview lines, when mapped. */
private val PREVIEW_FIELDS: List<CsvField> = listOf(
    CsvField.DATE,
    CsvField.AMOUNT,
    CsvField.TYPE,
    CsvField.DESCRIPTION,
    CsvField.ACCOUNT,
    CsvField.CATEGORY,
)

private val REQUIRED_FIELDS: Set<CsvField> = setOf(CsvField.DATE, CsvField.AMOUNT)

/**
 * The column-mapping stage of the CSV import (Fase 39, F5): one picker per
 * importer field over the file's header cells, the decimal convention, a
 * preview of the first rows read through the current mapping, and an optional
 * name to save the mapping under for the next file with the same header.
 */
@Composable
internal fun CsvMappingContent(stage: CsvImportStage.Mapping, callbacks: CsvMappingCallbacks) {
    SheetTitle(stringResource(R.string.csv_mapping_title))
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.csv_mapping_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MAPPING_FIELD_ORDER.forEach { field ->
            ColumnPicker(
                field = field,
                header = stage.header,
                selectedIndex = stage.fields[field],
                enabled = !stage.isBusy,
                onSelect = { index -> callbacks.onFieldChange(field, index) },
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    SectionLabel(stringResource(R.string.csv_mapping_decimal_title))
    Spacer(Modifier.height(8.dp))
    DecimalMarkSelector(
        selected = stage.decimalMark,
        inferred = stage.inferredDecimalMark,
        enabled = !stage.isBusy,
        onSelected = callbacks.onDecimalMarkChange,
    )
    Spacer(Modifier.height(20.dp))
    SectionLabel(stringResource(R.string.csv_mapping_preview_title))
    Spacer(Modifier.height(8.dp))
    MappingPreview(stage)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = stage.saveAsName,
        onValueChange = callbacks.onNameChange,
        enabled = !stage.isBusy,
        singleLine = true,
        label = { Text(stringResource(R.string.csv_mapping_save_as)) },
        supportingText = { Text(stringResource(R.string.csv_mapping_save_as_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = callbacks.onConfirm,
        enabled = stage.isComplete && !stage.isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (stage.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(stringResource(R.string.csv_mapping_continue))
        }
    }
}

/** One importer field bound to one of the file's columns, or to none. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnPicker(
    field: CsvField,
    header: List<String>,
    selectedIndex: Int?,
    enabled: Boolean,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val fieldLabel = stringResource(field.labelRes())
    val label = if (field in REQUIRED_FIELDS) {
        stringResource(R.string.csv_mapping_required_label, fieldLabel)
    } else {
        fieldLabel
    }
    val unused = stringResource(R.string.csv_mapping_column_unused)
    val value = selectedIndex?.let { columnLabel(header, it) } ?: unused
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(unused) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            header.forEachIndexed { index, _ ->
                DropdownMenuItem(
                    text = { Text(columnLabel(header, index)) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** The header cell as written, or a positional name when the cell is blank. */
@Composable
private fun columnLabel(header: List<String>, index: Int): String =
    header.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
        ?: stringResource(R.string.csv_mapping_column_blank, index + 1)

@Composable
private fun DecimalMarkSelector(
    selected: Char?,
    inferred: Char?,
    enabled: Boolean,
    onSelected: (Char?) -> Unit,
) {
    val options: List<Pair<Char?, Int>> = listOf(
        null to R.string.csv_mapping_decimal_auto,
        ',' to R.string.csv_mapping_decimal_comma,
        '.' to R.string.csv_mapping_decimal_dot,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mark, labelRes) ->
            SegmentedButton(
                selected = selected == mark,
                enabled = enabled,
                onClick = { onSelected(mark) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
    if (selected == null) {
        Spacer(Modifier.height(6.dp))
        val detectedExample = when (inferred) {
            null -> null
            ',' -> stringResource(R.string.csv_mapping_decimal_comma)
            else -> stringResource(R.string.csv_mapping_decimal_dot)
        }
        Text(
            text = detectedExample?.let { stringResource(R.string.csv_mapping_decimal_auto_detected, it) }
                ?: stringResource(R.string.csv_mapping_decimal_auto_locale),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The first rows, each read through the current mapping: "Date: … · Amount: … · …". */
@Composable
private fun MappingPreview(stage: CsvImportStage.Mapping) {
    if (!stage.isComplete || stage.sampleRows.isEmpty()) {
        Text(
            text = stringResource(R.string.csv_mapping_preview_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val separator = stringResource(R.string.csv_mapping_preview_separator)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        stage.sampleRows.forEach { row ->
            val parts = PREVIEW_FIELDS.mapNotNull { field ->
                val index = stage.fields[field] ?: return@mapNotNull null
                val value = row.getOrNull(index)?.trim().orEmpty()
                stringResource(R.string.csv_mapping_preview_cell, stringResource(field.labelRes()), value)
            }
            Text(
                text = parts.joinToString(separator = separator),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

/** The app's own column label for a field, the same text the export writes as header. */
@StringRes
internal fun CsvField.labelRes(): Int = when (this) {
    CsvField.DATE -> R.string.csv_header_date
    CsvField.TYPE -> R.string.csv_header_type
    CsvField.CATEGORY -> R.string.csv_header_category
    CsvField.DESCRIPTION -> R.string.csv_header_description
    CsvField.ACCOUNT -> R.string.csv_header_account
    CsvField.TO_ACCOUNT -> R.string.csv_header_to_account
    CsvField.AMOUNT -> R.string.csv_header_amount
    CsvField.CURRENCY -> R.string.csv_header_currency
    CsvField.RECEIVED_AMOUNT -> R.string.csv_header_received_amount
    CsvField.RECEIVED_CURRENCY -> R.string.csv_header_received_currency
    CsvField.TAGS -> R.string.csv_header_tags
    CsvField.NOTE -> R.string.csv_header_note
    CsvField.COUNTERPARTY -> R.string.csv_header_counterparty
    CsvField.EXCLUDED_FROM_STATS -> R.string.csv_header_excluded_from_stats
    CsvField.REFUND -> R.string.csv_header_refund
}
