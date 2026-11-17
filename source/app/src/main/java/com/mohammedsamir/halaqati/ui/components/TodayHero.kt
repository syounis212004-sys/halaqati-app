package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * RC75: the top of the teacher / admin home. One rich emerald card instead of a plain white note plus a grey button:
 * the day's message with its icon, the numbers that matter (present, waiting), and one clear start button.
 * While data is loading the button says so (it never claims "day off"), and a day off is shown in plain words.
 */
@Composable
fun TodayHeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    loaded: Boolean,
    offDay: Boolean,
    waitingForSession: Boolean,
    present: Int,
    waiting: Int,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    greeting: String = "",
    verse: String = "",
    verseSource: String = "",
    photo: String? = null,
    photoName: String = "",
    heard: Int = 0,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
        Column(
            Modifier.nourHeroFill(androidx.compose.material3.MaterialTheme.colorScheme.primary).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (verse.isNotBlank() || greeting.isNotBlank()) {
                // RC90: the photo sits at the start (right in Arabic), bigger, vertically centred with the verse; the verse uses the app font (Cairo).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (photoName.isNotBlank() && verse.isNotBlank()) Box(Modifier.background(Color.White, androidx.compose.foundation.shape.CircleShape).padding(3.dp)) { StudentAvatar(photo, photoName, 72.dp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (greeting.isNotBlank()) Text(greeting, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                        if (verse.isNotBlank()) Text(verse, style = MaterialTheme.typography.titleMedium, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 5, overflow = TextOverflow.Ellipsis)
                        if (verseSource.isNotBlank()) Text(verseSource, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .75f))
                    }
                }
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .25f)))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // RC104: with no verse the photo used to sit alone on a full row; it now leads the title row instead.
                val photoBesideTitle = verse.isBlank() && photoName.isNotBlank()
                if (photoBesideTitle) Box(Modifier.background(Color.White, androidx.compose.foundation.shape.CircleShape).padding(3.dp)) { StudentAvatar(photo, photoName, 64.dp) }
                GradientIcon(icon, HalaqatiGradients.Gold, tile = if (photoBesideTitle) 44.dp else 56.dp, glyph = if (photoBesideTitle) 24.dp else 28.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .95f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (loaded && !offDay) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroNumber("حاضر اليوم", present.toString(), Modifier.weight(1f))
                HeroNumber("بانتظارك", waiting.toString(), Modifier.weight(1f))
                HeroRing(heard, present)
            }
            Button(
                onClick = onStart,
                enabled = loaded && !offDay,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White, contentColor = Color(0xFF0B6E4F),
                    disabledContainerColor = Color.White.copy(alpha = .92f), disabledContentColor = Color(0xFF42564D),
                ),
            ) {
                if (!loaded) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp, color = Color(0xFF42564D))
                else Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        !loaded -> "جارٍ تجهيز بيانات اليوم…"
                        offDay -> "اليوم إجازة"
                        waitingForSession -> "ابدأ جلسة اليوم"
                        else -> "فتح جلسة اليوم"
                    },
                    fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun HeroNumber(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .16f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .95f))
        }
    }
}

/** RC90: how much of today's present students already recited (a thin white ring with the percentage). */
@Composable
private fun HeroRing(done: Int, total: Int, modifier: Modifier = Modifier) {
    val p = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    Box(modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val inset = 3.dp.toPx()
            val topLeft = Offset(inset, inset)
            val area = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(Color.White.copy(alpha = .25f), 0f, 360f, false, topLeft, area, style = stroke)
            drawArc(Color.White, -90f, 360f * p, false, topLeft, area, style = stroke)
        }
        Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}
