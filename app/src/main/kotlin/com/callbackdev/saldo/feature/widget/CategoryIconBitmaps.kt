package com.callbackdev.saldo.feature.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.graphics.Color as ComposeColor
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals

/**
 * Draws the app's category avatar as a bitmap, because Glance renders through
 * `RemoteViews` and cannot take a Compose `ImageVector`.
 *
 * Rather than shipping a second, hand-maintained set of vector drawables (which
 * would silently drift from [CategoryVisuals] the first time an icon is added),
 * the icon is rasterized at runtime from the very same [ImageVector] the app
 * draws: one mapping, no drift. Material icons are flat 24x24 fills, so walking
 * the vector tree and filling each path is enough; group transforms are honored
 * anyway so an icon that grows one keeps rendering.
 *
 * The tile is the squircle of `AvatarShape` (30% corner radius) filled with the
 * category color, with the glyph in the readable ink [contentColorOn] picks.
 * If a vector ever fails to rasterize the tile still comes back as the plain
 * colored squircle: a widget must never crash or show a hole.
 */
object CategoryIconBitmaps {

    /** 30% of the side, matching `AvatarShape`. */
    private const val CORNER_PERCENT = 0.30f

    /** The glyph occupies 55% of the tile, the same optical ratio as the in-app avatars. */
    private const val GLYPH_RATIO = 0.55f

    /** The 16% wash of `CategoryCell`, so a widget tile and an app tile are the same tile. */
    private const val TINT_ALPHA = 0.16f

    private const val CACHE_ENTRIES = 64

    private val cache = LruCache<String, Bitmap>(CACHE_ENTRIES)

    /**
     * A category tile, drawn exactly as the app draws an unselected category
     * cell: the squircle tinted with the category color at [TINT_ALPHA] and the
     * glyph in that same color at full strength. The widget used to fill the
     * squircle and put a white glyph on it, which is how the app draws the
     * *selected* cell - so every tile looked selected and none of them looked
     * like the app.
     *
     * The tint keeps its alpha in the bitmap rather than being flattened onto a
     * background color, so a translucent widget composites correctly over the
     * wallpaper.
     */
    fun categoryTile(iconKey: String?, colorRgb: Int?, sizePx: Int): Bitmap {
        val color = CategoryVisuals.color(colorRgb)
        return cached("category|$iconKey|${color.toArgb()}|$sizePx") {
            draw(vector = CategoryVisuals.icon(iconKey), color = color, sizePx = sizePx)
        }
    }

    /**
     * An action tile in the same language as the categories, for the "open the
     * app" entry. It reads as its own thing through the brand color, the glyph
     * and its label rather than through a different shape.
     */
    fun actionTile(vector: ImageVector, color: ComposeColor, sizePx: Int): Bitmap =
        cached("action|${vector.name}|${color.toArgb()}|$sizePx") {
            draw(vector = vector, color = color, sizePx = sizePx)
        }

    private fun cached(key: String, build: () -> Bitmap): Bitmap =
        cache.get(key) ?: build().also { cache.put(key, it) }

    private fun draw(vector: ImageVector, color: ComposeColor, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = sizePx * CORNER_PERCENT
        paint.color = color.copy(alpha = TINT_ALPHA).toArgb()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, paint)

        val glyphSize = sizePx * GLYPH_RATIO
        val inset = (sizePx - glyphSize) / 2f
        canvas.save()
        canvas.translate(inset, inset)
        paint.color = color.toArgb()
        // A malformed vector must not take the widget down with it: the tile
        // degrades to the bare tinted squircle instead.
        runCatching {
            val scale = glyphSize / vector.viewportWidth
            canvas.scale(scale, scale)
            drawNode(canvas, paint, vector.root)
        }
        canvas.restore()
        return bitmap
    }

    private fun drawNode(canvas: Canvas, paint: Paint, node: VectorNode) {
        when (node) {
            is VectorPath -> drawPath(canvas, paint, node)
            is VectorGroup -> {
                canvas.save()
                canvas.concat(node.matrix())
                node.forEach { drawNode(canvas, paint, it) }
                canvas.restore()
            }
        }
    }

    private fun drawPath(canvas: Canvas, paint: Paint, node: VectorPath) {
        // Outlined Material icons are fills; a path that only strokes would come
        // out invisible, and none of the mapped icons uses one.
        if (node.fill == null && node.stroke != null) return
        val path = node.pathData.toPath().asAndroidPath()
        path.fillType = if (node.pathFillType == PathFillType.EvenOdd) {
            android.graphics.Path.FillType.EVEN_ODD
        } else {
            android.graphics.Path.FillType.WINDING
        }
        canvas.drawPath(path, paint)
    }

    /** Group transform in the order the vector spec applies it: scale, rotate, translate, all about the pivot. */
    private fun VectorGroup.matrix(): Matrix = Matrix().apply {
        postTranslate(-pivotX, -pivotY)
        postScale(scaleX, scaleY)
        postRotate(rotation)
        postTranslate(pivotX + translationX, pivotY + translationY)
    }

}
