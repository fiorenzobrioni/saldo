package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale for cards and grouped rows, so the whole app breathes the
 * same way. Kept intentionally tight for a compact, information-dense layout:
 * more content fits on a screen without feeling cramped.
 */
object SaldoDimens {

    /** Inner padding of hero cards that carry a large headline figure. */
    val cardPaddingLarge = 16.dp

    /** Inner padding of standard cards. */
    val cardPadding = 14.dp

    /** Horizontal inner padding of a row inside a grouped card. */
    val rowPaddingHorizontal = 16.dp

    /** Vertical inner padding of a row inside a grouped card. */
    val rowPaddingVertical = 10.dp

    /** Vertical gap between cards stacked in a column. */
    val cardSpacing = 8.dp
}
