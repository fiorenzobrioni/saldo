package com.callbackdev.saldo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.core.common.applock.AppLockManager
import com.callbackdev.saldo.core.common.applock.AppLockRepository
import com.callbackdev.saldo.core.common.applock.AppLockState
import com.callbackdev.saldo.core.common.applock.bindSecureScreen
import com.callbackdev.saldo.core.common.di.ApplicationScope
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.designsystem.theme.SaldoTheme
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.usecase.GenerateRecurringMovementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ProcessDueCreditCardStatementsUseCase
import com.callbackdev.saldo.feature.applock.AppLockGate
import com.callbackdev.saldo.feature.onboarding.OnboardingScreen
import com.callbackdev.saldo.navigation.SaldoApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var generateRecurringMovements: GenerateRecurringMovementsUseCase

    @Inject
    lateinit var processDueStatements: ProcessDueCreditCardStatementsUseCase

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var userPreferences: UserPreferencesRepository

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var appLockRepository: AppLockRepository

    private val mainViewModel: MainViewModel by viewModels()

    /**
     * The quick action requested by a launcher app shortcut, consumed once the
     * navigation is up. Kept as state (rather than read straight from the
     * intent) so the app opens onto the right editor whether it was cold or
     * warm started, and so a configuration change never re-fires it.
     */
    private val pendingQuickAction = MutableStateFlow<TransactionType?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Before setContent: FLAG_SECURE must be on the window ahead of the
        // first frame, not after the first composition.
        bindSecureScreen(appLockRepository)
        // Only on a genuine start, never on a configuration-change recreation:
        // the catch-up is idempotent but pointless to repeat on every rotation,
        // and the launching intent must open its shortcut editor just once.
        if (savedInstanceState == null) {
            // Catch-up generation on launch, covering days the device was off
            // (PLANNING ADR 4). The periodic WorkManager job covers days the app
            // is not opened. Launched in the application scope so a configuration
            // change cannot cancel it mid-run.
            applicationScope.launch { runCatching { generateRecurringMovements() } }
            // Same catch-up rationale for auto-post credit card statements: settle
            // the cycles that came due while the app was closed. Idempotent, so
            // overlapping with the periodic worker is harmless.
            applicationScope.launch { runCatching { processDueStatements() } }
            pendingQuickAction.value = quickActionFrom(intent)
        }
        setContent {
            val themePreferences by userPreferences.themePreferences
                .collectAsStateWithLifecycle(initialValue = ThemePreferences())
            val darkTheme = when (themePreferences.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // The no-arg enableEdgeToEdge above follows the system uiMode, which is
            // wrong when the in-app theme is forced (dark app over light system left
            // the status bar icons dark on dark). Re-apply the bar styles keyed to
            // the resolved theme so icon contrast always matches what is on screen.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }
            SaldoTheme(
                darkTheme = darkTheme,
                dynamicColor = themePreferences.useDynamicColor,
            ) {
                // First-launch gate: onboarding lives above the Nav3 back stack,
                // so the app's navigation is untouched. LOADING renders nothing:
                // SaldoTheme's full-screen Surface is the themed backdrop, and
                // the decision (one DataStore read) resolves within a frame or
                // two, faster than any splash could fade.
                val gate by mainViewModel.gate.collectAsStateWithLifecycle()
                // The app lock is an overlay on top of the launch gate, never
                // a branch of it (ADR 39): switching the Crossfade would drop
                // the per-tab back stacks on every re-lock. While the gate is
                // not open, the covered content is also stripped of semantics
                // so TalkBack cannot walk the UI behind the lock.
                val lockState by appLockManager.state.collectAsStateWithLifecycle()
                Box(modifier = Modifier.fillMaxSize()) {
                    val contentModifier = if (lockState != AppLockState.UNLOCKED) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        Modifier
                    }
                    Box(modifier = contentModifier) {
                        Crossfade(targetState = gate, label = "launch-gate") { current ->
                            when (current) {
                                LaunchGate.LOADING -> Unit
                                LaunchGate.ONBOARDING -> OnboardingScreen(
                                    onFinished = mainViewModel::completeOnboarding,
                                )
                                LaunchGate.APP -> {
                                    val quickAction by pendingQuickAction.collectAsStateWithLifecycle()
                                    SaldoApp(
                                        quickAction = quickAction,
                                        onQuickActionHandled = { pendingQuickAction.value = null },
                                    )
                                }
                            }
                        }
                    }
                    AppLockGate()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        quickActionFrom(intent)?.let { pendingQuickAction.value = it }
    }

    /** Maps a launcher shortcut intent to the movement type it opens, or null. */
    private fun quickActionFrom(intent: Intent?): TransactionType? = when (intent?.action) {
        ACTION_ADD_EXPENSE -> TransactionType.EXPENSE
        ACTION_ADD_INCOME -> TransactionType.INCOME
        ACTION_ADD_TRANSFER -> TransactionType.TRANSFER
        else -> null
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "com.callbackdev.saldo.action.ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "com.callbackdev.saldo.action.ADD_INCOME"
        const val ACTION_ADD_TRANSFER = "com.callbackdev.saldo.action.ADD_TRANSFER"
    }
}
