@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.compose.ui.draw.clip
import java.time.LocalDate

/* ============================== read-only daily sessions for the supervisor ============================== */

private data class SupSessionRow(
    val id: String,
    val halaqaId: String,
    val date: String,
    val halaqaName: String,
    val teacherName: String,
    val closed: Boolean,
    val present: Int,
    val absent: Int,
    val excused: Int,
    val recited: Int,
    val planPercent: Int?,
)

@Composable
fun SupervisorSessionsScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<SupSessionRow>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var halaqaFilter by remember { mutableStateOf("all") }
    var halaqaOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var halaqaOfRow by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var days by remember { mutableStateOf(10) }
    var offDay by remember { mutableStateOf(false) }
    var teachersOf by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    suspend fun reload() {
        try {
            val result = withContext(Dispatchers.IO) {
                val repo = AppGraph.repo
                val since = LocalDate.now().minusDays(days.toLong()).toString()
                // Nour: scope to the supervisor's own halaqat; the teacher shown is the halaqa's registered teacher(s), not whoever created the session.
                val board = loadSupBoard(supervisorOnly = profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR)
                val hq = (board.halaqas + board.programs).associate { it.id to it.name }
                offDay = board.offDay
                teachersOf = (board.halaqas + board.programs).associate { it.id to it.teacherLine }
                val sessions = repo.table("sessions", "select=id,halaqa_id,session_date,status,created_by&session_date=gte.$since&order=session_date.desc&limit=600").sRows()
                    .filter { it.optString("halaqa_id") in hq.keys && it.optString("status") != "cancelled" }
                val ids = sessions.map { it.optString("id") }.filter { it.isNotBlank() }
                val att = if (ids.isEmpty()) emptyList() else repo.table("attendance_records", "select=session_id,student_id,status&session_id=in.(${ids.joinToString(",")})&limit=4000").sRows()
                val rec = if (ids.isEmpty()) emptyList() else repo.table("recitation_entries", "select=session_id,student_id&session_id=in.(${ids.joinToString(",")})&limit=5000").sRows()
                val plans = runCatching { repo.planSnapshots(LocalDate.now()) }.getOrDefault(emptyList())
                val planByHalaqa = plans.filter { it.student.halaqaId != null }.groupBy { it.student.halaqaId!! }
                    .mapValues { e -> e.value.map { it.progress.percentage }.average().toInt() }
                val built = sessions.map { s ->
                    val id = s.optString("id")
                    val a = att.filter { it.optString("session_id") == id }
                    SupSessionRow(
                        id = id,
                        halaqaId = s.optString("halaqa_id"),
                        date = s.optString("session_date"),
                        halaqaName = hq[s.optString("halaqa_id")] ?: "حلقة",
                        teacherName = (board.halaqas + board.programs).firstOrNull { it.id == s.optString("halaqa_id") }?.teacherLine ?: "—",
                        closed = s.optString("status") == "closed",
                        present = a.count { it.optString("status") == "present" || it.optString("status") == "late" },
                        absent = a.count { it.optString("status") == "absent" },
                        excused = a.count { it.optString("status") == "excused" },
                        recited = rec.filter { it.optString("session_id") == id }.map { it.optString("student_id") }.distinct().size,
                        planPercent = planByHalaqa[s.optString("halaqa_id")],
                    )
                }
                Triple(built, hq.map { it.key to it.value }, sessions.associate { it.optString("id") to it.optString("halaqa_id") })
            }
            rows = result.first
            halaqaOptions = result.second
            halaqaOfRow = result.third
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تحميل الجلسات" }
    }
    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch, days) { reload() }

    val list = rows
    Column {
        AppTopBar("الجلسات اليومية", onRefresh = { scope.launch { reload() }; onRefresh() }, pending = pending, kicker = "عرض للمتابعة فقط")
        message?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (list == null) { LoadingSkeleton(rows = 4, modifier = Modifier.padding(12.dp)); return@Column }
        if (halaqaOptions.size > 1) HalaqatiLazySelectField(
            label = "الحلقة", selectedKey = halaqaFilter,
            options = listOf("all" to "كل الحلقات") + halaqaOptions,
            onSelected = { halaqaFilter = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        val shown = list.filter { halaqaFilter == "all" || it.halaqaId == halaqaFilter }
        val today = LocalDate.now()
        val studyWeekdays = list.mapNotNull { runCatching { LocalDate.parse(it.date).dayOfWeek }.getOrNull() }.toSet()
        val halaqasShown = halaqaOptions.filter { halaqaFilter == "all" || it.first == halaqaFilter }
        val weekCount = shown.count { runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(it.date), today) <= 6 }.getOrDefault(false) }
        val missToday = if (offDay) 0 else halaqasShown.count { h -> shown.none { it.halaqaId == h.first && it.date == today.toString() } }
        val lowRec = shown.count { it.present + it.absent + it.excused > 0 && it.recited * 2 < (it.present + it.absent + it.excused) }
        LazyColumn(
            state = androidx.compose.foundation.lazy.rememberLazyListState(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(Triple(weekCount.toString(), "جلسات هذا الأسبوع", false), Triple(if (offDay) "إجازة" else missToday.toString(), "لم تبدأ اليوم", !offDay && missToday > 0), Triple(lowRec.toString(), "تسميع منخفض", lowRec > 0)))
            }
            val dayList = (0..days).map { today.minusDays(it.toLong()) }.filter { d ->
                val has = shown.any { it.date == d.toString() }
                has || (d.dayOfWeek in studyWeekdays && !(d == today && offDay))
            }
            if (dayList.isEmpty()) item { EmptyState("لا توجد جلسات في هذه الفترة") }
            dayList.forEach { d ->
                val ds = d.toString()
                item(key = "h-$ds") {
                    Text(ds + if (d == today) " · اليوم" else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                }
                val rowsOfDay = shown.filter { it.date == ds }
                items(rowsOfDay, key = { it.id }) { r ->
                    val ended = r.closed || d.isBefore(today)
                    NourCard(marker = if (ended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary, entity = com.mohammedsamir.halaqati.ui.theme.Entity.HALAQA) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.halaqaName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(r.teacherName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            StatusPill(if (ended) "مُنهاة" else "جارية", kind = if (ended) "success" else "warning")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("حضور ${r.present} · غياب ${r.absent}" + (if (r.excused > 0) " · بعذر ${r.excused}" else "") + " · سُمّع ${r.recited}", style = MaterialTheme.typography.bodyMedium)
                        r.planPercent?.let { pct ->
                            Spacer(Modifier.height(4.dp))
                            Text("إنجاز الخطط في الحلقة: $pct%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            NourProgress(pct / 100f)
                        }
                    }
                }
                // A study day on which a halaqa held no session is shown, so a teacher who never opened one is visible.
                val missing = halaqasShown.filter { h -> rowsOfDay.none { it.halaqaId == h.first } }
                items(missing, key = { "m-$ds-${it.first}" }) { h ->
                    NourCard(entity = com.mohammedsamir.halaqati.ui.theme.Entity.HALAQA) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(h.second, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(teachersOf[h.first].orEmpty().ifBlank { "بلا محفّظ" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            StatusPill("لم تُعقد جلسة", kind = "neutral")
                        }
                    }
                }
            }
            item { OutlinedButton(onClick = { days += 10 }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("عرض أيام أقدم") } }
        }
    }
}

/* ============================== follow-up and comparison for the supervisor ============================== */

@Composable
fun SupervisorInsightsScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf<SupBoard?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var metric by remember { mutableStateOf("commit") }
    var alertFor by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        try { board = withContext(Dispatchers.IO) { loadSupBoard(supervisorOnly = profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR) }; message = null }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { message = e.message ?: "تعذر التحميل" }
    }
    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { reload() }

    val b = board
    Column {
        AppTopBar("المتابعة والمقارنة", onRefresh = { scope.launch { reload() }; onRefresh() }, pending = pending)
        message?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        toast?.let { StateNotice(it, kind = "success", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        SegmentedTabs(listOf("المتابعة", "المقارنة"), tab, { tab = it }, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        if (b == null) { LoadingSkeleton(rows = 4, modifier = Modifier.padding(12.dp)); return@Column }
        LazyColumn(
            state = androidx.compose.foundation.lazy.rememberLazyListState(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tab == 0) {
                val notStarted = if (b.offDay) emptyList() else (b.halaqas + b.programs).filter { it.state == TeacherDayState.NOT_STARTED }
                val teachers = b.staff.filter { it.optString("role") == "teacher" }
                val quiet = teachers.filter { t -> (b.daysByCreator[t.optString("id")] ?: 0) == 0 && b.monthDays > 0 && (b.halaqas + b.programs).any { t.optString("id") in it.teacherIds } }
                val actions = b.visits.filter { it.supStr("action_items").isNotBlank() && !it.optBoolean("action_done", false) }
                val names = (b.halaqas + b.programs).associate { it.id to it.name }
                val absentToday = (b.halaqas + b.programs).sumOf { it.absent }
                val notRecited = (b.halaqas + b.programs).sumOf { (it.students - it.recited - it.absent - it.excused).coerceAtLeast(0) }
                item {
                    com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(Triple(notStarted.size.toString(), "لم يبدؤوا اليوم", notStarted.isNotEmpty()), Triple(quiet.size.toString(), "بلا نشاط هذا الشهر", quiet.isNotEmpty()), Triple(actions.size.toString(), "إجراءات مفتوحة", false)))
                }
                item { Text("الطلاب اليوم: $absentToday غائب · $notRecited لم يُسمَّع بعد", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { SupSectionTitle("محفظون لم يبدؤوا جلسة اليوم") }
                if (notStarted.isEmpty()) item { StateNotice(if (b.offDay) "اليوم إجازة: لا جلسات مقررة" else "الجميع بدأ جلسته اليوم", kind = if (b.offDay) "info" else "success") }
                items(notStarted, key = { "ns-" + it.id }) { h ->
                    SupPersonRow(h.teacherPhotos.firstOrNull(), h.teacherLine, h.name, HalaqatiGradients.Orange, "تنبيه", h.teacherIds.isNotEmpty()) {
                        alertFor = h.teacherIds.zip(h.teacherNames)
                    }
                }
                if (quiet.isNotEmpty()) {
                    item { SupSectionTitle("محفظون بلا جلسات هذا الشهر") }
                    items(quiet, key = { "q-" + it.optString("id") }) { t ->
                        SupPersonRow(t.supStr("avatar_path").ifBlank { null }, t.optString("full_name", "—"), "لم يفتح أي جلسة هذا الشهر", HalaqatiGradients.Gold, "تنبيه") {
                            alertFor = listOf(t.optString("id") to t.optString("full_name", "—"))
                        }
                    }
                }
                if (actions.isNotEmpty()) {
                    item { SupSectionTitle("إجراءات مطلوبة من الزيارات") }
                    items(actions.take(10), key = { "a-" + it.optString("id") }) { v ->
                        BorderedCard {
                            Text("${names[v.optString("halaqa_id")] ?: "حلقة"} · ${v.optString("visit_date")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v.supStr("action_items"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    item { OutlinedButton(onClick = { navigate("visits") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("فتح الزيارات") } }
                }
                item { OutlinedButton(onClick = { navigate("supervision") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("متابعة المحفظين بالتفصيل") } }
            } else {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProFilterChip(metric == "commit", { metric = "commit" }, "التزام المحفظين", showCheckIcon = false)
                        ProFilterChip(metric == "recited", { metric = "recited" }, "التسميع اليوم", showCheckIcon = false)
                        ProFilterChip(metric == "sessions", { metric = "sessions" }, "جلسات الشهر", showCheckIcon = false)
                        ProFilterChip(metric == "visit", { metric = "visit" }, "تقييم الزيارات", showCheckIcon = false)
                    }
                }
                val ranked: List<Triple<String, Double, String>> = when (metric) {
                    "commit" -> b.staff.filter { it.optString("role") == "teacher" }.map { t ->
                        val sc = b.scores[t.optString("id")]?.composite
                        Triple(t.optString("full_name", "—"), (sc ?: 0).toDouble(), if (sc != null) "$sc٪ · ${TeacherScore.band(sc).first}" else "لا بيانات")
                    }
                    "recited" -> (b.halaqas + b.programs).map { Triple(it.name, it.recitedPercent.toDouble(), "${it.recitedPercent}% (${it.recited} من ${it.students})") }
                    "sessions" -> (b.halaqas + b.programs).map { Triple(it.name, it.monthSessions.toDouble(), "${it.monthSessions} جلسة") }
                    else -> (b.halaqas + b.programs).mapNotNull { h -> h.lastVisitScore?.let { Triple(h.name, it * 20.0, "${oneDecimal(it)} من 5") } }
                }.sortedByDescending { it.second }
                val top = ranked.maxOfOrNull { it.second }?.takeIf { it > 0.0 } ?: 1.0
                if (ranked.isEmpty()) item { EmptyState("لا توجد بيانات كافية للمقارنة بعد") }
                items(ranked.size, key = { "r-$metric-$it" }) { i ->
                    val (name, value, label) = ranked[i]
                    NourCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (i < 3) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold,
                                    color = if (i < 3) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        NourProgress((value / top).toFloat())
                    }
                }
            }
        }
    }
    alertFor?.let { rs ->
        SupAlertDialog(recipients = rs, halaqaId = null, close = { alertFor = null }, onSent = { toast = "وصل التنبيه إلى ${rs.joinToString("، ") { it.second }}" })
    }
}
