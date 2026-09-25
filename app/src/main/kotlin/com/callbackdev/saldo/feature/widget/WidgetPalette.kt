package com.callbackdev.saldo.feature.widget

import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.callbackdev.saldo.core.designsystem.theme.MoneyColors
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor
import com.callbackdev.saldo.core.designsystem.theme.widgetCardContainer
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
 * Only the phone's card ([WidgetBackground.SYSTEM]) has two real branches; every
 * other ground is the same on both, so the launcher can flip all it likes and
 * the user's choice wins.
 */
internal data class WidgetColor(@ColorInt val light: Int, @ColorInt val dark: Int) {
    companion object {
        /** The same colour on both branches. */
        fun of(@ColorInt value: Int) = WidgetColor(value, value)
    }
}

/**
 * One theme as a widget needs it: both schemes and both sets of money colours at
 * once. A widget cannot ask the theme which mode it is in (the launcher draws
 * it, on a ground the app does not control), so every colour is resolved here
 * against the ground it really has - Passo's `WidgetDress`, for the same reason.
 */
data class WidgetDress(
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme,
    val lightMoney: MoneyColors,
    val darkMoney: MoneyColors,
) {
    fun scheme(dark: Boolean): ColorScheme = if (dark) darkScheme else lightScheme

    fun money(dark: Boolean): MoneyColors = if (dark) darkMoney else lightMoney

    /** The card's ground at full strength, on the branch [night]: what the opacity thins. */
    fun ground(background: WidgetBackground, cardColor: WidgetCardColor, night: Boolean): Color =
        when (background) {
            WidgetBackground.LIGHT -> lightScheme.surface
            WidgetBackground.DARK -> darkScheme.surface
            WidgetBackground.SYSTEM -> scheme(night).surface
            WidgetBackground.COLOR -> widgetCardContainer(cardColor)
        }
}

/**
 * Every colour a render needs, as ARGB ints - `RemoteViews` knows nothing of
 * Compose `Color`.
 *
 * The card and the washes carry their colour opaque and their strength apart
 * ([backgroundAlpha], [WashAlpha]), because the only tint a `RemoteViews` can
 * put on a shape is `setColorFilter`, which an `ImageView` runs in `SRC_ATOP`:
 * handed a translucent colour it composites over the white *shape* it tints,
 * not over what is behind the widget, and comes out a chalky pastel. An opaque
 * tint with the `ImageView`'s own image alpha composites over what is really
 * behind it: the card over the wallpaper, a wash over the card. That is also
 * what makes a see-through card possible at all.
 */
internal data class WidgetPalette(
    val background: WidgetColor,
    /** The card's solidity, 0 to 255: the user's opacity. */
    val backgroundAlpha: Int,
    val onSurface: WidgetColor,
    val onSurfaceVariant: WidgetColor,
    /** The selected half of the type selector. */
    val pillFill: WidgetColor,
    val pillInk: WidgetColor,
    /** The unselected half: no fill of its own, only quieter ink. */
    val pillIdleInk: WidgetColor,
    /** The two buttons of the bar, ink and (at [WashAlpha]) wash. */
    val expenseInk: WidgetColor,
    val incomeInk: WidgetColor,
    /** The quiet tonal fill of the app-shortcut button: present, not competing. */
    val neutralWash: WidgetColor,
    /** The "more" tile, drawn in the card's accent rather than a category's. */
    val moreInk: WidgetColor,
    /** How far a category's own colour is lifted towards white on each branch. */
    private val categoryLift: Pair<Float, Float>,
) {

    /**
     * A category's colour is the one its user picked, on every ground. On a
     * card colour it is lifted towards white, keeping its hue: the six colours
     * are dark on purpose, and a category's mid-tone would sink into them (the
     * seed's indigo sits at 2.4:1 on the blue card, lifted at 4.9:1).
     */
    fun categoryInk(color: Color): WidgetColor = WidgetColor(
        light = mix(color.toArgb(), White, categoryLift.first),
        dark = mix(color.toArgb(), White, categoryLift.second),
    )
}

/**
 * Builds the render palette from the dress and the look: [widgetInk]'s answer,
 * dressed in the colours it names, once per branch. Pure: the wallpaper's hint
 * is read by the caller, and only when [inkAsksWallpaper] says it matters.
 */
