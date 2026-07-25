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

    /**
     * Empties every table and re-seeds the localized default categories, in one
     * transaction: the result is the database a fresh install would create.
     *
     * The re-seed is not optional. The categories are planted by the Room
     * `onCreate` callback, which never runs again on an existing file, so a
     * plain wipe would leave the app with no categories at all and no way to
     * get them back short of reinstalling.
     */
    suspend fun eraseAll()
}
