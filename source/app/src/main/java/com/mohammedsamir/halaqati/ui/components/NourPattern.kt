package com.mohammedsamir.halaqati.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's one geometric pattern (the girih lattice in Girih.kt) at four fixed strengths, so every surface that carries it
 * uses the same tile size, line alpha and "quiet centre" instead of hand-picked numbers per screen.
 *
 * [quiet] is the alpha of a soft scrim over the middle band of the surface (edges stay untouched): the lattice stays visible
 * at the corners and in empty areas, and fades behind the text that sits in the middle.
 */
enum class PatternLevel(val alpha: Float, val cell: Dp, val quiet: Float) {
    /** Large hero surfaces (screen headers, the home hero). */
    Hero(alpha = .12f, cell = 44.dp, quiet = .10f),
    /** Card headers that carry a title, chips and a menu. */
    Card(alpha = .08f, cell = 40.dp, quiet = .16f),
    /** Small tiles and narrow stripes with a number or an icon. */
    Tile(alpha = .10f, cell = 26.dp, quiet = 0f),
    /** Large quiet backgrounds (empty states, dialogs). */
    Subtle(alpha = .05f, cell = 56.dp, quiet = 0f),
}

/** The pattern at a [PatternLevel]. Draw it on top of a gradient/colour background, behind the content. */
fun Modifier.nourPattern(level: PatternLevel, color: Color = Color.White): Modifier {
    val base = this.girih(color, level.alpha, level.cell)
    if (level.quiet <= 0f) return base
    return base.drawBehind {
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                .22f to Color.Black.copy(alpha = level.quiet),
                .78f to Color.Black.copy(alpha = level.quiet),
                1f to Color.Transparent,
            ),
        )
    }
}
