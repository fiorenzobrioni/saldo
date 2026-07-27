package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
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
                    onAppearanceSelected = viewModel::onAppearanceSelected,
                    onConfirm = ::confirm,
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun loadStoredConfig() {
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@QuickAddWidgetConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                QuickAddWidgetPrefs.read(
                    SaldoQuickAddWidget().getAppWidgetState(this@QuickAddWidgetConfigActivity, glanceId),
                )
            }.onSuccess(viewModel::initialize)
        }
    }

    private fun confirm() {
        val config = viewModel.uiState.value.config
        lifecycleScope.launch {
            runCatching {
                val manager = GlanceAppWidgetManager(this@QuickAddWidgetConfigActivity)
                val glanceId = manager.getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@QuickAddWidgetConfigActivity, glanceId) { prefs ->
                    prefs[QuickAddWidgetPrefs.AccountId] = QuickAddWidgetPrefs.encodeAccountId(config.accountId)
                    prefs[QuickAddWidgetPrefs.Type] = config.type.name
                    prefs[QuickAddWidgetPrefs.PinnedCategoryIds] =
                        QuickAddWidgetPrefs.encodePinned(config.pinnedCategoryIds)
                    prefs[QuickAddWidgetPrefs.ShowTodayTotal] = config.showTodayTotal
                    prefs[QuickAddWidgetPrefs.Appearance] = config.appearance.name
                    prefs[QuickAddWidgetPrefs.ShowAppShortcut] = config.showAppShortcut
                    // Bumped here as well as by the refresh watcher. The
                    // composition reloads on any change of its inputs, and the
                    // revision is the one input guaranteed to differ, so a
                    // setting that happens to round-trip to the same value still
                    // forces the redraw.
                    val revision = prefs[QuickAddWidgetPrefs.Revision] ?: 0L
                    prefs[QuickAddWidgetPrefs.Revision] = revision + 1
                }
                SaldoQuickAddWidget().update(this@QuickAddWidgetConfigActivity, glanceId)
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
