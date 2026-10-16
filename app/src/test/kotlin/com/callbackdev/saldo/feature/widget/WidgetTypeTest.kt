package com.callbackdev.saldo.feature.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The widgets' type scale: every fixed size a card prints in is a step of
 * [WidgetType]. A `RemoteViews` layout names its sizes in XML, so the test reads
 * the XML: the placed widgets' layouts and the picker's previews.
 */
class WidgetTypeTest {

    /** Unit tests run from the module directory. */
    private val res = File("src/main/res")

    private val layouts: List<File> = File(res, "layout").listFiles().orEmpty()
        .filter { it.name.startsWith("widget_") || it.name.endsWith("_widget_preview.xml") }
        .sortedBy { it.name }

    private val dimens: Map<String, Float> = Regex("""<dimen name="(widget_type_\w+)">([\d.]+)sp</dimen>""")
        .findAll(File(res, "values/widget_type.xml").readText())
        .associate { it.groupValues[1] to it.groupValues[2].toFloat() }

    @Test
    fun `the widget layouts are the ones the cards and the picker draw`() {
        val names = layouts.map { it.name }.toSet()
        listOf(
            "widget_quick_actions.xml",
            "widget_quick_grid_narrow.xml",
            "widget_quick_grid_wide.xml",
            "widget_quick_setup.xml",
            "quick_add_widget_preview.xml",
            "widget_month_words.xml",
            "widget_month_ring.xml",
            "widget_month_ring_square.xml",
            "month_words_widget_preview.xml",
            "month_ring_widget_preview.xml",
        ).forEach { assertTrue(it in names, "$it is not read") }
    }

    @Test
    fun `every widget text size names a step of the scale`() {
        val sizes = Regex("""android:textSize="([^"]+)"""")
        layouts.forEach { layout ->
            sizes.findAll(layout.readText()).forEach { match ->
                val size = match.groupValues[1]
                val name = size.removePrefix("@dimen/")
                assertTrue(
                    size.startsWith("@dimen/") && name in dimens,
                    "${layout.name}: textSize $size is not a step of the widget scale",
                )
            }
        }
    }

    @Test
    fun `every step dimen is its step`() {
        val steps = mapOf(
            "widget_type_caption" to WidgetType.CAPTION,
            "widget_type_label" to WidgetType.LABEL,
            "widget_type_body" to WidgetType.BODY,
            "widget_type_title" to WidgetType.TITLE,
            "widget_type_display" to WidgetType.DISPLAY,
            "widget_type_hero" to WidgetType.HERO,
        )
        assertTrue(dimens.isNotEmpty(), "No widget_type dimen found")
        dimens.forEach { (name, sp) ->
            val step = steps[name]
            assertEquals(step, sp, "$name is $sp sp")
        }
        assertEquals(steps.values.toSet(), WidgetType.steps)
    }

    @Test
    fun `the height budget of a label line follows the label step`() {
        // 1.3275 em: the system face's line with its font padding, measured.
        assertEquals(kotlin.math.ceil(WidgetType.LABEL * LineWithPaddingEm).toInt(), LabelLineDp)
    }

    private companion object {
        const val LineWithPaddingEm = 1.3275f
    }
}
