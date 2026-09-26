package com.callbackdev.saldo.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.R
import java.time.LocalTime

/**
 * What every Saldo notification wears besides its words, in the family's idiom
 * (Chiaro's notification review of 23 set 2026, which Passo follows; ADR 52):
 *
 * - **the brand's mark and accent**: the status icon is the launcher icon's
 *   mark, the header and the icon's disc wear the icon's blue;
 * - **collapsed is the sentence, expanded is the story**: the collapsed line is
 *   one sentence with the number that matters, and pulling the notification
 *   open keeps that sentence as the headline and puts the rest under it, one
 *   fact per line. Before this every notification said the same thing in both
 *   states, so opening one gave back exactly what it already said;
 * - **quiet hours**: between [QuietFrom] and [QuietUntil] on the phone's clock a
 *   notification still arrives, whole, but without sound or vibration. Nothing
 *   Saldo says is urgent at night: a statement, a renewal or a budget can wait
 *   for the morning.
 */
internal object SaldoNotifications {

    /** The common builder: mark, accent, one tap into the app that goes away. */
    fun builder(
        context: Context,
        channelId: String,
        contentIntent: PendingIntent = openApp(context),
    ): NotificationCompat.Builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_stat_saldo)
        .setColor(context.getColor(R.color.notification_accent))
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)

    /**
     * The collapsed sentence and the expanded story. [details] are the facts
     * under the headline, worth-first, because the system cuts a long body at
     * the bottom; with none the expanded body is the headline, which is honest.
     */
    fun NotificationCompat.Builder.story(headline: String, details: List<String>): NotificationCompat.Builder =
        setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded(headline, details)))

    /** The headline and its details as one text, as the big-text body shows them. */
    fun expanded(headline: String, details: List<String>): String =
        if (details.isEmpty()) headline else headline + "\n\n" + details.joinToString("\n")

    /** Silent in [quiet hours][isQuiet], still posted: it is there in the morning. */
    fun NotificationCompat.Builder.quietAtNight(now: LocalTime = LocalTime.now()): NotificationCompat.Builder =
        apply { if (isQuiet(now)) setSilent(true) }

    fun isQuiet(now: LocalTime): Boolean = now >= QuietFrom || now < QuietUntil

    val QuietFrom: LocalTime = LocalTime.of(22, 0)
    val QuietUntil: LocalTime = LocalTime.of(7, 0)

    /** POST_NOTIFICATIONS is a runtime permission from API 33, the app's minimum. */
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** A tap that brings back the app's task, on [action] when there is one. */
    fun openApp(context: Context, requestCode: Int = 0, action: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
