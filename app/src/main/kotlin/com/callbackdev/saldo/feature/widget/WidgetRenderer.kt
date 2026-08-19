package com.callbackdev.saldo.feature.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * Builds the `RemoteViews` for one widget instance.
 *
 * [sizeMap] returns the API 31+ sizes-map form: one pre-rendered layout per
 * breakpoint, all in a single update, so the launcher picks the best fit by
 * itself on every resize and per orientation, in its own process, with no round
 * trip back to ours. That is the whole reason the provider treats
 * `onAppWidgetOptionsChanged` as a no-op.
 *
 * Everything colour travels as a day/night pair through `setColorInt`, so a
 * widget follows the system theme without the app being woken at all - the
 * launcher re-resolves the branch on its own. Every tinted surface is an
 * ImageView carrying a solid-white shape, because `setColorFilter` is the only
 * tint a `RemoteViews` can apply and it only reaches an ImageView.
 */
internal object WidgetRenderer {

    /**
     * The cells of both grid layouts, by index in reading order. The two files
     * share the ids on purpose - aapt gives one value per name - so binding is
     * one loop whether the grid is two columns wide or four; the narrow layout
     * simply declares the first ten.
     */
    private val CellIds = listOf(
        R.id.widget_cell_0, R.id.widget_cell_1, R.id.widget_cell_2, R.id.widget_cell_3,
        R.id.widget_cell_4, R.id.widget_cell_5, R.id.widget_cell_6, R.id.widget_cell_7,
        R.id.widget_cell_8, R.id.widget_cell_9, R.id.widget_cell_10, R.id.widget_cell_11,
        R.id.widget_cell_12, R.id.widget_cell_13, R.id.widget_cell_14, R.id.widget_cell_15,
        R.id.widget_cell_16, R.id.widget_cell_17, R.id.widget_cell_18, R.id.widget_cell_19,
    )

    private val TileBackgroundIds = listOf(
        R.id.widget_tile_bg_0, R.id.widget_tile_bg_1, R.id.widget_tile_bg_2, R.id.widget_tile_bg_3,
        R.id.widget_tile_bg_4, R.id.widget_tile_bg_5, R.id.widget_tile_bg_6, R.id.widget_tile_bg_7,
        R.id.widget_tile_bg_8, R.id.widget_tile_bg_9, R.id.widget_tile_bg_10, R.id.widget_tile_bg_11,
        R.id.widget_tile_bg_12, R.id.widget_tile_bg_13, R.id.widget_tile_bg_14, R.id.widget_tile_bg_15,
        R.id.widget_tile_bg_16, R.id.widget_tile_bg_17, R.id.widget_tile_bg_18, R.id.widget_tile_bg_19,
    )

    private val TileIconIds = listOf(
        R.id.widget_tile_icon_0, R.id.widget_tile_icon_1, R.id.widget_tile_icon_2,
        R.id.widget_tile_icon_3, R.id.widget_tile_icon_4, R.id.widget_tile_icon_5,
        R.id.widget_tile_icon_6, R.id.widget_tile_icon_7, R.id.widget_tile_icon_8,
        R.id.widget_tile_icon_9, R.id.widget_tile_icon_10, R.id.widget_tile_icon_11,
        R.id.widget_tile_icon_12, R.id.widget_tile_icon_13, R.id.widget_tile_icon_14,
        R.id.widget_tile_icon_15, R.id.widget_tile_icon_16, R.id.widget_tile_icon_17,
        R.id.widget_tile_icon_18, R.id.widget_tile_icon_19,
    )

    private val TileLabelIds = listOf(
        R.id.widget_tile_label_0, R.id.widget_tile_label_1, R.id.widget_tile_label_2,
        R.id.widget_tile_label_3, R.id.widget_tile_label_4, R.id.widget_tile_label_5,
        R.id.widget_tile_label_6, R.id.widget_tile_label_7, R.id.widget_tile_label_8,
        R.id.widget_tile_label_9, R.id.widget_tile_label_10, R.id.widget_tile_label_11,
        R.id.widget_tile_label_12, R.id.widget_tile_label_13, R.id.widget_tile_label_14,
        R.id.widget_tile_label_15, R.id.widget_tile_label_16, R.id.widget_tile_label_17,
        R.id.widget_tile_label_18, R.id.widget_tile_label_19,
    )

