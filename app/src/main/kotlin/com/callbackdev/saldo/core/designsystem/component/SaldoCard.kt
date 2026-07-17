package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces

/**
 * The app's single card surface. Every screen builds its cards from this so the
 * card language stays consistent instead of each screen re-deriving colors,
 * shape and border inline.
 *
 * The look is "flat, border-led": a white panel on the grey screen canvas in the
 * light theme (a raised container over the darker canvas in dark), separated by a
 * 1dp hairline and no shadow. Semantic cards keep their own [containerColor]
 * (e.g. `errorContainer` for an overspent budget) and automatically get a
 * low-opacity in-tint hairline instead of the neutral grey one, so a colored
 * card keeps the same edge language without a mismatched ring.
 */
@Composable
fun SaldoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = SaldoCardDefaults.containerColor,
    contentColor: Color = SaldoCardDefaults.contentColorFor(containerColor),
    border: BorderStroke = SaldoCardDefaults.border(containerColor),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = contentColor,
    )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = SaldoCardDefaults.flatElevation,
            border = border,
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = SaldoCardDefaults.flatElevation,
            border = border,
            content = content,
        )
    }
}

object SaldoCardDefaults {

    /** Hairline width for the card border. */
    val BorderWidth = 1.dp

    /** Opacity of the in-tint hairline drawn around semantic (colored) cards. */
    private const val TINT_BORDER_ALPHA = 0.15f

    /** No shadow: separation is carried by the border, not by elevation. */
    val flatElevation
        @Composable get() = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        )

    /** The neutral card fill: white on light, a raised container on dark. */
    val containerColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.saldoSurfaces.card

    /** onSurface for the neutral card, the paired on-color for a semantic one. */
    @Composable
    @ReadOnlyComposable
    fun contentColorFor(containerColor: Color): Color =
        androidx.compose.material3.contentColorFor(containerColor)
            .takeOrElse { MaterialTheme.colorScheme.onSurface }

    /**
     * A neutral hairline for the default card, or a low-opacity in-tint hairline
     * derived from the card's own on-color for a semantic one.
     */
    @Composable
    @ReadOnlyComposable
    fun border(containerColor: Color): BorderStroke {
        val surfaces = MaterialTheme.saldoSurfaces
        val color = if (containerColor == surfaces.card) {
            surfaces.cardBorder
        } else {
            androidx.compose.material3.contentColorFor(containerColor)
                .takeOrElse { surfaces.cardBorder }
                .copy(alpha = TINT_BORDER_ALPHA)
        }
        return BorderStroke(BorderWidth, color)
    }
}
