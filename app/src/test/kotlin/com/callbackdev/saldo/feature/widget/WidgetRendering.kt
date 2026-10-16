package com.callbackdev.saldo.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.text.Layout
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.callbackdev.saldo.core.database.seed.DefaultCategories
import com.callbackdev.saldo.core.designsystem.theme.BrandDarkColorScheme
import com.callbackdev.saldo.core.designsystem.theme.BrandLightColorScheme
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.BudgetLevel
import com.callbackdev.saldo.core.domain.model.TransactionType
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import kotlin.math.roundToInt

/**
 * Draws a widget the way a launcher does: the provider's breakpoints rendered by
 * [WidgetRenderer], the one the launcher would pick for the room it grants
 * ([pickBreakpoint]), inflated by `RemoteViews.apply` into ordinary views, laid
 * out at the granted size and drawn.
 */
internal object WidgetRendering {

    /**
     * The reference grants, in dp: what a portrait home screen gives a widget of
     * one to four columns and one to five rows (a column 85, then 91 more each; a
     * row 85, then 104 more each).
     */
    val Widths = listOf(159f, 250f, 340f)
    val Heights = listOf(85f, 189f, 293f, 397f, 501f)

    fun palette(card: WidgetCardColor = WidgetCardColor.BLUE): WidgetPalette = widgetPalette(
        dress = WidgetDress(
            lightScheme = BrandLightColorScheme,
            darkScheme = BrandDarkColorScheme,
            lightMoney = moneyColors(BrandLightColorScheme, darkTheme = false),
            darkMoney = moneyColors(BrandDarkColorScheme, darkTheme = true),
        ),
        background = WidgetBackground.COLOR,
        cardColor = card,
        opacityPct = 100,
        wallpaperCarriesDarkInk = false,
    )

    /** The default expense categories, in the device's language, as a fresh install has them. */
    fun seedExpenses(context: Context): List<Category> = DefaultCategories.build(context)
        .filter { it.type == CategoryType.EXPENSE }
        .mapIndexed { index, entity ->
            Category(
                name = entity.name,
                type = entity.type,
                color = entity.color,
                icon = entity.icon,
                id = index + 1L,
                sortOrder = index,
            )
        }

    fun data(
        context: Context,
        categories: Int = Int.MAX_VALUE,
        accountName: String? = null,
        ready: Boolean = true,
    ) = QuickAddWidgetData(
        type = TransactionType.EXPENSE,
        categories = if (ready) seedExpenses(context).take(categories) else emptyList(),
        hasAccounts = ready,
        pinnedAccountId = accountName?.let { 1L },
        pinnedAccountName = accountName,
    )

    /**
     * A month as the month widgets read it: mid-October, an overall budget of
     * 1,500 with 960 spent and 410 still safe to spend, and the gym renewing on
     * Friday. [amounts] false is the app lock on; [budget] false is a reader
     * with no monthly budget.
     */
    fun monthData(
        amounts: Boolean = true,
        budget: Boolean = true,
        over: Boolean = false,
        ready: Boolean = true,
        nextName: String = "Gym",
    ) = MonthWidgetData(
        today = LocalDate.of(2026, 10, 16),
        currency = Currency.getInstance("EUR"),
        hasAccounts = ready,
        budget = if (!budget) {
            null
        } else if (over) {
            MonthBudget(
                limit = BigDecimal("1500.00"),
                spent = BigDecimal("1545.20"),
                fraction = 1.03f,
                level = BudgetLevel.OVER,
                safeToSpend = BigDecimal("-162.40"),
                perDay = null,
            )
        } else {
            MonthBudget(
                limit = BigDecimal("1500.00"),
                spent = BigDecimal("960.00"),
                fraction = 0.64f,
                level = BudgetLevel.UNDER,
                safeToSpend = BigDecimal("410.00"),
                perDay = BigDecimal("25.62"),
            )
        },
        spent = BigDecimal("960.00"),
        next = NextCharge(nextName, LocalDate.of(2026, 10, 23), BigDecimal("39.90"), Currency.getInstance("EUR")),
        amountsAllowed = amounts,
    )

