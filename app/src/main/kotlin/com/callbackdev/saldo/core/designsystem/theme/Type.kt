package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.callbackdev.saldo.R

/**
 * Inter (SIL Open Font License 1.1) bundled in res/font as a single variable
 * file: the wght axis (100-900) supplies every weight the type scale needs, so
 * one asset covers Regular, Medium and SemiBold. Embedded, never fetched at
 * runtime - the downloadable Google Fonts provider would need Play Services and
 * network, which offline-first forbids. Inter uses tabular figures and exposes
 * the "tnum" feature (see [tabularNumbers]), so monetary columns keep aligning.
 * License text: licenses/inter/OFL.txt.
 */
private val InterFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Medium),
    Font(R.font.inter_variable, FontWeight.SemiBold),
    Font(R.font.inter_variable, FontWeight.Bold),
)

/**
 * Material 3 type scale on the Inter family, with the app's adjustments:
 * headline and title styles carry more weight so figures and section titles
 * read crisper, without touching the body/label styles.
 */
val SaldoTypography: Typography = Typography().let { base ->
    fun TextStyle.inter() = copy(fontFamily = InterFamily)
    base.copy(
        displayLarge = base.displayLarge.inter(),
        displayMedium = base.displayMedium.inter(),
        displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter().copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.inter().copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.inter().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.inter().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.inter(),
        bodyLarge = base.bodyLarge.inter(),
        bodyMedium = base.bodyMedium.inter(),
        bodySmall = base.bodySmall.inter(),
        labelLarge = base.labelLarge.inter(),
        labelMedium = base.labelMedium.inter(),
        labelSmall = base.labelSmall.inter(),
    )
}

/**
 * Tabular (fixed-width) figures for monetary amounts: digits align vertically
 * in lists and totals do not shift while counting up. Apply to any style that
 * renders money.
 */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
