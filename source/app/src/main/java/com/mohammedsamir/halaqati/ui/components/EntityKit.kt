package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.ui.theme.Entity
import com.mohammedsamir.halaqati.ui.theme.EntityColors

/**
 * RC104: the one ornament for anything that names a halaqa, a teacher or a supervisor. A thin girih band (the same motif and
 * strip level as the top-bar band) on the entity's own tone from [EntityColors], so a halaqa is always green, a teacher teal
 * and a supervisor violet, and each carries the Islamic geometric identity. Decorative only (no semantics, no content).
 */
@Composable
fun EntityBand(entity: Entity, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val tone = EntityColors.of(entity)
    Box(modifier.fillMaxWidth().height(height).background(tone.container).nourPattern(PatternLevel.Strip, tone.accent)) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(tone.accent.copy(alpha = .35f)))
    }
}

/** The same band as an inset, rounded rule for padded containers where a full-bleed band would not reach the card edge. */
@Composable
fun EntityRule(entity: Entity, modifier: Modifier = Modifier) {
    EntityBand(entity, modifier.clip(RoundedCornerShape(4.dp)), height = 6.dp)
}
