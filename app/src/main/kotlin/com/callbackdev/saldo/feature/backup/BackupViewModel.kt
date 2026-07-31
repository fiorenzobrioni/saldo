package com.callbackdev.saldo.feature.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.BuildConfig
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.di.DefaultDispatcher
import com.callbackdev.saldo.core.common.di.IoDispatcher
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.backup.BackupFile
import com.callbackdev.saldo.core.domain.backup.BackupSummary
import com.callbackdev.saldo.core.domain.backup.EncryptedBackup
import com.callbackdev.saldo.core.domain.usecase.EraseAllDataUseCase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** The passphrase dialog of the restore, and what the last attempt did. */
data class UnlockRequest(
    val isUnlocking: Boolean = false,
    /** True after a rejected attempt, so the dialog can say so in place. */
    val failed: Boolean = false,
)

/** Screen state of the backup screen; the restore confirmation is a step in it. */
data class BackupUiState(
    /** When the last backup was exported successfully; null if never. */
    val lastBackupAt: Instant? = null,
    /** True while an export or restore is running (buttons are disabled). */
    val isWorking: Boolean = false,
    /** Whether the export encrypts the file with a passphrase (Fase 22). */
    val isEncryptionEnabled: Boolean = false,
    /** True while the export is asking for a new passphrase. */
    val isAskingExportPassphrase: Boolean = false,
    /** Set while a picked file waits for its passphrase; null otherwise. */
    val unlockRequest: UnlockRequest? = null,
    /** A validated backup awaiting the user's confirmation, or null. */
    val pendingRestore: BackupSummary? = null,
    /** True while the erase-everything confirmation is on screen. */
    val isConfirmingErase: Boolean = false,
)

/** One-shot outcomes surfaced as snackbars, plus the picker hand-offs. */
sealed interface BackupEvent {
    data class ExportCompleted(val summary: BackupSummary) : BackupEvent
    data object ExportFailed : BackupEvent
    data class RestoreCompleted(val summary: BackupSummary) : BackupEvent
    data object RestoreFailed : BackupEvent
    data class InvalidBackupFile(val error: ImportBackupUseCase.Error) : BackupEvent

    /**
     * The export is ready to write and needs a destination. It is an event and
     * not a state because the SAF picker belongs to the screen: with encryption
     * on, it is only sent once the passphrase has been confirmed.
     */
    data class LaunchExportPicker(val encrypted: Boolean) : BackupEvent

    /**
     * The erase failed and nothing was touched. There is no success twin: a
     * completed erase sends the app to the onboarding, so a snackbar would be
     * posted onto a screen that is already gone.
     */
    data object EraseFailed : BackupEvent
}

