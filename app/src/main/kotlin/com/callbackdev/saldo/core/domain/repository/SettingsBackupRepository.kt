package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.backup.SettingsBackup

/**
 * The settings half of a backup (ADR 45), next to [BackupRepository] for the
 * database half: a backup is complete only if it also carries what the user configured,
 * otherwise a restore on a new phone hands back the money and asks to set up
 * theme, currency, reminders and dashboard from scratch.
 *
 * Two separate interfaces on purpose. The database is replaced inside a single
 * transaction that rolls back as a unit; the settings live in DataStore, which
 * knows nothing of that transaction. Pretending they are one store would be a
 * lie about the atomicity actually available.
 */
interface SettingsBackupRepository {

    /** The settings as they are now, for the export. */
    suspend fun snapshotSettings(): SettingsBackup

    /**
     * Applies [settings], leaving every preference that describes *this* install
     * alone (see [SettingsBackup]). A value absent from the file is cleared
     * rather than left as it is: the restored install must match the exported
     * one, and "never set" is a state of its own (automatic currency, locale
     * week start), not an invitation to keep the local choice.
     */
    suspend fun restoreSettings(settings: SettingsBackup)
}
