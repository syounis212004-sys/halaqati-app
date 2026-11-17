@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.components.animatedCountText
import com.mohammedsamir.halaqati.ui.components.halaqatiEnter
import com.mohammedsamir.halaqati.ui.theme.cardHairline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.YearMonth

/** RC49: the donor's "أثري" page — what their giving achieved, badges, last month's report, the halaqa's news and a suggestion box. */
private fun streakOf(months: List<String>): Int {
    val parsed = months.mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }.sorted()
    if (parsed.isEmpty()) return 0
    var best = 1; var run = 1
    for (i in 1 until parsed.size) { if (parsed[i] == parsed[i - 1].plusMonths(1)) { run++; if (run > best) best = run } else run = 1 }
    return best
}

private fun statusText(s: String) = when (s) { "delivered" -> "وصل إلى مستحقيه"; "received" -> "استلمنا الدعم"; else -> "بانتظار التحويل" }

@Composable
internal fun DonorImpactTab(refreshTick: Int) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var impact by remember { mutableStateOf<JSONObject?>(null) }
    var pledges by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var news by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf("") }
    val reportMonth = remember { YearMonth.now().minusMonths(1) }

    fun reload() {
        scope.launch {
            loading = true
            withContext(Dispatchers.IO) {
                impact = runCatching { AppGraph.repo.donorImpact() }.getOrNull()
                pledges = runCatching { AppGraph.repo.myPledges() }.getOrDefault(emptyList())
                news = runCatching { AppGraph.repo.donorNews() }.getOrDefault(emptyList())
                report = runCatching { AppGraph.repo.donorReport("$reportMonth-01") }.getOrNull()
            }
            loading = false
        }
    }
    LaunchedEffect(refreshTick) { reload() }

    val months = impact?.optJSONArray("months")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
    val streak = streakOf(months)
    val total = impact?.optInt("pledges") ?: 0
    val delivered = impact?.optInt("delivered") ?: 0
    val beneficiaries = impact?.optInt("beneficiaries") ?: 0
    val badges = listOf(
        Triple("🌱", "أول كفالة", total >= 1), Triple("🔥", "٣ أشهر متواصلة", streak >= 3),
        Triple("⭐", "٥ كفالات", total >= 5), Triple("🏅", "١٠٠ طالب استفادوا", beneficiaries >= 100),
    )

    LazyColumn(
        state = rememberLazyListState(), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        message?.let { item { StateNotice(it, if (isError) "danger" else "success") } }
        if (loading && impact == null) item { LoadingSkeleton(rows = 3) }
        item {
            Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.mohammedsamir.halaqati.ui.components.NourSectionTitle("أثر دعمك", entity = com.mohammedsamir.halaqati.ui.theme.Entity.DONOR)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ImpactTile("كفالة", total.toString(), Modifier.weight(1f))
                        ImpactTile("وصلت", delivered.toString(), Modifier.weight(1f))
                        ImpactTile("طالب استفاد", beneficiaries.toString(), Modifier.weight(1f))
                    }
                    if (total == 0) Text("حين تتكفّل بأول بطاقة دعم سيظهر هنا أثر عطائك.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        badges.forEach { (icon, label, on) ->
                            Surface(shape = RoundedCornerShape(999.dp), color = if (on) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)) {
                                Text("$icon $label", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium,
                                    color = if (on) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
                            }
                        }
                    }
                }
            }
        }
        report?.let { r ->
            item {
                val title = awardMonthLabel(reportMonth.toString())
                val line = "حلقتك في $title: ${r.optInt("students")} طالبًا، ${r.optInt("sessions")} جلسة، ${awardNum(r.optDouble("hifz_pages"))} صفحة حفظ جديد، ${awardNum(r.optDouble("review_pages"))} صفحة مراجعة" +
                    (if (!r.isNull("attendance_pct")) "، حضور ${r.optInt("attendance_pct")}٪" else "") + (if (r.optInt("winners") > 0) "، و${r.optInt("winners")} مكرَّمين" else "") + ". تطبيق حلقتي."
                Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.mohammedsamir.halaqati.ui.components.NourSectionTitle("تقرير $title", entity = com.mohammedsamir.halaqati.ui.theme.Entity.HALAQA)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ImpactTile("طالبًا", r.optInt("students").toString(), Modifier.weight(1f))
                            ImpactTile("جلسة", r.optInt("sessions").toString(), Modifier.weight(1f))
                            ImpactTile("مكرَّمون", r.optInt("winners").toString(), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ImpactTile("صفحة حفظ", awardNum(r.optDouble("hifz_pages")), Modifier.weight(1f))
                            ImpactTile("صفحة مراجعة", awardNum(r.optDouble("review_pages")), Modifier.weight(1f))
                            ImpactTile("حضور", if (r.isNull("attendance_pct")) "—" else r.optInt("attendance_pct").toString() + "٪", Modifier.weight(1f))
                        }
                        OutlinedButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, line)
                            runCatching { context.startActivity(Intent.createChooser(send, "مشاركة الملخص").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Text("مشاركة الملخص") }
                    }
                }
            }
        }
        item { com.mohammedsamir.halaqati.ui.components.NourSectionTitle("سجل عطائي", entity = com.mohammedsamir.halaqati.ui.theme.Entity.DONOR) }
        if (!loading && pledges.isEmpty()) item { EmptyState("لا توجد كفالات بعد. افتح تبويب «الدعم» لتبدأ.") }
        items(pledges.size) { i ->
            val p = pledges[i]
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, border = cardHairline()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.optString("title"), style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(p.optString("period"), p.amountOrNull()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(statusText(p.optString("status")), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (news.isNotEmpty()) item { com.mohammedsamir.halaqati.ui.components.NourSectionTitle("أخبار الحلقة", entity = com.mohammedsamir.halaqati.ui.theme.Entity.HALAQA) }
        items(news.size) { i ->
            val n = news[i]
            Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, border = cardHairline()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(n.optString("title"), style = MaterialTheme.typography.titleSmall)
                    n.optString("body").takeIf { it.isNotBlank() && it != "null" }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(n.optString("created_at").take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.mohammedsamir.halaqati.ui.components.NourSectionTitle("صندوق الاقتراحات", entity = com.mohammedsamir.halaqati.ui.theme.Entity.DONOR)
                    Text("فكرة أو حاجة تراها في الحلقة؟ تصل مباشرة إلى المدير.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(suggestion, { suggestion = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("اقتراحك") })
                    Button(enabled = suggestion.trim().length >= 3, onClick = {
                        val text = suggestion.trim()
                        scope.launch {
                            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.donorSuggest(text) } }
                                .onSuccess { message = "وصل اقتراحك، جزاك الله خيرًا"; isError = false; suggestion = "" }
                                .onFailure { message = it.message ?: "تعذّر الإرسال"; isError = true }
                        }
                    }) { Text("إرسال") }
                }
            }
        }
    }
}

@Composable
private fun ImpactTile(label: String, value: String, modifier: Modifier) {
    // RC104: the shared stat tile (tonal gradient, same as the hero strips) instead of a flat grey tile.
    com.mohammedsamir.halaqati.ui.components.NourStatTile(animatedCountText(value), label, modifier)
}
