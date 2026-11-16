package com.mohammedsamir.halaqati.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.plans.OrgInsightRules
import com.mohammedsamir.halaqati.ui.theme.statusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.mohammedsamir.halaqati.data.hifzSpanEntryOf
import java.time.LocalDate

/** Insight data is live: refetch at most every 5 minutes (repo.table would otherwise serve its cache forever). */
private object InsightFreshness {
    private val last = HashMap<String, Long>()
    @Synchronized fun dueFor(key: String): Boolean {
        val now = System.currentTimeMillis()
        val due = now - (last[key] ?: 0L) > 5 * 60_000L
        if (due) last[key] = now
        return due
    }
}

/** RC42: the last loaded result of each insight card, kept for the process so a card shows instantly when you come back to it. */
private object InsightMemo { val values = java.util.concurrent.ConcurrentHashMap<String, Any>() }
@Suppress("UNCHECKED_CAST")
private fun <T : Any> memo(key: String): T? = InsightMemo.values[key] as? T
private fun memoPut(key: String, value: Any) { InsightMemo.values[key] = value }

private fun JSONArray.objs() = (0 until length()).mapNotNull { optJSONObject(it) }

private fun openGuardianMessage(context: Context, phone: String?, message: String) {
    val clean = phone?.filter { it.isDigit() || it == '+' }.orEmpty()
    val intent = if (clean.isNotBlank()) {
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$clean")).putExtra("sms_body", message)
    } else {
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message), "إرسال رسالة لولي الأمر")
    }
    runCatching { context.startActivity(intent) }
}

/** RC27 home card: students absent from 3+ consecutive sessions, with a ready guardian message. */
@Composable
fun AbsenceStreakCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf(memo<List<Triple<String, Int, String?>>>("abs.rows") ?: emptyList()) } // name, streak, phone
    var halaqaOf by remember { mutableStateOf(memo<Map<String, String>>("abs.halaqa") ?: emptyMap()) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                if (!AppGraph.repo.online()) return@withContext emptyList<Triple<String, Int, String?>>() to emptyMap<String, String>()
                val from = LocalDate.now().minusDays(60).toString()
                val arr = AppGraph.repo.table(
                    "attendance_records",
                    "select=student_id,status,sessions!inner(session_date,status)&sessions.session_date=gte.$from&limit=5000",
                    forceRefresh = InsightFreshness.dueFor("absence"),
                )
                val att = arr.objs().mapNotNull { o ->
                    val s = o.optJSONObject("sessions") ?: return@mapNotNull null
                    OrgInsightRules.Attendance(o.optString("student_id"), s.optString("session_date"), o.optString("status"), s.optString("status") == "cancelled")
                }
                val students = AppGraph.repo.students(forceRefresh = false).filter { it.status == "active" }.associateBy { it.id }
                val halaqas = AppGraph.repo.table("halaqas", "select=id,name&limit=200").objs().associate { it.optString("id") to it.optString("name") }
                val streaks = OrgInsightRules.absenceStreaks(att).filter { it.studentId in students }
                streaks.map { st -> Triple(students.getValue(st.studentId).fullName, st.count, students.getValue(st.studentId).guardianPhone) } to
                    streaks.associate { st -> students.getValue(st.studentId).fullName to (halaqas[students.getValue(st.studentId).halaqaId].orEmpty()) }
            }
        }.onSuccess { (r, h) -> rows = r; halaqaOf = h; memoPut("abs.rows", r); memoPut("abs.halaqa", h) }
    }
    if (rows.isEmpty()) return
    InsightCardShell(
        kind = "error",
        icon = Icons.Default.EventBusy,
        title = "غياب متكرر",
        summary = if (expanded) "غابوا 3 جلسات متتالية فأكثر" else rows.take(4).joinToString("، ") { it.first } + if (rows.size > 4) " …" else "",
        count = rows.size,
        expanded = expanded,
        onClick = { expanded = !expanded },
        modifier = modifier,
    ) {
        if (expanded) {
            rows.take(15).forEach { (name, count, phone) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text("غاب $count جلسات متتالية", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { openGuardianMessage(context, phone, OrgInsightRules.guardianMessage(name, count, halaqaOf[name])) }) {
                        Text("رسالة للولي")
                    }
                }
            }
        }
    }
}

