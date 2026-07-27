package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import kotlin.reflect.KClass

/**
 * Keeps the widget picker's generated previews (API 35+) published: the real
 * widget, composed with real data and the user's palette, which is what the
 * picker shows in place of the static `previewLayout`.
 *
 * Publishing once at startup is not enough, and that is the bug this exists
 * for. A generated preview is not a resource the app owns, it is state held by
 * `system_server`, and two ordinary events throw it away:
 *
 * - **an in-place app update**. `AppWidgetServiceImpl.updateProvidersForPackageLocked`
 *   re-parses the manifest and calls `Provider.setPartialInfoLocked` with a
 *   brand new `AppWidgetProviderInfo`, whose `generatedPreviewCategories` is 0.
 *   The stored `RemoteViews` survive, but that field is exactly what the
 *   launcher gates on (`DatabaseWidgetPreviewLoader` asks for a preview only
 *   when the home-screen bit is set), so from its side the preview is gone.
 * - **a reboot**. The previews live in a `SparseArray` on the provider and are
 *   never written to the widget state file.
 *
 * Both are routine on a test device, and both hit the second half of the trap:
 * `setWidgetPreview` allows about two calls an hour *per provider*
 * (`DEFAULT_GENERATED_PREVIEW_MAX_CALLS_PER_INTERVAL`). Republishing on every
 * cold start spent that budget on previews the launcher could already see, so
 * the call that mattered - the first launch after an update - was the one the
 * system refused. And the refusal went nowhere: the `@CheckResult` return that
 * tells a published preview from a rate-limited one was thrown away with it.
 *
 * Hence the two rules here: publish only for a provider the launcher currently
 * has no preview for, and read that return value, so a refusal arms a retry
 * instead of waiting for the next cold start.
 */
object WidgetPreviews {

    private const val WORK_NAME = "widget-preview-publish"

    /** Just past the system's own hourly window, so the retry finds a fresh budget. */
    private val RetryDelay: Duration = Duration.ofMinutes(65)

    private val receivers: List<KClass<out GlanceAppWidgetReceiver>> = listOf(
        SaldoQuickAddWidgetReceiver::class,
        SaldoQuickBarWidgetReceiver::class,
    )

    /**
     * Publishes a preview for every provider that has none, and arms a retry if
     * the system refused one.
     *
     * [fromRetry] tells the two callers apart, and both differences are about
     * not cancelling the work that is running: the retry worker re-arms with
     * [ExistingWorkPolicy.APPEND_OR_REPLACE] (REPLACE would cancel the very
     * work doing the enqueue) and never clears the queue, which only the
     * startup call does, to drop a retry that is no longer needed.
     */
    suspend fun publishMissing(context: Context, fromRetry: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        // The SDK guard cannot sit outside the lambda for lint's NewApi check,
        // so the API 35 surface is its own object and this is the only hop.
        val refused = Api35.publishMissing(context, receivers)
        val work = WorkManager.getInstance(context)
        when {
            refused -> {
                val policy =
                    if (fromRetry) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
                val request = OneTimeWorkRequestBuilder<WidgetPreviewWorker>()
                    .setInitialDelay(RetryDelay)
                    .build()
                work.enqueueUniqueWork(WORK_NAME, policy, request)
            }
            !fromRetry -> work.cancelUniqueWork(WORK_NAME)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private object Api35 {

        /** @return true when at least one publish was refused and is worth retrying. */
        suspend fun publishMissing(
            context: Context,
            receivers: List<KClass<out GlanceAppWidgetReceiver>>,
        ): Boolean {
            val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull()
                ?: return false
            val glanceManager = GlanceAppWidgetManager(context)
            var refused = false
            receivers.forEach { receiver ->
                val component = ComponentName(context, receiver.java)
                if (!manager.needsPreview(context, component)) return@forEach
                // A publish that throws is left to the next pass rather than
                // retried: the retry exists for the hourly budget, and an
                // exception here is not a budget problem.
                val result = runCatching { glanceManager.setWidgetPreviews(receiver) }.getOrNull()
                if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED) {
                    refused = true
                }
            }
            return refused
        }

        /**
         * Whether the launcher currently sees no preview for this provider.
         * Reads `generatedPreviewCategories` rather than `getWidgetPreview`
         * because that field is what the picker gates on, and an in-place update
         * clears it while leaving the stored `RemoteViews` behind: asking for
         * the preview itself would answer "there is one" precisely when the
         * launcher can no longer reach it.
         */
        private fun AppWidgetManager.needsPreview(context: Context, component: ComponentName): Boolean {
            val installed = runCatching { getInstalledProvidersForPackage(context.packageName, null) }
                .getOrDefault(emptyList<AppWidgetProviderInfo>())
            // No provider means nothing to publish for, not a missing preview.
            val info = installed.firstOrNull { it.provider == component } ?: return false
            return info.generatedPreviewCategories and
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN == 0
        }
    }
}

/**
 * The retry half of [WidgetPreviews], for the window where the system has
 * already spent the provider's hourly budget. Plain `CoroutineWorker` rather
 * than `@HiltWorker`: it needs a context and nothing else, and the Hilt factory
 * falls back to the default one for workers it does not know.
 */
class WidgetPreviewWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        WidgetPreviews.publishMissing(applicationContext, fromRetry = true)
        return Result.success()
    }
}
