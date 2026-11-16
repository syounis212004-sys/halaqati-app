package com.mohammedsamir.halaqati.ui.guardian

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale

/** RC27.44: last-7-days digest of one child, derived from the recitations already in [GuardianOverview.latest]. */
data class WeeklyDigest(val days: Int, val recitations: Int, val pages: Double, val quality: Double?)

object GuardianWeeklySummary {
    fun digest(o: GuardianOverview, today: LocalDate = LocalDate.now()): WeeklyDigest {
        val from = today.minusDays(6)
        val rows = o.latest.filter { line ->
            val d = runCatching { LocalDate.parse(line.date.take(10)) }.getOrNull()
            d != null && !d.isBefore(from) && !d.isAfter(today)
        }
        val q = rows.filterNot { it.pages <= 0.0 && (it.quality ?: 0.0) <= 0.0 }.mapNotNull { it.quality }
        return WeeklyDigest(
            days = rows.map { it.date.take(10) }.distinct().size,
            recitations = rows.size,
            pages = rows.sumOf { it.pages },
            quality = if (q.isEmpty()) null else q.average(),
        )
    }

    private fun num(v: Double): String {
        val r = kotlin.math.round(v * 10) / 10
        return if (kotlin.math.abs(r - r.toLong()) < 1e-6) r.toLong().toString() else String.format(Locale.US, "%.1f", r)
    }

    fun text(o: GuardianOverview, d: WeeklyDigest): String = buildString {
        append("*ملخص الأسبوع — ${o.childName}*\n")
        if (d.recitations == 0) append("لا توجد تسميعات مسجلة خلال آخر 7 أيام.")
        else {
            append("• أيام التسميع: ${d.days} من 7\n")
            append("• عدد التسميعات: ${d.recitations}\n")
            append("• الصفحات: ${num(d.pages)}\n")
            d.quality?.let { append("• متوسط الجودة: ${num(it)}٪\n") }
        }
    }.trimEnd()
}

@Composable
fun WeeklySummaryCard(overview: GuardianOverview, modifier: Modifier = Modifier) {
    val d = GuardianWeeklySummary.digest(overview)
    val context = LocalContext.current
    ElevatedCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ملخص آخر 7 أيام", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (d.recitations == 0) {
                Text("لا توجد تسميعات مسجلة خلال الأسبوع الماضي.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "سمّع ${d.days} ${if (d.days == 1) "يومًا" else "أيام"} · ${d.recitations} تسميع · ${String.format(Locale.US, "%.1f", d.pages).removeSuffix(".0")} صفحة" +
                        (d.quality?.let { " · جودة ${String.format(Locale.US, "%.0f", it)}٪" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, GuardianWeeklySummary.text(overview, d))
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "مشاركة ملخص الأسبوع")) }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("مشاركة الملخص") }
        }
    }
}
