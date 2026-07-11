package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.backup.BackupData

/** Whole-database snapshot and restore, backing export and import of backups. */
interface BackupRepository {

    /** A consistent snapshot of every table, read in a single transaction. */
    suspend fun createSnapshot(): BackupData

    /**
     * Replaces the entire database content with [data], atomically: either the
     * restore completes in full or the current data is left untouched. Row ids
     * are preserved, so every cross-reference in the backup stays valid.
     */
    suspend fun restore(data: BackupData)
}
