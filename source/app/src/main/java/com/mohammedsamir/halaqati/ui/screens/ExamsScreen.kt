@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.OfflineQueue
import com.mohammedsamir.halaqati.exams.ExamRules
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/* ============================== data ============================== */

private data class ExamStudent(val id: String, val name: String, val halaqaId: String?, val halaqaName: String)

/** Lays exams written offline (not uploaded yet) over the last server copy, marking them `_pending`. */
private object ExamOverlay {
    private val idInPath = Regex("(?:^|[?&])id=eq\\.([^&]+)")
    fun apply(server: List<JSONObject>, queue: List<OfflineQueue.SnapshotItem>): List<JSONObject> {
        val rows = LinkedHashMap<String, JSONObject>()
        server.forEach { rows[it.optString("id")] = JSONObject(it.toString()) }
        val pending = HashSet<String>()
        queue.filter { it.path.startsWith("/rest/v1/student_exams") }.sortedBy { it.created }.forEach { q ->
            val pathId = idInPath.find(q.path)?.groupValues?.getOrNull(1)
            when (q.method) {
                "POST" -> {
                    val body = runCatching { JSONObject(q.body ?: "") }.getOrNull()
                    val id = body?.optString("id").orEmpty()
                    if (body != null && id.isNotBlank()) { rows[id] = JSONObject(body.toString()); pending.add(id) }
                }
                "DELETE" -> if (pathId != null) { rows.remove(pathId); pending.remove(pathId) }
            }
        }
        pending.forEach { rows[it]?.put("_pending", true) }
        return rows.values.sortedWith(compareByDescending<JSONObject> { it.optString("exam_date") }.thenByDescending { it.optString("created_at") })
    }
}

private fun JSONObject.examNum(key: String): Double = if (!has(key) || isNull(key)) 0.0 else optDouble(key, 0.0)
private fun fmtScore(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

/* ============================== screen ============================== */

object ExamCertHandoff {
    data class Item(val studentId: String, val fromJuz: Int?, val toJuz: Int?)
    @Volatile var item: Item? = null
    fun take(): Item? = item.also { item = null }
}

@Composable
fun SubscriptionBanner(visible: Boolean, online: Boolean) {
    var sub by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(online, visible) {
        if (visible && online) sub = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.mySubscription() }.getOrNull() }
    }
    val s = sub ?: return
    val days = if (s.isNull("days_left")) null else s.optInt("days_left")
    val writable = s.optBoolean("writable", true)
    val text = when {
        !writable -> "انتهى اشتراك الجمعية وفترة السماح: التطبيق للقراءة فقط. تواصل مع الإدارة لتجديد الاشتراك."
        days != null && days < 0 -> "انتهى اشتراك الجمعية وأنت ضمن فترة السماح (${s.optInt("grace_days", 30) + days} يومًا متبقية). جدّد الاشتراك لتجنب القراءة فقط."
        days != null && days <= 15 -> "ينتهي اشتراك الجمعية بعد $days يومًا."
        else -> return
    }
    Surface(color = if (writable && (days ?: 0) >= 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            color = if (writable && (days ?: 0) >= 0) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onError)
    }
}

