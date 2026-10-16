package com.callbackdev.saldo.feature.widget

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.saldo.feature.widget.WidgetRendering.saveTo
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every form of every widget at the reference grants, drawn to
 * `app/build/screenshots` to be looked at after a change to a card. Not an
 * assertion ([WidgetFitTest] and `WidgetLayoutTest` are), and only on request:
 * `./gradlew testDebugUnitTest -PwidgetGallery --tests "*.WidgetGallery"`.
 * Each card is captioned with its grant and the breakpoint the launcher picks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rGB-w411dp-h891dp-xhdpi")
class WidgetGallery {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val out = File("build/screenshots")

    @Before
    fun requested() {
        assumeTrue("Run with -PwidgetGallery", System.getProperty("saldo.widgetGallery") != null)
    }

    private fun board(name: String, sizes: List<WidgetSize>, heights: List<Float>, data: QuickAddWidgetData) {
        val board = WidgetRendering.Board(context)
        heights.forEach { height ->
            val cards = WidgetRendering.Widths.map { width ->
                val pick = WidgetRendering.pickBreakpoint(sizes, width, height)
                "${width.toInt()}x${height.toInt()} <- $pick" to
                    WidgetRendering.draw(WidgetRendering.inflate(context, data, sizes, width, height))
            }
            board.row(*cards.toTypedArray())
        }
        board.draw().saveTo(out, name)
    }

    @Test
    fun grid() = board("widget-grid", GridWidgetSizes, WidgetRendering.Heights, WidgetRendering.data(context))

    @Test
    fun gridFewCategories() = board(
        "widget-grid-few",
        GridWidgetSizes,
        WidgetRendering.Heights,
        WidgetRendering.data(context, categories = 5),
    )

    @Test
    fun oneRow() = board("widget-one-row", GridWidgetSizes, listOf(85f), WidgetRendering.data(context))

    private fun monthBoard(name: String, kind: MonthWidgetKind, data: MonthWidgetData) {
        val board = WidgetRendering.Board(context)
        listOf(85f, 189f, 293f).forEach { height ->
            val cards = WidgetRendering.Widths.map { width ->
                "${width.toInt()}x${height.toInt()}" to
                    WidgetRendering.draw(WidgetRendering.inflateMonth(context, kind, data, width, height))
            }
            board.row(*cards.toTypedArray())
        }
        board.draw().saveTo(out, name)
    }

    @Test
    fun words() = monthBoard("widget-words", MonthWidgetKind.WORDS, WidgetRendering.monthData())

    @Test
    fun wordsLocked() = monthBoard("widget-words-locked", MonthWidgetKind.WORDS, WidgetRendering.monthData(amounts = false))

    @Test
    fun wordsNoBudget() = monthBoard("widget-words-no-budget", MonthWidgetKind.WORDS, WidgetRendering.monthData(budget = false))

    @Test
    fun ring() = monthBoard("widget-ring", MonthWidgetKind.RING, WidgetRendering.monthData())

    @Test
    fun ringOver() = monthBoard("widget-ring-over", MonthWidgetKind.RING, WidgetRendering.monthData(over = true))

    @Test
    fun ringLocked() = monthBoard("widget-ring-locked", MonthWidgetKind.RING, WidgetRendering.monthData(amounts = false))

    @Test
    fun ringNoBudget() = monthBoard("widget-ring-no-budget", MonthWidgetKind.RING, WidgetRendering.monthData(budget = false))

    @Test
    @Config(qualifiers = "it-rIT-w411dp-h891dp-xhdpi")
    fun wordsItalian() = monthBoard("widget-words-it", MonthWidgetKind.WORDS, WidgetRendering.monthData())

    @Test
    fun setup() = board(
        "widget-setup",
        GridWidgetSizes,
        WidgetRendering.Heights,
        WidgetRendering.data(context, ready = false),
    )

    @Test
    @Config(qualifiers = "it-rIT-w411dp-h891dp-xhdpi")
    fun gridItalian() = board(
        "widget-grid-it",
        GridWidgetSizes,
        WidgetRendering.Heights.take(3),
        WidgetRendering.data(context),
    )
}