internal fun widgetPalette(
    dress: WidgetDress,
    background: WidgetBackground,
    cardColor: WidgetCardColor,
    opacityPct: Int,
    wallpaperCarriesDarkInk: Boolean,
): WidgetPalette {
    val day = side(dress, background, cardColor, opacityPct, night = false, wallpaperCarriesDarkInk)
    val night = side(dress, background, cardColor, opacityPct, night = true, wallpaperCarriesDarkInk)
    fun pair(pick: (Side) -> Int) = WidgetColor(pick(day), pick(night))
    return WidgetPalette(
        background = pair(Side::ground),
        backgroundAlpha = (opacityPct.coerceIn(0, FullOpacity) * MaxChannel / FullOpacity.toFloat()).roundToInt(),
        onSurface = pair(Side::ink),
        onSurfaceVariant = pair(Side::quietInk),
        pillFill = pair(Side::pillFill),
        pillInk = pair(Side::pillInk),
        pillIdleInk = pair(Side::quietInk),
        expenseInk = pair(Side::expense),
        incomeInk = pair(Side::income),
        neutralWash = pair(Side::neutral),
        moreInk = pair(Side::accent),
        categoryLift = day.categoryLift to night.categoryLift,
    )
}

/** One branch of the palette, resolved against the ground it really has. */
private data class Side(
    val ground: Int,
    val ink: Int,
    val quietInk: Int,
    val pillFill: Int,
    val pillInk: Int,
    val expense: Int,
    val income: Int,
    val neutral: Int,
    val accent: Int,
    val categoryLift: Float,
)

@Suppress("LongParameterList")
private fun side(
    dress: WidgetDress,
    background: WidgetBackground,
    cardColor: WidgetCardColor,
    opacityPct: Int,
    night: Boolean,
    wallpaperCarriesDarkInk: Boolean,
): Side {
    val ground = dress.ground(background, cardColor, night).toArgb()
    return when (val ink = widgetInk(background, opacityPct, night, wallpaperCarriesDarkInk)) {
        // White on every card colour, as in Chiaro and Passo; the money colours
        // are the dark theme's, the set made to read on a dark ground.
        WidgetInk.OVER_COLOR -> Side(
            ground = ground,
            ink = White,
            quietInk = QuietWhite,
            pillFill = White,
            pillInk = ground,
            expense = dress.darkScheme.error.toArgb(),
            income = dress.darkMoney.income.toArgb(),
            neutral = White,
            accent = White,
            categoryLift = CategoryLiftOverColor,
        )

        WidgetInk.ON_LIGHT, WidgetInk.ON_DARK -> {
            val dark = ink.darkGround
            val scheme = dress.scheme(dark)
            Side(
                ground = ground,
                ink = scheme.onSurface.toArgb(),
                quietInk = scheme.onSurfaceVariant.toArgb(),
                pillFill = scheme.primary.toArgb(),
                pillInk = scheme.onPrimary.toArgb(),
                // A deliberate, narrow exception to MoneyColors, which keeps
                // expense neutral on purpose: colouring every expense in a
                // ledger would shout. Two action buttons alone on a widget are
                // not a ledger, so the colour does the fast work and the icons
                // still do the accessible work.
                expense = scheme.error.toArgb(),
                income = dress.money(dark).income.toArgb(),
                neutral = scheme.onSurfaceVariant.toArgb(),
                accent = scheme.primary.toArgb(),
                categoryLift = 0f,
            )
        }
    }
}

/**
 * [base] moved [fraction] of the way towards [over], opaque by construction.
 *
 * Hand-rolled rather than `ColorUtils.blendARGB`, for the same result: that one
 * reaches `android.graphics.Color` for every channel, which is a framework stub
 * in a JVM unit test, and this is the one piece of colour arithmetic in the
 * widget worth asserting without a device.
 */
@ColorInt
internal fun mix(@ColorInt base: Int, @ColorInt over: Int, fraction: Float): Int {
    fun channel(shift: Int): Int {
        val from = (base ushr shift) and MaxChannel
        val to = (over ushr shift) and MaxChannel
        return (from + (to - from) * fraction).roundToInt().coerceIn(0, MaxChannel)
    }
    return (MaxChannel shl AlphaShift) or
        (channel(RedShift) shl RedShift) or
        (channel(GreenShift) shl GreenShift) or
        channel(BlueShift)
}

/** A wash's strength as the `ImageView` image alpha the renderer sets. */
internal val WashImageAlpha: Int = (WashAlpha * 255).roundToInt()

private const val White: Int = -0x1 // 0xFFFFFFFF

/** Chiaro's quiet ink over a card colour: white at 75%. */
private const val QuietWhite: Int = -0x40000001 // 0xBFFFFFFF

/** See [WidgetPalette.categoryInk]. */
private const val CategoryLiftOverColor = 0.4f

private const val MaxChannel = 0xFF
private const val AlphaShift = 24
private const val RedShift = 16
private const val GreenShift = 8
private const val BlueShift = 0
