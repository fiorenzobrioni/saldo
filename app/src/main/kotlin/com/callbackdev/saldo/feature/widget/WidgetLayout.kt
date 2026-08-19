package com.callbackdev.saldo.feature.widget

import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.domain.model.TransactionType
import kotlin.math.ceil

/**
 * The geometry of the quick-add widgets: which layout a given size gets, and
 * the set of sizes the launcher is handed.
 *
 * Kept apart from the renderer because it is the one part that is silent when
 * wrong. `RemoteViews` has no measuring pass to fall back on, so a size is
 * either designed or broken (ADR 32), and a layout that does not fit its
 * declared size is clipped by the host without a word. `WidgetLayoutTest` pins
 * the arithmetic and the breakpoints against each other.
 */

/**
 * A size the launcher may draw the widget at, in dp.
 *
 * Deliberately not `android.util.SizeF`, which is what the sizes map is keyed by
 * in the end: that class is a framework stub in a JVM unit test, and the whole
 * point of keeping the geometry here is that it can be asserted without a device.
 * [WidgetRenderer] converts at the boundary.
 */
internal data class WidgetSize(val width: Float, val height: Float) {
    override fun toString(): String = "${width.toInt()}x${height.toInt()}dp"
}

/**
 * [WidgetStyle.ACTIONS] is not a smaller grid, it is a different widget: at one
 * launcher row high there is no room for a category, so the two movement types
 * become the content and the category is picked in the sheet that opens.
 */
internal enum class WidgetStyle { GRID, ACTIONS }

/**
 * How many tiles fit and whether there is room for a header. Deliberately a
 * hard split per size rather than a fluid layout: each size is designed rather
 * than squeezed.
 */
internal data class WidgetLayout(
    val style: WidgetStyle,
    val columns: Int = 0,
    val rows: Int = 0,
    val showHeader: Boolean = false,
    val showLabels: Boolean = false,
    val tileSize: Int = 0,
    /** Tile plus its label: what one row of the grid costs in height. */
    val rowHeight: Int = 0,
    /**
     * The inset between the widget edge and its content. The grid is short of
     * vertical room and spends less of it there; the single row has none of that
     * pressure, so its inset is even on all four sides - an uneven frame around
     * two big buttons is the first thing the eye picks up.
     *
     * Mirrored as literal dp in the layout XML, which is where a `RemoteViews`
     * actually pays it; here it is what the height budget below is computed
     * against, so the two must agree or the last row does not fit what it was
     * promised.
     */
    val paddingHorizontal: Int = GridPaddingHorizontal,
    val paddingVertical: Int = GridPaddingVertical,
    /**
     * The side of the square app-shortcut button in the single-row layout,
     * derived from the declared height so width can match height. The real
     * widget can run a little taller than the size it was rendered for, so
     * "square" is a close approximation rather than a guarantee - as close as
     * `RemoteViews` allows.
     */
    val shortcutSide: Int = 0,
) {
    /** One slot is always the "more" tile, the way out to the full editor. */
    val categorySlots: Int get() = columns * rows - 1
}

/**
 * [availableCategories] caps the rows from the other side: a widget dragged
 * taller than the categories it has to show would otherwise grow empty rows.
 *
 * [fontScale] keeps the height budget honest for large system fonts: a label
 * line that costs 17dp at scale 1 costs 26dp at 1.5, and a budget that ignored
 * that clipped every label on the grid.
 */
internal fun layoutFor(size: WidgetSize, availableCategories: Int, fontScale: Float = 1f): WidgetLayout {
    // Height first: a widget one row high can be any number of columns wide,
    // and none of those widths can hold a grid.
    if (size.height < GridMinHeight) {
        return WidgetLayout(
            style = WidgetStyle.ACTIONS,
            paddingHorizontal = ActionsPadding,
            paddingVertical = ActionsPadding,
            // What is left of the declared height once the inset is paid: the
            // button height, which is also the width a square wants. Floored
            // so the smallest size keeps a usable tap target.
            shortcutSide = (size.height.toInt() - 2 * ActionsPadding)
                .coerceAtLeast(MinShortcutSide),
        )
    }
    val wide = size.width >= WideMinWidth
    val columns = if (wide) WideColumns else NarrowColumns
    // A narrow widget has no room for a type selector or for labels, so it takes
    // its type from its own configuration and shows bigger, bare icons.
    val tileSize = if (wide) WideTileSize else NarrowTileSize
    val labelLine = if (wide) ceil(LabelLineDp * fontScale).toInt() else 0
    val rowHeight = tileSize + if (wide) LabelGapDp + labelLine else 0

    val header = if (wide) PillHeightDp + HeaderGapDp else 0
    val content = size.height.toInt() - 2 * GridPaddingVertical - header
    // Capped as well as computed: every tile carries a bitmap to the launcher,
    // and an unbounded grid would keep growing that payload towards the size
    // ceiling of the transaction that delivers it.
    val fits = ((content + RowGapDp) / (rowHeight + RowGapDp)).coerceIn(1, MaxGridRows)
    // One slot always belongs to the "open Saldo" tile.
    val needed = ceilDiv(availableCategories + 1, columns)

    return WidgetLayout(
        style = WidgetStyle.GRID,
        columns = columns,
        rows = minOf(fits, needed).coerceAtLeast(1),
        showHeader = wide,
        showLabels = wide,
        tileSize = tileSize,
        rowHeight = rowHeight,
    )
}

