package com.callbackdev.saldo.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
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
import androidx.glance.color.ColorProvider
import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.TransactionType
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
 * Everything the widget draws is read *inside* the composition, and that is
 * load-bearing rather than stylistic. `provideGlance` runs once, when the
 * session is created: an update sends `UpdateGlanceState`, which re-reads the
 * widget state into the session's `MutableState` and lets recomposition do the
 * rest. Anything captured before `provideContent` is therefore frozen for the
 * life of the session, which is why the state arrives through [currentState]
 * and the data through [produceState] keyed on it.
 */
class SaldoQuickAddWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(Bar, Small, Medium, Large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = context.widgetEntryPoint()
        val loader = entryPoint.quickAddWidgetDataLoader()
        val preferences = entryPoint.userPreferences()
        // Loaded once up front purely so the first frame is already right: the
        // composition below owns every read from here on.
        val initialInputs = WidgetInputs.from(getAppWidgetState(context, id))
        val initialData = loader.load(initialInputs.config, MaxCategorySlots)
        provideContent {
            val inputs = WidgetInputs.from(currentState())
            // Reloads on every change of the inputs, with no "but we already
            // have this" shortcut: the state the session starts on is also a
            // state the user comes back to, and skipping the load there left
            // the widget showing the type it had just moved away from.
            val data by produceState(initialData, inputs) {
                value = loader.load(inputs.config, MaxCategorySlots)
            }
            val themePreferences by preferences.themePreferences
                .collectAsState(initial = ThemePreferences())
            val theme = resolveWidgetTheme(LocalContext.current, themePreferences, inputs.config)
            GlanceTheme(colors = theme.providers) {
                // The selector follows the state, not the loaded data: the
                // control the user just pressed has to answer immediately, and
                // the grid catches up a frame later.
                WidgetBody(inputs.config.type, data, theme)
            }
        }
    }

    companion object {
        /** One launcher row: no room for a grid, so it becomes two buttons. */
        val Bar = DpSize(120.dp, 50.dp)
        val Small = DpSize(120.dp, 120.dp)
        val Medium = DpSize(250.dp, 120.dp)
        val Large = DpSize(250.dp, 190.dp)

        /** The largest bucket shows 4x2 tiles, one of which is always "more". */
        const val MaxCategorySlots = 7
    }
}

/**
 * Everything that makes the widget reload. The configuration is the user's
 * doing; [QuickAddWidgetConfig] alone would not notice a movement being
 * recorded, so [WidgetRefreshWatcher] bumps a revision through the same widget
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

/** Internal so the size-to-layout decision, which is silent when wrong, can be asserted. */
internal fun layoutFor(size: DpSize): WidgetLayout = when {
    // Height first: a widget one row high can be any number of columns wide,
    // and none of those widths can hold a grid.
    size.height < SaldoQuickAddWidget.Small.height -> WidgetLayout(style = WidgetStyle.ACTIONS)
    size.width >= SaldoQuickAddWidget.Medium.width && size.height >= SaldoQuickAddWidget.Large.height ->
        WidgetLayout(WidgetStyle.GRID, columns = 4, rows = 2, showHeader = true, showLabels = true, tileSize = 44)
    // One row only, and the header costs 34dp: the tile gives back 4dp rather
    // than letting the label be clipped.
    size.width >= SaldoQuickAddWidget.Medium.width ->
        WidgetLayout(WidgetStyle.GRID, columns = 4, rows = 1, showHeader = true, showLabels = true, tileSize = 40)
    // A 2x2 is about 110dp square: a header plus labels would leave the tiles
    // unusable, so this size shows four icons and takes its type from the
    // widget's own configuration instead of a selector.
    else -> WidgetLayout(WidgetStyle.GRID, columns = 2, rows = 2, tileSize = 52)
}

@Composable
private fun WidgetBody(
    selectedType: TransactionType,
    data: QuickAddWidgetData,
    theme: QuickAddWidgetTheme,
) {
    val layout = layoutFor(LocalSize.current)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(theme.background)
            .cornerRadius(WidgetCornerRadius)
            .padding(horizontal = WidgetPaddingHorizontal, vertical = WidgetPaddingVertical),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        when {
            !data.isReady -> NotReady()
            layout.style == WidgetStyle.ACTIONS -> MoneyActions(data, theme)
            else -> {
                if (layout.showHeader) {
                    Header(selectedType, data)
                    Spacer(GlanceModifier.height(HeaderGap))
                }
                CategoryGrid(data, theme, layout)
            }
        }
    }
}

