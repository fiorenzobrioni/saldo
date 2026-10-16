package com.callbackdev.saldo.feature.widget

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the launcher actually receives: one `RemoteViews` holding a layout per
 * breakpoint. The platform refuses more than 16 of them, by throwing, and the
 * provider swallows the throw (a broadcast must not crash the app), so a
 * widget past the limit silently stays on its initial layout: empty tiles,
 * never bound (13 Oct 2026, the grid at 21 breakpoints). Built here for every
 * provider at every text size the tests draw.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rGB-w411dp-h891dp-xhdpi")
class WidgetSizeMapTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `every provider's breakpoints fit in one RemoteViews`() {
        SaldoWidgetProvider.providers.forEach { (provider, kind) ->
            listOf(0.85f, 1f, 1.15f, 1.3f, 2f).forEach { scale ->
                val breakpoints = kind.sizes(scale)
                assertTrue(
                    "${provider.simpleName} at text $scale: ${breakpoints.size} breakpoints, the platform takes 16",
                    breakpoints.size <= MaxBreakpoints,
                )
                assertTrue(
                    "${provider.simpleName} at text $scale: two breakpoints share a size",
                    breakpoints.toSet().size == breakpoints.size,
                )
                // Throws past the platform's limit, as on a phone.
                val month = kind.month
                if (month == null) {
                    WidgetRenderer.sizeMap(
                        context = context,
                        appWidgetId = 1,
                        data = WidgetRendering.data(context),
                        palette = WidgetRendering.palette(),
                        sizes = breakpoints,
                    )
                } else {
                    MonthWidgetRenderer.sizeMap(
                        context = context,
                        kind = month,
                        data = WidgetRendering.monthData(),
                        config = MonthWidgetConfig(),
                        palette = WidgetRendering.palette(),
                        sizes = breakpoints,
                    )
                }
            }
        }
    }
}
