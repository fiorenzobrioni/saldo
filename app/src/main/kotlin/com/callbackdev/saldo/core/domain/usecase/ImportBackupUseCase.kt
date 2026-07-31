package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.backup.BackupCodec
import com.callbackdev.saldo.core.domain.backup.BackupContent
import com.callbackdev.saldo.core.domain.backup.BackupCrypto
import com.callbackdev.saldo.core.domain.backup.BackupCryptoException
import com.callbackdev.saldo.core.domain.backup.BackupDecodeException
import com.callbackdev.saldo.core.domain.backup.BackupFile
import com.callbackdev.saldo.core.domain.backup.BackupSummary
import com.callbackdev.saldo.core.domain.backup.EncryptedBackup
import com.callbackdev.saldo.core.domain.backup.summary
import com.callbackdev.saldo.core.domain.repository.BackupRepository
import com.callbackdev.saldo.core.domain.repository.SettingsBackupRepository
import javax.inject.Inject

/**
 * The steps of a guided restore: [inspect] validates a candidate file and tells
 * the user what it contains *before* anything is touched, [unlock] does the same
 * for an encrypted container once the passphrase is known, and [restore] then
 * replaces the data with the inspected content.
 *
 * An encrypted file is decrypted **to be inspected**, never after a restore has
 * already happened: the summary in the confirmation dialog always describes the
 * bytes that are about to land. Any failure during [restore] rolls the database
 * transaction back, so the current data is never lost to a bad file.
 */
class ImportBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsBackupRepository,
) {

    /** Outcome of validating a candidate backup file. */
    sealed interface Inspection {
        /** A readable backup; [summary] is shown in the confirmation step. */
        data class Valid(val file: BackupFile, val summary: BackupSummary) : Inspection

        /** A recognised container: readable, but only with the passphrase. */
        data class Locked(val envelope: EncryptedBackup) : Inspection

        data class Invalid(val error: Error) : Inspection
    }

    /** Why a file cannot be restored, mapped to a user-facing message. */
    enum class Error {
        NOT_A_BACKUP,
        UNSUPPORTED_VERSION,
        CORRUPTED,
        WRONG_PASSPHRASE,
        UNSUPPORTED_CONTAINER,
    }

    fun inspect(content: String): Inspection =
        try {
            when (val parsed = BackupCodec.read(content)) {
                is BackupContent.Plain -> Inspection.Valid(parsed.file, parsed.file.summary())
                is BackupContent.Encrypted -> Inspection.Locked(parsed.envelope)
            }
        } catch (error: BackupDecodeException) {
            Inspection.Invalid(error.toError())
        }

    /**
     * Opens [envelope] with [passphrase] and inspects what comes out. CPU-bound
     * by design (key derivation): callers keep it off the main thread.
     *
     * The passphrase is not retained; the caller owns wiping the array.
     */
    fun unlock(envelope: EncryptedBackup, passphrase: CharArray): Inspection =
        try {
            val file = BackupCodec.decode(BackupCrypto.open(envelope, passphrase))
            Inspection.Valid(file, file.summary(isEncrypted = true))
        } catch (error: BackupCryptoException) {
            Inspection.Invalid(error.toError())
        } catch (error: BackupDecodeException) {
            // The container opened but held something that is not a backup: the
            // passphrase was right, the content is not usable.
            Inspection.Invalid(error.toError())
        }

    /**
     * Replaces every table with [file]'s data and applies its settings, if it
     * carries any. Throws if the write fails.
     *
     * The database goes first, in its own transaction: a failure there rolls back
     * and leaves both data and settings as they were. The settings are written
     * after, in a single edit, because DataStore cannot join that transaction -
     * so the honest order is the one where the money is never at risk.
     */
    suspend fun restore(file: BackupFile) {
        backupRepository.restore(file.data)
        file.data.settings?.let { settingsRepository.restoreSettings(it) }
    }

    private fun BackupDecodeException.toError(): Error = when (this) {
        is BackupDecodeException.NotABackup -> Error.NOT_A_BACKUP
        is BackupDecodeException.UnsupportedVersion -> Error.UNSUPPORTED_VERSION
        is BackupDecodeException.Corrupted -> Error.CORRUPTED
    }

    private fun BackupCryptoException.toError(): Error = when (this) {
        is BackupCryptoException.WrongPassphrase -> Error.WRONG_PASSPHRASE
        is BackupCryptoException.UnsupportedContainer -> Error.UNSUPPORTED_CONTAINER
        is BackupCryptoException.Corrupted -> Error.CORRUPTED
    }
}
