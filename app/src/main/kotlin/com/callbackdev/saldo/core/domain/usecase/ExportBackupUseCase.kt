package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.backup.BackupCodec
import com.callbackdev.saldo.core.domain.backup.BackupCrypto
import com.callbackdev.saldo.core.domain.backup.BackupFile
import com.callbackdev.saldo.core.domain.backup.BackupSummary
import com.callbackdev.saldo.core.domain.backup.summary
import com.callbackdev.saldo.core.domain.repository.BackupRepository
import com.callbackdev.saldo.core.domain.repository.SettingsBackupRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Builds a complete backup as a versioned JSON document (PLANNING ADR 5/13):
 * every table of the database plus the settings the user chose, so the file is a
 * picture of the app and not only of its data.
 *
 * With a passphrase the same document is sealed inside the encrypted container
 * (Fase 22, ADR 44) - the payload is identical, the container only wraps it. The
 * caller decides where the bytes go (a SAF document today, a cloud object in a
 * future phase); this use case only produces them.
 */
class ExportBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsBackupRepository,
    private val clock: Clock,
) {

    /** The encoded document plus what it contains, for user feedback. */
    data class Export(
        val content: String,
        val summary: BackupSummary,
    )

    /**
     * @param appVersion the app's `versionName`, recorded in the file for diagnostics.
     * @param passphrase when non-null, the file is encrypted with it. Not
     *   retained: the caller owns wiping the array afterwards.
     */
    suspend operator fun invoke(appVersion: String?, passphrase: CharArray? = null): Export {
        val file = BackupFile(
            version = BackupFile.CURRENT_VERSION,
            exportedAtEpochMilli = clock.millis(),
            appVersion = appVersion,
            data = backupRepository.createSnapshot().copy(
                settings = settingsRepository.snapshotSettings(),
            ),
        )
        val json = BackupCodec.encode(file)
        val content = passphrase?.let { BackupCodec.encode(BackupCrypto.seal(json, it)) } ?: json
        return Export(content = content, summary = file.summary(isEncrypted = passphrase != null))
    }
}
