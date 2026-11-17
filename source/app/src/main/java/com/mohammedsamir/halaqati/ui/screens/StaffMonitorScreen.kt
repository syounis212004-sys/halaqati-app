@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.components.*
import com.mohammedsamir.halaqati.ui.navigation.AddActionBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/** One row of the staff dashboard (a teacher or a supervisor) with the figures of the current month. */
private data class StaffRow(
    val id: String,
    val name: String,
    val isSupervisor: Boolean,
    val photo: String?,
    val phone: String,
    val halaqas: List<Pair<String, String>>,
    val sessions: Int,
    val sessionDays: Int,
    val closed: Int,
    val recitations: Int?,
    val lastSession: String?,
    val visitsReceived: Int,
    val visitScore: Double?,
    val visitsMade: Int,
    val halaqasVisited: Int,
    val openActions: Int,
    val frozen: Boolean,
    val score: TeacherScore? = null,
) {
    /** 0..100, or null when there is nothing to compare with yet. Teachers: the composite evaluation; supervisors: visited / assigned halaqat. */
    fun commitment(@Suppress("UNUSED_PARAMETER") monthDays: Int): Int? = if (isSupervisor) {
        if (halaqas.isEmpty()) null else ((halaqasVisited * 100.0) / halaqas.size).toInt().coerceIn(0, 100)
    } else score?.composite
}

private fun commitmentLabel(v: Int?): Pair<String, String> = TeacherScore.band(v)

/**
 * RC53: "متابعة المحفظين" (supervisor) / "متابعة المحفظين والمشرفين" (administrator).
 * One expandable row per person: commitment, sessions, visits and open action items, with quick alert / visit buttons.
 */
