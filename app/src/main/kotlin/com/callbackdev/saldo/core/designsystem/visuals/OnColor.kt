package com.callbackdev.saldo.core.designsystem.visuals

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Glyph color readable on a solid avatar or swatch [background]: white on the
 * darker palette colors, a near-black on the light ones (lime, light green,
 * amber, light blue), where white lands around 1.6-2.5:1 contrast. The
 * threshold is tuned to the app palettes so the brand teal and the darker
 * swatches keep the conventional white glyph.
 */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > LIGHT_LUMINANCE_THRESHOLD) OnLightColor else Color.White

/** Relative luminance above which a white glyph stops being readable. */
private const val LIGHT_LUMINANCE_THRESHOLD = 0.35f

/** 87% black, the conventional dark ink on light Material surfaces. */
private val OnLightColor = Color(0xDE000000)
