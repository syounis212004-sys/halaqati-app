@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.plans.OrgAttentionRules
import com.mohammedsamir.halaqati.plans.OrgInsightRules
import com.mohammedsamir.halaqati.plans.SmartPlanEligibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * RC63: the follow-up page as a fixed grid of tiles. Every tile is on screen from the first frame (last known numbers,
 * or a calm placeholder), all numbers load together, and a tile opens a bottom sheet with its list. Replaces five cards that
 * used to pop in one after another and vanished when empty.
 */
internal data class FuItem(val title: String, val detail: String = "", val sub: String = "", val phone: String? = null, val message: String? = null)

private object FollowUpMemo {
    val data = java.util.concurrent.ConcurrentHashMap<String, List<FuItem>>()
    @Volatile var at: Long = 0L
}

private fun JSONArray.fuObjs(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun sendGuardianMessage(context: Context, phone: String?, message: String) {
    val clean = phone?.filter { it.isDigit() || it == '+' }.orEmpty()
    val intent = if (clean.isNotBlank()) {
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$clean")).putExtra("sms_body", message)
    } else {
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message), "إرسال رسالة لولي الأمر")
    }
    runCatching { context.startActivity(intent) }
}

private suspend fun fuAbsence(fresh: Boolean): List<FuItem>? = withContext(Dispatchers.IO) {
    if (!AppGraph.repo.online()) return@withContext null
    val from = LocalDate.now().minusDays(60).toString()
    val arr = AppGraph.repo.table(
        "attendance_records",
        "select=student_id,status,sessions!inner(session_date,status)&sessions.session_date=gte.$from&limit=5000",
        forceRefresh = fresh,
    )
    val att = arr.fuObjs().mapNotNull { o ->
        val s = o.optJSONObject("sessions") ?: return@mapNotNull null
        OrgInsightRules.Attendance(o.optString("student_id"), s.optString("session_date"), o.optString("status"), s.optString("status") == "cancelled")
    }
    val students = AppGraph.repo.students(forceRefresh = false).filter { it.status == "active" }.associateBy { it.id }
    val halaqas = AppGraph.repo.table("halaqas", "select=id,name&limit=200").fuObjs().associate { it.optString("id") to it.optString("name") }
    OrgInsightRules.absenceStreaks(att).filter { it.studentId in students }.map { st ->
        val s = students.getValue(st.studentId)
        val hq = halaqas[s.halaqaId].orEmpty()
        FuItem(s.fullName, "غاب ${st.count} جلسات متتالية", hq, s.guardianPhone, OrgInsightRules.guardianMessage(s.fullName, st.count, hq))
    }
}

private suspend fun fuInactive(fresh: Boolean): List<FuItem>? = withContext(Dispatchers.IO) {
    if (!AppGraph.repo.online()) return@withContext null
    val today = LocalDate.now()
    val from = today.minusDays(45).toString()
    val arr = AppGraph.repo.table(
        "recitation_entries",
        "select=student_id,sessions!inner(session_date,status)&sessions.session_date=gte.$from&limit=5000",
        forceRefresh = fresh,
    )
    val last = HashMap<String, String>()
    arr.fuObjs().forEach { o ->
        val s = o.optJSONObject("sessions") ?: return@forEach
        if (s.optString("status") == "cancelled") return@forEach
        val id = o.optString("student_id")
        val d = s.optString("session_date")
        if (id.isNotBlank() && d > (last[id] ?: "")) last[id] = d
    }
    // An empty window usually means a blocked/failed read, not "nobody recited": report "unavailable", never a false all-clear.
    if (last.isEmpty()) return@withContext null
    AppGraph.repo.smartPlanIneligibleHalaqaIds()
    val students = AppGraph.repo.students(forceRefresh = false)
        .filter { it.status == "active" && AppGraph.repo.isSmartPlanEligible(it.halaqaId) }.associateBy { it.id }
    OrgInsightRules.inactiveStudents(students.keys, last, today, 14, students.mapNotNull { (k, v) -> v.enrollmentDate?.let { k to it } }.toMap())
        .map { r ->
            val s = students.getValue(r.studentId)
            FuItem(
                s.fullName,
                if (r.daysSince != null) "آخر تسميع قبل ${r.daysSince} يومًا" else "لا تسميع خلال آخر 45 يومًا",
                "", s.guardianPhone, OrgInsightRules.inactiveGuardianMessage(s.fullName, r.daysSince),
            )
        }
}

