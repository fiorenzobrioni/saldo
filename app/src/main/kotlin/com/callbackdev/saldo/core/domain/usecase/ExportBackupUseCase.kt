package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.backup.BackupCodec
import com.callbackdev.saldo.core.domain.backup.BackupFile
import com.callbackdev.saldo.core.domain.backup.BackupSummary
import com.callbackdev.saldo.core.domain.backup.summary
import com.callbackdev.saldo.core.domain.repository.BackupRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Builds a complete backup of the database as a versioned JSON document
 * (PLANNING ADR 5/13). The caller decides where the bytes go (a SAF document
 * today, a cloud object in a future phase); this use case only produces them.
 */
class ExportBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val clock: Clock,
) {

    /** The encoded backup document plus what it contains, for user feedback. */
    data class Export(val json: String, val summary: BackupSummary)

    /** @param appVersion the app's `versionName`, recorded in the file for diagnostics. */
    suspend operator fun invoke(appVersion: String?): Export {
        val file = BackupFile(
            version = BackupFile.CURRENT_VERSION,
            exportedAtEpochMilli = clock.millis(),
            appVersion = appVersion,
            data = backupRepository.createSnapshot(),
        )
        return Export(json = BackupCodec.encode(file), summary = file.summary())
    }
}
