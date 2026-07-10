package com.callbackdev.saldo.recurring

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
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.GeneratedMovement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts notifications for movements created by the background generation worker:
 * an informative one for automatic movements, and a confirmation one for pending
 * movements (confirm mode / variable amount). Tapping either opens the app, where
 * the pending movements can be confirmed or skipped.
 *
 * On API 33+ posting is a no-op until the user grants POST_NOTIFICATIONS, so no
 * permission check is needed here.
 */
@Singleton
class RecurringNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
) {

    fun createChannels() {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ACTIVITY, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notif_channel_activity_name))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_CONFIRM, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_confirm_name))
                .build(),
        )
    }

    suspend fun notify(generated: List<GeneratedMovement>) {
        val autoCount = generated.count { !it.isPending }
        // The single confirm notification (fixed id, replaced on repost) reports
        // every movement still awaiting confirmation, not just this batch.
        val pendingCount = if (generated.any { it.isPending }) {
            transactionRepository.observePendingTransactions().first().size
        } else {
            0
        }
        if (autoCount > 0) {
            post(
                id = ID_ACTIVITY,
                channelId = CHANNEL_ACTIVITY,
                title = context.resources.getQuantityString(
                    R.plurals.notif_activity_title,
                    autoCount,
                    autoCount,
                ),
                body = context.getString(R.string.notif_activity_body),
            )
        }
        if (pendingCount > 0) {
            post(
                id = ID_CONFIRM,
                channelId = CHANNEL_CONFIRM,
                title = context.resources.getQuantityString(
                    R.plurals.notif_confirm_title,
                    pendingCount,
                    pendingCount,
                ),
                body = context.getString(R.string.notif_confirm_body),
            )
        }
    }

    // Guarded by hasNotificationPermission(); lint's flow analysis is intraprocedural.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, channelId: String, title: String, body: String) {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** POST_NOTIFICATIONS is a runtime permission from API 33; older versions grant it implicitly. */
    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_ACTIVITY = "recurring_activity"
        const val CHANNEL_CONFIRM = "recurring_confirm"
        const val ID_ACTIVITY = 1001
        const val ID_CONFIRM = 1002
    }
}
