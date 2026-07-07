package com.callbackdev.saldo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.callbackdev.saldo.core.designsystem.theme.SaldoTheme
import com.callbackdev.saldo.navigation.SaldoApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaldoTheme {
                SaldoApp()
            }
        }
    }
}
