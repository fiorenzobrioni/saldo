package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.backup.BackupCodec
import com.callbackdev.saldo.core.domain.backup.BackupDecodeException
import com.callbackdev.saldo.core.domain.backup.BackupFile
import com.callbackdev.saldo.core.domain.backup.BackupSummary
import com.callbackdev.saldo.core.domain.backup.summary
import com.callbackdev.saldo.core.domain.repository.BackupRepository
import javax.inject.Inject

/**
 * The two steps of a guided restore: [inspect] validates a candidate file and
 * tells the user what it contains *before* anything is touched; [restore]
 * then replaces the database atomically with the inspected content. Any
 * failure during [restore] rolls the transaction back, so the current data is
 * never lost to a bad file.
 */
class ImportBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
) {

    /** Outcome of validating a candidate backup file. */
    sealed interface Inspection {
        /** A readable backup; [summary] is shown in the confirmation step. */
        data class Valid(val file: BackupFile, val summary: BackupSummary) : Inspection

        data class Invalid(val error: Error) : Inspection
    }

    /** Why a file cannot be restored, mapped to a user-facing message. */
    enum class Error { NOT_A_BACKUP, UNSUPPORTED_VERSION, CORRUPTED }

    fun inspect(content: String): Inspection =
        try {
            val file = BackupCodec.decode(content)
            Inspection.Valid(file = file, summary = file.summary())
        } catch (error: BackupDecodeException) {
            Inspection.Invalid(error.toError())
        }

    /** Replaces every table with [file]'s data; throws if the write fails (rolled back). */
    suspend fun restore(file: BackupFile) {
        backupRepository.restore(file.data)
    }

    private fun BackupDecodeException.toError(): Error = when (this) {
        is BackupDecodeException.NotABackup -> Error.NOT_A_BACKUP
        is BackupDecodeException.UnsupportedVersion -> Error.UNSUPPORTED_VERSION
        is BackupDecodeException.Corrupted -> Error.CORRUPTED
    }
}
