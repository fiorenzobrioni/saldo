package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * the little a widget still shows - the category list, the account list, the
 * theme settings - and refreshes on change, debounced so a restore or a bulk
 * edit costs one redraw instead of hundreds.
 *
 * Deliberately *not* watched: the transactions table. The widget shows no
 * totals and no usage-derived ordering, so recording a movement changes
 * nothing it draws - a widget on a busy day redraws exactly as often as one on
 * a quiet day, which is to say almost never.
 *
 * The observers only run while at least one widget is placed, which is why the
 * receiver reports placement changes through [onWidgetsChanged] rather than
 * this collecting unconditionally: a user who never adds a widget pays nothing
 * for the feature.
 */
@Singleton
class WidgetRefreshWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferencesRepository,
) {

    private val hasPlacedWidgets = MutableStateFlow(false)
    private var scope: CoroutineScope? = null

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
        // The grid follows the categories screen: adding, renaming, recoloring
        // or reordering there must reach the launcher.
        categoryRepository.observeCategories(),
        // The plain rows, never the balances: the widget shows no balance, and
        // the balance query re-runs on every transaction write. This one is
        // invalidated only by writes to the accounts table itself. It matters
        // twice over: a widget placed before onboarding renders as the "open
        // Saldo to get started" tile, and creating the first account is exactly
        // what makes it usable; and a pinned account renamed or archived must
        // update its badge.
        accountRepository.observeAccounts().distinctUntilChanged(),
        // The theme is part of what a widget draws: switching the app's theme
        // mode or dynamic color used to leave placed widgets in the old palette.
        // Distinct because the DataStore emits on every write of any preference,
        // not just these.
        userPreferences.themePreferences.distinctUntilChanged(),
    ) { _, _, _ -> }
        // The first emission is the state already on screen.
        .drop(1)

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
     * One-shot redraw for the debounced signal collector.
     *
     * The revision bump is not ceremony. A Glance session composes once and
     * only reacts to its own widget state, so `updateAll` on its own would
     * re-render the identical snapshot; moving the revision is what makes the
     * composition re-read the database.
     */
    private suspend fun refresh() {
        // A failed redraw must not kill the watcher: the next change picks it up.
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
