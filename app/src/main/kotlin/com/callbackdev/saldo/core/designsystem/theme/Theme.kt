package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * App theme: the brand palette by default (a stable identity across devices
 * and store screenshots), with Material 3 dynamic color as an opt-in from
 * Settings. minSdk is 33, so when [dynamicColor] is on no availability check
 * is needed (revised ADR 9 in PLANNING.md).
 *
 * [applyBackground] draws the opaque themed backdrop described below. It is on
 * everywhere the app owns the whole window, and off in the one place that must
 * let the surface behind show through: the quick-entry sheet, whose translucent
 * window sits over the launcher.
 */
@Composable
fun SaldoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    applyBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> BrandDarkColorScheme
        else -> BrandLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SaldoTypography,
        shapes = SaldoShapes,
    ) {
        CompositionLocalProvider(
            LocalMoneyColors provides moneyColors(colorScheme, darkTheme),
            LocalSaldoSurfaces provides saldoSurfaces(colorScheme, darkTheme),
        ) {
            // An opaque themed backdrop behind everything: without it, the light
            // window background shows through the Nav 3 fade transitions when the
            // in-app theme is dark but the system (hence the XML window theme) is
            // light, causing a white flash between screens.
            if (applyBackground) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.background,
                    content = content,
                )
            } else {
                content()
            }
        }
    }
}
