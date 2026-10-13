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
 * Every form of both widgets at every reference grant, drawn to
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
    fun bar() = board("widget-bar", ActionSizes, listOf(85f), WidgetRendering.data(context))

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
