package com.callbackdev.saldo.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.TransactionType
import kotlin.math.ceil
import kotlinx.coroutines.flow.first

/**
 * Home screen quick-add widget: pick the category on the launcher, type the
 * amount in the sheet that opens over it ([QuickEntryActivity]), done.
 *
 * The amount deliberately does *not* live here (ADR 32): every tap on a widget
 * is a broadcast to the app process plus a `RemoteViews` round trip back to the
 * launcher, which a keypad would make the user feel on every single digit. What
 * a widget does well - one tap on glanceable content - it keeps.
 *
 * A static entry point by design: no balances, no daily totals, no
 * usage-derived ordering. Everything it draws changes only when the user edits
 * accounts, categories or the theme, so refreshes are rare and the launcher's
 * copy stays untouched in between.
 *
 * Everything the widget draws is read *inside* the composition, and that is
 * load-bearing rather than stylistic. `provideGlance` runs once, when the
 * session is created: an update sends `UpdateGlanceState`, which re-reads the
 * widget state into the session's `MutableState` and lets recomposition do the
 * rest. Anything captured before `provideContent` is therefore frozen for the
 * life of the session, which is why the state arrives through [currentState]
 * and the data through [produceState] keyed on it.
 */
class SaldoQuickAddWidget : GlanceAppWidget() {

    /**
     * Responsive over Exact, and the difference is who answers a resize. Every
     * update pre-renders each bucket in [WidgetBuckets] and hands the launcher
     * the whole set, so a resize is settled inside the launcher, instantly.
     * Exact was tried first: it reports the truly fluid size, but each resize
     * then needs a full round trip into the app process (options-changed
     * broadcast, a WorkManager-backed Glance session, recomposition), which on
     * a device with a cold process or an eager battery policy lands seconds or
     * minutes late. The layout is a step function of the size anyway
     * ([layoutFor]), so enumerating its steps as buckets loses nothing the
     * user can see.
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetBuckets)

    /** The picker preview renders at the widget's default 4x3 shape. */
    override val previewSizeMode = SizeMode.Responsive(setOf(PreviewBucket))

    override suspend fun provideGlance(context: Context, id: GlanceId) =
        provideQuickAddContent(context, id)

    override suspend fun providePreview(context: Context, widgetCategory: Int) =
        provideQuickAddPreview(context)

    companion object {
        /** Below this height there is no room for a category, so the widget becomes two buttons. */
        val GridMinHeight = 120.dp

        /** Below this width the grid drops to two icon-only columns. */
        val WideMinWidth = 250.dp

        /** How many categories the settings screen lets a user pin by hand. */
        const val MaxPinnedCategories = 12
    }
}

/**
 * Every size [layoutFor] distinguishes, one bucket per step. The heights solve
 * the row arithmetic exactly: a narrow row costs 52dp plus a 6dp gap over a
 * 16dp vertical inset, a wide one 64dp plus the gap under a 40dp header, so
 * each bucket is the smallest height at which its row count fits. The launcher
 * receives all of them pre-rendered and switches on its own during a resize;
 * asserted against [layoutFor] in `WidgetLayoutTest` because a bucket that
 * drifted from the arithmetic would silently pin the wrong layout.
 */
/**
 * The single-row sizes: the whole bucket set of the bar widget, and the low
 * end of the grid's (which still degrades to the two-button layout when
 * squashed, for the widgets placed before the bar existed). Four heights
 * rather than one, not for the layout (any height under 120 is the same two
 * buttons) but for the app-shortcut square: RemoteViews has no measure pass,
 * so the bucket height is the only estimate of the button height there is,
 * and one bucket at 40dp made the shortcut a fixed-width rectangle on every
 * launcher whose row is taller.
 */
internal val ActionBuckets: Set<DpSize> = setOf(
    DpSize(110.dp, 40.dp),
    DpSize(110.dp, 64.dp),
    DpSize(110.dp, 88.dp),
    DpSize(110.dp, 112.dp),
)

internal val GridBuckets: Set<DpSize> = setOf(
    // Narrow grid, two bare-icon columns, 1 to 5 rows.
    DpSize(110.dp, 120.dp),
    DpSize(110.dp, 126.dp),
    DpSize(110.dp, 184.dp),
    DpSize(110.dp, 242.dp),
    DpSize(110.dp, 300.dp),
    // Wide grid, four labelled columns under the selector, 1 to 5 rows.
    DpSize(250.dp, 120.dp),
    DpSize(250.dp, 190.dp),
    DpSize(250.dp, 260.dp),
    DpSize(250.dp, 330.dp),
    DpSize(250.dp, 400.dp),
)

