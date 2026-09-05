package com.callbackdev.saldo.feature.transactions.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R

/**
 * The guided CSV import surface. A single bottom sheet renders the current
 * [stage]: the file being read, the column mapping when the header is not
 * recognized (or on request from the preview), the dry-run preview (counts,
 * what will be created, and the tolerated fixes, with the safety options the
 * user can tune), or the final report. The preview never touches data; only
 * [onConfirm] does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // One callback per user action of the sheet.
@Composable
fun CsvImportSheet(
    stage: CsvImportStage,
    onOptionsChange: (CsvImportOptions) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onEditMapping: () -> Unit,
    mapping: CsvMappingCallbacks,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            when (stage) {
                CsvImportStage.Reading -> ReadingContent()
                is CsvImportStage.Mapping -> CsvMappingContent(stage, mapping)
                is CsvImportStage.Preview -> PreviewContent(stage, onOptionsChange, onConfirm, onEditMapping)
                is CsvImportStage.Done -> DoneContent(stage.report, onDismiss)
            }
        }
    }
}

@Composable
private fun ReadingContent() {
    SheetTitle(stringResource(R.string.csv_import_title))
    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.csv_import_reading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PreviewContent(
    stage: CsvImportStage.Preview,
    onOptionsChange: (CsvImportOptions) -> Unit,
    onConfirm: () -> Unit,
    onEditMapping: () -> Unit,
) {
    val analysis = stage.analysis
    SheetTitle(stringResource(R.string.csv_import_title))
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (analysis.isEmpty) {
            stringResource(R.string.csv_import_summary_none)
        } else {
            pluralStringResource(
                R.plurals.csv_import_summary_importable,
                analysis.importableCount,
                analysis.importableCount,
            )
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(16.dp))
    OutcomeStats(analysis)
    Spacer(Modifier.height(10.dp))
    ColumnsRow(mappingName = stage.mappingName, enabled = !stage.isBusy, onEdit = onEditMapping)
    if (analysis.newAccounts.isNotEmpty() || analysis.newCategories.isNotEmpty() ||
        analysis.newTags.isNotEmpty()
    ) {
        Spacer(Modifier.height(16.dp))
        CreatesSection(analysis)
    }
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(12.dp))
    ImportOptionsSection(stage.options, enabled = !stage.isBusy, onOptionsChange = onOptionsChange)
    Spacer(Modifier.height(12.dp))
    SafetyNote(stringResource(R.string.csv_import_safety_note))
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onConfirm,
        enabled = !analysis.isEmpty && !stage.isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (stage.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.csv_import_action,
                    analysis.importableCount,
                    analysis.importableCount,
                ),
            )
        }
    }
}

/** Where the columns came from (header names or a saved mapping) and the way to change them. */
@Composable
private fun ColumnsRow(mappingName: String?, enabled: Boolean, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.ViewColumn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = mappingName?.let { stringResource(R.string.csv_import_columns_saved, it) }
                ?: stringResource(R.string.csv_import_columns_auto),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onEdit, enabled = enabled) {
            Text(stringResource(R.string.csv_import_edit_columns))
        }
    }
}

@Composable
private fun OutcomeStats(analysis: CsvImportAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (analysis.duplicateCount > 0) {
            StatRow(
                icon = Icons.Outlined.ContentCopy,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                text = pluralStringResource(
                    R.plurals.csv_import_stat_duplicates,
                    analysis.duplicateCount,
                    analysis.duplicateCount,
                ),
            )
        }
        if (analysis.adjustedCount > 0) {
            StatRow(
                icon = Icons.Outlined.AutoFixHigh,
                tint = MaterialTheme.colorScheme.tertiary,
                text = pluralStringResource(
                    R.plurals.csv_import_stat_adjusted,
                    analysis.adjustedCount,
                    analysis.adjustedCount,
                ),
            )
        }
        if (analysis.invalidCount > 0) {
            StatRow(
                icon = Icons.Outlined.ErrorOutline,
                tint = MaterialTheme.colorScheme.error,
                text = pluralStringResource(
                    R.plurals.csv_import_stat_invalid,
                    analysis.invalidCount,
                    analysis.invalidCount,
                ),
            )
        }
    }
}

@Composable
private fun CreatesSection(analysis: CsvImportAnalysis) {
    Column {
        SectionLabel(stringResource(R.string.csv_import_creates_title))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (analysis.newAccounts.isNotEmpty()) {
                StatRow(
                    icon = Icons.Outlined.AddCircleOutline,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = pluralStringResource(
                        R.plurals.csv_import_creates_accounts,
                        analysis.newAccounts.size,
                        analysis.newAccounts.size,
                    ) + " · " + analysis.newAccounts.joinToString(", ") { it.name },
                )
            }
            if (analysis.newCategories.isNotEmpty()) {
                StatRow(
                    icon = Icons.Outlined.AddCircleOutline,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = pluralStringResource(
                        R.plurals.csv_import_creates_categories,
                        analysis.newCategories.size,
                        analysis.newCategories.size,
                    ) + " · " + analysis.newCategories.joinToString(", ") { it.name },
                )
            }
            if (analysis.newTags.isNotEmpty()) {
                StatRow(
                    icon = Icons.Outlined.AddCircleOutline,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = pluralStringResource(
                        R.plurals.csv_import_creates_tags,
                        analysis.newTags.size,
                        analysis.newTags.size,
                    ) + " · " + analysis.newTags.joinToString(", "),
                )
            }
        }
    }
}

