package com.callbackdev.saldo.feature.widget

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Keeps placed widgets in step with the data. A widget renders a snapshot (see
 * [SaldoQuickAddWidget]), so something has to ask for the redraw: this watches
 * everything a widget shows - the movements behind today's total, the category
 * list, the accounts, the theme settings - and refreshes on change, debounced
 * so a restore or a bulk delete costs one redraw instead of hundreds.
 *
 * The observers only run while at least one widget is placed, which is why the
 * receiver reports placement changes through [onWidgetsChanged] rather than
 * this collecting unconditionally: a user who never adds a widget pays nothing
 * for the feature. The same gate turns the midnight refresh and the wallpaper
 * listener on and off.
 */
@Singleton
class WidgetRefreshWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock,
) {

    private val hasPlacedWidgets = MutableStateFlow(false)
    private var scope: CoroutineScope? = null
    private var wallpaperListener: WallpaperManager.OnColorsChangedListener? = null

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        scope.launch {
            hasPlacedWidgets
                .flatMapLatest { placed ->
                    if (!placed) emptyFlow() else refreshSignals()
                }
                .debounce(DEBOUNCE_MILLIS)
                .collect { refresh() }
        }
        scope.launch {
            hasPlacedWidgets.collect { placed ->
                if (placed) onWidgetsPresent() else onNoWidgetsLeft()
            }
        }
        // Off the main thread: this is a binder call, and it runs during
        // Application.onCreate on every cold start.
        scope.launch { readPlacement() }
    }

    /**
     * Everything whose change makes a placed widget wrong. Named and internal so
     * the set can be asserted: an omission here is invisible in a build and
     * shows up only as a widget that quietly stops keeping up.
     */
    internal fun refreshSignals(): Flow<Unit> = combine(
        // One row is signal enough: any insert, edit or delete moves it, and
        // the redraw re-reads everything anyway.
        transactionRepository.observeRecentTransactions(1),
        categoryRepository.observeCategories(),
        // The accounts are not optional. A widget placed before onboarding has
        // no account, so it renders as the "open Saldo to get started" tile and
        // every tap opens the app; creating the first account is exactly what
        // makes it usable, and without this signal that moment went unnoticed
        // and the widget stayed a dead tile.
        accountRepository.observeAccountsWithBalance(),
        // The theme is part of what a widget draws: switching the app's theme
        // mode or dynamic color used to leave placed widgets in the old palette
        // until the next movement happened to redraw them. Distinct because the
        // DataStore emits on every write of any preference, not just these.
        userPreferences.themePreferences.distinctUntilChanged(),
    ) { _, _, _, _ -> }
        // The first emission is the state already on screen.
        .drop(1)

    /** Fire-and-forget redraw for callers outside a coroutine (the wallpaper listener). */
    fun requestRedraw() {
        scope?.launch { refresh() }
    }

    /** Called when a widget is added or removed, and on every framework update broadcast. */
    fun onWidgetsChanged() {
        scope?.launch { readPlacement() } ?: readPlacement()
    }

    private fun readPlacement() {
        hasPlacedWidgets.value = runCatching {
            val manager = AppWidgetManager.getInstance(context)
            widgetReceivers.any { receiver ->
                manager.getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty()
            }
        }.getOrDefault(false)
    }

    /**
     * What a placed widget needs beyond the data observers: the redraw at local
     * midnight that rolls "today's" total over with the day, and the wallpaper
     * listener that re-inks a mostly transparent widget when the picture under
     * it changes (see `resolveWidgetTheme`, which reads the wallpaper's own
     * dark-text hint). Re-arming the schedule on every placement pass is cheap
     * and re-anchors it after timezone moves.
     */
    private fun onWidgetsPresent() {
        WidgetMidnightRefresh.schedule(context, clock)
        if (wallpaperListener == null) {
            val listener = WallpaperManager.OnColorsChangedListener { _, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) requestRedraw()
            }
            runCatching {
                WallpaperManager.getInstance(context)
                    .addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            }.onSuccess { wallpaperListener = listener }
        }
    }

    private fun onNoWidgetsLeft() {
        WidgetMidnightRefresh.cancel(context)
        wallpaperListener?.let { listener ->
            runCatching { WallpaperManager.getInstance(context).removeOnColorsChangedListener(listener) }
        }
        wallpaperListener = null
    }

    /**
     * One-shot redraw for callers that already know something changed (the
     * daily worker, the midnight worker).
     *
     * The revision bump is not ceremony. A Glance session composes once and
     * only reacts to its own widget state, so `updateAll` on its own would
     * re-render the identical snapshot; moving the revision is what makes the
     * composition re-read the database.
     */
    suspend fun refresh() {
        // A failed redraw must not kill the watcher: the next change, or the
        // daily worker, picks it up.
        runCatching {
            val manager = GlanceAppWidgetManager(context)
            // Both providers, grid and bar: they render the same data.
            listOf(SaldoQuickAddWidget(), SaldoQuickBarWidget()).forEach { widget ->
                manager.getGlanceIds(widget.javaClass).forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { preferences ->
                        val current = preferences[QuickAddWidgetPrefs.Revision] ?: 0L
                        preferences[QuickAddWidgetPrefs.Revision] = current + 1
                    }
                }
                widget.updateAll(context)
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 500L

        /** Every manifest receiver whose widgets this watcher keeps fresh. */
        val widgetReceivers = listOf(
            SaldoQuickAddWidgetReceiver::class.java,
            SaldoQuickBarWidgetReceiver::class.java,
        )
    }
}