@Composable
fun StaffMonitorScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val isAdmin = profile.role == UserRole.ADMIN
    var board by remember { mutableStateOf<SupBoard?>(null) }
    var rows by remember { mutableStateOf<List<StaffRow>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var alertFor by remember { mutableStateOf<StaffRow?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }

    suspend fun reload() {
        try {
            val result = withContext(Dispatchers.IO) {
                val repo = AppGraph.repo
                val b = loadSupBoard(supervisorOnly = profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR)
                val today = LocalDate.now()
                val monthStart = today.withDayOfMonth(1).toString()
                val supLinks = repo.table("halaqa_supervisors", "select=halaqa_id,supervisor_id&limit=500", forceRefresh = repo.online()).sRows()
                val hq = repo.table("halaqas", "select=id,name,supervisor_id&active=eq.true&limit=200", forceRefresh = repo.online()).sRows()
                val names = hq.associate { it.optString("id") to it.optString("name", "حلقة") }
                val built = b.staff.filter { isAdmin || it.optString("role") == "teacher" }.map { st ->
                    val id = st.optString("id")
                    val sup = st.optString("role") == "supervisor"
                    val myHalaqas: List<Pair<String, String>> = if (sup) {
                        (supLinks.filter { it.optString("supervisor_id") == id }.map { it.optString("halaqa_id") } +
                            hq.filter { it.optString("supervisor_id") == id }.map { it.optString("id") })
                            .distinct().map { it to (names[it] ?: "حلقة") }
                    } else (b.halaqas + b.programs).filter { id in it.teacherIds }.map { it.id to it.name }
                    val myIds = myHalaqas.map { it.first }.toSet()
                    val received = b.visits.filter { it.optString("halaqa_id") in myIds && it.optString("visit_date") >= monthStart }
                    val made = b.visits.filter { it.optString("supervisor_id") == id && it.optString("visit_date") >= monthStart }
                    StaffRow(
                        id = id,
                        name = st.optString("full_name", "—"),
                        isSupervisor = sup,
                        photo = st.optString("avatar_path").takeIf { it.isNotBlank() && it != "null" },
                        phone = st.optString("phone").takeIf { it != "null" }.orEmpty(),
                        halaqas = myHalaqas,
                        sessions = b.sessionsByCreator[id] ?: 0,
                        sessionDays = b.daysByCreator[id] ?: 0,
                        closed = b.closedByCreator[id] ?: 0,
                        recitations = b.recitationsByTeacher[id],
                        lastSession = b.lastSessionByCreator[id],
                        visitsReceived = received.size,
                        visitScore = received.mapNotNull { visitAverage(it) }.takeIf { it.isNotEmpty() }?.average(),
                        visitsMade = made.size,
                        halaqasVisited = made.map { it.optString("halaqa_id") }.filter { it in myIds }.distinct().size,
                        openActions = b.visits.count { it.optString("halaqa_id") in myIds && it.supStr("action_items").isNotBlank() && !it.optBoolean("action_done", false) },
                        frozen = st.optBoolean("frozen", false),
                        score = b.scores[id],
                    )
                }
                b to built
            }
            board = result.first
            rows = result.second
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تحميل المتابعة" }
    }
    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { reload() }
    // The "+" sheet asks for a teacher alert: it just needs this page; the alert is chosen per row.
    val request by AddActionBus.requested.collectAsState()
    LaunchedEffect(request) { if (request == "alert") AddActionBus.consume() }

    val b = board
    val shownRows = rows.filter { (tab == 1) == it.isSupervisor && (query.isBlank() || it.name.contains(query.trim())) }
    val monthDays = b?.monthDays ?: 0

    Column {
        AppTopBar(
            if (isAdmin) "متابعة المحفظين والمشرفين" else "متابعة المحفظين",
            onRefresh = { scope.launch { reload() }; onRefresh() }, pending = pending,
        )
        message?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        toast?.let { StateNotice(it, kind = "success", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (isAdmin) SegmentedTabs(listOf("المحفظون", "المشرفون"), tab, { tab = it; expanded = null }, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, label = { Text("بحث بالاسم") },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Info, contentDescription = "كيف يُحسب التقييم") }
        }
        if (b == null) { LoadingSkeleton(rows = 4, modifier = Modifier.padding(12.dp)); return@Column }
        LazyColumn(
            state = androidx.compose.foundation.lazy.rememberLazyListState(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val good = shownRows.count { (it.commitment(monthDays) ?: 0) >= 65 }
                val low = shownRows.count { it.commitment(monthDays)?.let { v -> v < 50 } == true }
                com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(Triple(shownRows.size.toString(), "العدد", false), Triple(good.toString(), "أداء جيد فأعلى", false), Triple(low.toString(), "يحتاجون متابعة", low > 0)))
            }
            if (shownRows.isEmpty()) item { EmptyState(if (tab == 1) "لا يوجد مشرفون" else "لا يوجد محفظون") }
            items(shownRows, key = { it.id }) { r ->
                val c = r.commitment(monthDays)
                val (label, kind) = commitmentLabel(if (r.halaqas.isEmpty() && !r.isSupervisor) null else c)
                val noHalaqa = r.halaqas.isEmpty() && !r.isSupervisor
                val open = expanded == r.id
                val tone = nourTone(if (noHalaqa) "neutral" else kind).first
                NourCard(onClick = { expanded = if (open) null else r.id }, marker = if (noHalaqa) null else tone) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NourAvatar(r.photo, r.name, NourAvatarSize.Small)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = com.mohammedsamir.halaqati.ui.theme.EntityColors.of(if (r.isSupervisor) com.mohammedsamir.halaqati.ui.theme.Entity.SUPERVISOR else com.mohammedsamir.halaqati.ui.theme.Entity.TEACHER).accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (r.halaqas.isEmpty()) (if (r.isSupervisor) "بلا حلقات" else "بلا حلقة") else r.halaqas.joinToString("، ") { it.second },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (!noHalaqa) StatusPill(if (c != null) "$label · $c٪" else label, kind = kind)
                            else StatusPill("بلا حلقة", kind = "info")
                        }
                        Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (open) "طيّ" else "توسيع", modifier = Modifier.size(NourIcon.Action))
                    }
                    if (!r.isSupervisor) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(r.sessions.toString() to "جلسات الشهر", "${r.sessionDays}/$monthDays" to "أيام النشاط", r.closed.toString() to "مُنهاة").forEach { (v, l) ->
                            Surface(Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(v, style = MaterialTheme.typography.titleSmall.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold)
                                    Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                    if (open) {
                        HorizontalDivider()
                        if (r.frozen) StatusPill("الحساب مجمّد", kind = "danger")
                        val sc = r.score
                        if (!r.isSupervisor && sc != null) {
                            listOf(
                                Triple("الالتزام بالحضور (30٪)", sc.commitment, "أيام جلساته ÷ أيام الشهر"),
                                Triple("حضور الطلاب (20٪)", sc.studentAttendance, "حاضر ومتأخر ÷ (حاضر ومتأخر وغائب)"),
                                Triple("تغطية التسميع (25٪)", sc.recitationCoverage, "طلاب سمّعوا ÷ طلاب حلقته"),
                                Triple("إنهاء الجلسات (10٪)", sc.endedSessions, "الجلسات المنتهية ÷ كل جلساته"),
                                Triple("تقييم الزيارات (15٪)", sc.visits, "متوسط تقييم زيارات المشرف ÷ 5"),
                            ).forEach { (t, v, hint) ->
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(t, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Text(if (v != null) "${(v * 100).toInt()}٪" else "—", style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.Bold)
                                    }
                                    NourProgress((v ?: 0.0).toFloat())
                                    Text(if (v != null) hint else "لا بيانات بعد لهذا المؤشر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("التسميعات المسجلة: ${r.recitations?.toString() ?: "—"} · آخر جلسة: ${r.lastSession ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                            Text("زيارات المشرف له هذا الشهر: ${r.visitsReceived}" + (r.visitScore?.let { " · متوسط التقييم ${oneDecimal(it)}/5" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                            if (r.openActions > 0) Text("إجراءات مطلوبة منه لم تُنفّذ: ${r.openActions}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        } else if (r.isSupervisor) {
                            Text("حلقات تحت إشرافه: ${r.halaqas.size} · زار منها هذا الشهر: ${r.halaqasVisited}", style = MaterialTheme.typography.bodyMedium)
                            Text("عدد الزيارات المسجلة: ${r.visitsMade} · آخر نشاط: ${r.lastSession ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (r.phone.isNotBlank()) Text("الهاتف: ${r.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { alertFor = r }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("إرسال تنبيه", maxLines = 1) }
                            if (!r.isSupervisor && r.halaqas.isNotEmpty()) OutlinedButton(
                                onClick = { AddActionBus.request("visit:${r.halaqas.first().first}"); navigate("visits") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text("تسجيل زيارة", maxLines = 1) }
                            if (isAdmin) OutlinedButton(onClick = { navigate("users") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("الحساب", maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
    if (showInfo) AlertDialog(
        onDismissRequest = { showInfo = false },
        title = { Text("كيف يُحسب التقييم؟") },
        text = { Text("تقييم المحفّظ يجمع خمسة مؤشرات: الالتزام بالحضور 30٪، حضور الطلاب 20٪، تغطية التسميع 25٪، إنهاء الجلسات 10٪، تقييم زيارات المشرف 15٪. المؤشر غير المتوفر يُستبعد وتُعاد الأوزان. المشرف: الحلقات التي زارها ÷ حلقاته.") },
        confirmButton = { TextButton(onClick = { showInfo = false }) { Text("حسنًا") } },
    )
    alertFor?.let { r ->
        SupAlertDialog(
            recipients = listOf(r.id to r.name), halaqaId = r.halaqas.firstOrNull()?.first,
            close = { alertFor = null }, onSent = { toast = "وصل التنبيه إلى ${r.name}" },
        )
    }
}