/**
 * Drives manual file backup and guided restore (PLANNING ADR 13). The screen
 * owns the SAF pickers; this ViewModel receives their [Uri]s, moves the bytes
 * on the I/O dispatcher and reports outcomes as one-shot [BackupEvent]s.
 * Restore is a two-step flow: the picked file is validated and summarized
 * first ([BackupUiState.pendingRestore]), and only an explicit confirmation
 * replaces the data.
 *
 * With encryption on (Fase 22) each side gains one step before that: the export
 * asks for a passphrase before the destination picker, and a picked container
 * asks for one before it can even be summarized. Serialization, key derivation
 * and encryption run on the CPU dispatcher: the derivation alone is half a
 * second by design, and none of it belongs on the main thread.
 *
 * The passphrase never reaches a field of the UI state and is never persisted.
 * It is held in a [CharArray] only for the round trip through the SAF picker and
 * wiped as soon as it has been used - or as soon as the user backs out.
 */
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions") // Hilt wiring; one handler per user action.
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportBackup: ExportBackupUseCase,
    private val importBackup: ImportBackupUseCase,
    private val eraseAllData: EraseAllDataUseCase,
    private val generateRecurringMovements: GenerateRecurringMovementsUseCase,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val working = MutableStateFlow(false)
    private val askingExportPassphrase = MutableStateFlow(false)
    private val unlockRequest = MutableStateFlow<UnlockRequest?>(null)
    private val pendingRestore = MutableStateFlow<PendingRestore?>(null)
    private val confirmingErase = MutableStateFlow(false)

    /** A validated file held between inspection and confirmation. */
    private data class PendingRestore(val file: BackupFile, val summary: BackupSummary)

    /** The container of a picked file, held while its passphrase is asked. */
    private var lockedEnvelope: EncryptedBackup? = null

    /** The export passphrase, alive only across the destination picker. */
    private var exportPassphrase: CharArray? = null

    val uiState: StateFlow<BackupUiState> = combine(
        userPreferences.lastBackupAtEpochMilli.map { it?.let(Instant::ofEpochMilli) },
        userPreferences.backupEncryptionEnabled,
        combine(working, askingExportPassphrase, unlockRequest, ::Triple),
        pendingRestore,
        confirmingErase,
    ) { lastBackupAt, isEncryptionEnabled, (isWorking, isAsking, unlock), pending, isConfirmingErase ->
        BackupUiState(
            lastBackupAt = lastBackupAt,
            isWorking = isWorking,
            isEncryptionEnabled = isEncryptionEnabled,
            isAskingExportPassphrase = isAsking,
            unlockRequest = unlock,
            pendingRestore = pending?.summary,
            isConfirmingErase = isConfirmingErase,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = BackupUiState(),
    )

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    fun onEncryptionEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setBackupEncryptionEnabled(enabled) }
    }

    /**
     * Starts an export: with encryption on it asks for the passphrase first, so
     * the user is never sent to the file picker for a file that then cannot be
     * written.
     */
    fun onExportRequested() {
        if (working.value) return
        viewModelScope.launch {
            // Read the preference rather than the UI state: the state flow only
            // holds a value while the screen is collecting it.
            if (userPreferences.backupEncryptionEnabled.first()) {
                askingExportPassphrase.value = true
            } else {
                _events.send(BackupEvent.LaunchExportPicker(encrypted = false))
            }
        }
    }

    fun onExportPassphraseConfirmed(passphrase: String) {
        exportPassphrase?.fill(NUL)
        exportPassphrase = passphrase.toCharArray()
        askingExportPassphrase.value = false
        viewModelScope.launch { _events.send(BackupEvent.LaunchExportPicker(encrypted = true)) }
    }

    fun onExportPassphraseDismissed() {
        askingExportPassphrase.value = false
        clearExportPassphrase()
    }

    /** The user backed out of the destination picker: the passphrase goes with it. */
    fun onExportCancelled() {
        clearExportPassphrase()
    }

    /** Exports the whole database to the document the user just created. */
    fun onExportDestinationPicked(uri: Uri) {
        if (!working.compareAndSet(expect = false, update = true)) return
        val passphrase = exportPassphrase
        exportPassphrase = null
        viewModelScope.launch {
            val result = suspendRunCatching {
                val export = withContext(defaultDispatcher) {
                    exportBackup(appVersion = BuildConfig.VERSION_NAME, passphrase = passphrase)
                }
                writeDocument(uri, export.content)
                userPreferences.setLastBackupAt(clock.millis())
                export.summary
            }
            passphrase?.fill(NUL)
            working.value = false
            result
                .onSuccess { summary -> _events.send(BackupEvent.ExportCompleted(summary)) }
                .onFailure { _events.send(BackupEvent.ExportFailed) }
        }
    }

    /**
     * Validates the picked file: a plain backup goes straight to the
     * confirmation step, a container asks for its passphrase first.
     */
    fun onRestoreFilePicked(uri: Uri) {
        if (!working.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val inspection = suspendRunCatching {
                val content = readDocument(uri)
                withContext(defaultDispatcher) { importBackup.inspect(content) }
            }
            working.value = false
            inspection
                .onSuccess { outcome -> onInspected(outcome) }
                .onFailure { _events.send(BackupEvent.RestoreFailed) }
        }
    }

    /** Opens the picked container; a rejected passphrase keeps the dialog open. */
    fun onUnlockPassphraseSubmitted(passphrase: String) {
        val envelope = lockedEnvelope ?: return
        if (unlockRequest.value?.isUnlocking == true) return
        unlockRequest.value = UnlockRequest(isUnlocking = true)
        viewModelScope.launch {
            val characters = passphrase.toCharArray()
            val inspection = withContext(defaultDispatcher) {
                importBackup.unlock(envelope, characters)
            }
            characters.fill(NUL)
            if (inspection is ImportBackupUseCase.Inspection.Invalid &&
                inspection.error == ImportBackupUseCase.Error.WRONG_PASSPHRASE
            ) {
                // Only this one failure keeps the dialog open: it is the only one
                // the user can fix by typing again.
                unlockRequest.value = UnlockRequest(failed = true)
            } else {
                unlockRequest.value = null
                lockedEnvelope = null
                onInspected(inspection)
            }
        }
    }

    fun onUnlockDismissed() {
        unlockRequest.value = null
        lockedEnvelope = null
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

    fun onEraseRequested() {
        confirmingErase.value = true
    }

    fun onEraseDismissed() {
        confirmingErase.value = false
    }

    /**
     * Wipes everything and returns the app to its first-launch state. On
     * success the launch gate flips to the onboarding, so this screen is torn
     * down and only a failure has anything left to report.
     */
    fun onEraseConfirmed() {
        if (!working.compareAndSet(expect = false, update = true)) return
        confirmingErase.value = false
        viewModelScope.launch {
            val result = suspendRunCatching { eraseAllData() }
            working.value = false
            result.onFailure { _events.send(BackupEvent.EraseFailed) }
        }
    }

    override fun onCleared() {
        clearExportPassphrase()
        super.onCleared()
    }

    private suspend fun onInspected(inspection: ImportBackupUseCase.Inspection) {
        when (inspection) {
            is ImportBackupUseCase.Inspection.Valid ->
                pendingRestore.value = PendingRestore(inspection.file, inspection.summary)

            is ImportBackupUseCase.Inspection.Locked -> {
                lockedEnvelope = inspection.envelope
                unlockRequest.value = UnlockRequest()
            }

            is ImportBackupUseCase.Inspection.Invalid ->
                _events.send(BackupEvent.InvalidBackupFile(inspection.error))
        }
    }

    private fun clearExportPassphrase() {
        exportPassphrase?.fill(NUL)
        exportPassphrase = null
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

        /** What a wiped passphrase character looks like. */
        const val NUL = '\u0000'
    }
}
