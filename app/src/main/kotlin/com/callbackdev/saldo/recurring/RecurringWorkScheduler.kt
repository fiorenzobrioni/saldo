package com.callbackdev.saldo.recurring

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules the daily recurring-generation worker (kept if already enqueued). */
object RecurringWorkScheduler {

    private const val WORK_NAME = "recurring-generation"
    private const val INTERVAL_HOURS = 24L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RecurringGenerationWorker>(
            INTERVAL_HOURS,
            TimeUnit.HOURS,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
