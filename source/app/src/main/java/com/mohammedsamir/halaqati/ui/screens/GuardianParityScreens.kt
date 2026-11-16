@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.Student
import com.mohammedsamir.halaqati.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun GuardianCompetitionNativeScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var selectedId by remember { mutableStateOf("") }
    var track by remember { mutableStateOf("hifz") }
    var boardScope by remember { mutableStateOf("halaqa") }
    var period by remember { mutableStateOf("month") }
    var board by remember { mutableStateOf(JSONArray()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    fun range(): Pair<String,String> {
        val now = LocalDate.now()
        return when (period) {
            "week" -> now.minusDays(6).toString() to now.toString()
            "year" -> now.withDayOfYear(1).toString() to now.toString()
            else -> YearMonth.now().atDay(1).toString() to now.toString()
        }
    }

    // RC17: children load once; filters only reload the board. Tathbit students compete in tathbit only.
    val selectedStudent = students.firstOrNull { it.id == selectedId }
    val tathbitChild = selectedStudent?.track == "tathbit"
    val effectiveTrack = if (tathbitChild) "tathbit" else track

    suspend fun loadChildren(force: Boolean = false) {
        val loaded = withContext(Dispatchers.IO) { AppGraph.repo.guardianChildren(forceRefresh = force).filter { it.status == "active" } }
        students = loaded
        if (selectedId.isBlank() || loaded.none { it.id == selectedId }) selectedId = loaded.firstOrNull()?.id.orEmpty()
        if (loaded.isEmpty()) loading = false
    }

    suspend fun reload(force: Boolean = false) {
        if (force) loadChildren(true)
        val id = selectedId
        if (id.isBlank()) { board = JSONArray(); loading = false; return }
        loading = board.length() == 0
        try {
            val (start,end) = range()
            board = withContext(Dispatchers.IO) { AppGraph.repo.guardianCompetition(id, effectiveTrack, boardScope, start, end) }
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تحميل المنافسة" }
        finally { loading = false }
    }

    LaunchedEffect(Unit) { runCatching { loadChildren() }.onFailure { message = it.message; loading = false } }
    LaunchedEffect(selectedId, effectiveTrack, boardScope, period) { if (selectedId.isNotBlank()) reload() }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("إحصائيات ومنافسة", onRefresh = { scope.launch { reload(true) }; onRefresh() }, pending = pending)
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GoldenFeatureHero(
                    title = "منافسة أبنائي",
                    subtitle = "ترتيب فعلي بين طلاب نفس المسار داخل الحلقة أو الفئة أو المركز.",
                    badge = "المنافسة",
                    art = com.mohammedsamir.halaqati.ui.components.HalaqatiAnimation.Trophy,
                )
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الطالب", fontWeight = FontWeight.Bold)
                        SingleChoiceDropdown(
                            value = selectedId,
                            options = students.map { it.id to it.fullName },
                            onValueChange = { selectedId = it },
                            label = "الطالب",
                            emptyLabel = "لا يوجد طالب مرتبط",
                        )
                        Text("المسار", fontWeight = FontWeight.Bold)
                        if (tathbitChild) {
                            StatusPill("التثبيت", "success")
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("hifz" to "الحفظ", "review" to "المراجعة", "sard" to "السرد").forEach { (v,l) ->
                                    ProFilterChip(track == v, { track = v }, l, Modifier.weight(1f))
                                }
                            }
                        }
                        Text("نطاق المنافسة", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("halaqa" to "الحلقة", "category" to "الفئة", "center" to "المركز").forEach { (v,l) ->
                                ProFilterChip(boardScope == v, { boardScope = v }, l, Modifier.weight(1f))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("week" to "7 أيام", "month" to "الشهر", "year" to "السنة").forEach { (v,l) ->
                                ProFilterChip(period == v, { period = v }, l, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            message?.let { item { StateNotice(it, "error") } }
            if (loading) item { LoadingSkeleton(rows = 4) }
            if (!loading && board.length() == 0) item { EmptyState("لا توجد نتائج منافسة لهذه الفترة") }
            itemsIndexed((0 until board.length()).mapNotNull { board.optJSONObject(it) }) { index,row ->
                val rank = row.optInt("rank", index + 1).takeIf { it > 0 } ?: index + 1
                val name = row.optString("student_name", row.optString("full_name", "طالب"))
                val pages = row.optDouble("pages", row.optDouble("total_pages", 0.0))
                val safety = row.optDouble("safety", row.optDouble("avg_safety", -1.0))
                val mine = row.optBoolean("is_current", false) || row.optString("student_id") == selectedId
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(rank.toString(), Modifier.padding(horizontal = 13.dp, vertical = 9.dp), color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("${plainGuardianNumber(pages)} صفحة${if (safety >= 0) " · جودة ${plainGuardianNumber(safety)}%" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (mine) StatusPill("ابني", "success")
                    }
                }
            }
        }
    }
}

@Composable
fun GuardianAchievementsNativeScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var selectedId by remember { mutableStateOf("") }
    var dashboard by remember { mutableStateOf(JSONObject()) }
    var badges by remember { mutableStateOf(JSONArray()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    // RC52: explicit period — by month (‹ ›) or by last N days — so the numbers always say what they cover.
    var periodMode by remember { mutableIntStateOf(0) }
    var monthOffset by remember { mutableIntStateOf(0) }
    var daysIdx by remember { mutableIntStateOf(1) }
    val dayOptions = listOf(7, 30, 90)
    val periodMonth = YearMonth.now().minusMonths(monthOffset.toLong())
    val periodFrom: LocalDate = if (periodMode == 0) periodMonth.atDay(1) else LocalDate.now().minusDays(dayOptions[daysIdx] - 1L)
    val periodTo: LocalDate = if (periodMode == 0) minOf(periodMonth.atEndOfMonth(), LocalDate.now()) else LocalDate.now()
    val periodTitle = if (periodMode == 0) "إنجاز شهر ${guardianMonthName(periodMonth.monthValue)} ${periodMonth.year}" else "إنجاز آخر ${dayOptions[daysIdx]} ${if (dayOptions[daysIdx] == 7) "أيام" else "يومًا"}"

    suspend fun reload(force: Boolean = false) {
        loading = true
        try {
            val list = withContext(Dispatchers.IO) { AppGraph.repo.guardianChildren(forceRefresh = force).filter { it.status == "active" } }
            students = list
            if (selectedId.isBlank() || list.none { it.id == selectedId }) selectedId = list.firstOrNull()?.id.orEmpty()
            val id = selectedId.ifBlank { list.firstOrNull()?.id.orEmpty() }
            if (id.isNotBlank()) {
                val loaded = withContext(Dispatchers.IO) {
                    AppGraph.repo.guardianDashboard(id, periodFrom.toString(), periodTo.toString()) to
                        AppGraph.repo.studentAchievementBadges(id)
                }
                dashboard = loaded.first
                badges = loaded.second
            } else {
                dashboard = JSONObject()
                badges = JSONArray()
            }
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تحميل الإنجازات" }
        finally { loading = false }
    }
    LaunchedEffect(selectedId, periodMode, monthOffset, daysIdx) { reload() }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("الإنجازات", onRefresh = { scope.launch { reload(true) }; onRefresh() }, pending = pending)
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GoldenFeatureHero(
                    title = "إنجازات الطالب",
                    subtitle = "إنجاز الابن في مساره، مع الحضور والجودة.",
                    badge = "الإنجازات",
                    art = com.mohammedsamir.halaqati.ui.components.HalaqatiAnimation.Trophy,
                )
            }
            item {
                SingleChoiceDropdown(selectedId, students.map { it.id to it.fullName }, { selectedId = it }, "الطالب", "لا يوجد طالب مرتبط")
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.mohammedsamir.halaqati.ui.components.SegmentedTabs(listOf("حسب الشهر", "حسب الأيام"), periodMode, { periodMode = it })
                    if (periodMode == 0) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (monthOffset < 11) monthOffset++ }, modifier = Modifier.size(48.dp), enabled = monthOffset < 11) { Icon(Icons.Default.ChevronRight, "الشهر السابق") }
                            Text("${guardianMonthName(periodMonth.monthValue)} ${periodMonth.year}", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { if (monthOffset > 0) monthOffset-- }, modifier = Modifier.size(48.dp), enabled = monthOffset > 0) { Icon(Icons.Default.ChevronLeft, "الشهر التالي") }
                        }
                    } else {
                        com.mohammedsamir.halaqati.ui.components.SegmentedTabs(listOf("7 أيام", "30 يومًا", "90 يومًا"), daysIdx, { daysIdx = it })
                    }
                    Text(periodTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            message?.let { item { StateNotice(it, "error") } }
            if (loading) item { LoadingSkeleton(rows = 4) }
            if (!loading && selectedId.isNotBlank()) {
                val ranks = dashboard.optJSONObject("ranks") ?: JSONObject()
                val attendance = dashboard.optJSONObject("attendance") ?: JSONObject()
                val hifz = ranks.optJSONObject("hifz") ?: JSONObject()
                val review = ranks.optJSONObject("review") ?: JSONObject()
                val sard = ranks.optJSONObject("sard") ?: JSONObject()
                val present = attendance.optInt("present", 0)
                val absent = attendance.optInt("absent", 0)
                val excused = attendance.optInt("excused", 0)
                val late = attendance.optInt("late", 0)
                // RC17: a tathbit child sees tathbit only — never hifz/review/sard as separate tracks.
                val tathbitChild = students.firstOrNull { it.id == selectedId }?.track == "tathbit"
                val tathbitPages = (ranks.optJSONObject("tathbit")?.optDouble("pages", -1.0)?.takeIf { it >= 0 })
                    ?: (hifz.optDouble("pages",0.0) + review.optDouble("pages",0.0) + sard.optDouble("pages",0.0))
                if (tathbitChild) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuardianAchievementMetric("التثبيت", "${plainGuardianNumber(tathbitPages)} ص", Icons.Default.MenuBook, Modifier.weight(1f))
                    }
                } else item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuardianAchievementMetric("الحفظ", "${plainGuardianNumber(hifz.optDouble("pages",0.0))} ص", Icons.Default.MenuBook, Modifier.weight(1f))
                        GuardianAchievementMetric("المراجعة", "${plainGuardianNumber(review.optDouble("pages",0.0))} ص", Icons.Default.Replay, Modifier.weight(1f))
                        GuardianAchievementMetric("السرد", "${plainGuardianNumber(sard.optDouble("pages",0.0))} ص", Icons.Default.AutoStories, Modifier.weight(1f))
                    }
                }
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("الحضور", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GuardianSmallMetric("حاضر", present, Modifier.weight(1f))
                                GuardianSmallMetric("غياب", absent, Modifier.weight(1f))
                                GuardianSmallMetric("بعذر", excused, Modifier.weight(1f))
                                GuardianSmallMetric("تأخر", late, Modifier.weight(1f))
                            }
                        }
                    }
                }
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("شارات الإنجاز الموثقة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("تُمنح تلقائيًا من بيانات التسميع والحضور والخطة، مع حفظ سبب وتاريخ كل شارة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val badgeRows = (0 until badges.length()).mapNotNull { badges.optJSONObject(it) }.take(12)
                            if (badgeRows.isEmpty()) {
                                Text("لا توجد شارة مستحقة بعد. ستظهر تلقائيًا عند تحقق شروطها.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                badgeRows.forEach { badge ->
                                    Surface(
                                        Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.large,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
                                    ) {
                                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(9.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(badge.optString("title", "إنجاز"), fontWeight = FontWeight.Bold)
                                                Text(badge.optString("description"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(badge.optString("period_month").take(7).replace('-', '/'), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardianAchievementMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun GuardianSmallMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value.toString(), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun SingleChoiceDropdown(value: String, options: List<Pair<String,String>>, onValueChange: (String)->Unit, label: String, emptyLabel: String = label) {
    HalaqatiLazySelectField(
        label = label,
        selectedKey = value,
        options = options,
        onSelected = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        searchable = options.size > 12,
        emptyLabel = emptyLabel,
    )
}

private fun plainGuardianNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (kotlin.math.abs(rounded - rounded.toInt()) < 0.000001) rounded.toInt().toString() else rounded.toString().trimEnd('0').trimEnd('.')
}

private fun guardianMonthName(m: Int): String = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر").getOrElse(m - 1) { "" }
