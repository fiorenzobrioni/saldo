package com.callbackdev.saldo.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals

/**
 * Draws the app's category glyphs as bitmaps, because Glance renders through
 * `RemoteViews` and cannot take a Compose `ImageVector`.
 *
 * Rather than shipping a second, hand-maintained set of vector drawables (which
 * would silently drift from [CategoryVisuals] the first time an icon is added),
 * the icon is rasterized at runtime from the very same [ImageVector] the app
 * draws: one mapping, no drift. Material icons are flat 24x24 fills, so walking
 * the vector tree and filling each path is enough; group transforms are honored
 * anyway so an icon that grows one keeps rendering.
 *
 * Every glyph is rasterized as an *alpha mask* and colored at the
 * `RemoteViews` level with a day/night tint, never baked. That is what lets a
 * widget flip with the system theme without waking the app: the launcher
 * re-resolves the tint on its own, while a baked color would stay whatever
 * theme the bitmap was drawn under. It also collapses the cache to one bitmap
 * per icon and size, whatever palette is in fashion.
 *
 * If a vector ever fails to rasterize the image comes back as an empty mask: a
 * widget must never crash or show a hole.
 */
object CategoryIconBitmaps {

    /** 108/72: the adaptive icon canvas over its safe zone. */
    private const val SAFE_ZONE_SCALE = 1.5f

    /**
     * Sized in bytes rather than entries: an entry-counted cache holds 64
     * bitmaps whether they are 32px masks or 300px marks, so its real memory
     * was whatever the biggest mix happened to be.
     */
    private const val CACHE_BYTES = 512 * 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * The glyph as an alpha-only mask ([Bitmap.Config.ALPHA_8], a quarter of
     * the bytes of ARGB), meant to be tinted by the `Image` that shows it: the
     * tint's `SRC_IN` uses only the destination alpha, so the mask needs no
     * color channels of its own. The squircle behind it is not here on
     * purpose: the wash is a Glance background, so only the glyph pixels ride
     * the Binder transaction, around a third of a full tile bitmap - with four
     * columns of tiles per row that margin is what keeps the update payload
     * comfortably under the ceiling.
     */
    fun glyph(vector: ImageVector, sizePx: Int): Bitmap =
        cached("glyph|${vector.name}|$sizePx") {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ALPHA_8)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
            // A malformed vector must not take the widget down with it: the
            // image degrades to nothing and the tile stays its tinted wash.
            runCatching {
                canvas.scale(sizePx / vector.viewportWidth, sizePx / vector.viewportHeight)
                drawNode(canvas, paint, vector.root)
            }
            bitmap
        }

    /**
     * The app mark for the shortcut button, drawn past its own safe zone.
     *
     * An adaptive icon's foreground keeps its artwork inside the inner 72 of a
     * 108 canvas, so drawn at face value it lands at two thirds of the button
     * and reads as a stray small thing beside two large ones. Rendering it at
     * [SAFE_ZONE_SCALE] and letting the margin fall outside the bitmap gives
     * back the mark at the size the button is actually offering.
     *
     * Drawn through the platform's own drawable rather than through the vector
     * walker above, which flattens everything to one colour: this artwork has
     * gradients and is meant to keep them - which is also why it is the one
     * bitmap here that is not a tintable mask.
     */
    fun appMark(context: Context, resId: Int, sizePx: Int): Bitmap =
        cached("appmark|$resId|$sizePx") {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            context.getDrawable(resId)?.let { drawable ->
                val side = (sizePx * SAFE_ZONE_SCALE).toInt()
                val offset = (sizePx - side) / 2
                drawable.setBounds(offset, offset, offset + side, offset + side)
                drawable.draw(canvas)
            }
            bitmap
        }

    private fun cached(key: String, build: () -> Bitmap): Bitmap =
        cache.get(key) ?: build().also { cache.put(key, it) }

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
