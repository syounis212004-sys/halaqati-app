package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * RC72: the classic eight-fold girih lattice used in Islamic ornament (see Hankin's analysis of "geometrical arabesques"):
 * a grid of eight-pointed stars ({8/2} octagrams) whose gaps are filled by small squares rotated 45 degrees, drawn as
 * thin interlacing outlines. The octagrams sit on a square grid of side [cell]; the squares sit on the grid corners.
 * It only paints behind content (low alpha) so text keeps its contrast.
 */
fun Modifier.girih(color: Color = Color.White, alpha: Float = .16f, cell: Dp = 44.dp): Modifier = girihTiled(color, alpha, cell)

/** A pair of saturated colours used for one gradient. Light and dark text on top is white, so each stays >= 3:1 against white. */
data class GradientPair(val a: Color, val b: Color) {
    fun brush(): Brush = Brush.linearGradient(listOf(a, b))
}

object HalaqatiGradients {
    val Emerald = GradientPair(Color(0xFF0B6E4F), Color(0xFF1FA37A))
    val Deep = GradientPair(Color(0xFF084F39), Color(0xFF0B6E4F))
    val Gold = GradientPair(Color(0xFF9A6B00), Color(0xFFD9A94B))
    val Blue = GradientPair(Color(0xFF1451A3), Color(0xFF3F8EE0))
    val Violet = GradientPair(Color(0xFF5B3FA3), Color(0xFF8E6BE0))
    val Orange = GradientPair(Color(0xFFB5471C), Color(0xFFE88A4B))
    val Teal = GradientPair(Color(0xFF0B6B7A), Color(0xFF2BB3C0))
    val Rose = GradientPair(Color(0xFF9C2F6B), Color(0xFFD1569A))
    val Red = GradientPair(Color(0xFFA81E1E), Color(0xFFE0584F))
    val Slate = GradientPair(Color(0xFF2F4A5C), Color(0xFF5C8097))
}

/** An icon whose own tile carries the gradient: gradient disc/squircle behind a white glyph. */
@Composable
fun GradientIcon(icon: ImageVector, pair: GradientPair, tile: Dp = 56.dp, glyph: Dp = 28.dp, circle: Boolean = true, modifier: Modifier = Modifier) {
    Box(
        modifier.size(tile).clip(if (circle) CircleShape else RoundedCornerShape(tile / 3)).background(pair.brush()),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(glyph)) }
}
