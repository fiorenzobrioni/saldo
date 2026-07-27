package com.callbackdev.saldo.feature.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Redraws placed widgets just past local midnight, so "today's" total starts
 * over with the day. Without it the widget only redraws on a data change or on
 * the daily generation worker, whose 24h period runs at whatever hour it was
 * first scheduled - either way the number wore the label "today" while being
 * yesterday's for hours.
 *
 * WorkManager is not exact under Doze and a late run is fine: the total is
 * recomputed at render time, this only asks for the render.
 */
@HiltWorker
class WidgetMidnightRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val widgetRefreshWatcher: WidgetRefreshWatcher,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        widgetRefreshWatcher.refresh()
        // One-shot re-anchored after every run rather than periodic: a 24h
        // period drifts with each Doze delay and ignores DST, while re-solving
        // "the next local midnight" self-corrects. APPEND_OR_REPLACE because
        // REPLACE would cancel the very worker doing the enqueue.
        WidgetMidnightRefresh.schedule(applicationContext, clock, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

/**
 * The schedule half, called by [WidgetRefreshWatcher] while widgets are placed
 * and cancelled when the last one is removed: a user without widgets never has
 * this work in the queue at all.
 */
object WidgetMidnightRefresh {

    private const val WORK_NAME = "widget-midnight-refresh"

    fun schedule(
        context: Context,
        clock: Clock,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
    ) {
        val now = ZonedDateTime.now(clock)
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val request = OneTimeWorkRequestBuilder<WidgetMidnightRefreshWorker>()
            .setInitialDelay(Duration.between(now, nextMidnight))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
