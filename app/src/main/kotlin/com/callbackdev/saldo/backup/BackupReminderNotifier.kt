package com.callbackdev.saldo.backup

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.domain.usecase.BackupReminder
import com.callbackdev.saldo.notifications.SaldoNotifications
import com.callbackdev.saldo.notifications.SaldoNotifications.quietAtNight
import com.callbackdev.saldo.notifications.SaldoNotifications.story
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the opt-in backup reminder (Fase 39, F4): "your last backup is N days
 * old" or "you never made one". Its own channel, so the user can mute it
 * without touching the recurring or budget alerts. Tapping it opens the app on
 * the Backup screen through [MainActivity.ACTION_OPEN_BACKUP].
 *
 * Fixed notification id, replaced on repost: there is never more than one
 * pending reminder.
 */
@Singleton
class BackupReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun createChannel() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_REMINDER, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_backup_reminder_name))
                .build(),
        )
    }

    // Guarded by hasPermission(); lint's flow analysis is intraprocedural.
    @SuppressLint("MissingPermission")
    fun notify(reminder: BackupReminder?) {
        if (reminder == null || !SaldoNotifications.hasPermission(context)) return
        val body = reminder.daysSince
            ?.let { days -> context.resources.getQuantityString(R.plurals.notif_backup_reminder_body_days, days, days) }
            ?: context.getString(R.string.notif_backup_reminder_body_never)
        val details = buildList {
            reminder.lastBackupDate?.let { date ->
                val locale = context.resources.configuration.locales[0]
                val day = date.format(DateTimeFormatter.ofPattern(DAY_PATTERN, locale))
                add(context.getString(R.string.notif_backup_last, day))
            }
            add(context.getString(R.string.notif_backup_where))
        }
        val notification = SaldoNotifications.builder(
            context = context,
            channelId = CHANNEL_REMINDER,
            contentIntent = SaldoNotifications.openApp(context, REQUEST_OPEN_BACKUP, MainActivity.ACTION_OPEN_BACKUP),
        )
            .setContentTitle(context.getString(R.string.notif_backup_reminder_title))
            .story(body, details)
            .quietAtNight()
            .build()
        NotificationManagerCompat.from(context).notify(ID_REMINDER, notification)
    }

    private companion object {
        const val CHANNEL_REMINDER = "backup_reminder"

        /** Distinct from every other notifier's ids (1001..1008), so nothing replaces it. */
        const val ID_REMINDER = 1009
        const val REQUEST_OPEN_BACKUP = 1
        const val DAY_PATTERN = "d MMMM yyyy"
    }
}
