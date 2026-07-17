package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.callbackdev.saldo.R

/**
 * Inter (SIL Open Font License 1.1) bundled in res/font as a single variable
 * file: the wght axis (100-900) supplies every weight the type scale needs, so
 * one asset covers Regular, Medium and SemiBold. The opsz axis is left at the
 * font default: tying it to each style's size was tried and reverted, the
 * on-screen difference did not justify pinning an experimental API and it
 * darkened headlines and the money figures more than intended. Embedded, never
 * fetched at runtime - the downloadable Google Fonts provider would need Play
 * Services and network, which offline-first forbids. Inter uses tabular figures
 * and exposes "tnum"/"zero" (see [tabularNumbers]), so monetary columns keep
 * aligning. License text: licenses/inter/OFL.txt.
 */
private val InterFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Medium),
    Font(R.font.inter_variable, FontWeight.SemiBold),
    Font(R.font.inter_variable, FontWeight.Bold),
)

/**
 * Letter spacing re-tuned for Inter. Material 3's baseline tracking is tuned for
 * Roboto and reads a touch loose on Inter, most of all on the body/label styles
 * that carry the largest positive tracking. Large styles get a slight negative
 * tracking so titles and figures read tighter; body and label are set to zero to
 * drop the Roboto-era positive tracking without cramping small text. This is a
 * deliberate adaptation of the scale to the substituted typeface, not the M3
 * default.
 */
private val TightTracking: TextUnit = (-0.01).em
private val NeutralTracking: TextUnit = 0.0.em

/**
 * Material 3 type scale on the Inter family, with the app's adjustments:
 * headline and title styles carry more weight so figures and section titles
 * read crisper, without touching the body/label styles.
 */
val SaldoTypography: Typography = Typography().let { base ->
    fun TextStyle.inter(tracking: TextUnit) = copy(fontFamily = InterFamily, letterSpacing = tracking)
    base.copy(
        displayLarge = base.displayLarge.inter(TightTracking),
        displayMedium = base.displayMedium.inter(TightTracking),
        displaySmall = base.displaySmall.inter(TightTracking),
        headlineLarge = base.headlineLarge.inter(TightTracking).copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.inter(TightTracking).copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.inter(TightTracking).copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.inter(TightTracking).copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.inter(TightTracking).copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.inter(TightTracking),
        bodyLarge = base.bodyLarge.inter(NeutralTracking),
        bodyMedium = base.bodyMedium.inter(NeutralTracking),
        bodySmall = base.bodySmall.inter(NeutralTracking),
        labelLarge = base.labelLarge.inter(NeutralTracking),
        labelMedium = base.labelMedium.inter(NeutralTracking),
        labelSmall = base.labelSmall.inter(NeutralTracking),
    )
}

/**
 * Money styling for amounts: tabular (fixed-width) figures so digits align
 * vertically in lists and totals do not shift while counting up, plus the
 * slashed zero (Inter's "zero" feature) so a 0 never reads as an O. Apply to
 * any style that renders money.
 */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum, zero")
