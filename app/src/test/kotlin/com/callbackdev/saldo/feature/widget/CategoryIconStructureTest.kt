package com.callbackdev.saldo.feature.widget

import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [CategoryIconBitmaps] rasterizes the app's own `ImageVector`s for the widget,
 * because Glance cannot draw one. Rasterizing needs a real Android canvas, so
 * the pixels are checked in an instrumented test; what runs here in CI is the
 * structural half: every mapped icon must be something the renderer can draw.
 *
 * This is the guard that fires when someone adds a 41st icon of a shape the
 * renderer does not handle - a stroke-only path, or a group transform - before
 * it ships as a blank tile on someone's home screen.
 */
class CategoryIconStructureTest {

    @Test
    fun `every mapped icon has fillable path data`() {
        CategoryVisuals.iconKeys.forEach { key ->
            val paths = CategoryVisuals.icon(key).root.paths()
            assertTrue(paths.isNotEmpty(), "Icon '$key' has no path to draw")
            paths.forEach { path ->
                assertTrue(
                    path.pathData.isNotEmpty(),
                    "Icon '$key' has a path with no nodes",
                )
                assertTrue(
                    path.fill != null || path.stroke == null,
                    "Icon '$key' has a stroke-only path, which the widget renderer skips",
                )
            }
        }
    }

    @Test
    fun `the fallback icon is drawable too`() {
        assertTrue(CategoryVisuals.icon("no-such-icon").root.paths().isNotEmpty())
    }

    @Test
    fun `every icon uses the square viewport the renderer scales by`() {
        CategoryVisuals.iconKeys.forEach { key ->
            val vector = CategoryVisuals.icon(key)
            assertEquals(
                vector.viewportWidth,
                vector.viewportHeight,
                "Icon '$key' is not square: the renderer scales both axes by the width",
            )
        }
    }

    @Test
    fun `no icon relies on a clip path, which the renderer does not apply`() {
        CategoryVisuals.iconKeys.forEach { key ->
            CategoryVisuals.icon(key).root.groups().forEach { group ->
                assertTrue(
                    group.clipPathData.isEmpty(),
                    "Icon '$key' has a clipped group, which the widget renderer would draw unclipped",
                )
            }
        }
    }

    private fun VectorGroup.paths(): List<VectorPath> = flatten().filterIsInstance<VectorPath>()

    private fun VectorGroup.groups(): List<VectorGroup> = flatten().filterIsInstance<VectorGroup>()

    private fun VectorGroup.flatten(): List<VectorNode> = flatMap { node ->
        when (node) {
            is VectorGroup -> listOf(node) + node.flatten()
            else -> listOf(node)
        }
    }
}
