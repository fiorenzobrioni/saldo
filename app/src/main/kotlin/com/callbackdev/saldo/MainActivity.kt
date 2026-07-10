package com.callbackdev.saldo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // Catch-up generation on launch, covering days the device was off
        // (PLANNING ADR 4). Idempotent, so running it every launch is safe; the
        // periodic WorkManager job covers days the app is not opened.
        lifecycleScope.launch { runCatching { generateRecurringMovements() } }
        setContent {
            SaldoTheme {
                SaldoApp()
            }
        }
    }

    /** Asks for POST_NOTIFICATIONS once (Android 13+) so recurring notifications can show. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
