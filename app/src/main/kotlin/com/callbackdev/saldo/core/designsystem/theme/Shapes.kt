package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Short-radius, almost-squared shape scale for a premium, architectural look.
 * Corners are intentionally tighter than the Material 3 defaults (which round
 * cards up to 28dp): frames read as crisp panels, not pills.
 */
val SaldoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * Rounded-square shape for avatars / icon tiles. Percentage-based so it stays a
 * consistent squircle across the sizes used in the app (28-44dp), in line with
 * the tighter corner language above.
 */
val AvatarShape = RoundedCornerShape(percent = 30)
