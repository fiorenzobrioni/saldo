package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's structural surfaces, resolved once per theme so every screen shares
 * one figure/ground relationship: content cards sit visually *above* the screen
 * canvas.
 *
 * Material 3's tonal model runs opposite ways in the two themes: in light,
 * higher surface containers get darker (the framework's own default is a grey
 * card on a white surface), while in dark they get lighter. To keep the "card
 * pops above the canvas" reading in both, the canvas and card tokens are picked
 * per theme rather than from a single fixed scale:
 *
 * - light: a whisper-grey canvas ([ColorScheme.surfaceContainerLow]) with white
 *   cards ([ColorScheme.surfaceContainerLowest]);
 * - dark: the base background as canvas with a raised container as the card.
 *
 * Separation is carried by a 1dp [cardBorder] hairline, not a shadow.
 */
@Immutable
data class SaldoSurfaces(
    val canvas: Color,
    val card: Color,
    val cardBorder: Color,
)

internal fun saldoSurfaces(colorScheme: ColorScheme, darkTheme: Boolean): SaldoSurfaces =
    if (darkTheme) {
        SaldoSurfaces(
            canvas = colorScheme.background,
            card = colorScheme.surfaceContainer,
            cardBorder = colorScheme.outlineVariant,
        )
    } else {
        SaldoSurfaces(
            canvas = colorScheme.surfaceContainerLow,
            card = colorScheme.surfaceContainerLowest,
            cardBorder = colorScheme.outlineVariant,
        )
    }

internal val LocalSaldoSurfaces = staticCompositionLocalOf {
    SaldoSurfaces(
        canvas = Color.Unspecified,
        card = Color.Unspecified,
        cardBorder = Color.Unspecified,
    )
}

/** Access point: `MaterialTheme.saldoSurfaces.card`. */
val MaterialTheme.saldoSurfaces: SaldoSurfaces
    @Composable
    @ReadOnlyComposable
    get() = LocalSaldoSurfaces.current
