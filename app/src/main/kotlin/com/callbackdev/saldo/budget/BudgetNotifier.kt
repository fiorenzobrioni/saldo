package com.callbackdev.saldo.budget

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
import com.callbackdev.saldo.core.domain.model.BudgetLevel
import com.callbackdev.saldo.core.domain.usecase.BudgetAlert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the budget threshold notifications (80% reached, limit exceeded).
 * One notification per level with a fixed id, replaced on repost: a single
 * alert is named and quantified, several collapse into a summary. Tapping
 * opens the app. Like [com.callbackdev.saldo.recurring.RecurringNotifier],
 * posting is a silent no-op until POST_NOTIFICATIONS is granted.
 */
@Singleton
class BudgetNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun createChannel() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_budget_name))
                .build(),
        )
    }

    fun notify(alerts: List<BudgetAlert>) {
        post(
            id = ID_EXCEEDED,
            titleRes = R.string.notif_budget_exceeded_title,
            summaryRes = R.plurals.notif_budget_exceeded_summary_title,
            alerts = alerts.filter { it.level == BudgetLevel.OVER },
        )
        post(
            id = ID_WARNING,
            titleRes = R.string.notif_budget_warning_title,
            summaryRes = R.plurals.notif_budget_warning_summary_title,
            alerts = alerts.filter { it.level == BudgetLevel.WARNING },
        )
    }

    private fun post(id: Int, titleRes: Int, summaryRes: Int, alerts: List<BudgetAlert>) {
        val single = alerts.singleOrNull()
        when {
            alerts.isEmpty() -> return

            single != null -> post(
                id = id,
                title = context.getString(titleRes, single.name(), single.percent),
                body = context.getString(
                    R.string.notif_budget_body,
                    MoneyFormatter.format(single.spent, single.budget.currency),
                    MoneyFormatter.format(single.budget.amount, single.budget.currency),
                ),
            )

            else -> post(
                id = id,
                title = context.resources.getQuantityString(summaryRes, alerts.size, alerts.size),
                body = alerts.joinToString(separator = ", ") { it.name() },
            )
        }
    }

    private fun BudgetAlert.name(): String =
        categoryName ?: context.getString(R.string.budgets_overall_title)

    // Guarded by hasNotificationPermission(); lint's flow analysis is intraprocedural.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, title: String, body: String) {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
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
        const val CHANNEL_ALERTS = "budget_alerts"
        const val ID_WARNING = 1004
        const val ID_EXCEEDED = 1005
    }
}
