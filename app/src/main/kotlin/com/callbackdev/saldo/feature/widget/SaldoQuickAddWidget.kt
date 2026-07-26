package com.callbackdev.saldo.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.R
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
 * Rendering is a snapshot: Glance composes in a session driven from the app
 * process and cannot keep observing flows, so [WidgetRefreshWatcher] asks for a
 * redraw when the underlying data moves.
 */
class SaldoQuickAddWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(Small, Medium, Large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = context.widgetEntryPoint()
        val config = QuickAddWidgetPrefs.read(getAppWidgetState(context, id))
        // Loaded once for the widest bucket; the narrower layouts take what fits.
        val data = entryPoint.quickAddWidgetDataLoader().load(config, MaxCategorySlots)
        val theme = resolveWidgetTheme(context, entryPoint.userPreferences().themePreferences.first())
        provideContent {
            GlanceTheme(colors = theme.providers) {
                WidgetBody(data, theme)
            }
        }
    }

    companion object {
        val Small = DpSize(120.dp, 120.dp)
        val Medium = DpSize(250.dp, 120.dp)
        val Large = DpSize(250.dp, 190.dp)

        /** The largest bucket shows 4x2 tiles, one of which is always "more". */
        const val MaxCategorySlots = 7
    }
}

/**
 * How many tiles fit and whether there is room for a header. Deliberately a
 * hard split per bucket rather than a fluid layout: `RemoteViews` gives no
 * measuring pass to fall back on, so each size is designed rather than squeezed.
 */
private data class WidgetLayout(
    val columns: Int,
    val rows: Int,
    val showHeader: Boolean,
    val showLabels: Boolean,
    val tileSize: Int,
) {
    /** One slot is always the "more" tile, the way out to the full editor. */
    val categorySlots: Int get() = columns * rows - 1
}

private fun layoutFor(size: DpSize): WidgetLayout = when {
    size.width >= SaldoQuickAddWidget.Medium.width && size.height >= SaldoQuickAddWidget.Large.height ->
        WidgetLayout(columns = 4, rows = 2, showHeader = true, showLabels = true, tileSize = 40)
    size.width >= SaldoQuickAddWidget.Medium.width ->
        WidgetLayout(columns = 4, rows = 1, showHeader = true, showLabels = true, tileSize = 40)
    // A 2x2 is about 110dp square: a header plus labels would leave the tiles
    // unusable, so this size shows four icons and takes its type from the
    // widget's own configuration instead of a selector.
    else -> WidgetLayout(columns = 2, rows = 2, showHeader = false, showLabels = false, tileSize = 44)
}

@Composable
private fun WidgetBody(data: QuickAddWidgetData, theme: QuickAddWidgetTheme) {
    val layout = layoutFor(LocalSize.current)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(WidgetCornerRadius)
            .padding(WidgetPadding),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (data.isReady) {
            if (layout.showHeader) {
                Header(data)
                Spacer(GlanceModifier.height(HeaderGap))
            }
            CategoryGrid(data, theme, layout)
        } else {
            NotReady()
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
private fun Header(data: QuickAddWidgetData) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        TypePill(
            label = context.getString(R.string.widget_quick_add_expense),
            type = TransactionType.EXPENSE,
            selected = data.type == TransactionType.EXPENSE,
        )
        Spacer(GlanceModifier.width(PillGap))
        TypePill(
            label = context.getString(R.string.widget_quick_add_income),
            type = TransactionType.INCOME,
            selected = data.type == TransactionType.INCOME,
        )
        Spacer(GlanceModifier.defaultWeight())
        if (data.todayTotal != null) {
            Text(
                text = data.todayTotal,
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

@Composable
private fun TypePill(label: String, type: TransactionType, selected: Boolean) {
    Text(
        text = label,
        modifier = GlanceModifier
            .background(if (selected) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant)
            .cornerRadius(PillCornerRadius)
            .clickable(
                actionRunCallback<SetWidgetTypeAction>(
                    actionParametersOf(SetWidgetTypeAction.TypeKey to type.name),
                ),
            )
            .padding(horizontal = PillPaddingHorizontal, vertical = PillPaddingVertical),
        style = TextStyle(
            color = if (selected) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant,
            fontSize = LabelFontSize,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
    )
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
                        MoreTile(theme, layout)
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
            CategoryIconBitmaps.tile(
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

@Composable
private fun MoreTile(theme: QuickAddWidgetTheme, layout: WidgetLayout) {
    val context = LocalContext.current
    Tile(
        provider = ImageProvider(
            CategoryIconBitmaps.themedTile(
                iconKey = MoreIconKey,
                background = theme.scheme.surfaceVariant,
                glyph = theme.scheme.onSurfaceVariant,
                sizePx = context.pxOf(layout.tileSize),
            ),
        ),
        label = context.getString(R.string.widget_quick_add_more).takeIf { layout.showLabels },
        contentDescription = context.getString(R.string.widget_quick_add_more_a11y),
        tileSize = layout.tileSize,
        action = actionStartActivity(
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_ADD_EXPENSE),
        ),
    )
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
private val WidgetPadding = 12.dp
private val HeaderGap = 10.dp
private val RowGap = 8.dp
private val LabelGap = 4.dp
private val PillGap = 6.dp
private val PillCornerRadius = 14.dp
private val PillPaddingHorizontal = 10.dp
private val PillPaddingVertical = 4.dp
private val LabelFontSize = 12.sp
private val TileLabelFontSize = 11.sp

/** The "more" entry borrows the app's own generic category glyph. */
private const val MoreIconKey = "category"
