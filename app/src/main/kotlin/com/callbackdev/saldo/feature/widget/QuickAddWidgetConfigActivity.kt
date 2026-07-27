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
import androidx.compose.runtime.getValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.designsystem.theme.SaldoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Configuration screen of a placed widget, opened at placement and re-openable
 * later (the provider is declared `reconfigurable|configuration_optional`).
 *
 * The widget is already usable without ever coming here, so this screen never
 * blocks: cancelling still leaves a working widget on the defaults.
 */
@AndroidEntryPoint
class QuickAddWidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferencesRepository

    private val viewModel: QuickAddWidgetConfigViewModel by viewModels()

    private val appWidgetId: Int
        get() = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    /**
     * Which of the two providers this instance belongs to: the same activity
     * configures both, and the flavor decides which sections the screen shows
     * (the bar has no grid to configure, the grid no buttons).
     */
    private val isBar: Boolean
        get() = runCatching {
            AppWidgetManager.getInstance(this)
                .getAppWidgetInfo(appWidgetId)
                ?.provider?.className == SaldoQuickBarWidgetReceiver::class.java.name
        }.getOrDefault(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Declared up front: if the user backs out, the launcher must still keep
        // the widget rather than dropping it as a failed placement.
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        loadStoredConfig()
        setContent {
            val themePreferences by userPreferences.themePreferences
                .collectAsStateWithLifecycle(initialValue = ThemePreferences())
            val darkTheme = when (themePreferences.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SaldoTheme(darkTheme = darkTheme, dynamicColor = themePreferences.useDynamicColor) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                QuickAddWidgetConfigScreen(
                    state = state,
                    isBar = isBar,
                    // Resolved here, from the same function the widget uses, so
                    // the preview cannot drift from what actually gets drawn.
                    theme = resolveWidgetTheme(
                        context = this@QuickAddWidgetConfigActivity,
                        preferences = themePreferences,
                        config = state.config,
                    ),
                    onAccountSelected = viewModel::onAccountSelected,
                    onTypeSelected = viewModel::onTypeSelected,
                    onShowTodayTotalChanged = viewModel::onShowTodayTotalChanged,
                    onShowAppShortcutChanged = viewModel::onShowAppShortcutChanged,
                    onUseMostUsedChanged = viewModel::onUseMostUsedChanged,
                    onCategoryToggled = viewModel::onCategoryToggled,
                    onPinnedReordered = viewModel::onPinnedReordered,
                    onAppearanceSelected = viewModel::onAppearanceSelected,
                    onOpacityChanged = viewModel::onOpacityChanged,
                    onButtonsSelected = viewModel::onButtonsSelected,
                    onConfirm = ::confirm,
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun loadStoredConfig() {
        lifecycleScope.launch {
            val stored = runCatching {
                val glanceId = GlanceAppWidgetManager(this@QuickAddWidgetConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                val widget = if (isBar) SaldoQuickBarWidget() else SaldoQuickAddWidget()
                QuickAddWidgetPrefs.read(
                    widget.getAppWidgetState(this@QuickAddWidgetConfigActivity, glanceId),
                )
            }.getOrDefault(QuickAddWidgetConfig())
            // Always seeds, defaults included: the screen gates its content on
            // this, and a read that failed must degrade to an editable form
            // rather than to a spinner that never ends.
            viewModel.initialize(stored)
        }
    }

    private fun confirm() {
        val config = viewModel.uiState.value.config
        lifecycleScope.launch {
            runCatching {
                val bar = isBar
                val manager = GlanceAppWidgetManager(this@QuickAddWidgetConfigActivity)
                val glanceId = manager.getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@QuickAddWidgetConfigActivity, glanceId) { prefs ->
                    prefs[QuickAddWidgetPrefs.AccountId] = QuickAddWidgetPrefs.encodeAccountId(config.accountId)
                    prefs[QuickAddWidgetPrefs.Type] = config.type.name
                    prefs[QuickAddWidgetPrefs.PinnedCategoryIds] =
                        QuickAddWidgetPrefs.encodePinned(config.pinnedCategoryIds)
                    prefs[QuickAddWidgetPrefs.ShowTodayTotal] = config.showTodayTotal
                    prefs[QuickAddWidgetPrefs.Appearance] = config.appearance.name
                    prefs[QuickAddWidgetPrefs.BackgroundOpacity] = config.backgroundOpacity
                    prefs[QuickAddWidgetPrefs.Buttons] = config.buttons.name
                    // Confirming settings puts the widget back on its configured
                    // start: leaving the runtime choice behind would mean the
                    // widget ignored the value just chosen.
                    prefs[QuickAddWidgetPrefs.CurrentType] = config.type.name
                    prefs[QuickAddWidgetPrefs.ShowAppShortcut] = config.showAppShortcut
                    // Bumped here as well as by the refresh watcher. The
                    // composition reloads on any change of its inputs, and the
                    // revision is the one input guaranteed to differ, so a
                    // setting that happens to round-trip to the same value still
                    // forces the redraw.
                    val revision = prefs[QuickAddWidgetPrefs.Revision] ?: 0L
                    prefs[QuickAddWidgetPrefs.Revision] = revision + 1
                }
                if (bar) {
                    SaldoQuickBarWidget().update(this@QuickAddWidgetConfigActivity, glanceId)
                } else {
                    SaldoQuickAddWidget().update(this@QuickAddWidgetConfigActivity, glanceId)
                }
            }.onFailure {
                // The one thing worse than a failed save is a silent one: the
                // user just chose these settings and would find them undone.
                Toast.makeText(
                    this@QuickAddWidgetConfigActivity,
                    R.string.widget_config_save_error,
                    Toast.LENGTH_LONG,
                ).show()
            }
            // Even a failed write leaves a usable widget on the defaults, so the
            // placement is confirmed either way rather than silently discarded.
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