internal val WidgetBuckets: Set<DpSize> = ActionBuckets + GridBuckets

/** The default 4x3 placement: what the grid's generated preview shows. */
internal val PreviewBucket = DpSize(250.dp, 260.dp)

/** The single-row shape: the bar widget's generated preview. */
internal val PreviewRowBucket = DpSize(250.dp, 88.dp)

/**
 * The live composition, shared by the grid and the bar: same state, same data,
 * same body - the two widgets differ only in the bucket sets their providers
 * declare, so one is a grid that can degrade to the row and the other is the
 * row by contract.
 */
internal suspend fun GlanceAppWidget.provideQuickAddContent(context: Context, id: GlanceId) {
    val loader = context.widgetEntryPoint().quickAddWidgetDataLoader()
    // Loaded once up front purely so the first frame is already right - data
    // *and* palette, so no frame ever goes out in the default theme: the
    // composition below owns every read from here on.
    val initialInputs = WidgetInputs.from(getAppWidgetState(context, id))
    val initialSnapshot = loader.loadShared(initialInputs.config, initialInputs.revision)
    provideContent {
        val inputs = WidgetInputs.from(currentState())
        // Reloads on every change of the inputs. The content runs once per
        // bucket of the Responsive set, so the load is the shared one: many
        // compositions, one database pass, one theme resolution.
        val snapshot by produceState(initialSnapshot, inputs) {
            value = loader.loadShared(inputs.config, inputs.revision)
        }
        GlanceTheme(colors = snapshot.theme.providers) {
            // The selector follows the state, not the loaded data: the
            // control the user just pressed has to answer immediately, and
            // the grid catches up a frame later.
            WidgetBody(inputs.config, snapshot.data, snapshot.theme)
        }
    }
}

/**
 * The widget picker's generated preview (API 35+): the real layout in the
 * user's real palette and categories, where the static `previewLayout` XML
 * can only ever show a stand-in. Data reads are best-effort - before
 * onboarding this simply shows the honest "open Saldo to get started".
 *
 * Publishing it is [WidgetPreviews]' job, and not a one-shot: the system drops
 * the preview on every in-place app update and on every reboot.
 */
internal suspend fun GlanceAppWidget.provideQuickAddPreview(context: Context) {
    val entryPoint = context.widgetEntryPoint()
    val config = QuickAddWidgetConfig()
    val data = runCatching { entryPoint.quickAddWidgetDataLoader().load(config) }
        .getOrDefault(
            QuickAddWidgetData(
                type = config.type,
                categories = emptyList(),
                hasAccounts = false,
            ),
        )
    val themePreferences = runCatching { entryPoint.userPreferences().themePreferences.first() }
        .getOrDefault(ThemePreferences())
    val theme = resolveWidgetTheme(context, themePreferences, config)
    provideContent {
        GlanceTheme(colors = theme.providers) {
            WidgetBody(config, data, theme)
        }
    }
}

/**
 * Everything that makes the widget reload. The configuration is the user's
 * doing; [QuickAddWidgetConfig] alone would not notice a category or theme
 * edit, so [WidgetRefreshWatcher] bumps a revision through the same widget
 * state and this carries it into the composition.
 */
private data class WidgetInputs(val config: QuickAddWidgetConfig, val revision: Long) {
    companion object {
        fun from(preferences: Preferences) = WidgetInputs(
            config = QuickAddWidgetPrefs.read(preferences),
            revision = preferences[QuickAddWidgetPrefs.Revision] ?: 0L,
        )
    }
}

/**
 * How many tiles fit and whether there is room for a header. Deliberately a
 * hard split per bucket rather than a fluid layout: `RemoteViews` gives no
 * measuring pass to fall back on, so each size is designed rather than squeezed.
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
     */
    val paddingHorizontal: Int = 12,
    val paddingVertical: Int = 8,
    /**
     * The side of the square app-shortcut button in the single-row layout,
     * derived from the bucket height so width can match height. The real
     * widget can run a little taller than its bucket, so "square" is a close
     * approximation rather than a guarantee - as close as RemoteViews allows.
     */
    val shortcutSide: Int = 0,
) {
    /** One slot is always the "more" tile, the way out to the full editor. */
    val categorySlots: Int get() = columns * rows - 1
}

/**
 * [WidgetStyle.ACTIONS] is not a smaller grid, it is a different widget: at one
 * launcher row high there is no room for a category, so the two movement types
 * become the content and the category is picked in the sheet that opens.
 */