@Composable
fun ExamsScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val canExam = profile.role == UserRole.ADMIN || profile.role == UserRole.SUPERVISOR || profile.role == UserRole.TEACHER
    val isAdmin = profile.role == UserRole.ADMIN
    var exams by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var settings by remember { mutableStateOf(ExamRules.Settings()) }
    var students by remember { mutableStateOf<List<ExamStudent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var showNew by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showExaminers by remember { mutableStateOf(false) }

    suspend fun reload() {
        try {
            val r = withContext(Dispatchers.IO) {
                val repo = AppGraph.repo
                val st = repo.table("exam_settings", "select=*&limit=1").sRows().firstOrNull()
                val ex = ExamOverlay.apply(
                    repo.table("student_exams", "select=*&order=exam_date.desc,created_at.desc&limit=400", forceRefresh = repo.online()).sRows(),
                    repo.queueSnapshot(500),
                )
                val stu = repo.examStudents().map { ExamStudent(it.optString("id"), it.optString("full_name"), it.supStr("halaqa_id").ifBlank { null }, it.supStr("halaqa_name")) }
                Triple(st, ex, stu)
            }
            settings = ExamRules.settingsFrom(r.first)
            exams = r.second
            students = r.third
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val m = e.message.orEmpty()
            message = if (m.contains("student_exams") || m.contains("exam_settings") || m.contains("42P01") || m.contains("404")) "لم يُطبَّق تحديث قاعدة البيانات الخاص بالاختبارات (RC65) بعد." else (m.ifBlank { "تعذر تحميل الاختبارات" })
        }
        loading = false
    }
    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { reload() }

    val nameOf = remember(students) { students.associate { it.id to it.name } }
    val shown = exams.filter { e ->
        (filter == "all" || (filter == "passed") == e.optBoolean("passed")) &&
            (query.isBlank() || (nameOf[e.optString("student_id")].orEmpty() + " " + e.optString("label")).contains(query.trim()))
    }
    val monthPrefix = LocalDate.now().toString().take(7)
    val monthExams = exams.filter { it.optString("exam_date").startsWith(monthPrefix) }
    val monthPassed = monthExams.count { it.optBoolean("passed") }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("الاختبارات", onRefresh = { scope.launch { reload() }; onRefresh() }, pending = pending)
        message?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactSearchField(value = query, onValueChange = { query = it }, placeholder = "بحث باسم الطالب أو الجزء", modifier = Modifier.weight(1f))
            if (isAdmin) {
                IconButton(onClick = { showSettings = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Tune, contentDescription = "إعدادات الاختبار") }
                IconButton(onClick = { showExaminers = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.SupervisorAccount, contentDescription = "المختبِرون المستقلون") }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProFilterChip(filter == "all", { filter = "all" }, "الكل (${exams.size})", showCheckIcon = false)
            ProFilterChip(filter == "passed", { filter = "passed" }, "ناجح (${exams.count { it.optBoolean("passed") }})", showCheckIcon = false)
            ProFilterChip(filter == "failed", { filter = "failed" }, "لم يجتز (${exams.count { !it.optBoolean("passed") }})", showCheckIcon = false)
        }
        if (loading && exams.isEmpty()) LoadingSkeleton(rows = 3, modifier = Modifier.padding(12.dp))
        if (!loading && shown.isEmpty() && message == null) EmptyState(if (exams.isEmpty()) "لا توجد اختبارات بعد" else "لا توجد نتائج مطابقة", modifier = Modifier.padding(horizontal = 12.dp))
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // RC98: scrolls with the list (was fixed above it).
            item(key = "exams-strip", contentType = "strip") { com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(Triple(monthExams.size.toString(), "اختبارات هذا الشهر", false), Triple(monthPassed.toString(), "ناجحون", false), Triple((monthExams.size - monthPassed).toString(), "لم يجتازوا", monthExams.size - monthPassed > 0))) }
            items(shown, key = { it.optString("id") }) { e ->
                ExamCard(e, nameOf[e.optString("student_id")] ?: "طالب", settings) { detail = e }
            }
        }
        if (canExam) {
            Button(
                onClick = { showNew = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 52.dp),
            ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("اختبار جديد", fontWeight = FontWeight.Bold) }
        }
    }

    if (showNew) {
        NewExamSheet(profile, students, exams, settings, onDismiss = { showNew = false }, onSaved = { showNew = false; scope.launch { reload() }; onRefresh() })
    }
    detail?.let { e ->
        ExamDetailSheet(e, nameOf[e.optString("student_id")] ?: "طالب", settings, canCertify = profile.role == UserRole.ADMIN || profile.role == UserRole.TEACHER, onCertify = { ExamCertHandoff.item = ExamCertHandoff.Item(e.optString("student_id"), if (e.isNull("from_juz")) null else e.optInt("from_juz"), if (e.isNull("to_juz")) null else e.optInt("to_juz")); detail = null; navigate("certificates") }, canDelete = isAdmin || e.optString("examiner_id") == profile.id, onDismiss = { detail = null },
            onDeleted = { detail = null; scope.launch { reload() }; onRefresh() })
    }
    if (showSettings) ExamSettingsSheet(settings, onDismiss = { showSettings = false }, onSaved = { showSettings = false; scope.launch { reload() } })
    if (showExaminers) ExaminersSheet(onDismiss = { showExaminers = false })
}

