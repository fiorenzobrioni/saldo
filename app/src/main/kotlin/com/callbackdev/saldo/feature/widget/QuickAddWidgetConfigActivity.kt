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

    @Inject
    lateinit var appLockRepository: AppLockRepository

    @Inject
    lateinit var configStore: WidgetConfigStore

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    private val viewModel: QuickAddWidgetConfigViewModel by viewModels()

    private val appWidgetId: Int
        get() = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    /**
     * Which of the two providers this instance belongs to: the same activity
     * configures both, and the flavor decides which sections the screen shows
     * (the bar has no grid to configure, the grid no buttons). Lazy because it
     * is a binder call: resolved once per activity, never per frame.
     */
    private val isBar: Boolean by lazy {
        runCatching {
            AppWidgetManager.getInstance(this)
                .getAppWidgetInfo(appWidgetId)
                ?.provider?.className == QuickBarWidgetProvider::class.java.name
        }.getOrDefault(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Screen privacy applies here too (account names on screen); the lock
        // gate deliberately does not: this is a system placement flow that
        // shows no amounts (trade-off stated in PLANNING, Fase 14.5).
        bindSecureScreen(appLockRepository)
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
                // Resolved by the same function the widget uses, so the preview
                // cannot drift from what actually gets drawn - and remembered on
                // its real inputs, so it is not rebuilt on every recomposition.
                val theme = remember(themePreferences, state.config.appearance) {
                    resolveWidgetTheme(
                        context = this@QuickAddWidgetConfigActivity,
                        preferences = themePreferences,
                        config = state.config,
                    )
                }
                QuickAddWidgetConfigScreen(
                    state = state,
                    isBar = isBar,
                    theme = theme,
                    onAccountSelected = viewModel::onAccountSelected,
                    onTypeSelected = viewModel::onTypeSelected,
                    onShowAppShortcutChanged = viewModel::onShowAppShortcutChanged,
                    onCustomCategoriesChanged = viewModel::onCustomCategoriesChanged,
                    onCategoryToggled = viewModel::onCategoryToggled,
                    onPinnedReordered = viewModel::onPinnedReordered,
                    onAppearanceSelected = viewModel::onAppearanceSelected,
                    onButtonsSelected = viewModel::onButtonsSelected,
                    onConfirm = ::confirm,
                    onCancel = ::finish,
                )
            }
        }
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

    private fun confirm() {
        val config = viewModel.uiState.value.config
        lifecycleScope.launch {
            runCatching {
                configStore.write(appWidgetId, config)
                // The snapshot cache is keyed by configuration, so the write
                // above already misses it; only the render has to be asked for.
                widgetUpdater.update(
                    appWidgetIds = intArrayOf(appWidgetId),
                    sizes = if (isBar) ActionSizes else GridWidgetSizes,
                )
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
