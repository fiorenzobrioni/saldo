package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps placed widgets in step with the data. A widget renders a snapshot (see
 * [SaldoQuickAddWidget]), so something has to ask for the redraw: this watches
 * the two things a widget shows - the movements behind today's total and the
 * most used categories, and the category list itself - and refreshes on change,
 * debounced so a restore or a bulk delete costs one redraw instead of hundreds.
 *
 * The database observer only runs while at least one widget is placed, which is
 * why the receiver reports placement changes through [onWidgetsChanged] rather
 * than this collecting unconditionally: a user who never adds a widget pays
 * nothing for the feature.
 */
@Singleton
class WidgetRefreshWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
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
                    if (!placed) return@flatMapLatest emptyFlow()
                    combine(
                        // One row is signal enough: any insert, edit or delete
                        // moves it, and the redraw re-reads everything anyway.
                        transactionRepository.observeRecentTransactions(1),
                        categoryRepository.observeCategories(),
                    ) { _, _ -> }
                        // The first emission is the state already on screen.
                        .drop(1)
                }
                .debounce(DEBOUNCE_MILLIS)
                .collect { refresh() }
        }
        // Off the main thread: this is a binder call, and it runs during
        // Application.onCreate on every cold start.
        scope.launch { readPlacement() }
    }

    /** Called when a widget is added or removed, and on every framework update broadcast. */
    fun onWidgetsChanged() {
        scope?.launch { readPlacement() } ?: readPlacement()
    }

    private fun readPlacement() {
        hasPlacedWidgets.value = runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, SaldoQuickAddWidgetReceiver::class.java))
                .isNotEmpty()
        }.getOrDefault(false)
    }

    /**
     * One-shot redraw for callers that already know something changed (the
     * daily worker).
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
            val widget = SaldoQuickAddWidget()
            GlanceAppWidgetManager(context).getGlanceIds(SaldoQuickAddWidget::class.java)
                .forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { preferences ->
                        val current = preferences[QuickAddWidgetPrefs.Revision] ?: 0L
                        preferences[QuickAddWidgetPrefs.Revision] = current + 1
                    }
                }
            widget.updateAll(context)
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
    }
}
