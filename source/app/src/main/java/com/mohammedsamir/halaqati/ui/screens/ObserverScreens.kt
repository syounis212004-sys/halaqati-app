@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.MetricCard
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.theme.cardHairline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * RC32: the observer ("المراقب"). Read-only view of every halaqa and student with how much was recited in a period,
 * plus a request channel to the system admin (reward, honour, follow-up). All data comes from dedicated server RPCs.
 */

private val REQUEST_KINDS = listOf("reward" to "مكافأة", "honor" to "تكريم", "follow_up" to "متابعة", "other" to "أخرى")

private fun kindAr(kind: String) = REQUEST_KINDS.firstOrNull { it.first == kind }?.second ?: kind

private fun statusAr(status: String) = when (status) {
    "approved" -> "تمت الموافقة"
    "declined" -> "لم تتم الموافقة"
    else -> "بانتظار المدير"
}

private fun num(v: Double): String =
    if (v == Math.rint(v)) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

@Composable
fun ObserverScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, embedded: Boolean = false) {
    val scope = rememberCoroutineScope()
    var days by rememberSaveable { mutableIntStateOf(30) }
    var query by rememberSaveable { mutableStateOf("") }
    var halaqaFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var breakdown by remember { mutableStateOf<JSONObject?>(null) }
    var myRequests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var requestFor by remember { mutableStateOf<JSONObject?>(null) }
    var detailFor by remember { mutableStateOf<JSONObject?>(null) }
    var breakdownError by remember { mutableStateOf<String?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    suspend fun reload() {
        loading = true
        try {
            val from = LocalDate.now().minusDays((days - 1).toLong()).toString()
            val to = LocalDate.now().toString()
            val (overview, requests) = withContext(Dispatchers.IO) {
                AppGraph.repo.observerOverview(from, to) to runCatching { AppGraph.repo.observerRequests().objects() }.getOrDefault(emptyList())
            }
            val bd = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.observerBreakdown(from, to) } }
            breakdown = bd.getOrNull()
            breakdownError = bd.exceptionOrNull()?.message
            data = overview
            myRequests = requests
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            messageIsError = true
            message = if (e.message.orEmpty().contains("rc32_", true)) "لم يُطبَّق تحديث قاعدة البيانات الخاص بالمراقب بعد." else (e.message ?: "تعذر تحميل البيانات")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(days) { reload() }

    val halaqas = data?.optJSONArray("halaqas")?.objects().orEmpty()
    val bdStudents = breakdown?.optJSONArray("students")?.objects().orEmpty().associateBy { it.optString("student_id") }
    val bdHalaqas = breakdown?.optJSONArray("halaqas")?.objects().orEmpty().associateBy { it.optString("halaqa_id") }
    fun split(b: JSONObject?): String? = b?.let {
        "حفظ ${num(it.optDouble("hifz"))} · مراجعة ${num(it.optDouble("review"))} · سرد ${num(it.optDouble("sard"))}" +
            (if (it.optDouble("tathbit") > 0.0) " · تثبيت ${num(it.optDouble("tathbit"))}" else "") + " صفحة"
    }
    val halaqaNames = halaqas.associate { it.optString("halaqa_id") to it.optString("name", "حلقة") }
    val students = data?.optJSONArray("students")?.objects().orEmpty()
        .filter { halaqaFilter == null || it.optString("halaqa_id") == halaqaFilter }
        .filter { query.isBlank() || it.optString("name").contains(query.trim(), ignoreCase = true) }
    val totalStudents = halaqas.sumOf { it.optInt("students") }
    val totalRecitations = halaqas.sumOf { it.optInt("recitations") }
    val totalPages = halaqas.sumOf { it.optDouble("pages", 0.0) }

    Column(Modifier.fillMaxSize()) {
        if (!embedded) AppTopBar(title = "الإحصاءات", onRefresh = { scope.launch { reload() }; onRefresh() })
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            message?.let { item { StateNotice(it, if (messageIsError) "danger" else "success") } }
            breakdownError?.let { item { StateNotice("تعذر تحميل فصل الحفظ والمراجعة والسرد: $it", "warning") } }
            item {
                com.mohammedsamir.halaqati.ui.components.SegmentedTabs(
                    listOf("آخر 7 أيام", "آخر 30 يومًا", "آخر 90 يومًا"),
                    when (days) { 7 -> 0; 30 -> 1; else -> 2 },
                    { days = listOf(7, 30, 90)[it] },
                )
            }
            if (loading && data == null) item { LoadingSkeleton(rows = 3) }
            if (data != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { MetricCard("الطلاب", totalStudents.toString()) }
                        Box(Modifier.weight(1f)) { MetricCard("التسميعات", totalRecitations.toString()) }
                        if (breakdown == null) Box(Modifier.weight(1f)) { MetricCard("الصفحات", num(totalPages)) }
                    }
                }
                breakdown?.let { bd ->
                    val hs = bd.optJSONArray("halaqas")?.objects().orEmpty()
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { MetricCard("الحفظ", num(hs.sumOf { it.optDouble("hifz") })) }
                            Box(Modifier.weight(1f)) { MetricCard("المراجعة", num(hs.sumOf { it.optDouble("review") })) }
                            Box(Modifier.weight(1f)) { MetricCard("السرد (نطاق)", num(hs.sumOf { it.optDouble("sard") })) }
                        }
                    }
                }
                item { com.mohammedsamir.halaqati.ui.components.NourSectionTitle("الحلقات") }
                if (halaqas.isEmpty()) item { EmptyState("لا توجد حلقات مفعّلة", animated = false) }
                items(halaqas, key = { "h-" + it.optString("halaqa_id") }) { h ->
                    val id = h.optString("halaqa_id")
                    val selected = halaqaFilter == id
                    Card(
                        onClick = { halaqaFilter = if (selected) null else id },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        ),
                        border = cardHairline(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(h.optString("name", "حلقة"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            val safety = if (h.isNull("safety")) "—" else num(h.optDouble("safety")) + "٪"
                            Text(
                                "${h.optInt("students")} طالب · ${h.optInt("recitations")} تسميعة · سلامة التسميع $safety",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            split(bdHalaqas[h.optString("halaqa_id")])?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("بحث باسم الطالب") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        if (halaqaFilter == null) "الطلاب (${students.size})" else "طلاب ${halaqaNames[halaqaFilter.orEmpty()].orEmpty()} (${students.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (students.isEmpty()) item { EmptyState("لا يوجد طلاب مطابقون", animated = false) }
                items(students, key = { "s-" + it.optString("student_id") }) { s ->
                    Card(onClick = { detailFor = s }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = cardHairline(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(s.optString("name"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(halaqaNames[s.optString("halaqa_id")].orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val n = s.optInt("recitations")
                                val safety = if (s.isNull("safety")) "" else " · سلامة " + num(s.optDouble("safety")) + "٪"
                                val last = if (s.isNull("last_date")) "" else " · آخر تسميع " + s.optString("last_date")
                                Text(
                                    if (n == 0) "لا تسميعات في هذه الفترة" else "$n تسميعة$safety$last",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (n > 0) split(bdStudents[s.optString("student_id")])?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
                            }
                            TextButton(onClick = { requestFor = s }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("طلب")
                            }
                        }
                    }
                }
                if (myRequests.isNotEmpty()) {
                    item { com.mohammedsamir.halaqati.ui.components.NourSectionTitle("طلباتي") }
                    items(myRequests, key = { "r-" + it.optString("id") }) { r -> ObserverRequestCard(r, showObserver = false, onDecide = null) }
                }
            }
        }
    }

    detailFor?.let { student ->
        ObserverStudentSheet(student, bdStudents[student.optString("student_id")], days, onDismiss = { detailFor = null })
    }

    requestFor?.let { student ->
        var kind by remember { mutableStateOf("reward") }
        var note by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!sending) requestFor = null },
            title = { Text("طلب بخصوص ${student.optString("name")}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        REQUEST_KINDS.forEach { (value, label) -> ProFilterChip(kind == value, { kind = value }, label, showCheckIcon = false) }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("السبب أو التفاصيل") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Text("يصل الطلب إلى مدير النظام، وتصلك نتيجته في الإشعارات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    enabled = !sending && note.trim().length >= 3,
                    onClick = {
                        scope.launch {
                            sending = true
                            val error = withContext(Dispatchers.IO) {
                                runCatching { AppGraph.repo.observerSubmitRequest(student.optString("student_id"), kind, note) }.exceptionOrNull()?.message
                            }
                            sending = false
                            messageIsError = error != null
                            message = error ?: "تم إرسال الطلب إلى مدير النظام"
                            if (error == null) { requestFor = null; reload() }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (sending) "جارٍ الإرسال…" else "إرسال") }
            },
            dismissButton = { TextButton(onClick = { requestFor = null }, enabled = !sending, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun ObserverRequestCard(r: JSONObject, showObserver: Boolean, onDecide: ((Boolean) -> Unit)?) {
    val status = r.optString("status", "pending")
    val kindColors = com.mohammedsamir.halaqati.ui.theme.statusColors(
        when (status) { "approved" -> "success"; "declined" -> "danger"; else -> "warning" },
    )
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = cardHairline(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${kindAr(r.optString("kind"))} · ${r.optString("student_name").ifBlank { "طالب" }}",
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                )
                Surface(color = kindColors.first, contentColor = kindColors.second, shape = MaterialTheme.shapes.extraLarge) {
                    Text(statusAr(status), Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (showObserver && r.optString("observer_name").isNotBlank()) {
                Text("المانح: ${r.optString("observer_name")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (r.optString("halaqa_name").isNotBlank() && r.optString("halaqa_name") != "null") {
                Text(r.optString("halaqa_name"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(r.optString("note"), style = MaterialTheme.typography.bodyMedium)
            if (!r.isNull("admin_note") && r.optString("admin_note").isNotBlank()) {
                Text("ردّ المدير: ${r.optString("admin_note")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(r.optString("created_at").take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onDecide != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDecide(true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("موافقة") }
                    OutlinedButton(onClick = { onDecide(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("رفض") }
                }
            }
        }
    }
}

/** Admin inbox for observer requests: approve or decline with an optional note; the observer is notified. */
@Composable
fun ObserverRequestsAdminScreen(profile: Profile) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var deciding by remember { mutableStateOf<Pair<JSONObject, Boolean>?>(null) }

    suspend fun reload() {
        loading = true
        try {
            items = withContext(Dispatchers.IO) { AppGraph.repo.observerRequests().objects() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            messageIsError = true
            message = if (e.message.orEmpty().contains("rc32_", true)) "لم يُطبَّق تحديث قاعدة البيانات الخاص بالمراقب بعد." else (e.message ?: "تعذر تحميل الطلبات")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "طلبات المانحين", onRefresh = { scope.launch { reload() } })
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            message?.let { item { StateNotice(it, if (messageIsError) "danger" else "success") } }
            if (loading && items.isEmpty()) item { LoadingSkeleton(rows = 3) }
            if (!loading && items.isEmpty() && message == null) item { EmptyState("لا توجد طلبات من المانحين", animated = false) }
            if (items.isNotEmpty()) item {
                val pendingCount = items.count { it.optString("status") == "pending" }
                com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(
                    Triple(items.size.toString(), "طلب", false),
                    Triple(pendingCount.toString(), "بانتظار القرار", pendingCount > 0),
                    Triple(items.count { it.optString("status") == "approved" }.toString(), "موافَق عليها", false),
                ))
            }
            items(items, key = { it.optString("id") }) { r ->
                ObserverRequestCard(r, showObserver = true, onDecide = if (r.optString("status") == "pending") ({ approve: Boolean -> deciding = r to approve }) else null)
            }
        }
    }

    deciding?.let { (request, approve) ->
        var note by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) deciding = null },
            title = { Text(if (approve) "الموافقة على الطلب" else "رفض الطلب") },
            text = {
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظة للمراقب (اختياري)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val error = withContext(Dispatchers.IO) {
                                runCatching { AppGraph.repo.observerResolve(request.optString("id"), approve, note) }.exceptionOrNull()?.message
                            }
                            busy = false
                            messageIsError = error != null
                            message = error ?: if (approve) "تمت الموافقة وأُبلغ المراقب" else "تم رفض الطلب وأُبلغ المراقب"
                            deciding = null
                            reload()
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (approve) "موافقة" else "رفض") }
            },
            dismissButton = { TextButton(onClick = { deciding = null }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
        )
    }
}

private fun kindLabel(kind: String) = when (kind) {
    "new_hifz" -> "حفظ"; "review_near" -> "مراجعة قريبة"; "review_far" -> "مراجعة بعيدة"; "sard" -> "سرد"
    "tathbit" -> "تثبيت"; "tilawah" -> "تلاوة"; "test" -> "اختبار"; else -> kind
}

private fun evalLabel(v: String) = when (v) {
    "excellent" -> "ممتاز"; "very_good" -> "جيد جدًا"; "good" -> "جيد"; "repeat" -> "إعادة"; else -> ""
}

private fun rangeLabel(r: JSONObject): String {
    fun surah(n: Int) = runCatching { com.mohammedsamir.halaqati.quran.QuranMetadata.surahName(n) }.getOrDefault("سورة $n")
    val ss = r.optInt("start_surah").takeIf { it > 0 }
    val sa = r.optInt("start_ayah").takeIf { it > 0 }
    if (ss != null && sa != null) {
        val es = r.optInt("end_surah").takeIf { it > 0 } ?: ss
        val ea = r.optInt("end_ayah").takeIf { it > 0 } ?: sa
        return if (es == ss) "${surah(ss)} $sa ← $ea" else "${surah(ss)} $sa ← ${surah(es)} $ea"
    }
    val sp = r.optInt("start_page").takeIf { it > 0 }
    val ep = r.optInt("end_page").takeIf { it > 0 }
    return if (sp != null && ep != null) "صفحات $sp–$ep" else if (sp != null) "صفحة $sp" else "—"
}

/** RC42: tap a student to see the hifz / review / sard split, attendance and the latest recitations. */
@Composable
private fun ObserverStudentSheet(student: JSONObject, split: JSONObject?, days: Int, onDismiss: () -> Unit) {
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(student.optString("student_id"), days) {
        val from = LocalDate.now().minusDays((days - 1).toLong()).toString()
        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.observerStudentDetail(student.optString("student_id"), from, LocalDate.now().toString()) } }
            .onSuccess { detail = it }.onFailure { error = it.message ?: "تعذر تحميل التفاصيل" }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text(student.optString("name"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { Text("آخر $days يومًا", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { MetricCard("الحفظ", num(split?.optDouble("hifz") ?: 0.0)) }
                    Box(Modifier.weight(1f)) { MetricCard("المراجعة", num(split?.optDouble("review") ?: 0.0)) }
                    Box(Modifier.weight(1f)) { MetricCard("السرد", num(split?.optDouble("sard") ?: 0.0)) }
                }
            }
            error?.let { item { StateNotice(it, "warning") } }
            val d = detail
            if (d == null && error == null) item { LoadingSkeleton(rows = 3) }
            if (d != null) {
                val att = d.optJSONObject("attendance")
                item {
                    Text(
                        "الحضور: حاضر ${att?.optInt("present") ?: 0} · متأخر ${att?.optInt("late") ?: 0} · غائب ${att?.optInt("absent") ?: 0} · بعذر ${att?.optInt("excused") ?: 0}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val recs = d.optJSONArray("recitations")?.objects().orEmpty()
                item { com.mohammedsamir.halaqati.ui.components.NourSectionTitle("آخر التسميعات (${recs.size})") }
                if (recs.isEmpty()) item { EmptyState("لا تسميعات في هذه الفترة", animated = false) }
                items(recs.size) { i ->
                    val r = recs[i]
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), border = cardHairline(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${kindLabel(r.optString("kind"))} · ${r.optString("date")}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text(rangeLabel(r), style = MaterialTheme.typography.bodyMedium)
                            val pages = r.optDouble("pages", 0.0)
                            val ev = evalLabel(r.optString("evaluation"))
                            Text(
                                listOfNotNull(if (pages > 0.0) "${num(pages)} صفحة" else null, ev.takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