internal enum class WidgetStyle { GRID, ACTIONS }

/**
 * Internal so the size-to-layout decision, which is silent when wrong, can be
 * asserted.
 *
 * [availableCategories] caps the rows from the other side: a widget dragged
 * taller than the categories it has to show would otherwise grow empty rows.
 *
 * [fontScale] keeps the height budget honest for large system fonts: a label
 * line that costs 17dp at scale 1 costs 26dp at 1.5, and a budget that ignored
 * that clipped every label on the grid.
 */
internal fun layoutFor(size: DpSize, availableCategories: Int, fontScale: Float = 1f): WidgetLayout {
    // Height first: a widget one row high can be any number of columns wide,
    // and none of those widths can hold a grid.
    if (size.height < SaldoQuickAddWidget.GridMinHeight) {
        return WidgetLayout(
            style = WidgetStyle.ACTIONS,
            paddingHorizontal = ActionsPadding,
            paddingVertical = ActionsPadding,
            // What is left of the bucket height once the inset is paid: the
            // button height, which is also the width a square wants. Floored
            // so the smallest bucket keeps a usable tap target.
            shortcutSide = (size.height.value.toInt() - 2 * ActionsPadding)
                .coerceAtLeast(MinShortcutSide),
        )
    }
    val wide = size.width >= SaldoQuickAddWidget.WideMinWidth
    val columns = if (wide) WideColumns else NarrowColumns
    // A narrow widget has no room for a type selector or for labels, so it takes
    // its type from its own configuration and shows bigger, bare icons.
    val tileSize = if (wide) WideTileSize else NarrowTileSize
    val labelLine = if (wide) ceil(LabelLineDp * fontScale).toInt() else 0
    val rowHeight = tileSize + if (wide) LabelGapDp + labelLine else 0

    val header = if (wide) PillHeightDp + HeaderGapDp else 0
    val content = size.height.value.toInt() - 2 * GridPaddingVertical - header
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
        paddingHorizontal = GridPaddingHorizontal,
        paddingVertical = GridPaddingVertical,
    )
}

private fun ceilDiv(value: Int, by: Int): Int = (value + by - 1) / by

private const val MaxGridRows = 5

@Composable
private fun WidgetBody(
    config: QuickAddWidgetConfig,
    data: QuickAddWidgetData,
    theme: QuickAddWidgetTheme,
) {
    val layout = layoutFor(
        size = LocalSize.current,
        availableCategories = data.categories.size,
        fontScale = LocalContext.current.resources.configuration.fontScale,
    )
    Column(
        // appWidgetBackground marks this as the widget's face for the launcher,
        // which is what the placement and resize animations attach to; the
        // corner radius is the system's own, so the widget wears the same
        // rounding as every other widget on the device instead of a private 24dp.
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(theme.background)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .padding(
                horizontal = layout.paddingHorizontal.dp,
                vertical = layout.paddingVertical.dp,
            ),
        // Anchored to the top, never centred. Expense and income hold a
        // different number of categories, so a centred block would move the
        // selector and the amount every time the type changed; the leftover
        // room belongs at the bottom, where nothing is looking.
        verticalAlignment = Alignment.Vertical.Top,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        when {
            !data.isReady -> NotReady(compact = layout.style == WidgetStyle.ACTIONS)
            layout.style == WidgetStyle.ACTIONS -> MoneyActions(config, data, theme, layout)
            else -> {
                if (layout.showHeader) {
                    Header(config.effectiveType, data)
                    Spacer(GlanceModifier.height(HeaderGap))
                }
                CategoryGrid(data, theme, layout)
            }
        }
    }
}

/**
 * No account or no category of this type yet: the whole widget is a door into
 * the app, not just the line of text - a target the size of the widget for the
 * one moment the user has nothing else to tap.
 */