@Composable
private fun ImportOptionsSection(
    options: CsvImportOptions,
    enabled: Boolean,
    onOptionsChange: (CsvImportOptions) -> Unit,
) {
    SectionLabel(stringResource(R.string.csv_import_options_title))
    Spacer(Modifier.height(4.dp))
    OptionSwitch(
        title = stringResource(R.string.csv_import_option_skip_duplicates),
        hint = stringResource(R.string.csv_import_option_skip_duplicates_hint),
        checked = options.skipDuplicates,
        enabled = enabled,
        onCheckedChange = { onOptionsChange(options.copy(skipDuplicates = it)) },
    )
    OptionSwitch(
        title = stringResource(R.string.csv_import_option_create_accounts),
        hint = stringResource(R.string.csv_import_option_create_accounts_hint),
        checked = options.createMissingAccounts,
        enabled = enabled,
        onCheckedChange = { onOptionsChange(options.copy(createMissingAccounts = it)) },
    )
    OptionSwitch(
        title = stringResource(R.string.csv_import_option_create_categories),
        hint = stringResource(R.string.csv_import_option_create_categories_hint),
        checked = options.createMissingCategories,
        enabled = enabled,
        onCheckedChange = { onOptionsChange(options.copy(createMissingCategories = it)) },
    )
    OptionSwitch(
        title = stringResource(R.string.csv_import_option_create_tags),
        hint = stringResource(R.string.csv_import_option_create_tags_hint),
        checked = options.createMissingTags,
        enabled = enabled,
        onCheckedChange = { onOptionsChange(options.copy(createMissingTags = it)) },
    )
}

@Composable
private fun DoneContent(report: CsvImportReport, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.size(10.dp))
        SheetTitle(stringResource(R.string.csv_import_done_title))
    }
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatRow(
            icon = Icons.Outlined.CheckCircle,
            tint = MaterialTheme.colorScheme.primary,
            text = pluralStringResource(R.plurals.csv_import_done_imported, report.imported, report.imported),
        )
        if (report.duplicatesSkipped > 0) {
            StatRow(
                icon = Icons.Outlined.ContentCopy,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                text = pluralStringResource(
                    R.plurals.csv_import_stat_duplicates,
                    report.duplicatesSkipped,
                    report.duplicatesSkipped,
                ),
            )
        }
        if (report.adjusted > 0) {
            StatRow(
                icon = Icons.Outlined.AutoFixHigh,
                tint = MaterialTheme.colorScheme.tertiary,
                text = pluralStringResource(R.plurals.csv_import_stat_adjusted, report.adjusted, report.adjusted),
            )
        }
        if (report.invalid > 0) {
            StatRow(
                icon = Icons.Outlined.ErrorOutline,
                tint = MaterialTheme.colorScheme.error,
                text = pluralStringResource(R.plurals.csv_import_stat_invalid, report.invalid, report.invalid),
            )
        }
    }
    if (report.createdAccounts.isNotEmpty() || report.createdCategories.isNotEmpty() ||
        report.createdTags.isNotEmpty()
    ) {
        Spacer(Modifier.height(16.dp))
        DoneCreated(report)
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.csv_import_done_action))
    }
}

@Composable
private fun DoneCreated(report: CsvImportReport) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringResource(R.string.csv_import_creates_title))
        if (report.createdAccounts.isNotEmpty()) {
            CreatedLine(
                pluralStringResource(
                    R.plurals.csv_import_creates_accounts,
                    report.createdAccounts.size,
                    report.createdAccounts.size,
                ),
                report.createdAccounts,
            )
        }
        if (report.createdCategories.isNotEmpty()) {
            CreatedLine(
                pluralStringResource(
                    R.plurals.csv_import_creates_categories,
                    report.createdCategories.size,
                    report.createdCategories.size,
                ),
                report.createdCategories,
            )
        }
        if (report.createdTags.isNotEmpty()) {
            CreatedLine(
                pluralStringResource(
                    R.plurals.csv_import_creates_tags,
                    report.createdTags.size,
                    report.createdTags.size,
                ),
                report.createdTags,
            )
        }
    }
}

@Composable
private fun CreatedLine(label: String, names: List<String>) {
    Text(
        text = "$label · ${names.joinToString(", ")}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --- Small building blocks ---

@Composable
internal fun SheetTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge)
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun StatRow(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OptionSwitch(
    title: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SafetyNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