    /** A month widget inflated and laid out at the granted size, as [inflate] does for the quick-add one. */
    fun inflateMonth(
        context: Context,
        kind: MonthWidgetKind,
        data: MonthWidgetData,
        width: Float,
        height: Float,
        stretch: Float = 1f,
        config: MonthWidgetConfig = MonthWidgetConfig(),
    ): View {
        val density = context.resources.displayMetrics.density
        val sizes = if (kind == MonthWidgetKind.RING) RingSizes else WordsSizes
        val breakpoint = pickBreakpoint(sizes, width, height)
        val views = MonthWidgetRenderer.render(context, kind, data, config, palette(), breakpoint)
        val host = FrameLayout(context)
        host.addView(views.apply(context, host))
        if (stretch != 1f) texts(host).forEach { it.textScaleX = stretch }
        val w = (width * density).roundToInt()
        val h = (height * density).roundToInt()
        host.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, w, h)
        return host
    }

    /**
     * The breakpoint a launcher applies for a granted size: the one that fits
     * (both sides within the grant) and is largest, else the smallest there is.
     */
    fun pickBreakpoint(sizes: List<WidgetSize>, width: Float, height: Float): WidgetSize {
        val fitting = sizes.filter { it.width <= width + FIT_SLACK && it.height <= height + FIT_SLACK }
        return fitting.maxByOrNull { it.width * it.height } ?: sizes.minBy { it.width * it.height }
    }

    /** The widget inflated and laid out at the granted size, [stretch] widening every line. */
    fun inflate(
        context: Context,
        data: QuickAddWidgetData,
        sizes: List<WidgetSize>,
        width: Float,
        height: Float,
        stretch: Float = 1f,
    ): View {
        val density = context.resources.displayMetrics.density
        val breakpoint = pickBreakpoint(sizes, width, height)
        val views = WidgetRenderer.render(context, 1, data, palette(), breakpoint)
        val host = FrameLayout(context)
        host.addView(views.apply(context, host))
        if (stretch != 1f) texts(host).forEach { it.textScaleX = stretch }
        val w = (width * density).roundToInt()
        val h = (height * density).roundToInt()
        host.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, w, h)
        return host
    }

    fun draw(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    fun texts(root: View): List<TextView> {
        val found = mutableListOf<TextView>()
        fun walk(view: View) {
            if (view is TextView) found += view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return found
    }

    /**
     * What the launcher would cut: a line ellipsized or wider than its room, a
     * text taller than its box, a visible view outside the card. A category
     * name the user typed is theirs and may be long, so [allowEllipsis] says
     * which texts may end in an ellipsis by design.
     */
    fun cuts(root: View, allowEllipsis: (TextView) -> Boolean = { false }): List<String> {
        val found = mutableListOf<String>()
        val card = Rect(0, 0, root.width, root.height)
        fun walk(view: View) {
            if (view.visibility != View.VISIBLE) return
            val rect = boundsIn(root, view)
            if (view.width > 0 && view.height > 0 && !card.contains(rect)) {
                val what = (view as? TextView)?.text?.let { "«$it»" } ?: view.javaClass.simpleName
                found += "$what outside the card (${rect.toShortString()} of ${card.toShortString()})"
            }
            if (view is TextView) textCut(view, allowEllipsis(view))?.let { found += it }
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return found
    }

    private fun boundsIn(root: View, view: View): Rect {
        var x = 0
        var y = 0
        var v: View? = view
        while (v != null && v !== root) {
            x += v.left
            y += v.top
            v = v.parent as? View
        }
        return Rect(x, y, x + view.width, y + view.height)
    }

    /** How [text] is cut, or null when every line of it is whole and in its box. */
    private fun textCut(text: TextView, mayEllipsize: Boolean): String? {
        val layout: Layout = text.layout ?: return null
        if (text.text.isEmpty()) return null
        val density = text.resources.displayMetrics.density
        val lines = 0 until layout.lineCount
        val room = text.width - text.totalPaddingLeft - text.totalPaddingRight
        val box = text.height - text.totalPaddingTop - text.totalPaddingBottom
        val ellipsized = lines.any { layout.getEllipsisCount(it) > 0 }
        val wide = lines.any { line ->
            val visible = text.text.subSequence(layout.getLineStart(line), layout.getLineVisibleEnd(line))
            Layout.getDesiredWidth(visible, text.paint) > room + 1
        }
        val how = "(%d lines, %.1f dp tall in %.1f dp)".format(layout.lineCount, layout.height / density, box / density)
        val cut = when {
            ellipsized -> if (mayEllipsize) null else "ellipsized"
            layout.lineCount > text.maxLines -> "loses lines past its ${text.maxLines}"
            wide -> "clipped"
            layout.height > box + 1 -> "taller than its box"
            else -> null
        }
        return cut?.let { "«${text.text}» $it $how" }
    }

    /** Cards on a stand-in wallpaper, with a caption under each, a few to a row. */
    class Board(private val context: Context, private val widthDp: Int = 860) {
        private val rows = mutableListOf<List<Pair<String, Bitmap>>>()

        fun row(vararg cards: Pair<String, Bitmap>) = apply { rows += cards.toList() }

        fun draw(): Bitmap {
            val d = context.resources.displayMetrics.density
            val gap = 16 * d
            val caption = 14 * d
            val height = rows.sumOf { row -> row.maxOf { it.second.height }.toDouble() + caption + gap } + gap
            val bitmap = Bitmap.createBitmap((widthDp * d).roundToInt(), height.roundToInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    bitmap.width.toFloat(),
                    bitmap.height.toFloat(),
                    intArrayOf(Color.rgb(31, 78, 90), Color.rgb(217, 137, 91)),
                    null,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 11 * d
            }
            var y = gap
            rows.forEach { row ->
                var x = gap
                row.forEach { (name, card) ->
                    canvas.drawText(name, x, y + 11 * d, label)
                    canvas.drawBitmap(card, x, y + caption, null)
                    x += card.width + gap
                }
                y += row.maxOf { it.second.height } + caption + gap
            }
            return bitmap
        }
    }

    fun Bitmap.saveTo(dir: File, name: String) {
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A grant measured in whole dp can miss a breakpoint by rounding alone. */
    private const val FIT_SLACK = 0.5f
}
