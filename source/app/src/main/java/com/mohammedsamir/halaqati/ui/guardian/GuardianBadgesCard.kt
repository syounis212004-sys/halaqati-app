package com.mohammedsamir.halaqati.ui.guardian

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.plans.BadgeRules
import com.mohammedsamir.halaqati.ui.components.InsightCardShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** RC27.37 guardian home: daily badges (attendance streak, quality streak, pages in 30 days) for the selected child. */
@Composable
fun GuardianBadgesCard(studentId: String, modifier: Modifier = Modifier) {
    var result by remember(studentId) { mutableStateOf<BadgeRules.Result?>(null) }
    var expanded by remember(studentId) { mutableStateOf(true) }
    LaunchedEffect(studentId) {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                if (!AppGraph.repo.online()) return@withContext null
                val from = LocalDate.now().minusMonths(3).withDayOfMonth(1).toString()
                val att = AppGraph.repo.table(
                    "attendance_records",
                    "select=status,sessions!inner(session_date,status)&student_id=eq.$studentId&sessions.session_date=gte.$from&limit=900",
                )
                val rec = AppGraph.repo.table(
                    "recitation_entries",
                    "select=pages_count,evaluation,sessions!inner(session_date,status)&student_id=eq.$studentId&sessions.session_date=gte.$from&limit=2000",
                )
                val attendance = (0 until att.length()).mapNotNull { att.optJSONObject(it) }.mapNotNull { o ->
                    val s = o.optJSONObject("sessions") ?: return@mapNotNull null
                    BadgeRules.Attendance(s.optString("session_date"), o.optString("status"), s.optString("status") == "cancelled")
                }
                val recitations = (0 until rec.length()).mapNotNull { rec.optJSONObject(it) }.mapNotNull { o ->
                    val s = o.optJSONObject("sessions") ?: return@mapNotNull null
                    BadgeRules.Recitation(s.optString("session_date"), o.optDouble("pages_count", 0.0), o.optString("evaluation"), s.optString("status") == "cancelled")
                }
                BadgeRules.compute(attendance, recitations)
            }
        }.onSuccess { if (it != null) result = it }
    }
    val r = result ?: return
    if (r.earned.isEmpty() && r.next == null) return
    InsightCardShell(
        kind = "success",
        icon = Icons.Default.MilitaryTech,
        title = "شارات الإنجاز",
        summary = if (r.earned.isEmpty()) "ابدأ سلسلة الحضور لتحصل على أول شارة" else r.earned.joinToString(" · ") { medal(it.level) + " " + it.title },
        count = r.earned.size.takeIf { it > 0 },
        expanded = expanded,
        onClick = { expanded = !expanded },
        modifier = modifier,
    ) {
        if (expanded) {
            r.earned.forEach { b ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(medal(b.level), style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f)) {
                        Text(b.title, fontWeight = FontWeight.Bold)
                        Text(b.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            r.next?.let { n ->
                Text(
                    "الهدف القادم: ${n.title} — باقي ${n.remaining} ${n.unit}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun medal(level: Int): String = when (level) { 3 -> "🥇"; 2 -> "🥈"; else -> "🥉" }
