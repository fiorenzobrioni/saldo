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
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn

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

    /** Outline weight of an action tile, as a fraction of the side (2dp at 40dp). */
    private const val STROKE_RATIO = 0.05f

    private const val CACHE_ENTRIES = 64

    private val cache = LruCache<String, Bitmap>(CACHE_ENTRIES)

    /**
     * A category tile: filled squircle in the category color plus its glyph.
     * [colorRgb] is the stored 0xRRGGBB category color, [sizePx] the side in
     * pixels.
     */
    fun categoryTile(iconKey: String?, colorRgb: Int?, sizePx: Int): Bitmap {
        val background = CategoryVisuals.color(colorRgb)
        return cached("category|$iconKey|${background.toArgb()}|$sizePx") {
            draw(
                vector = CategoryVisuals.icon(iconKey),
                fillArgb = background.toArgb(),
                strokeArgb = null,
                glyphArgb = contentColorOn(background).toArgb(),
                sizePx = sizePx,
            )
        }
    }

    /**
     * An action tile: the same squircle drawn as an *outline* in a theme color,
     * so the eye reads it as a control rather than as one more category. Used by
     * the "more" entry, which sits in the same grid as the categories and must
     * not be mistaken for one.
     */
    fun actionTile(vector: ImageVector, stroke: ComposeColor, glyph: ComposeColor, sizePx: Int): Bitmap =
        cached("action|${vector.name}|${stroke.toArgb()}|${glyph.toArgb()}|$sizePx") {
            draw(
                vector = vector,
                fillArgb = null,
                strokeArgb = stroke.toArgb(),
                glyphArgb = glyph.toArgb(),
                sizePx = sizePx,
            )
        }

    private fun cached(key: String, build: () -> Bitmap): Bitmap =
        cache.get(key) ?: build().also { cache.put(key, it) }

    private fun draw(
        vector: ImageVector,
        fillArgb: Int?,
        strokeArgb: Int?,
        glyphArgb: Int,
        sizePx: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = sizePx * CORNER_PERCENT
        if (fillArgb != null) {
            paint.color = fillArgb
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, paint)
        }
        if (strokeArgb != null) {
            // Inset by half the stroke so the outline lands inside the tile
            // instead of being clipped at the bitmap edge.
            val width = sizePx * STROKE_RATIO
            val half = width / 2f
            paint.color = strokeArgb
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width
            canvas.drawRoundRect(
                RectF(half, half, sizePx - half, sizePx - half),
                radius - half,
                radius - half,
                paint,
            )
        }

        val glyphSize = sizePx * GLYPH_RATIO
        val inset = (sizePx - glyphSize) / 2f
        canvas.save()
        canvas.translate(inset, inset)
        paint.color = glyphArgb
        paint.style = Paint.Style.FILL
        // A malformed vector must not take the widget down with it: the tile
        // degrades to the bare squircle instead.
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
