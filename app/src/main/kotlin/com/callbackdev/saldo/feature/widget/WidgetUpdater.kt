package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-renders placed widgets from persisted state: the instance's configuration,
 * what it draws, and the resolved palette.
 *
 * Called from the provider (placement, restore, the selector, a new day) and
 * from [WidgetRefreshWatcher] when something a widget draws changes. No-op with
 * zero widgets, which is the whole of ADR 37's "costs nothing to whoever does
 * not use it": there is no path here that runs unless an instance is actually
 * placed.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configStore: WidgetConfigStore,
    private val loader: QuickAddWidgetDataLoader,
    private val monthLoader: MonthWidgetDataLoader,
    private val userPreferences: UserPreferencesRepository,
) {

    /** Every placed instance of every provider. */
    suspend fun updateAll() {
        updateQuickAdd()
        updateMonth()
    }

    /** Every placed quick-add widget. */
    suspend fun updateQuickAdd() {
        update(appWidgetIds(QuickAddWidgetProvider::class.java), SaldoWidgetKind.QUICK_ADD)
    }

    /**
     * Every placed month widget, of both kinds, from one data pass (ADR 54).
     * Arms the midnight redraw while any is placed, and only then.
     */
    suspend fun updateMonth() {
        val words = appWidgetIds(MonthWordsWidgetProvider::class.java)
        val ring = appWidgetIds(MonthRingWidgetProvider::class.java)
        if (words.isEmpty() && ring.isEmpty()) {
            MonthWidgetDayAlarm.cancel(context)
            return
        }
        val data = monthLoader.load()
        renderMonth(words, SaldoWidgetKind.WORDS, data)
        renderMonth(ring, SaldoWidgetKind.RING, data)
        MonthWidgetDayAlarm.schedule(context, data.today)
    }

    /**
     * The given instances, which are always one provider's: the sizes come from
     * the provider's kind, so a caller that mixed two would hand the launcher the
     * wrong breakpoints.
     */
    internal suspend fun update(appWidgetIds: IntArray, kind: SaldoWidgetKind) {
        if (appWidgetIds.isEmpty()) return
        if (kind.month != null) {
            renderMonth(appWidgetIds, kind, monthLoader.load())
        } else {
            updateQuickAdd(appWidgetIds)
        }
    }

    /**
     * Quick-add instances usually share a configuration, so the data pass is
     * grouped by it: one database read and one theme resolution per distinct
     * configuration, however many widgets carry it. The `RemoteViews`
     * themselves are still built per instance - the selector's broadcast names
     * the widget it belongs to, so two instances cannot share one set of
     * intents.
     */
    private suspend fun updateQuickAdd(appWidgetIds: IntArray) {
        // The text size this render is for: the single row's words need more
        // room at a larger one, and the launcher draws in the same configuration.
        val breakpoints = SaldoWidgetKind.QUICK_ADD.sizes(context.resources.configuration.fontScale)
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
                            sizes = breakpoints,
                        ),
                    )
                }
            }
    }

    /** One kind of month widget, from a data pass the caller already made. */
    private suspend fun renderMonth(appWidgetIds: IntArray, kind: SaldoWidgetKind, data: MonthWidgetData) {
        val month = kind.month ?: return
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val preferences = userPreferences.themePreferences.first()
        val breakpoints = kind.sizes(context.resources.configuration.fontScale)
        configStore.readMonthAll(appWidgetIds).forEach { (id, config) ->
            val theme = resolveWidgetTheme(context, preferences, config.background, config.cardColor, config.opacityPct)
            manager.updateAppWidget(
                id,
                MonthWidgetRenderer.sizeMap(context, month, data, config, theme.palette, breakpoints),
            )
        }
    }

    private fun appWidgetIds(provider: Class<*>): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, provider))
}
