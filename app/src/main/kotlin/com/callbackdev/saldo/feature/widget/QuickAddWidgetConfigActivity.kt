package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.applock.AppLockRepository
import com.callbackdev.saldo.core.common.applock.bindSecureScreen
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.designsystem.theme.SaldoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The settings screen of every placed widget, reached from the launcher's
 * reconfigure flow (the providers are declared
 * `reconfigurable|configuration_optional`, so placement never opens it). The
 * quick-add widget and the two month widgets (ADR 54) each get their own
 * screen, chosen by the widget's provider.
 *
 * Every choice is saved as it is made and repaints this one widget; "Done" and
 * the back gesture only close the door (16 Oct 2026: it used to hold the
 * choices until an "Update the widget" button, and backing out dropped them).
 */
@AndroidEntryPoint
class QuickAddWidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferencesRepository

    @Inject
    lateinit var appLockRepository: AppLockRepository

    @Inject
    lateinit var configStore: WidgetConfigStore

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    // Lazy, both: only the one this widget's kind needs is ever created.
    private val viewModel: QuickAddWidgetConfigViewModel by viewModels()
    private val monthViewModel: MonthWidgetConfigViewModel by viewModels()

    private val appWidgetId: Int
        get() = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    /**
     * Which widget this is, from its provider. Lazy because it is a binder
     * call: resolved once per activity, never per frame.
     */
    private val kind: SaldoWidgetKind by lazy {
        SaldoWidgetProvider.kindOf(this, appWidgetId) ?: SaldoWidgetKind.QUICK_ADD
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Screen privacy applies here too (account names, a month's figures);
        // the lock gate deliberately does not: this is a system flow, and with
        // the lock on the month widgets' preview shows no amount (ADR 54), as
        // the quick-add one never did (trade-off stated in PLANNING, Fase 14.5).
        bindSecureScreen(appLockRepository)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // The host expects this result whether or not anything changes, and
        // there is nothing to cancel: what was chosen is already saved.
        setResult(RESULT_OK, resultIntent())
        val month = kind.month
        if (month == null) {
            loadStoredConfig()
            saveAsChosen()
        } else {
            loadStoredMonthConfig()
            saveMonthAsChosen()
        }
        setContent {
            val themePreferences by userPreferences.themePreferences
                .collectAsStateWithLifecycle(initialValue = ThemePreferences())
            val darkTheme = when (themePreferences.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SaldoTheme(darkTheme = darkTheme, dynamicColor = themePreferences.useDynamicColor) {
                if (month == null) QuickAddContent(themePreferences) else MonthContent(month, themePreferences)
            }
        }
    }

    @Composable
    private fun QuickAddContent(themePreferences: ThemePreferences) {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        // Resolved by the same function the widget uses, so the preview
        // cannot drift from what actually gets drawn - and remembered on
        // its real inputs, so it is not rebuilt on every recomposition.
        val theme = remember(
            themePreferences,
            state.config.background,
            state.config.cardColor,
            state.config.opacityPct,
        ) {
            resolveWidgetTheme(
                context = this@QuickAddWidgetConfigActivity,
                preferences = themePreferences,
                config = state.config,
            )
        }
        QuickAddWidgetConfigScreen(
            state = state,
            theme = theme,
            onAccountSelected = viewModel::onAccountSelected,
            onTypeSelected = viewModel::onTypeSelected,
            onShowAppShortcutChanged = viewModel::onShowAppShortcutChanged,
            onCustomCategoriesChanged = viewModel::onCustomCategoriesChanged,
            onCategoryToggled = viewModel::onCategoryToggled,
            onPinnedReordered = viewModel::onPinnedReordered,
            onLookChanged = viewModel::onLookChanged,
            onOpacityDrag = viewModel::onOpacityDragged,
            onOpacityDone = viewModel::onOpacityDone,
            onButtonsSelected = viewModel::onButtonsSelected,
            onReset = viewModel::onReset,
            onDone = ::finish,
            placed = remember { placedWidgetSize(this@QuickAddWidgetConfigActivity, appWidgetId) },
        )
    }

    @Composable
    private fun MonthContent(month: MonthWidgetKind, themePreferences: ThemePreferences) {
        val state by monthViewModel.uiState.collectAsStateWithLifecycle()
        val config = state.config
        val theme = remember(themePreferences, config.background, config.cardColor, config.opacityPct) {
            resolveWidgetTheme(
                this@QuickAddWidgetConfigActivity,
                themePreferences,
                config.background,
                config.cardColor,
                config.opacityPct,
            )
        }
        MonthWidgetConfigScreen(
            state = state,
            kind = month,
            theme = theme,
            placed = remember { placedWidgetSize(this@QuickAddWidgetConfigActivity, appWidgetId) },
            onLookChanged = monthViewModel::onLookChanged,
            onOpacityDrag = monthViewModel::onOpacityDragged,
            onOpacityDone = monthViewModel::onOpacityDone,
            onShowAmountsChanged = monthViewModel::onShowAmountsChanged,
            onShowNextChargeChanged = monthViewModel::onShowNextChargeChanged,
            onReset = monthViewModel::onReset,
            onDone = ::finish,
        )
    }

    private fun loadStoredConfig() {
        lifecycleScope.launch {
            val stored = runCatching { configStore.read(appWidgetId) }
                .getOrDefault(QuickAddWidgetConfig())
            // Always seeds, defaults included: the screen gates its content on
            // this, and a read that failed must degrade to an editable form
            // rather than to a spinner that never ends.
            viewModel.initialize(stored)
        }
    }

    private fun loadStoredMonthConfig() {
        lifecycleScope.launch {
            monthViewModel.initialize(
                runCatching { configStore.readMonth(appWidgetId) }.getOrDefault(MonthWidgetConfig()),
            )
        }
    }

    /**
     * Writes every choice after the stored seed and repaints the widget. A
     * write that has started finishes even if "Done" closes the screen in the
     * same instant: the reader saw the choice land on the preview, and the
     * widget must not quietly keep the old one.
     */
    private fun saveAsChosen() {
        lifecycleScope.launch {
            viewModel.savedConfig.drop(1).collect { config ->
                withContext(NonCancellable) { persist { configStore.write(appWidgetId, config) } }
            }
        }
    }

    private fun saveMonthAsChosen() {
        lifecycleScope.launch {
            monthViewModel.savedConfig.drop(1).collect { config ->
                withContext(NonCancellable) { persist { configStore.writeMonth(appWidgetId, config) } }
            }
        }
    }

    private suspend fun persist(write: suspend () -> Unit) {
        runCatching {
            write()
            // The snapshot cache is keyed by configuration, so the write
            // above already misses it; only the render has to be asked for.
            widgetUpdater.update(intArrayOf(appWidgetId), kind)
        }.onFailure {
            // The one thing worse than a failed save is a silent one: the
            // user just chose this and would find it undone.
            Toast.makeText(
                this@QuickAddWidgetConfigActivity,
                R.string.widget_config_save_error,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun resultIntent(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
