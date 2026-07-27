package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The manifest-declared side of the widget. `onEnabled`/`onDisabled` bracket
 * the whole lifetime of the feature on a device (first widget placed, last one
 * removed) and are what gates [WidgetRefreshWatcher]: a user without widgets
 * never pays for a database observer.
 */
class SaldoQuickAddWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SaldoQuickAddWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.widgetEntryPoint().widgetRefreshWatcher().onWidgetsChanged()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.widgetEntryPoint().widgetRefreshWatcher().onWidgetsChanged()
    }

    /**
     * The size a widget was resized to arrives here, and with `SizeMode.Exact`
     * it is what the layout is worked out from. The base class already reacts;
     * the explicit redraw is belt and braces for launchers that deliver the
     * options change late or coalesce it away, where the widget would otherwise
     * keep drawing the layout of its previous size.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        context.widgetEntryPoint().widgetRefreshWatcher().requestRedraw()
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Covers the restore path too: widgets come back after a reinstall
        // without ever going through onEnabled.
        context.widgetEntryPoint().widgetRefreshWatcher().onWidgetsChanged()
    }
}
