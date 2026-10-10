package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The card colours a home-screen widget can wear: **six, hex for hex** the
 * table ADR 52 fixes.
 *
 * Each was measured against the inks it carries: every one of the six is a dark
 * ground under white ink, white never under 7.8:1, the 75% quiet ink never under
 * 5.2:1.
 *
 * They are not roles and they do not follow the theme: the user is choosing what
 * colour a card on their wallpaper is, which is a different act from the app's
 * own colour. So this is a table of hexes, kept beside the other palettes.
 */
enum class WidgetCardColor { BLUE, AZURE, GREEN, TEAL, PLUM, CLAY }

/** The ground each colour paints, at full solidity; the widget's opacity thins it. */
@Suppress("MagicNumber") // A palette is literal color values by nature.
fun widgetCardContainer(color: WidgetCardColor): Color = when (color) {
    WidgetCardColor.BLUE -> Color(0xFF0F3B6B)
    WidgetCardColor.AZURE -> Color(0xFF0F5580)
    WidgetCardColor.GREEN -> Color(0xFF17572E)
    WidgetCardColor.TEAL -> Color(0xFF0F5B5B)
    WidgetCardColor.PLUM -> Color(0xFF4A2C63)
    WidgetCardColor.CLAY -> Color(0xFF7A3320)
}
