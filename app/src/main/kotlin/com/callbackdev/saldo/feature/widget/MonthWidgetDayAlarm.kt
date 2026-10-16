package com.callbackdev.saldo.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

/**
 * The month widgets' one timer (ADR 54): a redraw just after midnight, so
 * "tomorrow" becomes "today", the days left drop by one and a new month starts
 * on a fresh ring.
 *
 * Inexact and not a wake-up (`AlarmManager.RTC`, never `RTC_WAKEUP`, never an
 * exact alarm): it fires when the phone is next awake, and a home screen is
 * only seen on an awake phone, so a card is never stale in front of anybody and
 * the alarm never wakes a sleeping one. Armed at every month render (the same
 * request code replaces the last), cancelled by the watcher when the last month
 * widget leaves the home screen.
 */
internal object MonthWidgetDayAlarm {

    fun schedule(context: Context, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + AFTER_MIDNIGHT_MILLIS
        runCatching { manager.set(AlarmManager.RTC, at, intent(context)) }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.cancel(intent(context)) }
    }

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, MonthWordsWidgetProvider::class.java).setAction(SaldoWidgetProvider.ACTION_NEW_DAY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** A minute past: a clock a few seconds behind still lands on the new day. */
    private const val AFTER_MIDNIGHT_MILLIS = 60_000L
    private const val REQUEST = 54
}
