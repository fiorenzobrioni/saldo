package com.callbackdev.saldo.creditcard

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
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.domain.usecase.DueStatement
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the credit card statement notifications: an informative one when an
 * auto-post card has been charged, and a confirmation one when a confirm-mode
 * card has a statement waiting to be paid (tapping opens the app, where the
 * dashboard card settles it). One notification per kind with a fixed id,
 * replaced on repost. Like the other notifiers, posting is a silent no-op until
 * POST_NOTIFICATIONS is granted.
 */
@Singleton
class CreditCardNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun createChannel() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_STATEMENT, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_statement_name))
                .build(),
        )
    }

    fun notify(statements: List<DueStatement>) {
        post(ID_POSTED, statements.filter { it.autoPosted }, auto = true)
        post(ID_CONFIRM, statements.filterNot { it.autoPosted }, auto = false)
    }

    private fun post(id: Int, statements: List<DueStatement>, auto: Boolean) {
        val single = statements.singleOrNull()
        when {
            statements.isEmpty() -> return

            single != null -> post(
                id = id,
                title = context.getString(
                    if (auto) R.string.notif_statement_posted_title else R.string.notif_statement_confirm_title,
                    single.cardName,
                ),
                body = context.getString(
                    if (auto) R.string.notif_statement_posted_body else R.string.notif_statement_confirm_body,
                    MoneyFormatter.format(single.amount, single.currency),
                ),
            )

            else -> post(
                id = id,
                title = context.resources.getQuantityString(
                    if (auto) {
                        R.plurals.notif_statement_posted_summary_title
                    } else {
                        R.plurals.notif_statement_confirm_summary_title
                    },
                    statements.size,
                    statements.size,
                ),
                body = statements.joinToString(separator = ", ") { it.cardName },
            )
        }
    }

    // Guarded by hasNotificationPermission(); lint's flow analysis is intraprocedural.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, title: String, body: String) {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_STATEMENT)
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
        const val CHANNEL_STATEMENT = "credit_card_statement"
        const val ID_POSTED = 1006
        const val ID_CONFIRM = 1007
    }
}
