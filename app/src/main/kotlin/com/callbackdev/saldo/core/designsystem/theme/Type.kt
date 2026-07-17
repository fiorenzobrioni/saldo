package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.callbackdev.saldo.R

/**
 * Inter (SIL Open Font License 1.1) bundled in res/font as a single variable
 * file. Two axes drive it: wght (100-900) supplies every weight the scale
 * needs, and opsz (14-32) is Inter's optical-size axis - the letterforms open
 * up and thicken for small text, tighten and refine for large text. Compose
 * does not vary opsz by rendered size on its own, so each style is bound to a
 * family whose opsz matches that style's own size (see [inter]); body and label
 * sit at the text end, headlines and the big money figures at the display end.
 * Embedded, never fetched at runtime: the downloadable Google Fonts provider
 * would need Play Services and network, which offline-first forbids.
 * License text: licenses/inter/OFL.txt.
 */
@OptIn(ExperimentalTextApi::class)
private fun interFamily(opticalSize: TextUnit): FontFamily {
    fun weightEntry(weight: FontWeight) = Font(
        resId = R.font.inter_variable,
        weight = weight,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight.weight),
            FontVariation.opticalSizing(opticalSize),
        ),
    )
    return FontFamily(
        weightEntry(FontWeight.Normal),
        weightEntry(FontWeight.Medium),
        weightEntry(FontWeight.SemiBold),
        weightEntry(FontWeight.Bold),
    )
}

/**
 * Binds a base Material style to Inter with its optical size set to the style's
 * own font size, clamped to the axis range. A style with no explicit sp size
 * falls back to the text end of the axis.
 */
private fun TextStyle.inter(): TextStyle {
    val size = if (fontSize.isSp) fontSize.value else OPSZ_MIN
    return copy(fontFamily = interFamily(size.coerceIn(OPSZ_MIN, OPSZ_MAX).sp))
}

/**
 * Material 3 type scale on the Inter family, with the app's adjustments:
 * headline and title styles carry more weight so figures and section titles
 * read crisper, without touching the body/label styles.
 */
val SaldoTypography: Typography = Typography().let { base ->
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
 * Money styling for amounts: tabular (fixed-width) figures so digits align
 * vertically in lists and totals do not shift while counting up, plus the
 * slashed zero (Inter's "zero" feature) so a 0 never reads as an O. Apply to
 * any style that renders money.
 */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum, zero")

private const val OPSZ_MIN = 14f
private const val OPSZ_MAX = 32f