private suspend fun fuGuardian(): List<FuItem>? = withContext(Dispatchers.IO) {
    if (!AppGraph.repo.online()) return@withContext null
    val students = AppGraph.repo.students(forceRefresh = false).map { OrgAttentionRules.StudentInput(it.id, it.fullName, it.status, it.halaqaId, it.categoryId) }
    val arr = AppGraph.repo.table("student_guardians", "select=student_id&limit=5000", forceRefresh = true)
    val linked = arr.fuObjs().map { it.optString("student_id") }.toSet()
    if (linked.isEmpty()) return@withContext null
    OrgAttentionRules.studentsWithoutGuardian(students, linked).map { FuItem(it.name) }
}

private suspend fun fuNoPlan(): List<FuItem>? = withContext(Dispatchers.IO) {
    val ineligible = AppGraph.repo.smartPlanIneligibleHalaqaIds()
    val students = AppGraph.repo.students(forceRefresh = false).map { OrgAttentionRules.StudentInput(it.id, it.fullName, it.status, it.halaqaId, it.categoryId) }
    val plans = AppGraph.repo.plans(forceRefresh = false).map {
        OrgAttentionRules.PlanInput(it.scopeType, it.studentId, it.categoryId, it.halaqaId, it.status, it.startDate, it.endDate)
    }
    OrgAttentionRules.studentsWithoutPlan(
        students.filter { SmartPlanEligibility.isEligible(it.halaqaId, ineligible) },
        plans, LocalDate.now().toString(),
    ).map { FuItem(it.name) }
}

private suspend fun fuHalaqaSessions(): List<FuItem>? = withContext(Dispatchers.IO) {
    val today = LocalDate.now().toString()
    val sessions = AppGraph.repo.sessions(forceRefresh = false, includeArchived = false)
    val h = AppGraph.repo.table("halaqas", "select=id,name,active,halaqa_type&order=name.asc&limit=200", forceRefresh = false).fuObjs()
        .filter { it.optBoolean("active", true) && SmartPlanEligibility.isHalaqaType(it.optString("halaqa_type").takeIf { t -> t != "null" }) }
    val done = sessions.filter { it.date.startsWith(today) && it.status != "cancelled" }.map { it.halaqaId }.toSet()
    h.filter { it.optString("id") !in done }.map { FuItem(it.optString("name"), "لم تُسجَّل جلسة اليوم") }
}

private suspend fun fuNoTeacher(fresh: Boolean): List<FuItem>? = withContext(Dispatchers.IO) {
    val h = AppGraph.repo.table("halaqas", "select=id,name,active,halaqa_type&order=name.asc&limit=200", forceRefresh = fresh).fuObjs()
        .filter { it.optBoolean("active", true) && SmartPlanEligibility.isHalaqaType(it.optString("halaqa_type").takeIf { t -> t != "null" }) }
    val links = AppGraph.repo.table("halaqa_teachers", "select=halaqa_id,teacher_id&limit=1000", forceRefresh = fresh).fuObjs()
    if (h.isNotEmpty() && links.isEmpty() && !AppGraph.repo.online()) return@withContext null
    val staffed = links.map { it.optString("halaqa_id") }.toSet()
    h.filter { it.optString("id") !in staffed }.map { FuItem(it.optString("name"), "لا محفّظ مرتبط بهذه الحلقة") }
}

private data class FuTile(val key: String, val title: String, val icon: ImageVector, val kind: String, val openLabel: String?, val route: String?)

