package com.mohammedsamir.halaqati.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * RC67 identity mark: the eight-pointed star that stands for a rub' (quarter hizb) of the Quran.
 * It is a data mark, not a decoration: the star fills from the bottom by [fraction] (0..1) — memorised share,
 * exam score, attendance. The fill animates once when it first appears.
 */
@Composable
fun RubStar(
    fraction: Float,
    modifier: Modifier = Modifier,
    starSize: Dp = 56.dp,
    fill: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    outline: Color = fill,
    description: String? = null,
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800), label = "rubStar")
    val sem = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.then(sem).size(starSize)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val path = Path().apply {
            for (i in 0 until 16) {
                val ang = Math.toRadians(i * 22.5 - 90.0)
                val rad = if (i % 2 == 0) r else r * 0.765f
                val x = c.x + (rad * cos(ang)).toFloat()
                val y = c.y + (rad * sin(ang)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path, track)
        clipPath(path) {
            clipRect(top = this.size.height * (1f - f)) { drawRect(fill) }
        }
        drawPath(path, outline, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** A bordered summary card: the star on one side, a title, one big figure and a caption on the other. */
@Composable
fun StarSummaryCard(title: String, big: String, caption: String, fraction: Float, description: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = cs.surface,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, cs.primary),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RubStar(fraction, starSize = 72.dp, fill = cs.primary, track = cs.surfaceVariant, outline = cs.primary, description = description)
            Column(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Text(big, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold, color = cs.onSurface)
                Text(caption, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** Home hero: today's recitation progress as a star beside the plain numbers. */
@Composable
fun TodayStarCard(heard: Int, present: Int, waiting: Int, modifier: Modifier = Modifier) {
    val fraction = if (present <= 0) 0f else (heard.toFloat() / present).coerceIn(0f, 1f)
    StarSummaryCard(
        title = "التسميع اليوم", big = "$heard من $present",
        caption = if (present <= 0) "لم تُسجَّل جلسة اليوم بعد" else if (waiting > 0) "بانتظارك $waiting طالبًا" else "أتمّ الجميع التسميع",
        fraction = fraction, description = "أتمّ التسميع $heard من $present", modifier = modifier,
    )
}
