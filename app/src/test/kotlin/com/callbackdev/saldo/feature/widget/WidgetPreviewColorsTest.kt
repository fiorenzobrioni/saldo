package com.callbackdev.saldo.feature.widget

import androidx.compose.ui.graphics.toArgb
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor
import com.callbackdev.saldo.core.designsystem.theme.widgetCardContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The picker previews and the un-bound widget bake the default card as a colour
 * resource, because a static layout cannot call Kotlin. This keeps the two
 * written copies of Chiaro's blue in step (Chiaro's and Passo's
 * `WidgetPreviewTest` do the same).
 */
class WidgetPreviewColorsTest {

    private val colors = File("src/main/res/values/colors.xml").readText()

    private fun resource(name: String): Int {
        val hex = Regex("""<color name="$name">#([0-9A-Fa-f]{8})</color>""").find(colors)?.groupValues?.get(1)
            ?: error("$name is not an #AARRGGBB colour in values/colors.xml")
        return hex.toLong(16).toInt()
    }

    @Test
    fun `the baked card is the Kotlin table's blue`() {
        assertEquals(widgetCardContainer(WidgetCardColor.BLUE).toArgb(), resource("widget_preview_card"))
        assertEquals(widgetCardContainer(WidgetCardColor.BLUE).toArgb(), resource("widget_preview_pill_ink"))
    }

    @Test
    fun `the baked inks are the white pair a card colour writes with`() {
        assertEquals(-1, resource("widget_preview_ink"))
        assertEquals(0xBFFFFFFF.toInt(), resource("widget_preview_ink_muted"))
    }
}
