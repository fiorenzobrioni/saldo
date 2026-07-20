package com.callbackdev.saldo.feature.backup

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.domain.backup.BackupSummary
import com.callbackdev.saldo.core.domain.usecase.ImportBackupUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val AVATAR_TINT_ALPHA = 0.16f

/** Mime types accepted by the restore picker: SAF providers often expose JSON as a generic stream. */
private val RESTORE_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")

/**
 * Manual backup and guided restore on a local file (PLANNING ADR 13): export
 * writes a versioned JSON document via SAF, restore validates the picked file
 * and asks for confirmation with its contents before replacing anything.
 * Fully offline, like everything else in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::onExportDestinationPicked) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onRestoreFilePicked) }

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is BackupEvent.ExportCompleted ->
                    resources.getString(R.string.backup_snackbar_exported, event.summary.transactions)

                BackupEvent.ExportFailed -> resources.getString(R.string.backup_snackbar_export_failed)

                is BackupEvent.RestoreCompleted ->
                    resources.getString(R.string.backup_snackbar_restored, event.summary.transactions)

                BackupEvent.RestoreFailed -> resources.getString(R.string.backup_snackbar_restore_failed)

                is BackupEvent.InvalidBackupFile -> resources.getString(
                    when (event.error) {
                        ImportBackupUseCase.Error.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
                        ImportBackupUseCase.Error.UNSUPPORTED_VERSION ->
                            R.string.backup_error_unsupported_version

                        ImportBackupUseCase.Error.CORRUPTED -> R.string.backup_error_corrupted
                    },
                )
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        ) {
            PrivacyHeroCard()
            Spacer(Modifier.height(SaldoDimens.cardSpacing))
            ExportCard(
                lastBackupAt = uiState.lastBackupAt,
                enabled = !uiState.isWorking,
                onExport = { exportLauncher.launch(suggestedBackupFileName()) },
            )
            Spacer(Modifier.height(SaldoDimens.cardSpacing))
            RestoreCard(
                enabled = !uiState.isWorking,
                onRestore = { restoreLauncher.launch(RESTORE_MIME_TYPES) },
            )
            Spacer(Modifier.height(SaldoDimens.cardSpacing))
            CsvHintNote()
        }
    }

    uiState.pendingRestore?.let { summary ->
        RestoreConfirmationDialog(
            summary = summary,
            onConfirm = viewModel::onRestoreConfirmed,
            onDismiss = viewModel::onRestoreDismissed,
        )
    }
}

/** `saldo-backup-YYYY-MM-DD.json`, the name suggested to the SAF create dialog. */
private fun suggestedBackupFileName(): String = "saldo-backup-${LocalDate.now()}.json"

/** Offline-first reassurance: what a backup is and where it lives (nowhere but the file). */
@Composable
private fun PrivacyHeroCard(modifier: Modifier = Modifier) {
    SaldoCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(SaldoDimens.cardPaddingLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AvatarShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = AVATAR_TINT_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.backup_hero_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.backup_hero_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExportCard(
    lastBackupAt: Instant?,
    enabled: Boolean,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(SaldoDimens.cardPadding)) {
            Text(
                text = stringResource(R.string.backup_export_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.backup_export_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = lastBackupAt
                    ?.let { stringResource(R.string.backup_last_backup, formatBackupInstant(it)) }
                    ?: stringResource(R.string.backup_last_backup_never),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onExport,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.backup_export_action))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.backup_unencrypted_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RestoreCard(
    enabled: Boolean,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(SaldoDimens.cardPadding)) {
            Text(
                text = stringResource(R.string.backup_restore_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.backup_restore_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onRestore,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.backup_restore_action))
            }
        }
    }
}

@Composable
private fun CsvHintNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
            text = stringResource(R.string.backup_csv_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The guided-restore gate: what the file contains, and that current data is replaced. */
@Composable
private fun RestoreConfirmationDialog(
    summary: BackupSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_dialog_title)) },
        text = {
            Column {
                Text(
                    text = summary.appVersion
                        ?.let {
                            stringResource(
                                R.string.backup_restore_dialog_metadata_versioned,
                                formatBackupInstant(summary.exportedAt),
                                it,
                            )
                        }
                        ?: stringResource(
                            R.string.backup_restore_dialog_metadata,
                            formatBackupInstant(summary.exportedAt),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.backup_restore_dialog_counts,
                        summary.accounts,
                        summary.transactions,
                        summary.categories,
                        summary.recurringRules,
                        summary.tags,
                        summary.budgets,
                        summary.savingsGoals,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.backup_restore_dialog_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_restore_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Localized "5 Jul 2026, 14:32" for the last-backup line and the restore dialog. */
@Composable
private fun formatBackupInstant(instant: Instant): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    return remember(instant, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMyyyyHHmm")
        instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, locale))
            .withLocaleDateCasing(locale)
    }
}
