package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
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

    // No onAppWidgetOptionsChanged override: with SizeMode.Responsive every
    // update already ships the launcher one layout per bucket, so a resize is
    // settled there, without waking this process at all. The Exact-era version
    // redrew everything from here on each resize and still arrived late.
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