@Composable
fun FollowUpBoard(admin: Boolean, showPlans: Boolean, reloadKey: Int, navigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var data by remember { mutableStateOf<Map<String, List<FuItem>>>(FollowUpMemo.data.toMap()) }
    var loading by remember { mutableStateOf(true) }
    var stamp by remember { mutableStateOf(FollowUpMemo.at) }
    var open by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey) {
        loading = true
        val fresh = reloadKey > 0 || System.currentTimeMillis() - FollowUpMemo.at > 5 * 60_000L
        coroutineScope {
            val jobs = buildList {
                add("absence" to suspend { fuAbsence(fresh) })
                add("inactive" to suspend { fuInactive(fresh) })
                add("guardian" to suspend { fuGuardian() })
                if (admin) add("session" to suspend { fuHalaqaSessions() })
                add("teacher" to suspend { fuNoTeacher(fresh) })
                if (showPlans) add("noplan" to suspend { fuNoPlan() })
            }
            jobs.map { (key, load) ->
                async {
                    val r = runCatching { load() }.getOrNull()
                    if (r != null) { FollowUpMemo.data[key] = r; data = FollowUpMemo.data.toMap() }
                }
            }.awaitAll()
        }
        FollowUpMemo.at = System.currentTimeMillis()
        stamp = FollowUpMemo.at
        loading = false
    }

    val tiles = buildList {
        add(FuTile("absence", "غياب متكرر", Icons.Default.PersonOff, "danger", null, null))
        add(FuTile("inactive", "طلاب لم يسمّعوا منذ فترة", Icons.Default.HourglassEmpty, "warning", null, null))
        add(FuTile("guardian", "طلاب بلا ولي أمر", Icons.Default.FamilyRestroom, "info", "فتح الطلاب", "students"))
        if (admin) add(FuTile("session", "حلقات لم تسجّل جلسة اليوم", Icons.Default.EventBusy, "warning", "فتح الجلسات", "sessions"))
        add(FuTile("teacher", "حلقات بلا محفّظ", Icons.Default.SupervisorAccount, "danger", "إدارة الحلقات", "halaqas"))
        if (showPlans) add(FuTile("noplan", "طلاب بلا خطة هذا الشهر", Icons.Default.AssignmentLate, "info", "فتح الخطط", "plans"))
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { t ->
                    val items = data[t.key]
                    FuTileCard(t, count = items?.size, loading = loading && items == null, modifier = Modifier.weight(1f)) { open = t.key }
                }
            }
        }
        Text(
            when {
                loading -> "جارٍ التحديث…"
                stamp > 0L -> "آخر تحديث ${GazaTime.fromMillis(stamp).substringAfter(' ')}"
                else -> ""
            },
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val current = tiles.firstOrNull { it.key == open }
    if (current != null) {
        val rows = data[current.key].orEmpty()
        ModalBottomSheet(onDismissRequest = { open = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(current.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(if (rows.isEmpty()) "كل شيء سليم هنا" else "${rows.size} حالة", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp), state = rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(rows.take(60)) { r ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (r.detail.isNotBlank()) Text(r.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (r.sub.isNotBlank()) Text(r.sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (r.message != null) TextButton(onClick = { sendGuardianMessage(context, r.phone, r.message) }) { Text("رسالة للولي") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                if (current.route != null && current.openLabel != null) {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { open = null; navigate(current.route) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(current.openLabel, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun FuTileCard(t: FuTile, count: Int?, loading: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val clear = count == 0
    val accent = when {
        count == null -> cs.outline
        clear -> cs.primary
        t.kind == "danger" -> cs.error
        t.kind == "warning" -> cs.tertiary
        else -> cs.secondary
    }
    Surface(
        modifier = modifier.heightIn(min = 128.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = cs.surface,
        border = BorderStroke(1.5.dp, accent),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(if (clear) Icons.Default.CheckCircle else t.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(count?.toString() ?: "–", style = MaterialTheme.typography.displaySmall.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold, color = accent)
            }
            Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                when { count == null -> if (loading) "جارٍ التحديث…" else "غير متاح الآن"; clear -> "كل شيء سليم"; else -> "اضغط للتفاصيل" },
                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
            )
        }
    }
}