/** No account or no category of this type yet: the tile stays a door into the app. */
@Composable
private fun NotReady() {
    val context = LocalContext.current
    Text(
        text = context.getString(R.string.widget_quick_add_setup),
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = LabelFontSize,
            textAlign = TextAlign.Center,
        ),
        maxLines = 3,
    )
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
        Spacer(GlanceModifier.defaultWeight())
        if (data.todayTotal != null) {
            Text(
                text = data.todayTotal,
                modifier = GlanceModifier.padding(start = AmountGap, end = AmountEndPadding),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = LabelFontSize,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
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
private fun ColumnScope.MoneyActions(data: QuickAddWidgetData, theme: QuickAddWidgetTheme) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        MoneyActionButton(
            label = context.getString(R.string.widget_quick_add_expense),
            icon = ExpenseIcon,
            accent = theme.expenseAccent,
            accountId = data.account?.id,
            type = TransactionType.EXPENSE,
        )
        Spacer(GlanceModifier.width(ActionGap))
        MoneyActionButton(
            label = context.getString(R.string.widget_quick_add_income),
            icon = IncomeIcon,
            accent = theme.incomeAccent,
            accountId = data.account?.id,
            type = TransactionType.INCOME,
        )
    }
}

@Composable
private fun RowScope.MoneyActionButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    accountId: Long?,
    type: TransactionType,
) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .background(accent.copy(alpha = ActionTintAlpha))
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
            // income to be told apart by more than colour.
            Image(
                provider = ImageProvider(
                    CategoryIconBitmaps.glyph(icon, accent, context.pxOf(ActionIconSize)),
                ),
                contentDescription = label,
                modifier = GlanceModifier.size(ActionIconSize.dp),
            )
            Spacer(GlanceModifier.width(ActionIconGap))
            Text(
                text = label,
                style = TextStyle(
                    // The accent is already resolved for the widget's own theme,
                    // so the same colour serves both branches.
                    color = ColorProvider(day = accent, night = accent),
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
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            row.forEach { category ->
                Box(modifier = GlanceModifier.defaultWeight()) {
                    if (category == null) {
                        MoreTile(data, theme, layout)
                    } else {
                        CategoryTile(category, data, layout)
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
private fun CategoryTile(category: Category, data: QuickAddWidgetData, layout: WidgetLayout) {
    val context = LocalContext.current
    Tile(
        provider = ImageProvider(
            CategoryIconBitmaps.categoryTile(
                iconKey = category.icon,
                colorRgb = category.color,
                sizePx = context.pxOf(layout.tileSize),
            ),
        ),
        label = category.name.takeIf { layout.showLabels },
        contentDescription = context.getString(R.string.widget_quick_add_category_a11y, category.name),
        tileSize = layout.tileSize,
        action = actionStartActivity(
            QuickEntryActivity.intent(
                context = context,
                type = data.type,
                categoryId = category.id,
                accountId = data.account?.id,
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
        provider = ImageProvider(
            CategoryIconBitmaps.actionTile(
                vector = MoreIcon,
                color = theme.scheme.primary,
                sizePx = context.pxOf(layout.tileSize),
            ),
        ),
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

@Composable
private fun Tile(
    provider: ImageProvider,
    label: String?,
    contentDescription: String,
    tileSize: Int,
    action: Action,
) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().clickable(action),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Image(
            provider = provider,
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(tileSize.dp),
        )
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

private val WidgetCornerRadius = 24.dp

/**
 * The vertical budget is tight and deliberate. At the large bucket (250x190dp)
 * the content box is 174dp: a 34dp selector and its gap leave 134, which is two
 * rows of 64 - a 44dp tile, its gap and a 12sp label. Horizontal padding is
 * larger than vertical because it has no budget to fight over, and because the
 * amount on the right needs it.
 */
private val WidgetPaddingHorizontal = 12.dp
private val WidgetPaddingVertical = 8.dp
private val HeaderGap = 6.dp
private val RowGap = 6.dp
private val LabelGap = 3.dp
private val PillGap = 6.dp

/** Still well clear of the 20dp the selector started at, and of a stray tap. */
private val PillHeight = 34.dp
private val PillCornerRadius = 17.dp
private val PillPaddingHorizontal = 14.dp

/** A text box hugs its glyphs tighter than a pill hugs its label. */
private val AmountGap = 8.dp
private val AmountEndPadding = 4.dp

private val LabelFontSize = 13.sp
private val TileLabelFontSize = 12.sp

private val ActionGap = 8.dp
private val ActionCornerRadius = 18.dp
private val ActionIconGap = 6.dp
private val ActionFontSize = 15.sp
private const val ActionIconSize = 20
private const val ActionTintAlpha = 0.16f

/** The same pair the dashboard speed-dial uses, so the gesture reads the same everywhere. */
private val ExpenseIcon: ImageVector = Icons.AutoMirrored.Outlined.TrendingDown
private val IncomeIcon: ImageVector = Icons.AutoMirrored.Outlined.TrendingUp

/** Reads as "more options" in any launcher, and is in no category icon set. */
private val MoreIcon: ImageVector = Icons.Outlined.MoreHoriz
