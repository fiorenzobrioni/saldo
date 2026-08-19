package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-renders placed widgets from persisted state: the instance's configuration,
 * the accounts and categories it draws, and the resolved palette.
 *
 * Called from the provider (placement, restore, the selector) and from
 * [WidgetRefreshWatcher] when something a widget draws changes. No-op with zero
 * widgets, which is the whole of ADR 37's "costs nothing to whoever does not use
 * it": there is no path here that runs unless an instance is actually placed.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configStore: WidgetConfigStore,
    private val loader: QuickAddWidgetDataLoader,
) {

    /** Every placed instance of both providers. */
    suspend fun updateAll() {
        SaldoWidgetProvider.providers.forEach { (provider, sizes) ->
            update(provider, appWidgetIds(provider), sizes)
        }
    }

    /**
     * The instances of one provider.
     *
     * Instances usually share a configuration, so the data pass is grouped by
     * it: one database read and one theme resolution per distinct configuration,
     * however many widgets carry it. The `RemoteViews` themselves are still
     * built per instance - the selector's broadcast names the widget it belongs
     * to, so two instances cannot share one set of intents.
     */
    internal suspend fun update(provider: Class<*>, appWidgetIds: IntArray, sizes: List<WidgetSize>) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val configs = configStore.readAll(appWidgetIds)
        configs.entries
            .groupBy({ it.value }, { it.key })
            .forEach { (config, ids) ->
                val snapshot = loader.loadShared(config)
                ids.forEach { id ->
                    manager.updateAppWidget(
                        id,
                        WidgetRenderer.sizeMap(
                            context = context,
                            appWidgetId = id,
                            data = snapshot.data,
                            palette = snapshot.theme.palette,
                            sizes = sizes,
                        ),
                    )
                }
            }
    }

    private fun appWidgetIds(provider: Class<*>): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, provider))
}