/**
 * The single-row sizes: the whole breakpoint set of the bar widget, and the low
 * end of the grid's (which still degrades to the two-button layout when
 * squashed, for the widgets placed before the bar existed).
 *
 * Four heights rather than one, not for the layout (any height under
 * [GridMinHeight] is the same two buttons) but for the app-shortcut square:
 * `RemoteViews` has no measure pass, so the declared height is the only
 * estimate of the button height there is, and one breakpoint at 40dp made the
 * shortcut a fixed-width rectangle on every launcher whose row is taller.
 */
internal val ActionSizes: List<WidgetSize> = listOf(
    WidgetSize(110f, 40f),
    WidgetSize(110f, 64f),
    WidgetSize(110f, 88f),
    WidgetSize(110f, 112f),
)

/**
 * One breakpoint per grid row, on both widths. The launcher only picks a size
 * whose layout it knows fits, so a missing rung means a widget with room for
 * four rows silently settles for three.
 *
 * The heights solve the row arithmetic of [layoutFor] exactly - each is the
 * smallest height at which its row count fits - and `WidgetLayoutTest` asserts
 * that, because a breakpoint that drifted from the arithmetic would either clip
 * the last row or waste it.
 */
internal val GridSizes: List<WidgetSize> =
    (1..MaxGridRows).map { rows -> WidgetSize(110f, narrowGridHeight(rows)) } +
        (1..MaxGridRows).map { rows -> WidgetSize(250f, wideGridHeight(rows)) }

/** Every size the grid provider hands the launcher; the bar provider ships [ActionSizes] alone. */
internal val GridWidgetSizes: List<WidgetSize> = ActionSizes + GridSizes

/**
 * A size-map key is a promise that the layout FITS in that many dp - the host
 * clips silently otherwise. Two insets, the rows, and the gaps between them;
 * never below [GridMinHeight], which is where the grid starts at all.
 */
internal fun narrowGridHeight(rows: Int): Float = maxOf(
    GridMinHeight,
    (2 * GridPaddingVertical + rows * NarrowTileSize + (rows - 1) * RowGapDp).toFloat(),
)

/** As [narrowGridHeight], plus the type selector and the label under every tile. */
internal fun wideGridHeight(rows: Int): Float {
    val rowHeight = WideTileSize + LabelGapDp + LabelLineDp
    return maxOf(
        GridMinHeight,
        (
            2 * GridPaddingVertical + PillHeightDp + HeaderGapDp +
                rows * rowHeight + (rows - 1) * RowGapDp
            ).toFloat(),
    )
}

private fun ceilDiv(value: Int, by: Int): Int = (value + by - 1) / by

/** The launcher-shortcut action that opens the editor already set to [type]. */
internal fun quickActionFor(type: TransactionType): String = when (type) {
    TransactionType.INCOME -> MainActivity.ACTION_ADD_INCOME
    else -> MainActivity.ACTION_ADD_EXPENSE
}

/** Below this height there is no room for a category, so the widget becomes two buttons. */
internal const val GridMinHeight = 120f

/** Below this width the grid drops to two icon-only columns. */
internal const val WideMinWidth = 250f

/** How many categories the settings screen lets a user pin by hand. */
internal const val MaxPinnedCategories = 12

internal const val MaxGridRows = 5
internal const val WideColumns = 4
internal const val NarrowColumns = 2
internal const val WideTileSize = 44
internal const val NarrowTileSize = 52
internal const val GridPaddingHorizontal = 12
internal const val GridPaddingVertical = 8
internal const val HeaderGapDp = 6
internal const val RowGapDp = 6
internal const val LabelGapDp = 3

/** A 12sp label with its line spacing at font scale 1, the base of the height budget. */
internal const val LabelLineDp = 17
internal const val PillHeightDp = 34

/** The single-row layout's even inset (see [WidgetLayout.paddingHorizontal]). */
internal const val ActionsPadding = 14

/** The smallest square the shortcut is allowed to shrink to: still a target. */
internal const val MinShortcutSide = 40

/** The mark against its square: most of it, with the margin a button implies. */
internal const val AppShortcutMarkRatio = 0.8f

/** Matches `CategoryCell`: the app's unselected category wash. */
internal const val WashAlpha = 0.16f

/**
 * The app mark, taken from the adaptive icon's *foreground* layer rather than
 * from `@mipmap/ic_launcher`.
 *
 * Two reasons, one of them a crash. `ic_launcher` is an `<adaptive-icon>`, and
 * `painterResource` loads vectors and rasters only, so the settings preview blew
 * up the moment the shortcut was switched on. And the adaptive icon carries its
 * own white background square, which is the opposite of the transparent mark
 * this button is meant to be.
 */
internal val AppShortcutIcon = R.drawable.ic_launcher_foreground
