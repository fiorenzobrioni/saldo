package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App theme based on Material 3 dynamic color.
 *
 * minSdk is 33, so dynamic color is always available and no static fallback
 * palette is needed (ADR 9 in PLANNING.md).
 */
@Composable
fun SaldoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SaldoTypography,
        content = content,
    )
}
