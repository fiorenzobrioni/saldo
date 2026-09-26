package com.callbackdev.saldo.feature.widget

/**
 * What a card is painted on: a plain card in the app's light or dark surface,
 * whichever of the two the phone is in, or one of the six colours of
 * `WidgetCardColor`. Chiaro's list without its sky, as in Passo; [COLOR] is one
 * value, and which colour is a second question.
 */
enum class WidgetBackground { LIGHT, DARK, SYSTEM, COLOR }

/**
 * Which set of inks a card writes with, named after the ground it is written
 * on. Chiaro's rule (its `WidgetInk`, DESIGN §2.6), copied as Passo copies it
 * and kept pure so `WidgetInkTest` pins it: [OVER_COLOR] is the white set every
 * card colour is picked dark enough to carry; the other two are the schemes'
 * own.
 */
enum class WidgetInk {
    OVER_COLOR,
    ON_LIGHT,
    ON_DARK,
    ;

    /** Whether the ground is dark, so the money colours pick their dark-theme set. */
    val darkGround: Boolean get() = this != ON_LIGHT
}

/** Below this solidity the card stops being the ink's ground. */
const val InkTrustFloorPct: Int = 50

/**
 * The whole ink rule. From [InkTrustFloorPct] up the card is its own ground.
 * Below it the card is see-through: a light or dark card is the user naming an
 * ink, so it keeps deciding; the phone's card and a colour hand the question to
 * the wallpaper, whose hint is read as the affirmative signal it is
 * ([wallpaperCarriesDarkInk]: dark ink only where the system says the ground is
 * bright). [night] is the branch being resolved: the launcher holds a day and a
 * night value of every colour and picks one itself.
 */
fun widgetInk(
    background: WidgetBackground,
    opacityPct: Int,
    night: Boolean,
    wallpaperCarriesDarkInk: Boolean,
): WidgetInk {
    if (opacityPct < InkTrustFloorPct) {
        return when (background) {
            WidgetBackground.LIGHT -> WidgetInk.ON_LIGHT
            WidgetBackground.DARK -> WidgetInk.ON_DARK
            WidgetBackground.SYSTEM, WidgetBackground.COLOR ->
                if (wallpaperCarriesDarkInk) WidgetInk.ON_LIGHT else WidgetInk.ON_DARK
        }
    }
    return when (background) {
        WidgetBackground.COLOR -> WidgetInk.OVER_COLOR
        WidgetBackground.LIGHT -> WidgetInk.ON_LIGHT
        WidgetBackground.DARK -> WidgetInk.ON_DARK
        WidgetBackground.SYSTEM -> if (night) WidgetInk.ON_DARK else WidgetInk.ON_LIGHT
    }
}

/**
 * True when [widgetInk] has to ask the wallpaper at all: a see-through card
 * whose ink the user did not name. The only case in which a render reads the
 * wallpaper's colours, so a solid widget never makes that binder call.
 */
fun inkAsksWallpaper(background: WidgetBackground, opacityPct: Int): Boolean =
    opacityPct < InkTrustFloorPct &&
        (background == WidgetBackground.SYSTEM || background == WidgetBackground.COLOR)
