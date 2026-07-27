package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode

/**
 * The single-row sibling of [SaldoQuickAddWidget]: the two-button bar as its
 * own provider rather than a squashed grid.
 *
 * A separate provider is what gives the bar its own card in the launcher's
 * widget picker, with its own preview, so the user chooses the shape at
 * placement instead of discovering it by resizing. It also cleans both
 * configuration screens: the bar's settings show only what a bar can use, and
 * the grid's stop carrying options whose effect was invisible at the size on
 * screen. The provider info pins the shape with `maxResizeHeight`, so this
 * widget composes only the [ActionBuckets].
 *
 * The content is [provideQuickAddContent], shared with the grid: same
 * per-instance state, same data, same body.
 */
class SaldoQuickBarWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(ActionBuckets)

    /** The picker preview renders at the bar's one-row shape. */
    override val previewSizeMode = SizeMode.Responsive(setOf(PreviewRowBucket))

    override suspend fun provideGlance(context: Context, id: GlanceId) =
        provideQuickAddContent(context, id)

    override suspend fun providePreview(context: Context, widgetCategory: Int) =
        provideQuickAddPreview(context)
}

/** The manifest-declared side of the bar; same watcher gating as the grid's. */
class SaldoQuickBarWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SaldoQuickBarWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.widgetEntryPoint().widgetRefreshWatcher().onWidgetsChanged()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.widgetEntryPoint().widgetRefreshWatcher().onWidgetsChanged()
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        context.widgetEntryPoint().widgetRefreshWatcher().onWidgetsChanged()
    }
}
