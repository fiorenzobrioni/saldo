package com.callbackdev.saldo.feature.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The DI door for the widget. `GlanceAppWidget` and `ActionCallback` are
 * instantiated by the framework, not by Hilt, so they cannot take constructor
 * injection: this is the one place in the app that reaches into the graph by
 * hand.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {

    fun quickAddWidgetDataLoader(): QuickAddWidgetDataLoader

    fun widgetRefreshWatcher(): WidgetRefreshWatcher
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
