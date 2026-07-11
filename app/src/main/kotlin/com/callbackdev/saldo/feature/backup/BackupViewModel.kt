package com.callbackdev.saldo.feature.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.BuildConfig
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.di.IoDispatcher
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.backup.BackupFile
import com.callbackdev.saldo.core.domain.backup.BackupSummary
import com.callbackdev.saldo.core.domain.usecase.ExportBackupUseCase
import com.callbackdev.saldo.core.domain.usecase.GenerateRecurringMovementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ImportBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Screen state of the backup screen; the restore confirmation is a step in it. */
data class BackupUiState(
    /** When the last backup was exported successfully; null if never. */
    val lastBackupAt: Instant? = null,
    /** True while an export or restore is running (buttons are disabled). */
    val isWorking: Boolean = false,
    /** A validated backup awaiting the user's confirmation, or null. */
    val pendingRestore: BackupSummary? = null,
)

/** One-shot outcomes surfaced as snackbars. */
sealed interface BackupEvent {
    data class ExportCompleted(val summary: BackupSummary) : BackupEvent
    data object ExportFailed : BackupEvent
    data class RestoreCompleted(val summary: BackupSummary) : BackupEvent
    data object RestoreFailed : BackupEvent
    data class InvalidBackupFile(val error: ImportBackupUseCase.Error) : BackupEvent
}

/**
 * Drives manual file backup and guided restore (PLANNING ADR 13). The screen
 * owns the SAF pickers; this ViewModel receives their [Uri]s, moves the bytes
 * on the I/O dispatcher and reports outcomes as one-shot [BackupEvent]s.
 * Restore is a two-step flow: the picked file is validated and summarized
 * first ([BackupUiState.pendingRestore]), and only an explicit confirmation
 * replaces the data.
 */
@HiltViewModel
@Suppress("LongParameterList") // Hilt wiring: one dependency per concern.
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportBackup: ExportBackupUseCase,
    private val importBackup: ImportBackupUseCase,
    private val generateRecurringMovements: GenerateRecurringMovementsUseCase,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val working = MutableStateFlow(false)
    private val pendingRestore = MutableStateFlow<PendingRestore?>(null)

    /** A validated file held between inspection and confirmation. */
    private data class PendingRestore(val file: BackupFile, val summary: BackupSummary)

    val uiState: StateFlow<BackupUiState> = combine(
        userPreferences.lastBackupAtEpochMilli.map { it?.let(Instant::ofEpochMilli) },
        working,
        pendingRestore,
    ) { lastBackupAt, isWorking, pending ->
        BackupUiState(
            lastBackupAt = lastBackupAt,
            isWorking = isWorking,
            pendingRestore = pending?.summary,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = BackupUiState(),
    )

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    /** Exports the whole database to the document the user just created. */
    fun onExportDestinationPicked(uri: Uri) {
        if (!working.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val result = suspendRunCatching {
                val export = exportBackup(appVersion = BuildConfig.VERSION_NAME)
                writeDocument(uri, export.json)
                userPreferences.setLastBackupAt(clock.millis())
                export.summary
            }
            working.value = false
            result
                .onSuccess { summary -> _events.send(BackupEvent.ExportCompleted(summary)) }
                .onFailure { _events.send(BackupEvent.ExportFailed) }
        }
    }

    /** Validates the picked file and, if readable, asks for confirmation. */
    fun onRestoreFilePicked(uri: Uri) {
        if (!working.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val inspection = suspendRunCatching { importBackup.inspect(readDocument(uri)) }
            working.value = false
            inspection
                .onSuccess { outcome ->
                    when (outcome) {
                        is ImportBackupUseCase.Inspection.Valid ->
                            pendingRestore.value = PendingRestore(outcome.file, outcome.summary)

                        is ImportBackupUseCase.Inspection.Invalid ->
                            _events.send(BackupEvent.InvalidBackupFile(outcome.error))
                    }
                }
                .onFailure { _events.send(BackupEvent.RestoreFailed) }
        }
    }

    /** Replaces every table with the confirmed backup; rolled back on failure. */
    fun onRestoreConfirmed() {
        val pending = pendingRestore.value ?: return
        if (!working.compareAndSet(expect = false, update = true)) return
        pendingRestore.value = null
        viewModelScope.launch {
            val result = suspendRunCatching { importBackup.restore(pending.file) }
            // Catch up restored rules right away (a backup may be days old), so
            // subscriptions do not wait for the next app start; failures here do
            // not fail the restore (the regular catch-up will retry at launch).
            result.onSuccess { suspendRunCatching { generateRecurringMovements() } }
            working.value = false
            result
                .onSuccess { _events.send(BackupEvent.RestoreCompleted(pending.summary)) }
                .onFailure { _events.send(BackupEvent.RestoreFailed) }
        }
    }

    fun onRestoreDismissed() {
        pendingRestore.value = null
    }

    private suspend fun writeDocument(uri: Uri, content: String) {
        withContext(ioDispatcher) {
            // "wt" truncates an existing document: re-exporting over an old
            // backup must never leave trailing bytes of the previous content.
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Cannot open $uri for writing")
            stream.bufferedWriter().use { it.write(content) }
        }
    }

    private suspend fun readDocument(uri: Uri): String =
        withContext(ioDispatcher) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open $uri for reading")
            stream.bufferedReader().use { it.readText() }
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
