package com.mohammedsamir.halaqati.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * RC52: segmented control with a sliding indicator and a clear border. The chosen pill glides to the new segment
 * (spring) instead of jumping, the track has a visible outline, and every segment has a 44dp touch height.
 */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val track = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(12.dp)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, track)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f), track)
            .padding(4.dp),
    ) {
        val count = options.size.coerceAtLeast(1)
        val segment = maxWidth / count
        val x by animateDpAsState(segment * selected.coerceIn(0, count - 1), spring(dampingRatio = .82f, stiffness = 520f), label = "seg-x")
        Box(
            Modifier.offset(x = x).width(segment).height(44.dp)
                .shadow(2.dp, pill)
                .background(MaterialTheme.colorScheme.surface, pill)
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = .55f), pill),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val on = index == selected
                val tint by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "seg-tint")
                Box(
                    Modifier.weight(1f).height(44.dp)
                        .clip(pill)
                        .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onSelect(index) }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = tint,
                    )
                }
            }
        }
    }
}

/** RC52: content that fades and slides in the direction of the chosen tab; the old content fades out first. */
@Composable
fun <T> TabContentTransition(target: T, order: (T) -> Int, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    androidx.compose.animation.AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            val dir = if (order(targetState) >= order(initialState)) 1 else -1
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240, delayMillis = 60)) +
                androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { it / 10 * -dir }) togetherWith
                (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
                    androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(160)) { it / 14 * dir })
        },
        label = "tab-content",
    ) { content(it) }
}

/** RC50: a light selectable pill (no outline) for month and period pickers. */
@Composable
fun PillChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, label = "pill")
    Box(
        modifier.background(bg, RoundedCornerShape(999.dp)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