    private val RowIds = listOf(
        R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2,
        R.id.widget_row_3, R.id.widget_row_4,
    )

    /** The same pair the dashboard speed-dial uses, so the gesture reads the same everywhere. */
    private val ExpenseIcon: ImageVector = Icons.AutoMirrored.Outlined.TrendingDown
    private val IncomeIcon: ImageVector = Icons.AutoMirrored.Outlined.TrendingUp

    /** Reads as "more options" in any launcher, and is in no category icon set. */
    private val MoreIcon: ImageVector = Icons.Outlined.MoreHoriz

    fun sizeMap(
        context: Context,
        appWidgetId: Int,
        data: QuickAddWidgetData,
        palette: WidgetPalette,
        sizes: List<WidgetSize>,
    ): RemoteViews = RemoteViews(
        sizes.associate { size ->
            SizeF(size.width, size.height) to render(context, appWidgetId, data, palette, size)
        },
    )

    internal fun render(
        context: Context,
        appWidgetId: Int,
        data: QuickAddWidgetData,
        palette: WidgetPalette,
        size: WidgetSize,
    ): RemoteViews {
        val layout = layoutFor(
            size = size,
            availableCategories = data.categories.size,
            fontScale = context.resources.configuration.fontScale,
        )
        if (!data.isReady) return renderSetup(context, palette, layout)
        return when (layout.style) {
            WidgetStyle.ACTIONS -> renderActions(context, appWidgetId, data, palette, layout)
            WidgetStyle.GRID -> renderGrid(context, appWidgetId, data, palette, layout)
        }
    }

    private fun renderSetup(
        context: Context,
        palette: WidgetPalette,
        layout: WidgetLayout,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_quick_setup).apply {
        paintBackground(palette)
        // At one launcher row the mark would crowd out the words that explain
        // the tap, so only the taller sizes carry it.
        val compact = layout.style == WidgetStyle.ACTIONS
        setViewVisibility(R.id.widget_setup_mark, if (compact) View.GONE else View.VISIBLE)
        if (!compact) {
            setImageViewBitmap(
                R.id.widget_setup_mark,
                CategoryIconBitmaps.appMark(context, AppShortcutIcon, context.px(SetupMarkSize)),
            )
        }
        setColor(R.id.widget_setup_text, "setTextColor", palette.onSurfaceVariant)
        setOnClickPendingIntent(R.id.widget_root, openApp(context, SETUP_REQUEST))
    }