/** RC27.26 home card: active students who have not recited for 14+ days, with a ready guardian message. */
@Composable
fun InactiveStudentsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val windowDays = 45
    val threshold = 14
    var rows by remember { mutableStateOf(memo<List<Triple<String, Int?, String?>>>("inactive.rows") ?: emptyList()) } // name, daysSince, phone
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                if (!AppGraph.repo.online()) return@withContext emptyList<Triple<String, Int?, String?>>()
                val today = LocalDate.now()
                val from = today.minusDays(windowDays.toLong()).toString()
                val arr = AppGraph.repo.table(
                    "recitation_entries",
                    "select=student_id,sessions!inner(session_date,status)&sessions.session_date=gte.$from&limit=5000",
                    forceRefresh = InsightFreshness.dueFor("inactive"),
                )
                val last = HashMap<String, String>()
                arr.objs().forEach { o ->
                    val s = o.optJSONObject("sessions") ?: return@forEach
                    if (s.optString("status") == "cancelled") return@forEach
                    val id = o.optString("student_id")
                    val d = s.optString("session_date")
                    if (id.isNotBlank() && d > (last[id] ?: "")) last[id] = d
                }
                // An empty window usually means a blocked/failed read, not "nobody recited": stay silent.
                if (last.isEmpty()) return@withContext emptyList<Triple<String, Int?, String?>>()
                AppGraph.repo.smartPlanIneligibleHalaqaIds()
                // Program students (courses, tilawah/tajweed) are not measured by hifz recitation.
                val students = AppGraph.repo.students(forceRefresh = false).filter { it.status == "active" && AppGraph.repo.isSmartPlanEligible(it.halaqaId) }.associateBy { it.id }
                OrgInsightRules.inactiveStudents(students.keys, last, today, threshold, students.mapNotNull { (k, v) -> v.enrollmentDate?.let { k to it } }.toMap())
                    .map { Triple(students.getValue(it.studentId).fullName, it.daysSince, students.getValue(it.studentId).guardianPhone) }
            }
        }.onSuccess { rows = it; memoPut("inactive.rows", it) }
    }
    if (rows.isEmpty()) return
    InsightCardShell(
        kind = "warning",
        icon = Icons.Default.HourglassEmpty,
        title = "طلاب لم يسمّعوا منذ فترة",
        summary = if (expanded) "لم يُسجَّل لهم تسميع منذ $threshold يومًا فأكثر" else rows.take(4).joinToString("، ") { it.first } + if (rows.size > 4) " …" else "",
        count = rows.size,
        expanded = expanded,
        onClick = { expanded = !expanded },
        modifier = modifier,
    ) {
        if (expanded) {
            rows.take(15).forEach { (name, days, phone) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text(if (days != null) "آخر تسميع قبل $days يومًا" else "لا تسميع خلال آخر $windowDays يومًا", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { openGuardianMessage(context, phone, OrgInsightRules.inactiveGuardianMessage(name, days)) }) {
                        Text("رسالة للولي")
                    }
                }
            }
        }
    }
}

/**
 * RC37: Hifz and Sard pages are the continuous stretch from the first ayah to the last ayah reached, per student
 * (same definition as the plan screen), so reciting the same ayahs again does not add pages. Each recitation keeps its
 * own row for quality; the pages ride on one extra row per student and activity.
 */
private fun spanAwareRecitations(
    rows: List<JSONObject>,
    groupOf: (JSONObject, JSONObject) -> String?,
): List<OrgInsightRules.HalaqaRecitation> {
    val live = rows.filter { o -> o.optJSONObject("sessions")?.let { it.optString("status") != "cancelled" } == true }
    val out = ArrayList<OrgInsightRules.HalaqaRecitation>()
    fun spanned(o: JSONObject) = o.optString("activity_type").let { it == "new_hifz" || it == "sard" }
    live.forEach { o ->
        val s = o.optJSONObject("sessions") ?: return@forEach
        val group = groupOf(o, s) ?: return@forEach
        out += OrgInsightRules.HalaqaRecitation(
            group, o.optString("activity_type"),
            if (spanned(o)) 0.0 else o.optDouble("pages_count", 0.0),
            o.optString("evaluation"),
            o.optDouble("safety_percentage", Double.NaN).takeUnless { it.isNaN() },
        )
    }
    live.filter(::spanned).groupBy { it.optString("student_id") to it.optString("activity_type") }.forEach { (key, list) ->
        val last = list.last()
        val group = groupOf(last, last.optJSONObject("sessions") ?: return@forEach) ?: return@forEach
        out += OrgInsightRules.HalaqaRecitation(
            group, key.second,
            com.mohammedsamir.halaqati.plans.HifzSpanRules.pages(list.map(::hifzSpanEntryOf)),
            "", null,
        )
    }
    return out
}

