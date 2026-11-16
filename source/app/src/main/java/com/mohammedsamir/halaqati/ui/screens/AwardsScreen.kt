@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.theme.cardHairline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** RC46: hand-off from the awards page to the certificate studio (one tap per winner). */
object AwardCertificateBus {
    data class Request(val month: String, val studentId: String, val categoryId: String?, val rank: Int)

    @Volatile
    var pending: Request? = null
}

internal fun JSONArray.awardObjects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

internal fun awardNum(v: Double): String =
    if (v == Math.rint(v)) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

internal fun medalLabel(rank: Int) = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$rank" }

internal fun medalColor(rank: Int): Color = when (rank) {
    1 -> Color(0xFFC9A227)
    2 -> Color(0xFF8E9AAF)
    3 -> Color(0xFFB87333)
    else -> Color(0xFF6B7280)
}

internal fun awardMonthLabel(month: String): String {
    val parts = month.take(7).split("-")
    val names = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return month
    return "${names.getOrNull(m - 1) ?: month} ${parts[0]}"
}

@Composable
fun AwardsScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var months by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var month by remember { mutableStateOf<String?>(null) }
    var scopeKind by remember { mutableStateOf("halaqa") }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var confirmApprove by remember { mutableStateOf(false) }
    var excludeFor by remember { mutableStateOf<JSONObject?>(null) }
    var excludeReason by remember { mutableStateOf("") }

    fun loadMonths(pick: String? = null) {
        scope.launch {
            loading = true
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.awardMonths() } }
                .onSuccess { list ->
                    months = list
                    month = pick ?: month?.takeIf { m -> list.any { it.optString("month").take(7) == m } } ?: list.firstOrNull()?.optString("month")?.take(7)
                }
                .onFailure { message = it.message ?: "تعذر تحميل الأشهر"; isError = true }
            loading = false
        }
    }

    fun loadMonth() {
        val m = month ?: run { data = null; return }
        scope.launch {
            loading = true
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.adminAwards("$m-01", scopeKind) } }
                .onSuccess { data = it }
                .onFailure { message = it.message ?: "تعذر تحميل التكريم"; isError = true; data = null }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadMonths() }
    LaunchedEffect(month, scopeKind) { loadMonth() }

    val status = months.firstOrNull { it.optString("month").take(7) == month }?.optString("status") ?: data?.optString("status").orEmpty()
    val isDraft = status == "draft"
    val groups = data?.optJSONArray("groups")?.awardObjects().orEmpty()

    fun compute() {
        val target = month ?: java.time.YearMonth.now().minusMonths(1).toString()
        scope.launch {
            busy = true
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.computeAwards("$target-01") } }
                .onSuccess { message = "تم حساب شهر ${awardMonthLabel(target)}"; isError = false; loadMonths(target) }
                .onFailure { message = it.message ?: "تعذر الحساب"; isError = true }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "التكريم الشهري", onRefresh = { loadMonths(); onRefresh() }, pending = pending)
        LazyColumn(
            state = androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            message?.let { item { StateNotice(it, if (isError) "danger" else "success") } }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), border = cardHairline(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("الدرجة من ١٠٠", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "حفظ ٤٠ · سرد ٣٠ · حضور ١٠ · سلوك ١٠ · جودة التسميع ١٠. الشروط: حفظ أكثر من ٩٠٪ وسرد ١٠٠٪ وغياب بلا عذر لا يزيد على جلستين. عند التساوي: الصفحات ثم الجودة ثم السلوك ثم الحضور.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (months.isNotEmpty()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(months) { row ->
                        val key = row.optString("month").take(7)
                        val tag = if (row.optString("status") == "approved") "معتمد" else "مسودة"
                        ProFilterChip(month == key, { month = key }, "${awardMonthLabel(key)} · $tag")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProFilterChip(scopeKind == "halaqa", { scopeKind = "halaqa" }, "كل حلقة على حدة")
                    ProFilterChip(scopeKind == "category", { scopeKind = "category" }, "الفئة عبر كل الحلقات")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (month == null || isDraft) {
                        OutlinedButton(onClick = { compute() }, enabled = !busy) { Text(if (month == null) "حساب الشهر الماضي" else "إعادة الحساب") }
                    }
                    if (isDraft) Button(onClick = { confirmApprove = true }, enabled = !busy && groups.isNotEmpty()) { Text("اعتماد الشهر") }
                    if (status == "approved") Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text("معتمد · ظاهر للمانح", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge) }
                }
            }
            if (loading && data == null) item { LoadingSkeleton(rows = 3) }
            if (!loading && month == null) item {
                EmptyState("لم يُحسب أي شهر بعد. يُحسب تلقائيًا فجر اليوم الأول من كل شهر، أو اضغط «حساب الشهر الماضي».")
            }
            if (month != null && !loading && groups.isEmpty()) item { EmptyState("لا توجد نتائج لهذا الشهر.") }
            items(groups) { g -> AwardGroupCard(g, isDraft, status == "approved", month, scope, navigate,
                onExclude = { excludeFor = it; excludeReason = "" },
                onContact = { id, allowed ->
                    scope.launch {
                        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.setDonorContact(id, allowed) } }
                            .onSuccess { loadMonth() }
                            .onFailure { message = it.message; isError = true }
                    }
                }) }
        }
    }

    if (confirmApprove) AlertDialog(
        onDismissRequest = { confirmApprove = false },
        title = { Text("اعتماد تكريم ${month?.let { awardMonthLabel(it) }.orEmpty()}") },
        text = { Text("بعد الاعتماد يرى المانح الأسماء الكاملة وأفضل ٣ في كل فئة، ويصله إشعار. لا يمكن التراجع من التطبيق.") },
        confirmButton = {
            TextButton(onClick = {
                confirmApprove = false
                val m = month ?: return@TextButton
                scope.launch {
                    busy = true
                    com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.approveAwards("$m-01") } }
                        .onSuccess { ok -> message = if (ok) "تم اعتماد الشهر" else "الشهر معتمد مسبقًا"; isError = !ok; loadMonths(m) }
                        .onFailure { message = it.message ?: "تعذر الاعتماد"; isError = true }
                    busy = false
                }
            }) { Text("اعتماد") }
        },
        dismissButton = { TextButton(onClick = { confirmApprove = false }) { Text("إلغاء") } },
    )

    excludeFor?.let { w ->
        AlertDialog(
            onDismissRequest = { excludeFor = null },
            title = { Text("استبعاد ${w.optString("name")}") },
            text = {
                OutlinedTextField(value = excludeReason, onValueChange = { excludeReason = it }, label = { Text("سبب الاستبعاد (٣ أحرف فأكثر)") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(enabled = excludeReason.trim().length >= 3, onClick = {
                    val m = month ?: return@TextButton
                    val sid = w.optString("student_id")
                    val reason = excludeReason.trim()
                    excludeFor = null
                    scope.launch {
                        busy = true
                        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.excludeAward("$m-01", sid, reason) } }
                            .onSuccess { message = "تم الاستبعاد وإعادة الترتيب"; isError = false; loadMonth() }
                            .onFailure { message = it.message ?: "تعذر الاستبعاد"; isError = true }
                        busy = false
                    }
                }) { Text("استبعاد") }
            },
            dismissButton = { TextButton(onClick = { excludeFor = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun AwardGroupCard(
    g: JSONObject, isDraft: Boolean, approved: Boolean, month: String?, scope: kotlinx.coroutines.CoroutineScope,
    navigate: (String) -> Unit, onExclude: (JSONObject) -> Unit, onContact: (String, Boolean) -> Unit,
) {
    val winners = g.optJSONArray("winners")?.awardObjects().orEmpty()
    val title = listOf(g.optString("halaqa_name").takeIf { it.isNotBlank() && it != "null" }, g.optString("category_name")).filterNotNull().joinToString(" · ")
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), border = cardHairline(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${g.optInt("students")} طالبًا", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (winners.isEmpty()) {
                val miss = g.optJSONObject("best_miss")
                StateNotice("لا شهادة لهذه المجموعة: لم يستوفِ أي طالب الشروط." + (miss?.let { " أقرب طالب: ${it.optString("name")} (${awardNum(it.optDouble("score"))}) — ${it.optString("reason")}" } ?: ""), "warning")
            }
            winners.forEach { w ->
                val rank = w.optInt("rank")
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), border = BorderStroke(1.dp, medalColor(rank).copy(alpha = .5f))) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(medalLabel(rank), style = MaterialTheme.typography.titleLarge)
                            Column(Modifier.weight(1f)) {
                                Text(w.optString("name"), fontWeight = FontWeight.SemiBold)
                                Text("${awardNum(w.optDouble("score"))} / 100 · ${awardNum(w.optDouble("pages"))} صفحة", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            "حفظ ${awardNum(w.optDouble("hifz_pct"))}٪ · سرد ${awardNum(w.optDouble("sard_pct").coerceAtMost(100.0))}٪ · حضور ${awardNum(w.optDouble("attendance_pct").coerceAtMost(100.0))}٪ · سلوك ${awardNum(w.optDouble("behavior").coerceAtMost(100.0))}٪ · جودة ${awardNum(w.optDouble("quality").coerceAtMost(100.0))}٪",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("رقم ولي الأمر للمانح", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Switch(checked = w.optBoolean("contact_allowed"), onCheckedChange = { onContact(w.optString("student_id"), it) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isDraft) TextButton(onClick = { onExclude(w) }) { Text("استبعاد") }
                            if (approved && rank == 1 && month != null) TextButton(onClick = {
                                AwardCertificateBus.pending = AwardCertificateBus.Request(
                                    month, w.optString("student_id"), g.optString("category_id").takeIf { it.isNotBlank() && it != "null" }, rank,
                                )
                                navigate("certificates")
                            }) { Text("إصدار شهادة") }
                        }
                    }
                }
            }
        }
    }
}