@Composable
private fun ExamTile(label: String, value: String, tint: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.5.dp, tint)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ExamCard(e: JSONObject, student: String, s: ExamRules.Settings, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val passed = e.optBoolean("passed")
    val score = e.examNum("score")
    val accent = if (passed) cs.primary else cs.error
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(18.dp), color = cs.surface, border = BorderStroke(1.5.dp, accent),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
RubStar((score / 100.0).toFloat(), starSize = 58.dp, fill = accent, track = cs.surfaceVariant, outline = accent, description = "الدرجة ${fmtScore(score)}")
            Column(Modifier.weight(1f)) {
                Text(student, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(e.optString("label") + " · " + ExamRules.modeAr(e.optString("mode")), style = MaterialTheme.typography.bodyMedium)
                Text(
                    GazaTime.dayOnly(e.optString("exam_date")) + (if (e.optInt("attempt", 1) > 1) " · المحاولة ${e.optInt("attempt")}" else "") + (if (e.optBoolean("_pending")) " · بانتظار الرفع" else ""),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                )
            }
            Surface(shape = RoundedCornerShape(50), color = accent) {
                Text(fmtScore(score) + " · " + ExamRules.gradeAr(score, s), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* ============================== new exam ============================== */

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, min: Int, max: Int, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val controls: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { if (value > min) onChange(value - 1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Remove, contentDescription = "إنقاص $label") }
            Text(value.toString(), Modifier.widthIn(min = 40.dp), style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            FilledTonalIconButton(onClick = { if (value < max) onChange(value + 1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Add, contentDescription = "زيادة $label") }
        }
    }
    if (horizontal) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            controls()
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            controls()
        }
    }
}

@Composable
private fun NewExamSheet(
    profile: Profile,
    students: List<ExamStudent>,
    existing: List<JSONObject>,
    settings: ExamRules.Settings,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var studentId by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("juz") }
    var fromJuz by remember { mutableIntStateOf(30) }
    var toJuz by remember { mutableIntStateOf(30) }
    var custom by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("hifz") }
    var scoring by remember { mutableStateOf("deduction") }
    var mistakes by remember { mutableIntStateOf(0) }
    var hesitations by remember { mutableIntStateOf(0) }
    var prompts by remember { mutableIntStateOf(0) }
    var qCount by remember { mutableIntStateOf(5) }
    val qScores = remember { mutableStateListOf("", "", "", "", "") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val label = ExamRules.labelFor(kind, fromJuz, toJuz, custom)
    val perQuestion = 100.0 / qCount
    val parts = (0 until qCount).map { i -> (qScores.getOrNull(i)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0) to perQuestion }
    val score = if (scoring == "deduction") ExamRules.deductionScore(mistakes, hesitations, prompts, settings) else ExamRules.questionsScore(parts)
    val passed = ExamRules.passed(score, settings)
    val student = students.firstOrNull { it.id == studentId }
    val attempt = existing.count { it.optString("student_id") == studentId && it.optString("label") == label } + 1

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("اختبار جديد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            HalaqatiLazySelectField(
                label = "الطالب",
                selectedKey = studentId.ifBlank { null },
                options = students.map { it.id to (it.name + if (it.halaqaName.isNotBlank()) " · ${it.halaqaName}" else "") },
                onSelected = { studentId = it },
                modifier = Modifier.fillMaxWidth(),
                searchable = true,
            )
            Text("نطاق الاختبار", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProFilterChip(kind == "juz", { kind = "juz" }, "جزء كامل", showCheckIcon = false)
                ProFilterChip(kind == "juz_range", { kind = "juz_range" }, "أجزاء مجتمعة", showCheckIcon = false)
                ProFilterChip(kind == "range", { kind = "range" }, "نطاق مخصص", showCheckIcon = false)
            }
            when (kind) {
                "juz" -> Stepper("رقم الجزء", fromJuz, { fromJuz = it; toJuz = it }, 1, 30, Modifier.fillMaxWidth())
                "juz_range" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stepper("من الجزء", fromJuz, { fromJuz = it; if (toJuz < it) toJuz = it }, 1, 30)
                    Stepper("إلى الجزء", toJuz, { toJuz = it; if (fromJuz > it) fromJuz = it }, 1, 30)
                }
                else -> OutlinedTextField(custom, { custom = it }, label = { Text("وصف النطاق (مثال: سورة الكهف)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Text("نمط الاختبار", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("hifz", "tajweed", "hifz_tajweed", "sard").forEach { m -> ProFilterChip(mode == m, { mode = m }, ExamRules.modeAr(m), showCheckIcon = false) }
            }
            Text("طريقة الدرجة", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProFilterChip(scoring == "deduction", { scoring = "deduction" }, "خصم بالأخطاء", showCheckIcon = false)
                ProFilterChip(scoring == "questions", { scoring = "questions" }, "بالأسئلة", showCheckIcon = false)
            }
            if (scoring == "deduction") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Stepper("أخطاء (−${fmtScore(settings.mistakeWeight)})", mistakes, { mistakes = it }, 0, 99, horizontal = true)
                    Stepper("ترددات (−${fmtScore(settings.hesitationWeight)})", hesitations, { hesitations = it }, 0, 99, horizontal = true)
                    Stepper("تنبيهات (−${fmtScore(settings.promptWeight)})", prompts, { prompts = it }, 0, 99, horizontal = true)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stepper("عدد الأسئلة", qCount, { n -> qCount = n; while (qScores.size < n) qScores.add("") }, 1, 10)
                    Text("كل سؤال من ${fmtScore(Math.round(perQuestion * 10.0) / 10.0)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                (0 until qCount).chunked(5).forEach { rowIdx ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowIdx.forEach { i ->
                            OutlinedTextField(
                                value = qScores.getOrNull(i).orEmpty(), onValueChange = { v -> if (i < qScores.size) qScores[i] = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) },
                                label = { Text("س${i + 1}") }, singleLine = true, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                        repeat(5 - rowIdx.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات (اختياري)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
            val accent = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Surface(shape = RoundedCornerShape(18.dp), color = accent) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الدرجة ${fmtScore(score)}", color = Color.White, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold)
                        Text(ExamRules.gradeAr(score, settings) + " · حد النجاح ${fmtScore(settings.passMark)}" + if (attempt > 1) " · المحاولة $attempt" else "", color = Color.White.copy(alpha = .92f), style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
            error?.let { StateNotice(it, kind = "error") }
            Button(
                enabled = !saving && student != null && (kind != "range" || custom.isNotBlank()),
                onClick = {
                    val st = student ?: return@Button
                    saving = true; error = null
                    val obj = JSONObject()
                        .put("id", UUID.randomUUID().toString())
                        .put("student_id", st.id)
                        .put("halaqa_id", st.halaqaId ?: JSONObject.NULL)
                        .put("examiner_id", profile.id)
                        .put("exam_date", LocalDate.now(GazaTime.zone).toString())
                        .put("kind", kind)
                        .put("from_juz", if (kind == "range") JSONObject.NULL else fromJuz)
                        .put("to_juz", if (kind == "range") JSONObject.NULL else toJuz)
                        .put("label", label)
                        .put("mode", mode)
                        .put("scoring", scoring)
                        .put("mistakes", if (scoring == "deduction") mistakes else 0)
                        .put("hesitations", if (scoring == "deduction") hesitations else 0)
                        .put("prompts", if (scoring == "deduction") prompts else 0)
                        .put("questions", if (scoring == "questions") JSONArray().also { arr -> parts.forEach { p -> arr.put(JSONObject().put("score", p.first).put("max", Math.round(p.second * 10.0) / 10.0)) } } else JSONObject.NULL)
                        .put("score", score)
                        .put("pass_mark", settings.passMark)
                        .put("passed", passed)
                        .put("attempt", attempt)
                        .put("notes", notes.trim().ifBlank { JSONObject.NULL })
                    scope.launch {
                        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.write("student_exams", obj, dedupe = "exam:${obj.optString("id")}") } }
                            .onSuccess { onSaved() }
                            .onFailure { error = it.message ?: "تعذر حفظ الاختبار" }
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(if (saving) "جارٍ الحفظ…" else "حفظ نتيجة الاختبار", fontWeight = FontWeight.Bold) }
        }
    }
}

/* ============================== details, settings, examiners ============================== */

@Composable
private fun ExamDetailSheet(e: JSONObject, student: String, s: ExamRules.Settings, canCertify: Boolean, onCertify: () -> Unit, canDelete: Boolean, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val score = e.examNum("score")
    val passed = e.optBoolean("passed")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(student, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(e.optString("label") + " · " + ExamRules.modeAr(e.optString("mode")), style = MaterialTheme.typography.titleMedium)
            Surface(shape = RoundedCornerShape(16.dp), color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) {
                Text("${fmtScore(score)} · ${ExamRules.gradeAr(score, s)}", Modifier.fillMaxWidth().padding(14.dp), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }
            Text("التاريخ: ${GazaTime.dayOnly(e.optString("exam_date"))}  ·  المحاولة ${e.optInt("attempt", 1)}  ·  حد النجاح ${fmtScore(e.examNum("pass_mark"))}", style = MaterialTheme.typography.bodyMedium)
            if (e.optString("scoring") == "deduction") {
                Text("أخطاء ${e.optInt("mistakes")} · ترددات ${e.optInt("hesitations")} · تنبيهات ${e.optInt("prompts")}", style = MaterialTheme.typography.bodyMedium)
            } else {
                val q = e.optJSONArray("questions")
                if (q != null) Text("الأسئلة: " + (0 until q.length()).joinToString(" · ") { i -> val o = q.optJSONObject(i); "${fmtScore(o?.optDouble("score", 0.0) ?: 0.0)}/${fmtScore(o?.optDouble("max", 0.0) ?: 0.0)}" }, style = MaterialTheme.typography.bodyMedium)
            }
            e.supStr("notes").takeIf { it.isNotBlank() }?.let { Text("ملاحظات: $it", style = MaterialTheme.typography.bodyMedium) }
            if (passed && canCertify) Button(onClick = onCertify, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.WorkspacePremium, null); Spacer(Modifier.width(6.dp)); Text("إصدار شهادة اجتياز (تعبئة تلقائية)", fontWeight = FontWeight.Bold) }
            else if (passed) Text("اجتاز الاختبار: تُصدر الشهادة من صفحة «الشهادات».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            error?.let { StateNotice(it, kind = "error") }
            if (canDelete) {
                if (!confirm) OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("حذف الاختبار") }
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.delete("student_exams", "id=eq.${e.optString("id")}", dedupe = "exam:${e.optString("id")}:delete") } }
                                    .onSuccess { onDeleted() }.onFailure { error = it.message ?: "تعذر الحذف" }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("تأكيد الحذف") }
                    OutlinedButton(onClick = { confirm = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("إلغاء") }
                }
            }
        }
    }
}

