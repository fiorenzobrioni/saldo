package com.callbackdev.saldo.feature.widget

/**
 * **The widgets' type scale** (13 Oct 2026, widget review, ADR 52): the sizes a
 * home-screen card prints in, by role, so a label or a message is the same size
 * on every card. Six steps, each a role:
 *
 * - [CAPTION]: read last (the badge of the account a widget is pinned to).
 * - [LABEL]: a cell's label, a header, a note (the tiles' names, the expense
 *   and income selector, the bar's two buttons, which say the same two words).
 * - [BODY]: the second thing read, and the message of a card with nothing to
 *   show yet ("Open Saldo to get started").
 * - [TITLE]: the text of a card whose hero is a drawing.
 * - [DISPLAY]: a hero wider than a figure.
 * - [HERO]: the figure of a one-row card.
 *
 * A `RemoteViews` layout cannot read a Kotlin constant, so the steps the cards
 * use are mirrored as `@dimen/widget_type_*` (res/values/widget_type.xml), and
 * every `textSize` of a widget layout names one of them. `WidgetTypeTest` holds
 * both: no literal size in a layout, no dimen off its step.
 */
internal object WidgetType {
    const val CAPTION = 11f
    const val LABEL = 13f
    const val BODY = 16f
    const val TITLE = 22f
    const val DISPLAY = 30f
    const val HERO = 34f

    /** Every step, for the test that holds the cards to them. */
    val steps: Set<Float> = setOf(CAPTION, LABEL, BODY, TITLE, DISPLAY, HERO)
}
