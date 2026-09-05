package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** A backup reminder that is due: what the notification says. */
data class BackupReminder(
    /** Day of the last backup export, null when none was ever made. */
    val lastBackupDate: LocalDate?,
    /** Days elapsed since [lastBackupDate]; null when no backup was ever made. */
    val daysSince: Int?,
)

/**
 * Decides whether today is the day to remind the user to export a backup
 * (Fase 39, F4). Runs from the daily worker, so the decision has to be
 * idempotent across runs on the same day and quiet on the days in between.
 *
 * The reminder is opt-in and fires when the last backup is older than the
 * chosen interval, or when there has never been one. It repeats once per
 * interval, not once per day: the "current deadline" is the last multiple of
 * the interval since the backup, and a watermark keeps the day the reminder
 * was last posted, so a deadline is announced once and the next one is only
 * announced when its own day comes. A new backup moves every deadline forward
 * and re-arms the reminder by construction. With no backup ever, the deadline
 * is counted from the previous reminder instead, which is the only anchor
 * there is.
 *
 * An install without accounts has nothing to protect (no account, no
 * movements), so it is never reminded.
 */
class CheckBackupReminderUseCase @Inject constructor(
    private val userPreferences: UserPreferencesRepository,
    private val accountRepository: AccountRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(today: LocalDate = LocalDate.now(clock)): BackupReminder? {
        val prefs = userPreferences.backupReminderPreferences.first()
        if (!prefs.enabled || accountRepository.observeAccounts().first().isEmpty()) return null

        val lastBackup = userPreferences.lastBackupAtEpochMilli.first()
            ?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate() }
        val notifiedOn = userPreferences.backupReminderNotifiedOn.first()
        val due = currentDeadline(lastBackup, notifiedOn, prefs.intervalDays.toLong(), today)
        val isDue = due != null && today >= due && (notifiedOn == null || notifiedOn < due)

        return if (!isDue) {
            null
        } else {
            userPreferences.setBackupReminderNotifiedOn(today)
            BackupReminder(
                lastBackupDate = lastBackup,
                daysSince = lastBackup?.let { ChronoUnit.DAYS.between(it, today).toInt() },
            )
        }
    }

    /**
     * The deadline in force on [today]: the last multiple of [interval] since the
     * backup, null while the backup is still younger than one interval. With no
     * backup ever, the only anchor is the previous reminder (or today itself).
     */
    private fun currentDeadline(
        lastBackup: LocalDate?,
        notifiedOn: LocalDate?,
        interval: Long,
        today: LocalDate,
    ): LocalDate? {
        if (lastBackup == null) return notifiedOn?.plusDays(interval) ?: today
        val elapsed = ChronoUnit.DAYS.between(lastBackup, today)
        return if (elapsed < interval) null else lastBackup.plusDays(elapsed / interval * interval)
    }
}
