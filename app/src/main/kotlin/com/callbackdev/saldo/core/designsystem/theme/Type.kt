package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.prefs.AppFont

/*
 * The typefaces (ADR 57): Google Sans, the default, and Inter, both variable fonts under the SIL
 * Open Font License 1.1 (licenses/google-sans, licenses/inter), plus the phone's own font.
 * Embedded, never fetched at runtime: the downloadable Google Fonts provider would need Play
 * Services and network, which offline-first forbids. Google Sans is the family's cut, down to the
 * scripts the app prints.
 *
 * Every face carries tabular figures ("tnum", see [tabularNumbers]), so monetary columns keep
 * aligning whichever one the user picks. Inter also draws the slashed zero ("zero"); Google Sans
 * has none and ignores the request, and in an amount there is no letter O to tell a zero from.
 */

// The variationSettings overload is still experimental. Without it Android synthesises the
// weights by smearing one outline, which is what a variable font is bundled to avoid.
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Google Sans: its weight axis runs from 400 to 700, which is every weight the scale asks for. */
private val GoogleSansFamily = FontFamily(
    variable(R.font.google_sans_variable, 400),
    variable(R.font.google_sans_variable, 500),
    variable(R.font.google_sans_variable, 600),
    variable(R.font.google_sans_variable, 700),
)

/**
 * Inter: the wght axis (100-900) supplies every weight. The opsz axis is left at the font
 * default: tying it to each style's size was tried and reverted, the on-screen difference did not
 * justify pinning an experimental API and it darkened headlines and the money figures more than
 * intended.
 */
private val InterFamily = FontFamily(
    variable(R.font.inter_variable, 400),
    variable(R.font.inter_variable, 500),
    variable(R.font.inter_variable, 600),
    variable(R.font.inter_variable, 700),
)

/**
 * The phone's own sans: a different font on every device, with whatever weights and figures it
 * has. The same face the home-screen widgets are drawn in, since RemoteViews always use it.
 */
private val SystemFamily = FontFamily.Default

private fun familyFor(font: AppFont): FontFamily = when (font) {
    AppFont.GOOGLE_SANS -> GoogleSansFamily
    AppFont.INTER -> InterFamily
    AppFont.SYSTEM -> SystemFamily
}

/**
 * Letter spacing re-tuned away from Roboto's. Material 3's baseline tracking is tuned for Roboto
 * and reads a touch loose on the bundled faces, most of all on the body/label styles that carry
 * the largest positive tracking. Large styles get a slight negative tracking so titles and figures
 * read tighter; body and label are set to zero to drop the Roboto-era positive tracking without
 * cramping small text. The same tuning holds for Google Sans and Inter, both of which are drawn
 * wider than Roboto.
 */
private const val TIGHT_TRACKING_EM = -0.01
private val TightTracking: TextUnit = TIGHT_TRACKING_EM.em
private val NeutralTracking: TextUnit = 0.0.em

/**
 * Material 3 type scale in [family], with the app's adjustments: headline and title styles carry
 * more weight so figures and section titles read crisper, without touching the body/label styles.
 */
private fun typographyFor(family: FontFamily): Typography = Typography().let { base ->
    fun TextStyle.face(tracking: TextUnit) = copy(fontFamily = family, letterSpacing = tracking)
    base.copy(
        displayLarge = base.displayLarge.face(TightTracking),
        displayMedium = base.displayMedium.face(TightTracking),
        displaySmall = base.displaySmall.face(TightTracking),
        headlineLarge = base.headlineLarge.face(TightTracking).copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.face(TightTracking).copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.face(TightTracking).copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.face(TightTracking).copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.face(TightTracking).copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.face(TightTracking),
        bodyLarge = base.bodyLarge.face(NeutralTracking),
        bodyMedium = base.bodyMedium.face(NeutralTracking),
        bodySmall = base.bodySmall.face(NeutralTracking),
        labelLarge = base.labelLarge.face(NeutralTracking),
        labelMedium = base.labelMedium.face(NeutralTracking),
        labelSmall = base.labelSmall.face(NeutralTracking),
    )
}

private val typographies = AppFont.entries.associateWith { typographyFor(familyFor(it)) }

/** The scale in the user's face: built once per face and picked, the user changes it about once. */
fun saldoTypography(font: AppFont): Typography = typographies.getValue(font)

/** The family of [font], for a sample drawn in a face other than the one on screen (the picker). */
fun fontFamilyOf(font: AppFont): FontFamily = familyFor(font)

/**
 * Money styling for amounts: tabular (fixed-width) figures so digits align
 * vertically in lists and totals do not shift while counting up, plus the
 * slashed zero (Inter's "zero" feature; a face without it ignores it) so a 0
 * never reads as an O. Apply to any style that renders money.
 */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum, zero")
