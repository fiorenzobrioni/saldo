package com.callbackdev.saldo.feature.widget

import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

/**
 * One colour as the two branches the launcher chooses between.
 *
 * `RemoteViews.setColorInt` takes a day value and a night value and lets the
 * host resolve which one applies, on its own, whenever the system theme flips.
 * That is the whole reason the palette is a pair rather than a resolved colour:
 * a widget is drawn in the launcher's process, and a single resolved value
 * would freeze it in whichever theme the app happened to render it under until
 * the next data change came along.
 *
 * When the user forces an appearance on a widget, both branches carry the same
 * value: the launcher can flip all it likes, the choice wins.
 */
internal data class WidgetColor(@ColorInt val light: Int, @ColorInt val dark: Int) {
    companion object {
        /** The same colour on both branches: a forced appearance, or a category's own hue. */
        fun of(@ColorInt value: Int) = WidgetColor(value, value)
    }
}

/**
 * Every colour a render needs, as ARGB ints - `RemoteViews` knows nothing of
 * Compose `Color`, and nothing of alpha compositing either.
 *
 * The washes are **pre-blended over the widget background** rather than carried
 * as translucent colours, and that is not a shortcut. The only tint a
 * `RemoteViews` can apply to a shape is `setColorFilter`, which an `ImageView`
 * runs in `SRC_ATOP`: handed a colour at 16% alpha it composites the accent over
 * the *shape* it is tinting, not over the widget behind it, so the app's tonal
 * wash came out a chalky pastel. Compositing over the known container colour here
 * yields one opaque value, which `SRC_ATOP` then reproduces exactly.
 */
internal data class WidgetPalette(
    val background: WidgetColor,
    val onSurface: WidgetColor,
    val onSurfaceVariant: WidgetColor,
    /** The selected half of the type selector. */
    val pillFill: WidgetColor,
    val pillInk: WidgetColor,
    /** The unselected half: no fill of its own, only quieter ink. */
    val pillIdleInk: WidgetColor,
    val expenseInk: WidgetColor,
    val expenseWash: WidgetColor,
    val incomeInk: WidgetColor,
    val incomeWash: WidgetColor,
    /** The quiet tonal fill of the app-shortcut button: present, not competing. */
    val neutralWash: WidgetColor,
    /** The "more" tile, drawn in the brand colour rather than a category's. */
    val moreInk: WidgetColor,
    val moreWash: WidgetColor,
) {

    /**
     * A category's colour is its own in both themes - the user picked that hue,
     * not a token - so the ink is the same on both branches and only the wash
     * differs, because the surface it is composited over does.
     */
    fun categoryInk(color: Color): WidgetColor = WidgetColor.of(color.toArgb())

    fun categoryWash(color: Color): WidgetColor = WidgetColor(
        light = washOver(background.light, color.toArgb()),
        dark = washOver(background.dark, color.toArgb()),
    )
}

/**
 * Builds the render palette from the light/dark scheme pair the widget theme
 * resolved. Pure: no context, no binder calls, so it is cheap enough to keep
 * beside the data snapshot rather than recompute per size.
 */
internal fun widgetPalette(
    light: ColorScheme,
    dark: ColorScheme,
    lightIncome: Color,
    darkIncome: Color,
): WidgetPalette {
    val backgroundLight = widgetBackgroundColorOf(light).toArgb()
    val backgroundDark = widgetBackgroundColorOf(dark).toArgb()
    fun wash(day: Color, night: Color) = WidgetColor(
        light = washOver(backgroundLight, day.toArgb()),
        dark = washOver(backgroundDark, night.toArgb()),
    )
    fun ink(day: Color, night: Color) = WidgetColor(day.toArgb(), night.toArgb())
    return WidgetPalette(
        background = WidgetColor(backgroundLight, backgroundDark),
        onSurface = ink(light.onSurface, dark.onSurface),
        onSurfaceVariant = ink(light.onSurfaceVariant, dark.onSurfaceVariant),
        pillFill = ink(light.primary, dark.primary),
        pillInk = ink(light.onPrimary, dark.onPrimary),
        pillIdleInk = ink(light.onSurfaceVariant, dark.onSurfaceVariant),
        // A deliberate, narrow exception to MoneyColors, which keeps expense
        // neutral on purpose: colouring every expense in a ledger would shout,
        // and there the minus sign and the icon carry the distinction. Two
        // action buttons alone on a widget are not a ledger - there is no other
        // context to read them by - so the colour does the fast work and the
        // icons still do the accessible work.
        expenseInk = ink(light.error, dark.error),
        expenseWash = wash(light.error, dark.error),
        incomeInk = ink(lightIncome, darkIncome),
        incomeWash = wash(lightIncome, darkIncome),
        neutralWash = wash(light.onSurfaceVariant, dark.onSurfaceVariant),
        moreInk = ink(light.primary, dark.primary),
        moreWash = wash(light.primary, dark.primary),
    )
}

/**
 * The app's unselected-category wash, flattened against the opaque surface it
 * sits on. Opaque by construction, which is what [WidgetPalette] needs.
 *
 * Hand-rolled rather than `ColorUtils.compositeColors`, for the same result: that
 * one reaches `android.graphics.Color` for every channel, which is a framework
 * stub in a JVM unit test - so the one piece of colour arithmetic in the widget
 * that can be asserted without a device stays that way. It is also called once
 * per tile per breakpoint, where plain integer maths is the cheaper answer.
 */
@ColorInt
internal fun washOver(@ColorInt background: Int, @ColorInt accent: Int): Int {
    fun channel(shift: Int): Int {
        val foreground = (accent ushr shift) and MaxChannel
        val behind = (background ushr shift) and MaxChannel
        return (foreground * WashAlpha + behind * (1f - WashAlpha))
            .roundToInt()
            .coerceIn(0, MaxChannel)
    }
    return (MaxChannel shl AlphaShift) or
        (channel(RedShift) shl RedShift) or
        (channel(GreenShift) shl GreenShift) or
        channel(BlueShift)
}

private const val MaxChannel = 0xFF
private const val AlphaShift = 24
private const val RedShift = 16
private const val GreenShift = 8
private const val BlueShift = 0