@Composable
private fun ExamSettingsSheet(s: ExamRules.Settings, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf(fmtScore(s.passMark)) }
    var ex by remember { mutableStateOf(fmtScore(s.excellent)) }
    var vg by remember { mutableStateOf(fmtScore(s.veryGood)) }
    var good by remember { mutableStateOf(fmtScore(s.good)) }
    var wm by remember { mutableStateOf(fmtScore(s.mistakeWeight)) }
    var wh by remember { mutableStateOf(fmtScore(s.hesitationWeight)) }
    var wp by remember { mutableStateOf(fmtScore(s.promptWeight)) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("إعدادات الاختبار", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("حد النجاح والتقديرات", style = MaterialTheme.typography.labelLarge)
            NumField("حد النجاح", pass) { pass = it }
            NumField("ممتاز من", ex) { ex = it }
            NumField("جيد جدًا من", vg) { vg = it }
            NumField("جيد من", good) { good = it }
            Text("قيم الخصم", style = MaterialTheme.typography.labelLarge)
            NumField("الخطأ", wm) { wm = it }
            NumField("التردد", wh) { wh = it }
            NumField("التنبيه", wp) { wp = it }
            error?.let { StateNotice(it, kind = "error") }
            Button(
                enabled = !saving,
                onClick = {
                    val p = pass.toDoubleOrNull(); val e1 = ex.toDoubleOrNull(); val v = vg.toDoubleOrNull(); val g = good.toDoubleOrNull()
                    val a = wm.toDoubleOrNull(); val b = wh.toDoubleOrNull(); val c = wp.toDoubleOrNull()
                    if (p == null || e1 == null || v == null || g == null || a == null || b == null || c == null) { error = "تحقق من الأرقام"; return@Button }
                    if (!(p <= g && g <= v && v <= e1 && e1 <= 100.0)) { error = "رتّب الحدود: النجاح ≤ جيد ≤ جيد جدًا ≤ ممتاز ≤ 100"; return@Button }
                    saving = true; error = null
                    scope.launch {
                        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                            withContext(Dispatchers.IO) {
                                AppGraph.repo.patchConfirmed("exam_settings", "id=eq.1", JSONObject().put("pass_mark", p).put("excellent", e1).put("very_good", v).put("good", g).put("mistake_weight", a).put("hesitation_weight", b).put("prompt_weight", c))
                            }
                        }.onSuccess { onSaved() }.onFailure { error = it.message ?: "تعذر الحفظ" }
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(if (saving) "جارٍ الحفظ…" else "حفظ", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ExaminersSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var staff by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var userId by remember { mutableStateOf("") }
    var halaqaId by remember { mutableStateOf("all") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                val repo = AppGraph.repo
                Triple(
                    repo.table("exam_examiners", "select=*&active=eq.true&order=created_at.desc&limit=200", forceRefresh = repo.online()).sRows(),
                    staffProfiles(),
                    repo.table("halaqas", "select=id,name,active&active=eq.true&order=name.asc&limit=200").sRows(),
                )
            }
        }.onSuccess { rows = it.first; staff = it.second; halaqas = it.third; error = null }
            .onFailure { error = it.message ?: "تعذر التحميل" }
    }
    LaunchedEffect(Unit) { reload() }
    val staffName = staff.associate { it.optString("id") to it.optString("full_name") }
    val hqName = halaqas.associate { it.optString("id") to it.optString("name") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("المختبِرون المستقلون", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("حساب محفّظ أو مشرف يُسمح له باختبار طلاب حلقة من خارجها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HalaqatiLazySelectField(
                label = "الحساب", selectedKey = userId.ifBlank { null },
                options = staff.map { it.optString("id") to it.optString("full_name") }, onSelected = { userId = it },
                modifier = Modifier.fillMaxWidth(), searchable = true,
            )
            HalaqatiLazySelectField(
                label = "الحلقة", selectedKey = halaqaId,
                options = listOf("all" to "كل الحلقات") + halaqas.map { it.optString("id") to it.optString("name") },
                onSelected = { halaqaId = it }, modifier = Modifier.fillMaxWidth(),
            )
            error?.let { StateNotice(it, kind = "error") }
            Button(
                enabled = !busy && userId.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                            withContext(Dispatchers.IO) {
                                AppGraph.repo.write("exam_examiners", JSONObject().put("id", UUID.randomUUID().toString()).put("user_id", userId).put("halaqa_id", if (halaqaId == "all") JSONObject.NULL else halaqaId).put("active", true))
                            }
                        }.onSuccess { userId = ""; reload() }.onFailure { error = it.message ?: "تعذر الإضافة" }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("إضافة مختبِر", fontWeight = FontWeight.Bold) }
            HorizontalDivider()
            if (rows.isEmpty()) Text("لا يوجد مختبِرون مستقلون بعد", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(staffName[r.optString("user_id")] ?: "حساب", fontWeight = FontWeight.Bold)
                        Text(if (r.supStr("halaqa_id").isBlank()) "كل الحلقات" else (hqName[r.optString("halaqa_id")] ?: "حلقة"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.delete("exam_examiners", "id=eq.${r.optString("id")}", dedupe = "examiner:${r.optString("id")}:delete") } }
                                .onSuccess { reload() }.onFailure { error = it.message ?: "تعذر الإزالة" }
                        }
                    }) { Text("إزالة", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun NumField(label: String, v: String, set: (String) -> Unit) {
    OutlinedTextField(v, { x -> set(x.filter { c -> c.isDigit() || c == '.' }.take(5)) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}
