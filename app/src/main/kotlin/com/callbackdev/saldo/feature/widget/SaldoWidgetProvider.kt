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
 * Every kind of widget the app places, with the breakpoints its provider hands
 * the launcher: the quick-add widget (ADR 37) and the two month widgets (ADR 54).
 */
enum class SaldoWidgetKind(internal val sizes: (fontScale: Float) -> List<WidgetSize>, val month: MonthWidgetKind?) {
    QUICK_ADD({ scale -> gridWidgetSizes(scale) }, null),
    WORDS({ WordsSizes }, MonthWidgetKind.WORDS),
    RING({ RingSizes }, MonthWidgetKind.RING),
}

/**
 * The manifest side of the widgets: a plain [AppWidgetProvider] driving
 * `RemoteViews` directly.
 *
 * Deliberately passive on battery. There is no `updatePeriodMillis`, no polling
 * and no work of its own. The quick-add widget draws a snapshot of things that
 * only change when the user edits accounts, categories, the theme or the
 * widget's own settings (ADR 37); the month widgets also follow the ledger, the
 * budgets and the recurring rules, and the day (ADR 54). [WidgetRefreshWatcher]
 * pushes a redraw when one of those moves, and only while a widget that draws
 * it is placed.
 *
 * The lifecycle hooks only record what the broadcast needs; the suspend work
 * runs once, in [onReceive]. `goAsync()` is consume-once and a single broadcast
 * can reach two hooks - `ACTION_APPWIDGET_ENABLE_AND_UPDATE` fires `onEnabled`
 * and `onUpdate` together - where a second `goAsync()` would return null.
 */
abstract class SaldoWidgetProvider : AppWidgetProvider() {

    /**
     * The breakpoints this provider hands the launcher at a text size (see
     * [WidgetRenderer.sizeMap]): where the single row's words fit depends on it.
     */
    internal abstract val kind: SaldoWidgetKind

    private var render: IntArray? = null
    private var placementChanged = false
    private var deleted: IntArray? = null
    private var restored: Pair<IntArray, IntArray>? = null
    private var requestedType: Pair<Int, TransactionType>? = null
    private var dayChanged = false

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
        dayChanged = false
        super.onReceive(context, intent)
        // A month card is about a day: a new day (the watcher's alarm), a clock
        // or zone moved by hand, or a new language all redraw every one of them.
        // Either month provider speaks for both: the alarm is aimed at one.
        if (kind.month != null && intent.action in DayActions) dayChanged = true
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
        val newDay = dayChanged
        val wanted = listOfNotNull(ids, forget, remap, selector).isNotEmpty() || placement || newDay
        if (!wanted) return

        // Nullable despite the platform signature: goAsync() only returns a
        // result while a real broadcast is being dispatched. The work still runs.
        val pendingResult: BroadcastReceiver.PendingResult? = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            // Wrapped whole: a throw on this path would crash the app out of a
            // broadcast, where the worst acceptable outcome is a widget that
            // keeps showing whatever it already had.
            runCatching {
                val entryPoint = context.widgetEntryPoint()
                val store = entryPoint.widgetConfigStore()
                val updater = entryPoint.widgetUpdater()
                // Before any render: RESTORED is followed by onUpdate in the
                // same broadcast, so the ids have to be re-keyed first.
                remap?.let { (old, new) -> store.remap(old, new) }
                forget?.let { store.forget(it) }
                selector?.let { (id, type) ->
                    store.setCurrentType(id, type)
                    updater.update(intArrayOf(id), kind)
                }
                if (placement) entryPoint.widgetRefreshWatcher().onWidgetsChanged()
                ids?.let { updater.update(it, kind) }
                if (newDay) updater.updateMonth()
            }
            pendingResult?.finish()
        }
    }

    companion object {
        /** The home-screen expense/income selector, handled by the grid provider. */
        const val ACTION_SET_TYPE = "com.callbackdev.saldo.widget.SET_TYPE"

        /** The month widgets' midnight alarm (ADR 54), see [MonthWidgetDayAlarm]. */
        const val ACTION_NEW_DAY = "com.callbackdev.saldo.widget.NEW_DAY"

        const val EXTRA_APPWIDGET_ID = "com.callbackdev.saldo.widget.EXTRA_APPWIDGET_ID"
        const val EXTRA_TYPE = "com.callbackdev.saldo.widget.EXTRA_TYPE"

        /**
         * The broadcasts that change a month card's day: the alarm, and the
         * three a manifest receiver still gets (all exempt from the implicit
         * broadcast limits), for a clock or a zone moved by hand and a new
         * language.
         */
        private val DayActions = setOf(
            ACTION_NEW_DAY,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
        )

        /** Every provider the watcher keeps fresh, with its kind. */
        internal val providers: List<Pair<Class<out SaldoWidgetProvider>, SaldoWidgetKind>> = listOf(
            QuickAddWidgetProvider::class.java to SaldoWidgetKind.QUICK_ADD,
            MonthWordsWidgetProvider::class.java to SaldoWidgetKind.WORDS,
            MonthRingWidgetProvider::class.java to SaldoWidgetKind.RING,
        )

        /** True while at least one widget of [kinds] is placed. */
        fun hasWidgets(context: Context, kinds: Set<SaldoWidgetKind> = SaldoWidgetKind.entries.toSet()): Boolean =
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                providers.any { (provider, kind) ->
                    kind in kinds && manager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
                }
            }.getOrDefault(false)

        /** Which kind a placed widget is, from its provider; null for an id that is not ours. */
        fun kindOf(context: Context, appWidgetId: Int): SaldoWidgetKind? = runCatching {
            val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className
            providers.firstOrNull { (cls, _) -> cls.name == provider }?.second
        }.getOrNull()
    }
}

/**
 * The quick-add widget: the expense/income selector over a grid of category
 * tiles, and the two-button row it becomes when squashed to one launcher row.
 * Since 16 Oct 2026 it is the only quick-add widget: the bar had its own
 * provider, whose whole range this one already covered (Fase 45).
 */
class QuickAddWidgetProvider : SaldoWidgetProvider() {
    override val kind = SaldoWidgetKind.QUICK_ADD
}

/** «In words»: the month in one sentence (ADR 54). */
class MonthWordsWidgetProvider : SaldoWidgetProvider() {
    override val kind = SaldoWidgetKind.WORDS
}

/** «The month»: the ring of the overall monthly budget (ADR 54). */
class MonthRingWidgetProvider : SaldoWidgetProvider() {
    override val kind = SaldoWidgetKind.RING
}