    private fun renderActions(
        context: Context,
        appWidgetId: Int,
        data: QuickAddWidgetData,
        palette: WidgetPalette,
        layout: WidgetLayout,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_quick_actions).apply {
        paintBackground(palette)
        val expense = data.buttons.shows(TransactionType.EXPENSE)
        val income = data.buttons.shows(TransactionType.INCOME)

        setViewVisibility(R.id.widget_action_expense, expense.asVisibility())
        setViewVisibility(R.id.widget_action_income, income.asVisibility())
        // The gap earns its width only between two visible buttons.
        setViewVisibility(R.id.widget_action_gap, (expense && income).asVisibility())

        if (expense) {
            paintAction(
                context = context,
                appWidgetId = appWidgetId,
                root = R.id.widget_action_expense,
                background = R.id.widget_action_expense_bg,
                icon = R.id.widget_action_expense_icon,
                label = R.id.widget_action_expense_label,
                text = context.getString(R.string.widget_quick_add_expense),
                glyph = ExpenseIcon,
                ink = palette.expenseInk,
                wash = palette.expenseWash,
                type = TransactionType.EXPENSE,
                accountId = data.pinnedAccountId,
            )
        }
        if (income) {
            paintAction(
                context = context,
                appWidgetId = appWidgetId,
                root = R.id.widget_action_income,
                background = R.id.widget_action_income_bg,
                icon = R.id.widget_action_income_icon,
                label = R.id.widget_action_income_label,
                text = context.getString(R.string.widget_quick_add_income),
                glyph = IncomeIcon,
                ink = palette.incomeInk,
                wash = palette.incomeWash,
                type = TransactionType.INCOME,
                accountId = data.pinnedAccountId,
            )
        }

        setViewVisibility(R.id.widget_action_shortcut, data.showAppShortcut.asVisibility())
        setViewVisibility(R.id.widget_shortcut_gap, data.showAppShortcut.asVisibility())
        if (data.showAppShortcut) {
            val mark = (layout.shortcutSide * AppShortcutMarkRatio).toInt()
            // Squared against the height this render was asked for: a fixed
            // width read as a tall rectangle on any launcher whose row is taller.
            setViewLayoutWidth(
                R.id.widget_action_shortcut,
                layout.shortcutSide.toFloat(),
                TypedValue.COMPLEX_UNIT_DIP,
            )
            setViewLayoutWidth(R.id.widget_action_shortcut_icon, mark.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            setViewLayoutHeight(R.id.widget_action_shortcut_icon, mark.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            setColor(R.id.widget_action_shortcut_bg, "setColorFilter", palette.neutralWash)
            setImageViewBitmap(
                R.id.widget_action_shortcut_icon,
                CategoryIconBitmaps.appMark(context, AppShortcutIcon, context.px(mark)),
            )
            setContentDescription(
                R.id.widget_action_shortcut,
                context.getString(R.string.widget_quick_add_open_a11y),
            )
            setOnClickPendingIntent(R.id.widget_action_shortcut, openApp(context, appWidgetId))
        }
    }

    @Suppress("LongParameterList")
    private fun RemoteViews.paintAction(
        context: Context,
        appWidgetId: Int,
        root: Int,
        background: Int,
        icon: Int,
        label: Int,
        text: String,
        glyph: ImageVector,
        ink: WidgetColor,
        wash: WidgetColor,
        type: TransactionType,
        accountId: Long?,
    ) {
        setColor(background, "setColorFilter", wash)
        setImageViewBitmap(icon, CategoryIconBitmaps.glyph(glyph, context.px(GlyphRasterSize)))
        // Tinted here rather than baked into the mask, so the launcher flips
        // the shade with the system theme on its own.
        setColor(icon, "setColorFilter", ink)
        setTextViewText(label, text)
        setColor(label, "setTextColor", ink)
        setContentDescription(root, text)
        setOnClickPendingIntent(
            root,
            quickEntry(
                context = context,
                appWidgetId = appWidgetId,
                type = type,
                // No category: the sheet preselects the most used one.
                categoryId = null,
                accountId = accountId,
            ),
        )
    }

    private fun renderGrid(
        context: Context,
        appWidgetId: Int,
        data: QuickAddWidgetData,
        palette: WidgetPalette,
        layout: WidgetLayout,
    ): RemoteViews {
        val resource = if (layout.showLabels) {
            R.layout.widget_quick_grid_wide
        } else {
            R.layout.widget_quick_grid_narrow
        }
        return RemoteViews(context.packageName, resource).apply {
            paintBackground(palette)
            if (layout.showHeader) paintHeader(context, appWidgetId, data, palette)

            // The "more" tile (null) closes the grid, so a category outside it -
            // or a transfer, a note, another date - is always one tap from the
            // full editor.
            val slots: List<Category?> = data.categories.take(layout.categorySlots) + listOf(null)
            RowIds.forEachIndexed { row, id ->
                setViewVisibility(id, (row < layout.rows).asVisibility())
            }
            val cells = layout.rows * layout.columns
            repeat(cells) { index ->
                val slot = slots.getOrNull(index)
                if (slot == null && index >= slots.size) {
                    // INVISIBLE, never GONE: the cells carry the column weight,
                    // so hiding one outright would stretch the rest of the row
                    // out of line with the rows above it.
                    setViewVisibility(CellIds[index], View.INVISIBLE)
                    return@repeat
                }
                setViewVisibility(CellIds[index], View.VISIBLE)
                if (slot == null) {
                    paintMoreTile(context, appWidgetId, data, palette, layout, index)
                } else {
                    paintCategoryTile(context, appWidgetId, data, palette, layout, index, slot)
                }
            }
        }
    }

    private fun RemoteViews.paintHeader(
        context: Context,
        appWidgetId: Int,
        data: QuickAddWidgetData,
        palette: WidgetPalette,
    ) {
        paintPill(
            context = context,
            appWidgetId = appWidgetId,
            background = R.id.widget_pill_expense_bg,
            label = R.id.widget_pill_expense,
            text = context.getString(R.string.widget_quick_add_expense),
            selected = data.type == TransactionType.EXPENSE,
            palette = palette,
            type = TransactionType.EXPENSE,
        )
        paintPill(
            context = context,
            appWidgetId = appWidgetId,
            background = R.id.widget_pill_income_bg,
            label = R.id.widget_pill_income,
            text = context.getString(R.string.widget_quick_add_income),
            selected = data.type == TransactionType.INCOME,
            palette = palette,
            type = TransactionType.INCOME,
        )
        val badge = data.pinnedAccountName
        setViewVisibility(R.id.widget_badge, (badge != null).asVisibility())
        if (badge != null) {
            setTextViewText(R.id.widget_badge, badge)
            setColor(R.id.widget_badge, "setTextColor", palette.onSurfaceVariant)
        }
    }

    @Suppress("LongParameterList")
    private fun RemoteViews.paintPill(
        context: Context,
        appWidgetId: Int,
        background: Int,
        label: Int,
        text: String,
        selected: Boolean,
        palette: WidgetPalette,
        type: TransactionType,
    ) {
        // The unselected half has no fill at all, exactly like the in-app
        // segmented control; INVISIBLE rather than GONE so the pill keeps its
        // width and the selector does not jump as the type changes.
        setViewVisibility(background, if (selected) View.VISIBLE else View.INVISIBLE)
        if (selected) setColor(background, "setColorFilter", palette.pillFill)
        setTextViewText(label, text)
        setColor(label, "setTextColor", if (selected) palette.pillInk else palette.pillIdleInk)
        setContentDescription(label, text)
        setOnClickPendingIntent(label, setType(context, appWidgetId, type))
    }

    @Suppress("LongParameterList")
    private fun RemoteViews.paintCategoryTile(
        context: Context,
        appWidgetId: Int,
        data: QuickAddWidgetData,
        palette: WidgetPalette,
        layout: WidgetLayout,
        index: Int,
        category: Category,
    ) {
        val accent = CategoryVisuals.color(category.color)
        paintTile(
            context = context,
            palette = palette,
            layout = layout,
            index = index,
            glyph = CategoryVisuals.icon(category.icon),
            ink = palette.categoryInk(accent),
            wash = palette.categoryWash(accent),
            label = category.name,
            description = context.getString(R.string.widget_quick_add_category_a11y, category.name),
        )
        setOnClickPendingIntent(
            CellIds[index],
            quickEntry(
                context = context,
                appWidgetId = appWidgetId,
                type = data.type,
                categoryId = category.id,
                // Null unless the widget is pinned to a live account: the sheet
                // resolves the app default itself at open time, so the widget
                // never has to redraw to track it.
                accountId = data.pinnedAccountId,
            ),
        )
    }

    /**
     * The way out to the full editor. Drawn in the brand colour with a "more"
     * glyph, deliberately unlike a category avatar: the default seed ships a
     * category actually named "Altro"/"Other", and two identical-looking tiles
     * with the same label is the one confusion this grid cannot afford.
     */
    private fun RemoteViews.paintMoreTile(
        context: Context,
        appWidgetId: Int,
        data: QuickAddWidgetData,
        palette: WidgetPalette,
        layout: WidgetLayout,
        index: Int,
    ) {
        paintTile(
            context = context,
            palette = palette,
            layout = layout,
            index = index,
            glyph = MoreIcon,
            ink = palette.moreInk,
            wash = palette.moreWash,
            label = context.getString(R.string.widget_quick_add_open),
            description = context.getString(R.string.widget_quick_add_open_a11y),
        )
        // The editor has to open on the type the widget is showing: an income
        // widget that lands the user on a new expense is worse than no shortcut.
        setOnClickPendingIntent(
            CellIds[index],
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setAction(quickActionFor(data.type))
                    .setData(uri("$appWidgetId/more/${data.type.name}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun RemoteViews.paintTile(
        context: Context,
        palette: WidgetPalette,
        layout: WidgetLayout,
        index: Int,
        glyph: ImageVector,
        ink: WidgetColor,
        wash: WidgetColor,
        label: String,
        description: String,
    ) {
        setColor(TileBackgroundIds[index], "setColorFilter", wash)
        setImageViewBitmap(
            TileIconIds[index],
            CategoryIconBitmaps.glyph(glyph, context.px(GlyphRasterSize)),
        )
        setColor(TileIconIds[index], "setColorFilter", ink)
        setContentDescription(CellIds[index], description)
        if (layout.showLabels) {
            setTextViewText(TileLabelIds[index], label)
            setColor(TileLabelIds[index], "setTextColor", palette.onSurfaceVariant)
        }
    }

    private fun RemoteViews.paintBackground(palette: WidgetPalette) {
        setColor(R.id.widget_background, "setColorFilter", palette.background)
    }

    /** The day/night pair the launcher resolves on its own, whatever theme it wakes in. */
    private fun RemoteViews.setColor(viewId: Int, method: String, color: WidgetColor) {
        setColorInt(viewId, method, color.light, color.dark)
    }

    /**
     * A tap that opens the amount sheet over the launcher.
     *
     * The target is carried in the data URI as well as in the extras, and that
     * is not belt and braces: extras do not count towards `PendingIntent`
     * identity (`filterEquals` ignores them), so twenty tiles pointing at the
     * same activity would collapse into one intent and every tile would open
     * the category of whichever was bound last.
     */
    private fun quickEntry(
        context: Context,
        appWidgetId: Int,
        type: TransactionType,
        categoryId: Long?,
        accountId: Long?,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        QuickEntryActivity.intent(context, type, categoryId, accountId)
            .setData(uri("$appWidgetId/add/${type.name}/$categoryId/$accountId")),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The selector: a broadcast back to our own provider, which writes and re-renders. */
    private fun setType(context: Context, appWidgetId: Int, type: TransactionType): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, providerFor(appWidgetId))
                .setAction(SaldoWidgetProvider.ACTION_SET_TYPE)
                .setData(uri("$appWidgetId/type/${type.name}"))
                .putExtra(SaldoWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(SaldoWidgetProvider.EXTRA_TYPE, type.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Only the grid draws a selector, so a set-type broadcast can only ever be
     * meant for its provider.
     */
    private fun providerFor(@Suppress("UNUSED_PARAMETER") appWidgetId: Int) =
        QuickAddWidgetProvider::class.java

    private fun openApp(context: Context, requestKey: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setData(uri("$requestKey/open"))
                // SINGLE_TOP is what makes CLEAR_TOP resume the running activity
                // instead of finishing and rebuilding it, which would replay the
                // splash and drop the user somewhere they did not ask for.
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun uri(path: String): Uri = Uri.parse("$SCHEME://widget/$path")

    private fun Boolean.asVisibility(): Int = if (this) View.VISIBLE else View.GONE

    private fun Context.px(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private const val SCHEME = "saldo"

    /**
     * One raster size for every glyph on the widget, whatever tile shows it.
     *
     * The mask is scaled down by the ImageView that draws it, which costs
     * nothing and buys the thing that matters: a `RemoteViews` dedupes bitmaps
     * by identity across the whole sizes map, so a single cached instance per
     * icon rides the Binder transaction once instead of once per tile size per
     * breakpoint.
     */
    private const val GlyphRasterSize = 32

    /** The mark above the "open Saldo to get started" text of the taller sizes. */
    private const val SetupMarkSize = 36

    /** The setup layout is the same on every instance, so they can share one intent. */
    private const val SETUP_REQUEST = 0
}
