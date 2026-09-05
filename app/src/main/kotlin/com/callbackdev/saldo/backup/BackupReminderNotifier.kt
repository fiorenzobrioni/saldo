package com.callbackdev.saldo.backup

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.domain.usecase.BackupReminder
import dagger.hilt.android.qualifiers.ApplicationContext
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

    // Guarded by hasNotificationPermission(); lint's flow analysis is intraprocedural.
    @SuppressLint("MissingPermission")
    fun notify(reminder: BackupReminder?) {
        if (reminder == null || !hasNotificationPermission()) return
        val body = reminder.daysSince
            ?.let { days -> context.resources.getQuantityString(R.plurals.notif_backup_reminder_body_days, days, days) }
            ?: context.getString(R.string.notif_backup_reminder_body_never)
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_backup_reminder_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openBackupIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_REMINDER, notification)
    }

    /** POST_NOTIFICATIONS is a runtime permission from API 33; older versions grant it implicitly. */
    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openBackupIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_BACKUP)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_BACKUP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_REMINDER = "backup_reminder"

        /** Distinct from every other notifier's ids (1001..1008), so nothing replaces it. */
        const val ID_REMINDER = 1009
        const val REQUEST_OPEN_BACKUP = 1
    }
}
