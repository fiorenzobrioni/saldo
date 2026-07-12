package com.callbackdev.saldo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.core.common.di.ApplicationScope
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.designsystem.theme.SaldoTheme
import com.callbackdev.saldo.core.domain.usecase.GenerateRecurringMovementsUseCase
import com.callbackdev.saldo.feature.onboarding.OnboardingScreen
import com.callbackdev.saldo.navigation.SaldoApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var generateRecurringMovements: GenerateRecurringMovementsUseCase

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var userPreferences: UserPreferencesRepository

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Catch-up generation on launch, covering days the device was off
        // (PLANNING ADR 4). Idempotent, so running it every launch is safe; the
        // periodic WorkManager job covers days the app is not opened. Launched in
        // the application scope so a configuration change cannot cancel it mid-run.
        applicationScope.launch { runCatching { generateRecurringMovements() } }
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
                Crossfade(targetState = gate, label = "launch-gate") { current ->
                    when (current) {
                        LaunchGate.LOADING -> Unit
                        LaunchGate.ONBOARDING -> OnboardingScreen(
                            onFinished = mainViewModel::completeOnboarding,
                        )
                        LaunchGate.APP -> SaldoApp()
                    }
                }
            }
        }
    }
}