@Composable
private fun NotReady(compact: Boolean) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            // At one launcher row the mark would crowd out the words that
            // explain the tap, so only the taller sizes carry it.
            if (!compact) {
                Image(
                    provider = ImageProvider(
                        CategoryIconBitmaps.appMark(context, AppShortcutIcon, context.pxOf(SetupMarkSize)),
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(SetupMarkSize.dp),
                )
                Spacer(GlanceModifier.height(SetupGap))
            }
            Text(
                text = context.getString(R.string.widget_quick_add_setup),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = LabelFontSize,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun Header(selectedType: TransactionType, data: QuickAddWidgetData) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        TypePill(
            label = context.getString(R.string.widget_quick_add_expense),
            type = TransactionType.EXPENSE,
            selected = selectedType == TransactionType.EXPENSE,
        )
        Spacer(GlanceModifier.width(PillGap))
        TypePill(
            label = context.getString(R.string.widget_quick_add_income),
            type = TransactionType.INCOME,
            selected = selectedType == TransactionType.INCOME,
        )
        if (data.pinnedAccountName != null) {
            // A widget pinned to one account says which: with two widgets on
            // two accounts, an unlabelled pair is a wrong-account entry waiting
            // to happen. Weighted so a long name gives way, never the pills.
            Text(
                text = data.pinnedAccountName,
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(start = BadgeGap, end = BadgeEndPadding),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = BadgeFontSize,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
            )
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

/**
 * One segment of the type selector.
 *
 * The height is fixed and the whole pill is the tap target, not the text box:
 * padding around a 12sp label came out around 20dp tall against the 48dp
 * minimum, so the selector was genuinely hard to hit - and "Spesa", being the
 * shorter word, was the harder of the two, which made the failure look like it
 * only affected one direction. The horizontal padding is generous for the same
 * reason: it is the only thing giving the shorter label a usable width.
 */
@Composable
private fun TypePill(label: String, type: TransactionType, selected: Boolean) {
    Box(
        modifier = GlanceModifier
            .height(PillHeight)
            .background(if (selected) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant)
            .cornerRadius(PillCornerRadius)
            .clickable(
                actionRunCallback<SetWidgetTypeAction>(
                    actionParametersOf(SetWidgetTypeAction.TypeKey to type.name),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = GlanceModifier.padding(horizontal = PillPaddingHorizontal),
            style = TextStyle(
                color = if (selected) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant,
                fontSize = LabelFontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}


/**
 * The single-row layout: expense and income share the width and grow with it,
 * so the same widget works at two launcher columns and at five. Tapping one
 * opens the amount sheet on the category the user reaches for most, which is
 * shown at the top of the sheet and is one tap from being changed - the grid's
 * job, done in the sheet because there is no room for it out here.
 */
@Composable
private fun ColumnScope.MoneyActions(
    config: QuickAddWidgetConfig,
    data: QuickAddWidgetData,
    theme: QuickAddWidgetTheme,
    layout: WidgetLayout,
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        val expense = config.showsButton(TransactionType.EXPENSE)
        val income = config.showsButton(TransactionType.INCOME)
        if (expense) {
            MoneyActionButton(
                label = context.getString(R.string.widget_quick_add_expense),
                icon = ExpenseIcon,
                ink = theme.expenseAccent,
                wash = theme.expenseWash,
                accountId = data.pinnedAccountId,
                type = TransactionType.EXPENSE,
            )
        }
        if (expense && income) Spacer(GlanceModifier.width(ActionGap))
        if (income) {
            MoneyActionButton(
                label = context.getString(R.string.widget_quick_add_income),
                icon = IncomeIcon,
                ink = theme.incomeAccent,
                wash = theme.incomeWash,
                accountId = data.pinnedAccountId,
                type = TransactionType.INCOME,
            )
        }
        if (config.showAppShortcut) {
            Spacer(GlanceModifier.width(ActionGap))
            AppShortcutButton(theme, layout.shortcutSide)
        }
    }
}

/**
 * The way into the app from the single-row layout, which has no room for the
 * "open Saldo" tile the taller ones carry. A quiet neutral wash rather than a
 * bare floating mark or a third accent: the wash makes it read as a control in
 * the same family as the two buttons - on a transparent widget a naked icon
 * just hovered on the wallpaper - while the neutral keeps it from competing
 * with the pair that matters.
 *
 * Squared, not fixed-width: the width follows [WidgetLayout.shortcutSide],
 * the bucket's estimate of the button height, and the mark scales with it. A
 * fixed 56dp width read as a tall rectangle on any launcher whose row runs
 * taller than that.
 */
@Composable
private fun RowScope.AppShortcutButton(theme: QuickAddWidgetTheme, side: Int) {
    val context = LocalContext.current
    val markSize = (side * AppShortcutMarkRatio).toInt()
    Box(
        modifier = GlanceModifier
            .width(side.dp)
            .fillMaxHeight()
            .background(theme.neutralWash)
            .cornerRadius(ActionCornerRadius)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                CategoryIconBitmaps.appMark(context, AppShortcutIcon, context.pxOf(markSize)),
            ),
            contentDescription = context.getString(R.string.widget_quick_add_open_a11y),
            modifier = GlanceModifier.size(markSize.dp),
        )
    }
}

@Composable
private fun RowScope.MoneyActionButton(
    label: String,
    icon: ImageVector,
    ink: GlanceColorProvider,
    wash: GlanceColorProvider,
    accountId: Long?,
    type: TransactionType,
) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .background(wash)
            .cornerRadius(ActionCornerRadius)
            .clickable(
                actionStartActivity(
                    QuickEntryActivity.intent(
                        context = context,
                        type = type,
                        // No category: the sheet preselects the most used one.
                        categoryId = null,
                        accountId = accountId,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            // The icon is not decoration: the project requires expense and
            // income to be told apart by more than colour. Tinted here rather
            // than baked into the bitmap, so the launcher can flip the shade
            // with the system theme on its own.
            Image(
                provider = ImageProvider(
                    CategoryIconBitmaps.glyph(icon, context.pxOf(ActionIconSize)),
                ),
                contentDescription = label,
                colorFilter = ColorFilter.tint(ink),
                modifier = GlanceModifier.size(ActionIconSize.dp),
            )
            Spacer(GlanceModifier.width(ActionIconGap))
            Text(
                text = label,
                style = TextStyle(
                    color = ink,
                    fontSize = ActionFontSize,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ColumnScope.CategoryGrid(
    data: QuickAddWidgetData,
    theme: QuickAddWidgetTheme,
    layout: WidgetLayout,
) {
    // The "more" tile (null) closes the grid, so a category outside it - or a
    // transfer, a note, another date - is always one tap from the full editor.
    val slots: List<Category?> = data.categories.take(layout.categorySlots) + listOf(null)
    slots.chunked(layout.columns).forEachIndexed { index, row ->
        if (index > 0) Spacer(GlanceModifier.height(RowGap))
        Row(
            // A fixed height, not a weight: a widget taller than its categories
            // would otherwise stretch the rows apart instead of leaving the
            // block centred with room around it.
            modifier = GlanceModifier.fillMaxWidth().height(layout.rowHeight.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            row.forEach { category ->
                Box(modifier = GlanceModifier.defaultWeight()) {
                    if (category == null) {
                        MoreTile(data, theme, layout)
                    } else {
                        CategoryTile(category, data, theme, layout)
                    }
                }
            }
            // Keeps a short last row aligned with the rows above instead of
            // stretching its tiles across the leftover width.
            repeat(layout.columns - row.size) {
                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    data: QuickAddWidgetData,
    theme: QuickAddWidgetTheme,
    layout: WidgetLayout,
) {
    val context = LocalContext.current
    // A category's colour is its own in both themes; the day/night pair is
    // still what carries it, so every tile takes the same launcher-side path.
    val accent = CategoryVisuals.color(category.color)
    Tile(
        ink = theme.ink(accent, accent),
        wash = theme.wash(accent, accent),
        icon = CategoryVisuals.icon(category.icon),
        label = category.name.takeIf { layout.showLabels },
        contentDescription = context.getString(R.string.widget_quick_add_category_a11y, category.name),
        tileSize = layout.tileSize,
        action = actionStartActivity(
            QuickEntryActivity.intent(
                context = context,
                type = data.type,
                categoryId = category.id,
                // Null unless the widget is pinned to a live account: the
                // sheet resolves the app default itself at open time, so the
                // widget never has to redraw to track it.
                accountId = data.pinnedAccountId,
            ),
        ),
    )
}

/**
 * The way out to the full editor. Drawn as an outlined tile in the brand color
 * with a "more" glyph, deliberately unlike a category avatar (a filled squircle
 * from the category palette): the default seed ships a category actually named
 * "Altro"/"Other", and two identical-looking tiles with the same label is the
 * one confusion this grid cannot afford.
 */
@Composable
private fun MoreTile(data: QuickAddWidgetData, theme: QuickAddWidgetTheme, layout: WidgetLayout) {
    val context = LocalContext.current
    Tile(
        ink = theme.ink(theme.lightScheme.primary, theme.darkScheme.primary),
        wash = theme.wash(theme.lightScheme.primary, theme.darkScheme.primary),
        icon = MoreIcon,
        label = context.getString(R.string.widget_quick_add_open).takeIf { layout.showLabels },
        contentDescription = context.getString(R.string.widget_quick_add_open_a11y),
        tileSize = layout.tileSize,
        // The editor has to open on the type the widget is showing: an income
        // widget that lands the user on a new expense is worse than no shortcut.
        action = actionStartActivity(
            Intent(context, MainActivity::class.java).setAction(quickActionFor(data.type)),
        ),
    )
}

/** The launcher-shortcut action that opens the editor already set to [type]. */
internal fun quickActionFor(type: TransactionType): String = when (type) {
    TransactionType.INCOME -> MainActivity.ACTION_ADD_INCOME
    else -> MainActivity.ACTION_ADD_EXPENSE
}

/**
 * One tile of the grid: the app's unselected category cell, a wash of the
 * colour with the glyph in it at full strength.
 *
 * The wash is a Glance background and only the glyph is a bitmap, which is not
 * a detail. A `RemoteViews` carries its bitmaps to the launcher through a
 * Binder transaction with a hard size ceiling, and a full tile bitmap is around
 * three times the pixels of the glyph inside it: with four rows of four the
 * whole-tile version was heading for that ceiling, and an update over it fails
 * silently. The glyph itself is a white mask tinted through [ColorFilter], so
 * one bitmap serves both themes and the launcher flips the shade on its own.
 */
@Composable
private fun Tile(
    ink: GlanceColorProvider,
    wash: GlanceColorProvider,
    icon: ImageVector,
    label: String?,
    contentDescription: String,
    tileSize: Int,
    action: Action,
) {
    val context = LocalContext.current
    val glyphSize = (tileSize * GlyphRatio).toInt()
    Column(
        modifier = GlanceModifier.fillMaxWidth().clickable(action),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(tileSize.dp)
                .background(wash)
                .cornerRadius((tileSize * TileCornerRatio).dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(
                    CategoryIconBitmaps.glyph(icon, context.pxOf(glyphSize)),
                ),
                contentDescription = contentDescription,
                colorFilter = ColorFilter.tint(ink),
                modifier = GlanceModifier.size(glyphSize.dp),
            )
        }
        if (label != null) {
            Spacer(GlanceModifier.height(LabelGap))
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = TileLabelFontSize,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun Context.pxOf(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

private const val WideColumns = 4
private const val NarrowColumns = 2
private const val WideTileSize = 44
private const val NarrowTileSize = 52
private const val GridPaddingHorizontal = 12
private const val GridPaddingVertical = 8
private const val HeaderGapDp = 6
private const val RowGapDp = 6
private const val LabelGapDp = 3

/** A 12sp label with its line spacing at font scale 1, the base of the height budget. */
private const val LabelLineDp = 17
private const val PillHeightDp = 34

private val HeaderGap = HeaderGapDp.dp
private val RowGap = RowGapDp.dp
private val LabelGap = LabelGapDp.dp
private val PillGap = 6.dp

/** Still well clear of the 20dp the selector started at, and of a stray tap. */
private val PillHeight = PillHeightDp.dp
private val PillCornerRadius = 17.dp
private val PillPaddingHorizontal = 14.dp

/** A text box hugs its glyphs tighter than a pill hugs its label. */
private val BadgeGap = 8.dp
private val BadgeEndPadding = 4.dp

private val LabelFontSize = 13.sp
private val TileLabelFontSize = 12.sp

/** The pinned-account badge: present, quieter than the total beside it. */
private val BadgeFontSize = 11.sp

private val ActionGap = 8.dp
private val ActionCornerRadius = 18.dp
private val ActionIconGap = 6.dp
private val ActionFontSize = 15.sp
private const val ActionIconSize = 20

/** The single-row layout's even inset (see [WidgetLayout.paddingHorizontal]). */
private const val ActionsPadding = 14

/** The smallest square the shortcut is allowed to shrink to: still a target. */
private const val MinShortcutSide = 40

/** The mark against its square: most of it, with the margin a button implies. */
private const val AppShortcutMarkRatio = 0.8f

/** The mark above the "open Saldo to get started" text of the taller sizes. */
private const val SetupMarkSize = 36
private val SetupGap = 6.dp

/** Matches `CategoryCell`: a wash of the colour with the glyph on top. */
private const val TileCornerRatio = 0.30f
private const val GlyphRatio = 0.62f

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

/** The same pair the dashboard speed-dial uses, so the gesture reads the same everywhere. */
private val ExpenseIcon: ImageVector = Icons.AutoMirrored.Outlined.TrendingDown
private val IncomeIcon: ImageVector = Icons.AutoMirrored.Outlined.TrendingUp

/** Reads as "more options" in any launcher, and is in no category icon set. */
private val MoreIcon: ImageVector = Icons.Outlined.MoreHoriz
