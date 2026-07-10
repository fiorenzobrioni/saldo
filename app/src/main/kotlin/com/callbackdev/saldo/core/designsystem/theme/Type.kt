package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Material 3 type scale with the app's adjustments: headline and title styles
 * carry more weight so figures and section titles read crisper, without
 * touching the body/label styles.
 */
val SaldoTypography: Typography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * Tabular (fixed-width) figures for monetary amounts: digits align vertically
 * in lists and totals do not shift while counting up. Apply to any style that
 * renders money.
 */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
