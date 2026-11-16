package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * RC47: the app's one decorative motif, drawn with Canvas (no image assets): an eight-point star built from two
 * overlapping squares, tiled. It is only ever painted very faintly behind hero surfaces, never behind body text.
 */
fun Modifier.islamicPattern(color: Color = Color.White, alpha: Float = .10f, cell: Dp = 64.dp): Modifier = drawBehind {
    val step = cell.toPx()
    if (step <= 0f) return@drawBehind
    val r = step * .34f
    val stroke = Stroke(width = 1.1.dp.toPx())
    val c = color.copy(alpha = alpha)
    var y = 0f
    var row = 0
    while (y < size.height + step) {
        var x = if (row % 2 == 0) 0f else step / 2f
        while (x < size.width + step) {
            val path = Path()
            for (turn in 0..1) {
                for (k in 0 until 4) {
                    val a = Math.toRadians((turn * 45 + k * 90 + 45).toDouble())
                    val px = x + (r * Math.cos(a)).toFloat() * 1.41f
                    val py = y + (r * Math.sin(a)).toFloat() * 1.41f
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            drawPath(path, c, style = stroke)
            drawCircle(c, radius = r * .35f, center = Offset(x, y), style = stroke)
            x += step
        }
        y += step * .5f
        row++
    }
}

/** Emerald hero fill: the brand primary deepening towards one corner. */
@Composable
fun heroBrush(primary: Color): Brush = Brush.linearGradient(
    listOf(lerp(primary, Color.Black, .32f), primary, lerp(primary, Color.White, .10f)),
)

/** A hero surface background: gradient plus the faint pattern. */
fun Modifier.heroBackground(brush: Brush): Modifier = this
    .drawBehind { drawRect(brush) }
    // RC95: the cached girih tile (one bitmap shader) at one strength for every hero, instead of re-drawing paths.
    .girih(Color.White, .14f, 44.dp)

@Composable
fun PatternBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.islamicPattern()) { content() }
}
