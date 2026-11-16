package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * RC67: a faint tiling of eight-pointed stars (the geometric lattice of Islamic ornament) drawn behind a band.
 * It only paints; it never carries information, so it stays at a low alpha. Nour: drawn from one cached tile (see PatternTile.kt).
 */
fun Modifier.latticeOverlay(color: Color = Color.White, alpha: Float = .09f, cell: Dp = 30.dp): Modifier = latticeTiled(color, alpha, cell)

/** A thin lattice strip that closes the top bar of every page. */
@Composable
fun LatticeStrip(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(9.dp).latticeOverlay(MaterialTheme.colorScheme.primary, alpha = .5f, cell = 12.dp))
        HorizontalDivider(Modifier.align(androidx.compose.ui.Alignment.BottomStart), thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = .25f))
    }
}
