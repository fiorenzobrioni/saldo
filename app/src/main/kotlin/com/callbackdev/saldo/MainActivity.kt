package com.callbackdev.saldo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.callbackdev.saldo.core.designsystem.theme.SaldoTheme
import com.callbackdev.saldo.core.domain.usecase.GenerateRecurringMovementsUseCase
import com.callbackdev.saldo.navigation.SaldoApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var generateRecurringMovements: GenerateRecurringMovementsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Catch-up generation on launch, covering days the device was off
        // (PLANNING ADR 4). Idempotent, so running it every launch is safe; the
        // periodic WorkManager job is a later increment.
        lifecycleScope.launch { runCatching { generateRecurringMovements() } }
        setContent {
            SaldoTheme {
                SaldoApp()
            }
        }
    }
}
