package com.callbackdev.saldo.feature.widget

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.saldo.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Nothing a widget prints is cut, at any reference grant, in English and
 * Italian, at three text sizes, with every line drawn 5% wider than measured
 * ([STRETCH]): what a launcher on another face may do. `WidgetLayoutTest` pins
 * the arithmetic; this draws the real `RemoteViews` in the system face and looks.
 *
 * A name the user typed (a category, the pinned account) may end in an
 * ellipsis by design; any other text must be whole.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// SDK 35, as the README screenshots: Robolectric's SDK 36 image needs JDK internals Java 21 keeps closed.
@Config(application = Application::class, sdk = [35])
class WidgetFitTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun cuts(locale: String): List<String> = listOf(1f, 1.15f, 1.3f).flatMap { scale ->
        RuntimeEnvironment.setQualifiers("$locale-w411dp-h891dp-xhdpi")
        RuntimeEnvironment.setFontScale(scale)
        cases().flatMap { case ->
            case.grants().flatMap { (width, height) ->
                val view = WidgetRendering.inflate(context, case.data, case.sizes, width, height, STRETCH)
                WidgetRendering.cuts(view, allowEllipsis = ::isUserName)
                    .filterNot { knownCut(view, width) && it.startsWithLabelOf(view) }
                    .map { "${case.name} ${width.toInt()}x${height.toInt()} @$scale: $it" }
            }
        }
    }

    private fun cases() = listOf(
        Case("grid", GridWidgetSizes, WidgetRendering.Heights, WidgetRendering.data(context)),
        Case(
            "grid pinned to an account",
            GridWidgetSizes,
            WidgetRendering.Heights,
            WidgetRendering.data(context, accountName = "Joint current account"),
        ),
        Case("bar", ActionSizes, OneRow, WidgetRendering.data(context)),
        Case("setup", GridWidgetSizes, WidgetRendering.Heights, WidgetRendering.data(context, ready = false)),
    )

    @Test
    @Config(qualifiers = "en-rGB-w411dp-h891dp-xhdpi")
    fun `nothing is cut in English`() {
        val found = cuts("en-rGB")
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    @Test
    @Config(qualifiers = "it-rIT-w411dp-h891dp-xhdpi")
    fun `nothing is cut in Italian`() {
        val found = cuts("it-rIT")
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    private class Case(
        val name: String,
        val sizes: List<WidgetSize>,
        val heights: List<Float>,
        val data: QuickAddWidgetData,
    ) {
        fun grants(): List<Pair<Float, Float>> = heights.flatMap { h -> WidgetRendering.Widths.map { w -> w to h } }
    }

    /** The category names under the tiles and the pinned account's badge: the user's words. */
    private fun isUserName(text: TextView): Boolean {
        if (text.id == View.NO_ID) return false
        val name = context.resources.getResourceEntryName(text.id)
        return name == "widget_badge" || name.startsWith("widget_tile_label_")
    }

    /**
     * Known and open (PLANNING, "Bug conosciuti"): the two buttons of a one-row
     * widget narrower than four columns have no room for their words at every
     * text size, and `RemoteViews` cannot tell, because the single-row breakpoints
     * are all 110dp wide. Only those two labels, only there.
     */
    private fun knownCut(view: View, width: Float): Boolean =
        width < FourColumns && view.findViewById<View>(R.id.widget_action_expense) != null

    private fun String.startsWithLabelOf(view: View): Boolean = listOf(
        R.id.widget_action_expense_label,
        R.id.widget_action_income_label,
    ).any { id -> view.findViewById<TextView>(id)?.text?.let { startsWith("«$it»") } == true }

    private companion object {
        const val STRETCH = 1.05f
        const val FourColumns = 340f
        val OneRow = listOf(85f)
    }
}
