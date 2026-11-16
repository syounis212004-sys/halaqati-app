package com.mohammedsamir.halaqati.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.ui.theme.LocalReducedMotion
import com.mohammedsamir.halaqati.ui.theme.calmContentSize
import com.mohammedsamir.halaqati.ui.theme.motionMillis
import com.mohammedsamir.halaqati.ui.theme.statusColors

/**
 * RC27.20: one shape for every dashboard "insight" list card (attention, absence streak, halaqa comparison...).
 * An icon badge that breathes (static under reduced motion), an animated count pill, a chevron that turns when the card
 * opens, and a body that grows smoothly. Text always sits in its own column: nothing is drawn over a line of text.
 */
@Composable
fun InsightCardShell(
    kind: String,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    summary: String = "",
    count: Int? = null,
    expanded: Boolean? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val (bg, fg) = if (kind == "neutral") MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant else statusColors(kind)
    val reduced = LocalReducedMotion.current
    val breathe by rememberInfiniteTransition(label = "insightBreathe").animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "insightBreatheScale",
    )
    val scale = if (reduced) 1f else breathe
    val turn by animateFloatAsState(if (expanded == true) 180f else 0f, tween(motionMillis(260)), label = "insightChevron")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .halaqatiEnter()
            .then(if (onClick != null) Modifier.halaqatiPressable { onClick() } else Modifier),
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp).calmContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(42.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape)
                        .background(fg.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (summary.isNotBlank()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (expanded == true) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (count != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(fg.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(animatedCountText(count.toString()), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (expanded != null) {
                    Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(24.dp).rotate(turn))
                }
            }
            content?.invoke(this)
        }
    }
}

/** A labelled value with a thin bar that fills when it appears (e.g. attendance 82%). [fraction] null = no data. */
@Composable
fun InsightMeter(label: String, value: String, fraction: Float?, modifier: Modifier = Modifier) {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(fraction) { target = (fraction ?: 0f).coerceIn(0f, 1f) }
    val filled by animateFloatAsState(target, tween(motionMillis(850), easing = FastOutSlowInEasing), label = "insightMeter")
    val color = when {
        fraction == null -> MaterialTheme.colorScheme.outline
        fraction >= 0.75f -> MaterialTheme.colorScheme.primary
        fraction >= 0.5f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
            Box(Modifier.fillMaxWidth(filled).height(6.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}
