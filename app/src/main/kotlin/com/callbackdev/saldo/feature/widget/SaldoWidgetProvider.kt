package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.callbackdev.saldo.core.domain.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The manifest side of the quick-add widgets: a plain [AppWidgetProvider]
 * driving `RemoteViews` directly.
 *
 * Deliberately passive on battery. There is no `updatePeriodMillis`, no polling
 * and no work of its own: the widget draws a snapshot of things that only change
 * when the user edits accounts, categories, the theme or the widget's own
 * settings, and [WidgetRefreshWatcher] pushes a redraw when one of those moves
 * (ADR 37). Recording a movement changes nothing a widget draws, so a busy day
 * costs exactly as many redraws as a quiet one, which is none.
 *
 * The lifecycle hooks only record what the broadcast needs; the suspend work
 * runs once, in [onReceive]. `goAsync()` is consume-once and a single broadcast
 * can reach two hooks - `ACTION_APPWIDGET_ENABLE_AND_UPDATE` fires `onEnabled`
 * and `onUpdate` together - where a second `goAsync()` would return null.
 */
abstract class SaldoWidgetProvider : AppWidgetProvider() {

    /** The breakpoints this provider hands the launcher (see [WidgetRenderer.sizeMap]). */
    internal abstract val sizes: List<WidgetSize>

    private var render: IntArray? = null
    private var placementChanged = false
    private var deleted: IntArray? = null
    private var restored: Pair<IntArray, IntArray>? = null
    private var requestedType: Pair<Int, TransactionType>? = null

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        render = appWidgetIds
        // Covers the restore path too: widgets come back after a reinstall
        // without ever going through onEnabled.
        placementChanged = true
    }

    /**
     * Resize is deliberately NOT handled. The sizes map exists so the launcher
     * re-picks the right breakpoint itself, in its own process, instantly.
     * Pushing a fresh `RemoteViews` from here would race that - the host applies
     * the update against the size it still has on record - and the round trip
     * into this process (broadcast, database read, render) is exactly the
     * seconds-late resize the Glance `SizeMode.Exact` build suffered from.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) = Unit

    /** First instance placed: the database observers may now be wanted. */
    override fun onEnabled(context: Context) {
        placementChanged = true
    }

    /** Last instance removed: nothing left to keep fresh, so the observers stop. */
    override fun onDisabled(context: Context) {
        placementChanged = true
    }

    /** Removed instances must not leave their settings behind in the store. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        deleted = appWidgetIds
    }

    /**
     * A restore hands the same widget a new id while its settings are keyed by
     * the old one (the DataStore file rides along in the backup), so they must be
     * re-keyed or a widget would inherit whichever record happened to reuse its
     * number - and quietly start adding to somebody else's account.
     */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        restored = oldWidgetIds to newWidgetIds
    }

    override fun onReceive(context: Context, intent: Intent) {
        render = null
        placementChanged = false
        deleted = null
        restored = null
        requestedType = null
        super.onReceive(context, intent)
        if (intent.action == ACTION_SET_TYPE) {
            val id = intent.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val type = intent.getStringExtra(EXTRA_TYPE)
                ?.let { name -> TransactionType.entries.firstOrNull { it.name == name } }
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID && type != null) {
                requestedType = id to type
            }
        }

        val ids = render
        val placement = placementChanged
        val forget = deleted
        val remap = restored
        val selector = requestedType
        if (ids == null && !placement && forget == null && remap == null && selector == null) return

        // Nullable despite the platform signature: goAsync() only returns a
        // result while a real broadcast is being dispatched. The work still runs.
        val pendingResult: BroadcastReceiver.PendingResult? = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val entryPoint = context.widgetEntryPoint()
                val store = entryPoint.widgetConfigStore()
                val updater = entryPoint.widgetUpdater()
                // Before any render: RESTORED is followed by onUpdate in the
                // same broadcast, so the ids have to be re-keyed first.
                remap?.let { (old, new) -> store.remap(old, new) }
                forget?.let { store.forget(it) }
                selector?.let { (id, type) ->
                    store.setCurrentType(id, type)
                    updater.update(javaClass, intArrayOf(id), sizes)
                }
                if (placement) entryPoint.widgetRefreshWatcher().onWidgetsChanged()
                ids?.let { updater.update(javaClass, it, sizes) }
            } catch (e: Exception) {
                // An unhandled throw here would crash the app from a broadcast;
                // the widget simply keeps whatever it was already showing.
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        /** The home-screen expense/income selector, handled by the grid provider. */
        const val ACTION_SET_TYPE = "com.callbackdev.saldo.widget.SET_TYPE"

        const val EXTRA_APPWIDGET_ID = "com.callbackdev.saldo.widget.EXTRA_APPWIDGET_ID"
        const val EXTRA_TYPE = "com.callbackdev.saldo.widget.EXTRA_TYPE"

        /** Every provider whose widgets the watcher keeps fresh, with its breakpoints. */
        internal val providers: List<Pair<Class<out SaldoWidgetProvider>, List<WidgetSize>>> = listOf(
            QuickAddWidgetProvider::class.java to GridWidgetSizes,
            QuickBarWidgetProvider::class.java to ActionSizes,
        )

        /** True while at least one widget of any of our providers is placed. */
        fun hasWidgets(context: Context): Boolean = runCatching {
            val manager = AppWidgetManager.getInstance(context)
            providers.any { (provider, _) ->
                manager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
            }
        }.getOrDefault(false)
    }
}

/**
 * The grid: the expense/income selector over a grid of category tiles, degrading
 * to the two-button row when squashed (for the widgets placed before the bar
 * existed).
 */
class QuickAddWidgetProvider : SaldoWidgetProvider() {
    override val sizes: List<WidgetSize> = GridWidgetSizes
}

/**
 * The single-row sibling, as its own provider rather than a squashed grid.
 *
 * A separate provider is what gives the bar its own card in the launcher's
 * widget picker, with its own preview, so the user chooses the shape at
 * placement instead of discovering it by resizing. It also cleans both
 * configuration screens: the bar's settings show only what a bar can use, and
 * the grid's stop carrying options whose effect was invisible at the size on
 * screen. The provider info pins the shape with `maxResizeHeight`, so this
 * widget only ever renders the [ActionSizes].
 */
class QuickBarWidgetProvider : SaldoWidgetProvider() {
    override val sizes: List<WidgetSize> = ActionSizes
}