/** Shared by the comparison card and the seasons card: standings of halaqas (or categories) for a date range; null when unavailable. */
internal suspend fun compareGroups(fromD: LocalDate, toD: LocalDate, byCategory: Boolean, fresh: Boolean): Pair<List<OrgInsightRules.HalaqaStats>, Map<String, String>>? =
    withContext(Dispatchers.IO) {
                if (!AppGraph.repo.online()) return@withContext null
                val range = "sessions.session_date=gte.$fromD&sessions.session_date=lte.$toD"
                AppGraph.repo.smartPlanIneligibleHalaqaIds()
                val halaqas = AppGraph.repo.table("halaqas", "select=id,name,active,halaqa_type&order=name.asc&limit=200").objs()
                    // Only regular halaqas are compared; programs are not recitation-based.
                    .filter { it.optBoolean("active", true) && com.mohammedsamir.halaqati.plans.SmartPlanEligibility.isHalaqaType(it.optString("halaqa_type").takeIf { t -> t != "null" }) }
                if (halaqas.size < 2) return@withContext null
                val halaqaName = halaqas.associate { it.optString("id") to it.optString("name") }
                val students = AppGraph.repo.students(forceRefresh = false)
                    .filter { it.status == "active" && it.halaqaId.orEmpty() in halaqaName }
                    .associateBy { it.id }
                val att = AppGraph.repo.table(
                    "attendance_records",
                    "select=student_id,status,sessions!inner(session_date,halaqa_id,status)&$range&limit=10000",
                    forceRefresh = fresh,
                ).objs()
                val rec = AppGraph.repo.table(
                    "recitation_entries",
                    "select=student_id,activity_type,start_surah,start_ayah,end_surah,end_ayah,pages_count,evaluation,safety_percentage,sessions!inner(session_date,halaqa_id,status)&$range&limit=10000",
                    forceRefresh = fresh,
                ).objs()
                if (!byCategory) {
                    val sessions = AppGraph.repo.table("sessions", "select=id,halaqa_id,session_date,status&session_date=gte.$fromD&session_date=lte.$toD&limit=5000", forceRefresh = fresh).objs().map {
                        OrgInsightRules.HalaqaSession(it.optString("halaqa_id"), it.optString("session_date"), it.optString("status") == "cancelled")
                    }
                    val attendance = att.mapNotNull { o ->
                        val s = o.optJSONObject("sessions") ?: return@mapNotNull null
                        if (s.optString("status") == "cancelled") null else OrgInsightRules.HalaqaAttendance(s.optString("halaqa_id"), o.optString("status"))
                    }
                    val recitations = spanAwareRecitations(rec) { _, s -> s.optString("halaqa_id") }
                    val perHalaqa = students.values.groupBy { it.halaqaId.orEmpty() }.mapValues { it.value.size }
                    OrgInsightRules.compare(halaqas.map { it.optString("id") }, perHalaqa, sessions, attendance, recitations) to halaqaName
                } else {
                    val categories = AppGraph.repo.table("student_categories", "select=id,halaqa_id,name,active&order=name.asc&limit=500", forceRefresh = false).objs()
                        .filter { it.optBoolean("active", true) && it.optString("halaqa_id") in halaqaName }
                    val catName = categories.associate { it.optString("id") to (it.optString("name") + " · " + halaqaName[it.optString("halaqa_id")].orEmpty()) }
                    val catOf = students.mapNotNull { (id, st) -> st.categoryId?.takeIf { it in catName }?.let { id to it } }.toMap()
                    val sessionKeys = ArrayList<OrgInsightRules.GroupedSession>()
                    val attendance = att.mapNotNull { o ->
                        val s = o.optJSONObject("sessions") ?: return@mapNotNull null
                        if (s.optString("status") == "cancelled") return@mapNotNull null
                        val cat = catOf[o.optString("student_id")] ?: return@mapNotNull null
                        sessionKeys += OrgInsightRules.GroupedSession(cat, s.optString("halaqa_id") + "|" + s.optString("session_date"))
                        OrgInsightRules.HalaqaAttendance(cat, o.optString("status"))
                    }
                    val recitations = spanAwareRecitations(rec) { o, _ -> catOf[o.optString("student_id")] }
                    val sessionCount = OrgInsightRules.distinctSessionsByGroup(sessionKeys)
                    val sessions = sessionCount.flatMap { (cat, n) -> (0 until n).map { OrgInsightRules.HalaqaSession(cat, it.toString(), false) } }
                    val perCategory = catOf.values.groupingBy { it }.eachCount()
                    OrgInsightRules.compare(perCategory.keys.toList(), perCategory, sessions, attendance, recitations) to catName
                }
    }

