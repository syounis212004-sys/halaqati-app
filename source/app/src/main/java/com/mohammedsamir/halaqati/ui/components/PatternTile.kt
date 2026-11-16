package com.mohammedsamir.halaqati.ui.components

import android.util.LruCache
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nour performance: the geometric ornaments used to be drawn with hundreds of drawPath calls on every draw pass (and two Path
 * objects were rebuilt each time). Now one repeating tile is rendered ONCE into a bitmap (cached by size/colour) and every draw is
 * a single drawRect with an image shader, so scrolling and ripples no longer repaint the lattice path by path.
 */
private object TileCache {
    val cache = object : LruCache<String, ImageBitmap>(24) {}
}

private fun starPath(r: Float): Path = Path().apply {
    for (i in 0 until 16) {
        val ang = Math.toRadians(i * 22.5 - 90.0)
        val rad = if (i % 2 == 0) r else r * 0.765f
        val x = (rad * cos(ang)).toFloat()
        val y = (rad * sin(ang)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun renderTile(key: String, sidePx: Int, density: Density, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): ImageBitmap {
    TileCache.cache.get(key)?.let { return it }
    val bmp = ImageBitmap(sidePx.coerceAtLeast(2), sidePx.coerceAtLeast(2))
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bmp), Size(sidePx.toFloat(), sidePx.toFloat()), draw)
    TileCache.cache.put(key, bmp)
    return bmp
}

/** Girih: octagram + rotated octagram on a square grid of side [cell], small squares on the grid centres. */
fun Modifier.girihTiled(color: Color, alpha: Float, cell: Dp): Modifier = this.clipToBounds().drawWithCache {
    val c = cell.toPx()
    val side = c.toInt().coerceAtLeast(8)
    val sc = side.toFloat()
    val strokePx = 1.2.dp.toPx()
    val key = "g:$side:${strokePx}:${color.value}:$alpha"
    val tile = renderTile(key, side, this) {
        val r = sc * 0.5f
        val star = starPath(r)
        val sq = Path().apply { val h = sc * 0.2f; moveTo(0f, -h); lineTo(h, 0f); lineTo(0f, h); lineTo(-h, 0f); close() }
        val stroke = Stroke(width = strokePx)
        val col = color.copy(alpha = alpha)
        val col2 = color.copy(alpha = alpha * .55f)
        for (cx in listOf(0f, sc)) for (cy in listOf(0f, sc)) translate(cx, cy) {
            drawPath(star, col, style = stroke)
            rotate(22.5f, Offset.Zero) { drawPath(star, col2, style = stroke) }
        }
        translate(sc / 2f, sc / 2f) { drawPath(sq, col, style = stroke) }
    }
    val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind { drawRect(brush, size = size) }
}

/** Lattice of eight-pointed stars with rows shifted by half a cell (the original latticeOverlay look). */
fun Modifier.latticeTiled(color: Color, alpha: Float, cell: Dp): Modifier = this.clipToBounds().drawWithCache {
    val c = cell.toPx()
    val side = c.toInt().coerceAtLeast(8)
    val sc = side.toFloat()
    val strokePx = 1.dp.toPx()
    val key = "l:$side:${strokePx}:${color.value}:$alpha"
    val tile = renderTile(key, side, this) {
        val star = starPath(sc * 0.36f)
        val stroke = Stroke(width = strokePx)
        val col = color.copy(alpha = alpha)
        for (cx in listOf(0f, sc)) for (cy in listOf(0f, sc)) translate(cx, cy) { drawPath(star, col, style = stroke) }
        translate(sc / 2f, sc / 2f) { drawPath(star, col, style = stroke) }
    }
    val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind { drawRect(brush, size = size) }
}
