package com.callbackdev.saldo.feature.widget

import kotlin.math.roundToInt

/*
 * The geometry of the two month widgets (ADR 54): which form a size gets and
 * the breakpoints each provider hands the launcher. Pure, like WidgetLayout.kt,
 * because a `RemoteViews` has no measuring pass: a form either fits its size or
 * is clipped by the host without a word, and `WidgetFitTest` draws every one.
 */

/** «The month»'s forms. */
internal enum class RingForm {
    /** One launcher row: the ring at the row's height, the words beside it when there is room. */
    ROW,

    /** Narrow and tall: the ring large, the words under it. */
    SQUARE,

    /** Wide and tall: the ring at the card's height, three lines beside it. */
    PANEL,
}

internal data class RingLayout(val form: RingForm, val ringSide: Int, val showText: Boolean)

/** The card's even inset, the family's 14 dp. */
internal const val MonthInset = 14

/** One row's tighter inset: the ring wants every dp of a row's height. */
internal const val MonthRowInset = 10

/** From this height a card has room for more than one row's worth. */
internal const val MonthTallMin = 150f

/** From this width a card is wide: words beside the ring, the long sentence. */
internal const val MonthWideMin = 250f

/** From this width a one-row ring has room for its words. */
internal const val RingTextMinWidth = 200f

private const val RingMinSide = 28
private const val RingRowMaxSide = 64
private const val RingMinTallSide = 48

/** What the square form keeps under the ring for two label lines and the gap, at text size 1. */
internal const val SquareCaptionDp = 42

/** The ring's share of a wide panel's width. */
private const val PanelRingShare = 0.4f

/**
 * The ring's form and side at [size]. [captionDp] is what the square form keeps
 * under the ring for its lines, measured by the renderer at the text size it
 * draws in; the default is two label lines at text size 1 and their gap.
 */
internal fun ringLayout(size: WidgetSize, captionDp: Int = SquareCaptionDp): RingLayout {
    val width = size.width.roundToInt()
    val height = size.height.roundToInt()
    return when {
        size.height < MonthTallMin -> RingLayout(
            form = RingForm.ROW,
            ringSide = (height - 2 * MonthRowInset).coerceIn(RingMinSide, RingRowMaxSide),
            showText = size.width >= RingTextMinWidth,
        )

        size.width < MonthWideMin -> RingLayout(
            form = RingForm.SQUARE,
            ringSide = minOf(width - 2 * MonthInset, height - 2 * MonthInset - captionDp)
                .coerceAtLeast(RingMinTallSide),
            showText = true,
        )

        else -> RingLayout(
            form = RingForm.PANEL,
            ringSide = minOf(height - 2 * MonthInset, ((width - 2 * MonthInset) * PanelRingShare).toInt())
                .coerceAtLeast(RingMinTallSide),
            showText = true,
        )
    }
}

/**
 * «The month»'s breakpoints: three row heights at three widths (the ring alone,
 * then with its words, then with room for them), and the tall forms at the
 * reference grants. The ring's
 * side is set from the breakpoint (`setViewLayoutWidth`), so a row of any height
 * gets a ring that fills it.
 */
internal val RingSizes: List<WidgetSize> =
    listOf(40f, 64f, 85f).flatMap { h ->
        listOf(WidgetSize(110f, h), WidgetSize(RingTextMinWidth, h), WidgetSize(MonthWideMin, h))
    } +
        listOf(
            WidgetSize(110f, MonthTallMin),
            WidgetSize(159f, 189f),
            WidgetSize(159f, 293f),
            WidgetSize(MonthWideMin, MonthTallMin),
            WidgetSize(MonthWideMin, 189f),
            WidgetSize(340f, 293f),
        )

/** «In words»'s forms. */
internal enum class WordsForm {
    /** Narrow and short: the short sentence, which is the figure that matters. */
    SHORT,

    /** Wide and short: the whole sentence, in as many lines as the row holds. */
    LINE,

    /** Tall: the month's name, the sentence, then the facts that fit. */
    PANEL,
}

/**
 * The form for [size]. How many lines the sentence takes, whether it is the
 * long or the short one, and how many facts follow is measured by the renderer
 * on the real text at the real text size ([MonthTextMeasure]): a sentence's
 * length depends on the language and the figures, and a guess either clips it
 * or leaves a card half empty.
 */
internal fun wordsForm(size: WidgetSize): WordsForm = when {
    size.height >= MonthTallMin -> WordsForm.PANEL
    size.width >= MonthWideMin -> WordsForm.LINE
    else -> WordsForm.SHORT
}

/** One row's height where a second line of the sentence may fit. */
private const val TwoLineRowMin = 64f

/** A portrait launcher's one row. */
private const val FullRow = 85f

/** «In words»'s breakpoints: three row heights at two widths, then the tall grants. */
internal val WordsSizes: List<WidgetSize> = listOf(
    WidgetSize(110f, 40f),
    WidgetSize(110f, TwoLineRowMin),
    WidgetSize(110f, FullRow),
    WidgetSize(MonthWideMin, 40f),
    WidgetSize(MonthWideMin, TwoLineRowMin),
    WidgetSize(MonthWideMin, FullRow),
    WidgetSize(110f, MonthTallMin),
    WidgetSize(159f, 189f),
    WidgetSize(159f, 293f),
    WidgetSize(MonthWideMin, MonthTallMin),
    WidgetSize(MonthWideMin, 189f),
    WidgetSize(MonthWideMin, 293f),
    WidgetSize(340f, 293f),
)