/**
 * RC27.31 home card (admin/supervisor): halaqas side by side, or categories side by side, for a chosen period.
 * Programs (tilawah/tajweed, courses) are never compared with halaqas.
 */
@Composable
fun HalaqaComparisonCard(modifier: Modifier = Modifier) {
    val memoStats = memo<List<OrgInsightRules.HalaqaStats>>("compare.stats")
    var stats by remember { mutableStateOf(memoStats ?: emptyList()) }
    var names by remember { mutableStateOf(memo<Map<String, String>>("compare.names") ?: emptyMap()) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    var period by rememberSaveable { mutableStateOf("month") }
    var byCategory by rememberSaveable { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(memoStats != null) }
    var unavailable by remember { mutableStateOf(false) }
    LaunchedEffect(period, byCategory) {
        runCatching {
            val (fromD, toD) = OrgInsightRules.comparePeriodRange(period, LocalDate.now())
            compareGroups(fromD, toD, byCategory, InsightFreshness.dueFor("compare:$period"))
        }.onSuccess { r ->
            if (r == null) unavailable = true else { unavailable = false; stats = r.first; names = r.second; loaded = true; if (period == "month" && !byCategory) { memoPut("compare.stats", r.first); memoPut("compare.names", r.second) } }
        }
    }
    // Keep the card (and its filters) visible once it has loaded, so switching to an empty period does not make it vanish.
    if (!loaded || unavailable) return
    InsightCardShell(
        kind = "neutral",
        icon = Icons.Default.Leaderboard,
        title = if (byCategory) "مقارنة الفئات" else "مقارنة الحلقات",
        summary = OrgInsightRules.COMPARE_PERIODS.first { it.first == period }.second + " · " + stats.size + (if (byCategory) " فئات" else " حلقات"),
        expanded = expanded,
        onClick = { expanded = !expanded },
        modifier = modifier,
    ) {
        if (expanded) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProFilterChip(!byCategory, { byCategory = false }, "الحلقات ككل", showCheckIcon = false)
                ProFilterChip(byCategory, { byCategory = true }, "حسب الفئات", showCheckIcon = false)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrgInsightRules.COMPARE_PERIODS.forEach { (key, label) ->
                    ProFilterChip(period == key, { period = key }, label, showCheckIcon = false)
                }
            }
            if (stats.isEmpty()) {
                Text("لا بيانات في هذه الفترة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Ranked list: groups with data first (best attendance on top); a group with no sessions yet is one quiet line.
            val ranked = OrgInsightRules.rank(stats)
            ranked.forEachIndexed { index, st ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))
                CompareRow(index + 1, names[st.halaqaId].orEmpty().ifBlank { if (byCategory) "فئة" else "حلقة" }, st)
            }
        }
    }
}

@Composable
private fun CompareRow(rank: Int, name: String, st: OrgInsightRules.HalaqaStats) {
    val hasData = st.sessions > 0
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = hasData) { open = !open }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = if (hasData) .14f else .06f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(rank.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (hasData) "${st.students} طالبًا · ${st.sessions} جلسات" else "${st.students} طالبًا · لا جلسات هذا الشهر بعد",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (hasData) {
            InsightMeter("الحضور", st.attendancePercent?.let { "$it٪" } ?: "—", st.attendancePercent?.let { it / 100f })
            InsightMeter("جودة التسميع", st.qualityPercent?.let { "$it٪" } ?: "—", st.qualityPercent?.let { it / 100f })
            if (open) {
                Text(
                    "صفحات/طالب %.1f".format(java.util.Locale.US, st.pagesPerStudent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "حفظ %.0f ص · مراجعة وتثبيت وسرد %.0f ص".format(java.util.Locale.US, st.hifzPages, st.reviewPages),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("اضغط لعرض الصفحات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
            }
        }
    }
}
