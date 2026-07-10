@file:Suppress("MagicNumber") // A palette is literal color values by nature.

package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Saldo's own Material 3 palette, used by default so the app has a recognizable
 * identity independent of the wallpaper (dynamic color remains an opt-in from
 * Settings). Seeded on a deep teal; the tertiary family is deliberately a
 * green, because [MoneyColors.income] maps to tertiary and income should read
 * green under the brand palette.
 */
internal val BrandLightColorScheme = lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F5),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6364),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E9),
    onSecondaryContainer = Color(0xFF041F21),
    tertiary = Color(0xFF3E6837),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFEFB1),
    onTertiaryContainer = Color(0xFF002201),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4849),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C9),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2D3131),
    inverseOnSurface = Color(0xFFEFF1F1),
    inversePrimary = Color(0xFF80D5D9),
    surfaceDim = Color(0xFFDADEDD),
    surfaceBright = Color(0xFFFAFDFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F7F6),
    surfaceContainer = Color(0xFFEEF1F1),
    surfaceContainerHigh = Color(0xFFE9EBEB),
    surfaceContainerHighest = Color(0xFFE3E6E5),
)

internal val BrandDarkColorScheme = darkColorScheme(
    primary = Color(0xFF80D5D9),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFF9CF1F5),
    secondary = Color(0xFFB0CCCD),
    onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF324B4C),
    onSecondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFFA4D397),
    onTertiary = Color(0xFF11380D),
    tertiaryContainer = Color(0xFF275021),
    onTertiaryContainer = Color(0xFFBFEFB1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111414),
    onBackground = Color(0xFFE1E3E3),
    surface = Color(0xFF111414),
    onSurface = Color(0xFFE1E3E3),
    surfaceVariant = Color(0xFF3F4849),
    onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF889392),
    outlineVariant = Color(0xFF3F4849),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE1E3E3),
    inverseOnSurface = Color(0xFF2D3131),
    inversePrimary = Color(0xFF00696D),
    surfaceDim = Color(0xFF111414),
    surfaceBright = Color(0xFF373A3A),
    surfaceContainerLowest = Color(0xFF0C0F0F),
    surfaceContainerLow = Color(0xFF191C1C),
    surfaceContainer = Color(0xFF1D2021),
    surfaceContainerHigh = Color(0xFF272B2B),
    surfaceContainerHighest = Color(0xFF323535),
)
