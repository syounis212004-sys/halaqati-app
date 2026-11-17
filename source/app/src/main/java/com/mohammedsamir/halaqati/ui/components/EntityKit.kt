package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.ui.theme.Entity

/**
 * RC104: the one ornament for anything that names a halaqa, a teacher, a supervisor, a donor... The whole header (or the whole
 * small row) is a gradient in the entity's own colour with the girih pattern and white text, exactly like the halaqa management
 * card. The pairs are dark enough that white text keeps >= 4.5:1 in both themes.
 */
fun Entity.gradient(): GradientPair = when (this) {
    Entity.HALAQA -> GradientPair(Color(0xFF084F39), Color(0xFF0B6E4F))
    Entity.TEACHER -> GradientPair(Color(0xFF0B5B66), Color(0xFF0B7A87))
    Entity.SUPERVISOR -> GradientPair(Color(0xFF4B3290), Color(0xFF6A4DB8))
    Entity.DONOR -> GradientPair(Color(0xFF8A2548), Color(0xFFB03A66))
    Entity.CATEGORY -> GradientPair(Color(0xFF7A5200), Color(0xFF8A5F00))
    Entity.ASSOCIATION -> GradientPair(Color(0xFF8A3A10), Color(0xFFB5501C))
    Entity.ANNOUNCEMENT -> GradientPair(Color(0xFF14407F), Color(0xFF2763C0))
    Entity.STUDENT -> GradientPair(Color(0xFF0B5F85), Color(0xFF1478A8))
}

/** Gradient + girih pattern drawn on the FULL box first, then the padding (padding before background would inset the fill). */
@Composable
private fun EntityFill(entity: Entity, modifier: Modifier, padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(entity.gradient().brush()).nourPattern(PatternLevel.Card).padding(padding), content = {
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    })
}

/** A status label that stays readable on any entity gradient: white text on a dark translucent pill. */
@Composable
fun EntityPill(text: String, modifier: Modifier = Modifier, kind: String = "info") {
    Surface(modifier, shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = .28f), contentColor = Color.White) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // The status colour survives as a dot (the pill itself is neutral so it reads on every entity colour).
            androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(nourTone(kind).second))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** A small row (a teacher, a supervisor, a halaqa line): the whole row is the ornamented, coloured background, edge to edge. */
@Composable
fun EntityRow(entity: Entity, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
    EntityFill(entity, if (onClick != null) base.clickable(onClick = onClick) else base, PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** A full card: ornamented header (white text, edge to edge) + plain body, the structure of HalaqaManagementCard. */
@Composable
fun EntityCard(
    entity: Entity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    header: @Composable ColumnScope.() -> Unit,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    ElevatedCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
            EntityFill(entity, Modifier.fillMaxWidth(), PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = header)
            }
            if (body != null) Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
        }
    }
}

/** An ornamented title bar (gradient + girih + white title), edge to edge of its container. */
@Composable
fun EntityHeader(
    entity: Entity,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    trailing: (@Composable () -> Unit)? = null,
) {
    EntityFill(entity, modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)), PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .88f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
        }
    }
}
