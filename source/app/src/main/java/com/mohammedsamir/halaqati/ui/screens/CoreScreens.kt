@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.theme.calmContentSize
import com.mohammedsamir.halaqati.ui.theme.motionEnter
import com.mohammedsamir.halaqati.ui.theme.motionExit
import com.mohammedsamir.halaqati.ui.scope.*
import com.mohammedsamir.halaqati.ui.components.HalaqaScopeBar
import com.mohammedsamir.halaqati.ui.components.WorkKindToggle
import com.mohammedsamir.halaqati.ui.components.rememberWorkKind
import com.mohammedsamir.halaqati.ui.components.MonthSectionHeader
import com.mohammedsamir.halaqati.ui.components.HalaqaOverviewCard
import com.mohammedsamir.halaqati.ui.components.PlanHistoryLine
import com.mohammedsamir.halaqati.plans.PlanHistoryRules
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import com.mohammedsamir.halaqati.ui.components.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.PlanSnapshot
import com.mohammedsamir.halaqati.data.SafetyScoring
import com.mohammedsamir.halaqati.data.StudentGuardianRules
import com.mohammedsamir.halaqati.data.StudentEnrollmentRules
import com.mohammedsamir.halaqati.data.OfflineQueue
import com.mohammedsamir.halaqati.model.*
import com.mohammedsamir.halaqati.quran.QuranCore
import com.mohammedsamir.halaqati.quran.QuranLocation
import com.mohammedsamir.halaqati.quran.QuranMetadata
import com.mohammedsamir.halaqati.quran.QuranRangeRequest
import com.mohammedsamir.halaqati.quran.QuranRangeResult
import com.mohammedsamir.halaqati.quran.QuranSelection
import com.mohammedsamir.halaqati.quran.QuranTraversalDirection
import com.mohammedsamir.halaqati.quran.RecitationParityRules
import com.mohammedsamir.halaqati.plans.SmartPlanEngine
import com.mohammedsamir.halaqati.plans.SmartPlanUiRules
import com.mohammedsamir.halaqati.plans.SmartPlanHealthRules
import com.mohammedsamir.halaqati.plans.SmartPlanActivityRules
import com.mohammedsamir.halaqati.ui.components.*
import com.mohammedsamir.halaqati.ui.sessions.SessionWorkflowRules
import com.mohammedsamir.halaqati.ui.sessions.EducationScoreRules
import com.mohammedsamir.halaqati.ui.sessions.SessionScheduleRules
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import com.mohammedsamir.halaqati.data.hifzSpanEntryOf

@Composable
private fun CompactFilterSelect(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    HalaqatiLazySelectField(
        label = label,
        selectedKey = value,
        options = options,
        onSelected = onSelected,
        modifier = modifier.fillMaxWidth(),
        searchable = options.size > 12,
    )
}

@Composable
fun StudentsScreen(
    pending: Int,
    onRefresh: () -> Unit,
    initialStudentId: String? = null,
    onInitialStudentConsumed: () -> Unit = {},
    openStudentProfile: (String) -> Unit = {},
    role: String = "",
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Student>>(emptyList()) }
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var categories by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var snapshotsByStudent by remember { mutableStateOf<Map<String, PlanSnapshot>>(emptyMap()) }
    var monthStatsByStudent by remember { mutableStateOf<Map<String, JSONObject>>(emptyMap()) }
    var monthStatsScope by remember { mutableStateOf<String?>(null) }
    var secondaryHalaqasByStudent by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var studentFiltersOpen by rememberSaveable { mutableStateOf(false) }
    var expandedStudentId by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var trackFilter by rememberSaveable { mutableStateOf("all") }
    var statusFilter by rememberSaveable { mutableStateOf("active") }
    var halaqaFilter by rememberSaveable { mutableStateOf("all") }
    var categoryFilter by rememberSaveable { mutableStateOf("all") }
    var sortMode by rememberSaveable { mutableStateOf("alpha") }
    var add by remember { mutableStateOf(false) }
    val studentAddRequested by com.mohammedsamir.halaqati.ui.navigation.AddActionBus.requested.collectAsState()
    LaunchedEffect(studentAddRequested) {
        if (studentAddRequested == "student") {
            com.mohammedsamir.halaqati.ui.navigation.AddActionBus.consume()
            add = true
        }
    }
    var selectedStudent by remember { mutableStateOf<Student?>(null) }
    var directRecitation by remember { mutableStateOf<Triple<Student, SessionRow, PlanSnapshot?>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val studentsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val workKind = rememberWorkKind()

    suspend fun reloadStudents(force: Boolean = false) {
        loading = rows.isEmpty()
        try {
            val loaded = withContext(Dispatchers.IO) {
                val studentRows = AppGraph.repo.students(forceRefresh = force)
                val h = AppGraph.repo.table("halaqas", "select=id,name,active,halaqa_type,program_specialization,affects_primary_plan,starts_on,ends_on&order=name.asc&limit=200", forceRefresh = force)
                val c = AppGraph.repo.table("student_categories", "select=id,halaqa_id,name,active&order=name.asc&limit=500", forceRefresh = force)
                val m = AppGraph.repo.studentHalaqaMemberships(forceRefresh = force)
                val membershipMap = (0 until m.length()).mapNotNull { m.optJSONObject(it) }
                    .filter { it.optString("status", "active") == "active" }
                    .groupBy { it.optString("student_id") }
                    .mapValues { (_, list) -> list.mapNotNull { it.optString("halaqa_id").takeIf(String::isNotBlank) }.toSet() }
                (Triple(
                    studentRows,
                    (0 until h.length()).mapNotNull { h.optJSONObject(it) },
                    (0 until c.length()).mapNotNull { c.optJSONObject(it) },
                )) to membershipMap
            }
            rows = loaded.first.first
            halaqas = loaded.first.second
            categories = loaded.first.third
            secondaryHalaqasByStudent = loaded.second
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر تحميل الطلاب"
        } finally {
            loading = false
        }
    }

    suspend fun reload(force: Boolean = false) {
        val startedAt = System.nanoTime()
        reloadStudents(force)
        com.mohammedsamir.halaqati.data.PerfLog.record("students.reload", (System.nanoTime() - startedAt) / 1_000_000L)
    }

    var recitationChoices by remember { mutableStateOf<Pair<Student, List<SessionRow>>?>(null) }

    fun openStudentRecitation(student: Student, session: SessionRow) {
        val snapshot = snapshotsByStudent[student.id]?.takeIf {
            it.plan.halaqaId == session.halaqaId &&
                it.progress.assignment?.kind?.let(SessionWorkflowRules::sessionTypeForAssignment) == session.type
        }
        directRecitation = Triple(student, session, snapshot)
    }

    fun startTodayRecitation(student: Student) {
        if (student.status != "active") {
            message = "الطالب غير نشط"
            return
        }
        scope.launch {
            try {
                val accessibleMemberships = secondaryHalaqasByStudent[student.id].orEmpty() + listOfNotNull(student.halaqaId)
                val requestedScope = halaqaFilter.takeUnless { it == "all" }
                val candidates = halaqas.filter { halaqa ->
                    val program = com.mohammedsamir.halaqati.plans.WorkModeRules.isProgramType(halaqa.optString("halaqa_type"))
                    halaqa.optString("id") in accessibleMemberships &&
                        halaqa.optBoolean("active", true) &&
                        (requestedScope != null || program == (workKind == com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM))
                }.map { it.optString("id") }
                val halaqaId = (requestedScope ?: candidates.singleOrNull())
                    ?.takeIf { it in candidates }
                    ?: error("اختر حلقة أو برنامجًا يشارك فيه الطالب أولًا")
                val today = LocalDate.now().toString()
                val sessions = withContext(Dispatchers.IO) { AppGraph.repo.sessions() }.filter {
                    it.halaqaId == halaqaId && it.date == today && it.status == "open" &&
                        it.type in setOf("tasmee", "sard", "review", "test", "tilawah", "tajweed")
                }
                check(sessions.isNotEmpty()) { "لا توجد جلسة تسميع أو تلاوة مفتوحة اليوم في هذا النشاط" }
                selectedStudent = null
                message = null
                if (sessions.size == 1) {
                    openStudentRecitation(student, sessions.single())
                } else {
                    recitationChoices = student to sessions
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                selectedStudent = null
                message = e.message ?: "تعذر فتح جلسة اليوم"
            }
        }
    }

    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(syncEpoch) { if (syncEpoch > 0L) reload() }

    LaunchedEffect(initialStudentId, rows) {
        val id = initialStudentId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        rows.firstOrNull { it.id == id }?.let { student ->
            selectedStudent = student
            onInitialStudentConsumed()
        }
    }

    LaunchedEffect(rows.map { it.id }, halaqaFilter) {
        val requestedScope = halaqaFilter
        if (rows.isEmpty()) {
            snapshotsByStudent = emptyMap()
            monthStatsByStudent = emptyMap()
        } else {
            com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                withContext(Dispatchers.IO) {
                    val snapshots = AppGraph.repo.planSnapshots(LocalDate.now()).associateBy { it.student.id }
                    val ym = YearMonth.now()
                    val summary = AppGraph.repo.reportSummary(ym.atDay(1).toString(), LocalDate.now().toString(), "all", requestedScope, "all")
                    val stats = buildMap<String, JSONObject> {
                        for (i in 0 until summary.length()) summary.optJSONObject(i)?.let { row ->
                            row.optString("student_id").takeIf { it.isNotBlank() }?.let { put(it, row) }
                        }
                    }
                    snapshots to stats
                }
            }.onSuccess { (snapshots, stats) ->
                snapshotsByStudent = snapshots
                monthStatsByStudent = stats
                monthStatsScope = requestedScope
            }
        }
    }

    // RC25: students are shown by halaqa. The scope (chosen halaqa) drives the filter.
    val studentsUserId = remember { AppGraph.store.load()?.userId }
    val studentScopes by AppGraph.halaqaScope.selected.collectAsState()
    val studentHalaqaChoices = halaqas.filter { it.optBoolean("active", true) }.map { it.optString("id") to it.optString("name", "حلقة") }
    val studentScope = HalaqaScopeRules.resolve(role, studentHalaqaChoices.map { it.first }, studentScopes[studentsUserId])
    LaunchedEffect(studentScope.selectedId, halaqas) { halaqaFilter = studentScope.selectedId ?: "all" }

    Column {
        AppTopBar(
            title = "الطلاب",
            onRefresh = {
                scope.launch { reload(force = true) }
                onRefresh()
            },
            pending = pending,
        )

        val activeFilterCount = listOf(
            statusFilter != "active", trackFilter != "all", categoryFilter != "all", sortMode != "alpha"
        ).count { it }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "بحث بالاسم أو الهوية",
                modifier = Modifier.weight(1f),
                trailing = {
                    IconButton(onClick = { studentFiltersOpen = true }, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                        BadgedBox(badge = { if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) } }) {
                            Icon(Icons.Default.Tune, contentDescription = "الفلاتر")
                        }
                    }
                },
            )
            FilledIconButton(
                onClick = { add = true },
                modifier = Modifier.size(HalaqatiUiTokens.TouchTarget),
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة")
            }
        }

        if (studentScope.showSwitcher) {
            HalaqaScopeBar(
                halaqas = studentHalaqaChoices,
                selectedId = studentScope.selectedId,
                onSelect = { AppGraph.halaqaScope.select(studentsUserId, it); categoryFilter = "all" },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                allLabel = "كل الحلقات (مجمّعة)",
            )
        }

        WorkKindToggle()
        val workTypes by com.mohammedsamir.halaqati.data.WorkModeHolder.state.collectAsState()
        val visibleCategories = categories.filter {
            it.optBoolean("active", true) && (halaqaFilter == "all" || it.optString("halaqa_id") == halaqaFilter)
        }
        val statusOptions = listOf(
            "active" to "الطلاب الحاليون", "all" to "كل الحالات", "inactive" to "غير نشط",
            "suspended" to "موقوف", "withdrawn" to "المنسحبون / الأرشيف",
        )
        val trackOptions = listOf("all" to "كل المسارات", "general_hifz" to "الحفظ العام", "tathbit" to "التثبيت")
        val halaqaOptions = listOf("all" to "كل الحلقات") + halaqas
            .filter { it.optBoolean("active", true) }
            .map { it.optString("id") to it.optString("name", "حلقة") }
        val categoryOptions = listOf("all" to "كل الفئات", "none" to "بدون فئة") + visibleCategories
            .map { it.optString("id") to it.optString("name", "فئة") }
        val sortOptions = listOf("alpha" to "ترتيب أبجدي", "recent" to "الأحدث", "progress" to "حسب المسار")

        message?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = HalaqatiUiTokens.ScreenHorizontal, vertical = 6.dp)) }

        val filteredBase = rows.filter { student ->
            val needle = query.trim()
            val searchOk = needle.isBlank() || listOfNotNull(
                student.fullName, student.nationalId, student.phone, student.guardianPhone, student.guardianName,
            ).any { SessionWorkflowRules.matchesSearch(it, needle) }
            // Direct lookup must still reveal a suspended student so the administrator can see
            // that the record exists instead of assuming it was deleted. The status badge remains
            // visible; normal browsing with an empty query still respects the active filter.
            val statusOk = statusFilter == "all" || student.status == statusFilter ||
                (needle.isNotBlank() && statusFilter == "active" && student.status == "suspended")
            val trackOk = trackFilter == "all" || student.track == trackFilter
            val halaqaOk = halaqaFilter == "all" || student.halaqaId == halaqaFilter || halaqaFilter in secondaryHalaqasByStudent[student.id].orEmpty()
            val categoryOk = when (categoryFilter) {
                "all" -> true
                "none" -> student.categoryId.isNullOrBlank()
                else -> student.categoryId == categoryFilter
            }
            // RC27.10: work mode - program teachers see program students only (members + primary); halaqa mode hides program-only students.
            val kindOk = com.mohammedsamir.halaqati.plans.WorkModeRules.studentBelongs(
                workKind,
                student.halaqaId?.let { workTypes.typeById[it] },
                secondaryHalaqasByStudent[student.id].orEmpty().map { workTypes.typeById[it] },
            )
            searchOk && statusOk && trackOk && halaqaOk && categoryOk && kindOk
        }
        val filtered = when (sortMode) {
            "progress" -> filteredBase.sortedWith(compareBy<Student> { it.track }.thenBy { it.fullName })
            "recent" -> filteredBase.reversed()
            else -> filteredBase.sortedBy { it.fullName }
        }
        val groupStudentsByHalaqa = halaqaFilter == "all" && studentHalaqaChoices.size > 1
        val groupedStudents: List<Pair<String?, List<Student>>> = if (!groupStudentsByHalaqa) {
            listOf(null to filtered)
        } else {
            val halaqaNames = studentHalaqaChoices.toMap()
            filtered.groupBy { it.halaqaId.orEmpty() }.entries
                .sortedWith(compareBy({ if (it.key.isBlank()) 1 else 0 }, { halaqaNames[it.key].orEmpty() }))
                .map { entry -> ((halaqaNames[entry.key] ?: "بدون حلقة") + " · " + entry.value.size + " طالب") to entry.value }
        }
        if (loading && rows.isEmpty()) {
            LoadingSkeleton(rows = 4, modifier = Modifier.padding(12.dp))
        }
        if (!loading && filtered.isEmpty()) {
            EmptyState(
                text = if (query.isBlank()) "لا يوجد طلاب حتى الآن" else "لا توجد نتائج مطابقة للبحث",
                actionLabel = "إعادة تعيين الفلاتر",
                onAction = {
                    query = ""
                    trackFilter = "all"
                    statusFilter = "active"
                    halaqaFilter = "all"
                    categoryFilter = "all"
                },
            )
        }

        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { scope.launch { reload(force = true) }; onRefresh() },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                state = studentsListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              // RC98: the numbers scroll away with the list instead of taking ~110dp of a small screen for good.
              if (rows.isNotEmpty()) item(key = "students-strip", contentType = "strip") {
                  com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(
                Triple(rows.count { it.status == "active" }.toString(), "طالب نشط", false),
                Triple(rows.count { it.status == "active" && it.track == "tathbit" }.toString(), "في التثبيت", false),
                Triple(rows.count { it.status == "active" && (it.nationalId.isNullOrBlank() || it.dateOfBirth.isNullOrBlank()) }.toString(), "ناقص الهوية أو الميلاد", rows.any { it.status == "active" && (it.nationalId.isNullOrBlank() || it.dateOfBirth.isNullOrBlank()) }),
            ))
              }
              groupedStudents.forEach { (groupTitle, groupRows) ->
                if (groupTitle != null) {
                    item(key = "halaqa-header-" + groupTitle, contentType = "halaqa-header") {
                        com.mohammedsamir.halaqati.ui.components.NourSectionTitle(groupTitle, entity = com.mohammedsamir.halaqati.ui.theme.Entity.HALAQA)
                    }
                }
                items(groupRows, key = { it.id }, contentType = { "student" }) { student ->
                    val scopeId = halaqaFilter.takeUnless { it == "all" } ?: student.halaqaId
                    val scopedHalaqa = halaqas.firstOrNull { it.optString("id") == scopeId }
                    val programContext = if (halaqaFilter == "all" && workKind == com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM) true
                        else com.mohammedsamir.halaqati.plans.WorkModeRules.isProgramType(scopedHalaqa?.optString("halaqa_type"))
                    val halaqaName = scopedHalaqa?.optString("name")?.takeUnless {
                        programContext && !com.mohammedsamir.halaqati.plans.WorkModeRules.isProgramType(scopedHalaqa?.optString("halaqa_type"))
                    }
                    val categoryName = categories.firstOrNull { it.optString("id") == student.categoryId && it.optString("halaqa_id") == scopeId }?.optString("name")
                    val secondaryNames = secondaryHalaqasByStudent[student.id].orEmpty()
                        .filter { it != student.halaqaId }
                        .mapNotNull { id -> halaqas.firstOrNull { it.optString("id") == id }?.optString("name") }
                    androidx.compose.foundation.layout.Box(Modifier.animateItem()) {
GoldenStudentCard(
                        student = student,
                        halaqaName = halaqaName,
                        categoryName = categoryName,
                        secondaryHalaqaNames = secondaryNames,
                        snapshot = snapshotsByStudent[student.id]?.takeIf { !programContext && it.plan.halaqaId == scopeId },
                        monthStats = monthStatsByStudent[student.id]?.takeIf { monthStatsScope == halaqaFilter && (halaqaFilter != "all" || (!programContext && secondaryNames.isEmpty())) },
                        programContext = programContext,
                        expanded = expandedStudentId == student.id,
                        onToggle = { expandedStudentId = if (expandedStudentId == student.id) null else student.id },
                        onOpenProfile = { openStudentProfile(student.id) },
                        onRecitation = { startTodayRecitation(student) },
                        onPlan = { selectedStudent = student },
                        onEdit = { selectedStudent = student },
                        readOnly = role.equals("supervisor", true),
                    )
}
                }
              }
            }
        }
    }

    if (studentFiltersOpen) {
        val modalVisibleCategories = categories.filter {
            it.optBoolean("active", true) && (halaqaFilter == "all" || it.optString("halaqa_id") == halaqaFilter)
        }
        val modalStatusOptions = listOf("active" to "الطلاب الحاليون", "all" to "كل الحالات", "inactive" to "غير نشط", "suspended" to "موقوف", "withdrawn" to "المنسحبون / الأرشيف")
        val modalTrackOptions = listOf("all" to "كل المسارات", "general_hifz" to "الحفظ العام", "tathbit" to "التثبيت")
        val modalHalaqaOptions = listOf("all" to "كل الحلقات") + halaqas.filter { it.optBoolean("active", true) }.map { it.optString("id") to it.optString("name", "حلقة") }
        val modalCategoryOptions = listOf("all" to "كل الفئات", "none" to "بدون فئة") + modalVisibleCategories.map { it.optString("id") to it.optString("name", "فئة") }
        val modalSortOptions = listOf("alpha" to "ترتيب أبجدي", "recent" to "الأحدث", "progress" to "حسب المسار")
        ModalBottomSheet(onDismissRequest = { studentFiltersOpen = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("فلاتر الطلاب", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactFilterSelect("الحالة", statusFilter, modalStatusOptions, { statusFilter = it }, Modifier.weight(1f))
                    if (workKind != com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM) CompactFilterSelect("المسار", trackFilter, modalTrackOptions, { trackFilter = it }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactFilterSelect("الحلقة", halaqaFilter, modalHalaqaOptions, { halaqaFilter = it; categoryFilter = "all"; AppGraph.halaqaScope.select(studentsUserId, it.takeIf { id -> id != "all" }) }, Modifier.weight(1f))
                    CompactFilterSelect("الفئة", categoryFilter, modalCategoryOptions, { categoryFilter = it }, Modifier.weight(1f))
                }
                CompactFilterSelect("الترتيب", sortMode, modalSortOptions, { sortMode = it }, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            trackFilter = "all"; statusFilter = "active"; halaqaFilter = "all"; categoryFilter = "all"; sortMode = "alpha"
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("إعادة تعيين") }
                    Button(onClick = { studentFiltersOpen = false }, modifier = Modifier.weight(1f)) { Text("عرض الطلاب") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (add) {
        StudentDialog(
            initialHalaqaId = halaqaFilter.takeUnless { it == "all" },
            accessibleStudents = rows,
            scopeNames = halaqas.associate { it.optString("id") to it.optString("name") },
            close = { add = false },
            onSaved = {
                add = false
                scope.launch { reload() }
                onRefresh()
            },
        )
    }


    selectedStudent?.let { student ->
        val visibleScopes = halaqas.map(::enrollmentScopeOf)
        val detailScopeId = StudentEnrollmentRules.detailScope(
            student.halaqaId, halaqaFilter.takeUnless { it == "all" }, secondaryHalaqasByStudent[student.id].orEmpty(),
            visibleScopes, workKind == com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM,
        )
        if (detailScopeId == null) {
            HalaqatiAlertDialog(
                onDismissRequest = { selectedStudent = null },
                title = { Text("حدد الحلقة أو البرنامج") },
                text = { Text("اختر نشاطًا محددًا من شريط النطاق قبل عرض تفاصيل الطالب؛ لن تُجمع إحصاءات عضويات مختلفة.") },
                confirmButton = { TextButton(onClick = { selectedStudent = null }) { Text("حسنًا") } },
            )
        } else {
        val detailProgram = visibleScopes.firstOrNull { it.id == detailScopeId }?.program == true
        key(student.id, detailScopeId) {
        StudentDetailSheet(
            student = student,
            detailScopeId = detailScopeId,
            programScope = detailProgram,
            canEditPrimary = role in setOf("admin", "teacher", "supervisor") &&
                StudentEnrollmentRules.canEditPrimary(student.halaqaId, detailScopeId, visibleScopes.map { it.id }.toSet()),
            onDismiss = { selectedStudent = null },
            onChanged = {
                scope.launch { reload(force = true) }
                onRefresh()
            },
            onStartRecitation = { startTodayRecitation(student) },
        )
        }
        }
    }

    recitationChoices?.let { (student, sessions) ->
        HalaqatiAlertDialog(
            onDismissRequest = { recitationChoices = null },
            title = { Text("اختر جلسة اليوم") },
            text = {
                Column {
                    sessions.forEach { session ->
                        TextButton(onClick = {
                            recitationChoices = null
                            openStudentRecitation(student, session)
                        }) { Text(session.title.ifBlank { sessionTypeAr(session.type) }) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { recitationChoices = null }) { Text("إلغاء") } },
        )
    }

    directRecitation?.let { (student, session, snapshot) ->
        val assignment = snapshot?.progress?.assignment
        RecitationDialog(
            student = student,
            session = session,
            close = { directRecitation = null },
            onSaved = { _ ->
                directRecitation = null
                scope.launch { reload(force = true) }
                onRefresh()
            },
            prefillStartPage = assignment?.startPage,
            prefillEndPage = assignment?.endPage,
            prefillPages = assignment?.pages?.takeIf { it > 0.0 },
            smartPlanId = snapshot?.plan?.id,
            smartPlanPhaseId = snapshot?.phase?.id,
            smartPlanType = snapshot?.plan?.type,
            smartPlanDate = snapshot?.let { LocalDate.now().toString() },
            qcfLineKeys = assignment?.lineKeys.orEmpty(),
            activityTypeOverride = assignment?.kind?.let { kind ->
                when (kind) {
                    "sard" -> "sard"
                    "review" -> "review_near"
                    "tathbit" -> "tathbit"
                    "test" -> "test"
                    else -> "new_hifz"
                }
            },
        )
    }
}

@Composable
private fun StudentDetailSheet(
    student: Student,
    detailScopeId: String,
    programScope: Boolean,
    canEditPrimary: Boolean,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onStartRecitation: (PlanSnapshot?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember(student.id) { mutableStateOf(true) }
    var online by remember(student.id) { mutableStateOf(true) }
    var saving by remember(student.id) { mutableStateOf(false) }
    var error by remember(student.id) { mutableStateOf<String?>(null) }
    var raw by remember(student.id) { mutableStateOf<JSONObject?>(null) }
    var recitations by remember(student.id) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var attendance by remember(student.id) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var snapshot by remember(student.id) { mutableStateOf<PlanSnapshot?>(null) }
    // RC27.8: current-month numbers come from the same summary used by ranking/statistics (works offline from cache).
    var monthRow by remember(student.id) { mutableStateOf<JSONObject?>(null) }
    var track by remember(student.id) { mutableStateOf(student.track) }
    var status by remember(student.id) { mutableStateOf(student.status) }
    var showFullEdit by remember(student.id) { mutableStateOf(false) }
    var showGuardianLink by remember(student.id) { mutableStateOf(false) }
    var showArchiveConfirm by remember(student.id) { mutableStateOf(false) }

    suspend fun reloadDetail() {
        loading = true
        try {
            val today = LocalDate.now()
            val from = today.withDayOfMonth(1).toString()
            val to = today.toString()
            online = withContext(Dispatchers.IO) { AppGraph.repo.online() }
            monthRow = com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                withContext(Dispatchers.IO) {
                    val summary = AppGraph.repo.reportSummary(from, to, "all", detailScopeId, "all")
                    (0 until summary.length()).mapNotNull { summary.optJSONObject(it) }.firstOrNull { it.optString("student_id") == student.id }
                }
            }.getOrNull()
            if (!online) {
                raw = withContext(Dispatchers.IO) { AppGraph.repo.studentRaw(student.id) }
                recitations = emptyList()
                attendance = emptyList()
                snapshot = com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                    withContext(Dispatchers.IO) { if (programScope) null else AppGraph.repo.planSnapshots(today).firstOrNull { it.student.id == student.id && it.plan.halaqaId == detailScopeId } }
                }.getOrNull()
                error = null
                track = raw?.optString("track_code").orEmpty().ifBlank { student.track }
                status = raw?.optString("status").orEmpty().ifBlank { student.status }
                return
            }
            val gateKey = "student:${student.id}:$detailScopeId"
            val netDue = com.mohammedsamir.halaqati.data.RefreshGate.due(gateKey)
            val result = withContext(Dispatchers.IO) {
                val studentRaw = AppGraph.repo.table(
                    "students",
                    "select=*&id=eq.${student.id}&limit=1",
                ).optJSONObject(0)
                val recitationArray = AppGraph.repo.table(
                    "recitation_entries",
                    "select=id,activity_type,start_page,end_page,pages_count,evaluation,points,mistakes,prompts,created_at," +
                        "sessions!inner(session_date,session_type,title,halaqa_id)" +
                        "&student_id=eq.${student.id}&sessions.halaqa_id=eq.$detailScopeId&sessions.session_date=gte.$from&sessions.session_date=lte.$to" +
                        "&order=created_at.desc&limit=1000",
                    forceRefresh = netDue, preferCache = !netDue,
                )
                val attendanceArray = AppGraph.repo.table(
                    "attendance_records",
                    "select=status,arrival_minutes_late,excuse_reason,recorded_at,sessions!inner(session_date,halaqa_id)" +
                        "&student_id=eq.${student.id}&sessions.halaqa_id=eq.$detailScopeId&sessions.session_date=gte.$from&sessions.session_date=lte.$to" +
                        "&order=recorded_at.desc&limit=1000",
                    forceRefresh = netDue, preferCache = !netDue,
                )
                if (netDue) com.mohammedsamir.halaqati.data.RefreshGate.mark(gateKey)
                val planSnapshot = if (programScope) null else runCatching { AppGraph.repo.planSnapshots(today) }
                    .getOrDefault(emptyList())
                    .firstOrNull { it.student.id == student.id && it.plan.halaqaId == detailScopeId }
                Quadruple(
                    studentRaw,
                    (0 until recitationArray.length()).mapNotNull { recitationArray.optJSONObject(it) },
                    (0 until attendanceArray.length()).mapNotNull { attendanceArray.optJSONObject(it) },
                    planSnapshot,
                )
            }
            raw = result.first
            recitations = result.second
            attendance = result.third
            snapshot = result.fourth
            track = result.first?.optString("track_code").orEmpty().ifBlank { student.track }
            status = result.first?.optString("status").orEmpty().ifBlank { student.status }
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "تعذر تحميل تفاصيل الطالب"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(student.id, detailScopeId) { reloadDetail() }

    val monthLabel = remember { YearMonth.now().let { ym -> "${listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")[ym.monthValue - 1]} ${ym.year}" } }
    val hasMonthData = monthRow != null || recitations.isNotEmpty() || attendance.isNotEmpty()
    val hifz = monthRow?.optDouble("hifz_pages", 0.0)
        ?: com.mohammedsamir.halaqati.plans.HifzSpanRules.pages(
            recitations.filter { it.optString("activity_type") == "new_hifz" }.map(::hifzSpanEntryOf),
        )
    val review = monthRow?.optDouble("review_pages", 0.0)
        ?: recitations.filter { it.optString("activity_type") in setOf("review_near", "review_far") }.sumOf { it.optDouble("pages_count", 0.0) }
    val sard = monthRow?.optDouble("sard_pages", 0.0)
        ?: com.mohammedsamir.halaqati.plans.HifzSpanRules.pages(
            recitations.filter { it.optString("activity_type") == "sard" }.map(::hifzSpanEntryOf),
        )
    val present = monthRow?.let { it.optInt("present", 0) + it.optInt("late", 0) }
        ?: attendance.count { it.optString("status") in setOf("present", "late") }
    val absent = monthRow?.optInt("absent", 0) ?: attendance.count { it.optString("status") == "absent" }
    val late = monthRow?.optInt("late", 0) ?: attendance.count { it.optString("status") == "late" }
    fun metric(value: String) = if (hasMonthData) value else "—"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                com.mohammedsamir.halaqati.ui.components.StudentAvatar(raw?.optString("photo_path")?.takeIf { it.isNotBlank() && it != "null" } ?: student.photoPath, student.fullName, 64.dp)
                Column(Modifier.weight(1f)) {
                    OneLineName(student.fullName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (programScope) "مشارك في برنامج" else if (track == "tathbit") "مسار التثبيت" else "مسار الحفظ العام",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = when (status) {
                        "active" -> MaterialTheme.colorScheme.primaryContainer
                        "suspended" -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        studentStatusAr(status),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }

            error?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            if (!online) {
                StateNotice(
                    "وضع الأوفلاين: بيانات الطالب محفوظة محليًا، وأي تعديل أو تسميع سيُضاف إلى صندوق المزامنة تلقائيًا.",
                    kind = "warning",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (loading) LoadingSkeleton(rows = 3, modifier = Modifier.padding(16.dp))

            if (!loading) {
                LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 620.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        if (programScope) {
                            val tilawah = monthRow?.optDouble("tilawah_pages", 0.0)
                                ?: recitations.filter { it.optString("activity_type") == "tilawah" }.sumOf { it.optDouble("pages_count", 0.0) }
                            Row(Modifier.fillMaxWidth()) {
                                StudentMiniMetric("تلاوة الشهر", metric("${plainNumber(tilawah)} ص"), Modifier.weight(1f))
                            }
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudentMiniMetric("حفظ الشهر", metric("${plainNumber(hifz)} ص"), Modifier.weight(1f))
                            StudentMiniMetric("مراجعة", metric("${plainNumber(review)} ص"), Modifier.weight(1f))
                            StudentMiniMetric("سرد", metric("${plainNumber(sard)} ص"), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudentMiniMetric("الحضور", metric(present.toString()), Modifier.weight(1f))
                            StudentMiniMetric("الغياب", metric(absent.toString()), Modifier.weight(1f))
                            StudentMiniMetric("التأخر", metric(late.toString()), Modifier.weight(1f))
                        }
                        Text(
                            if (hasMonthData) "أرقام $monthLabel فقط" + if (!online) " — من آخر مزامنة" else ""
                            else "لا توجد بيانات محفوظة لهذا الشهر على الجهاز بعد، تظهر بعد أول مزامنة.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    snapshot?.let { plan ->
                        item {
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("الخطة الذكية", style = MaterialTheme.typography.titleMedium)
                                            Text(plan.plan.title.ifBlank { planTypeAr(plan.plan.type) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        PlanHealthBadge(plan.progress.health, plan.progress.healthLabel)
                                    }
                                    LinearProgressIndicator(
                                        drawStopIndicator = {}, progress = { (plan.progress.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).graphicsLayer(scaleX = -1f),
                                    )
                                    Text(
                                        "الإنجاز ${plainNumber(plan.progress.percentage)}% · المتبقي ${plainNumber(plan.progress.remaining)} صفحة · اليوم ${plainNumber(plan.progress.assignment.pages)} صفحة",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("البيانات الأساسية", style = MaterialTheme.typography.titleMedium)
                                StudentDetailLine("المرحلة", jsonText(raw, "school_grade"))
                                StudentDetailLine("جوال ولي الأمر", jsonText(raw, "guardian_phone"))
                                StudentDetailLine("بريد ولي الأمر", jsonText(raw, "guardian_email"))
                                StudentDetailLine("تاريخ الميلاد", jsonText(raw, "date_of_birth"))
                            }
                        }
                    }

                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("إدارة الطالب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { showFullEdit = true },
                                        enabled = canEditPrimary,
                                        modifier = Modifier.weight(1f).heightIn(min = HalaqatiUiTokens.TouchTarget),
                                    ) { Text("تعديل البيانات") }
                                    OutlinedButton(
                                        onClick = { showGuardianLink = true },
                                        modifier = Modifier.weight(1f).heightIn(min = HalaqatiUiTokens.TouchTarget),
                                    ) { Text("أولياء الأمور") }
                                }
                                if (canEditPrimary && !programScope) Button(
                                    onClick = { showArchiveConfirm = true },
                                    enabled = !saving,
                                    colors = if (status == "withdrawn") ButtonDefaults.buttonColors()
                                    else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
                                ) { Text(if (status == "withdrawn") "استعادة الطالب" else "أرشفة / سحب الطالب") }
                                Text(
                                    if (!canEditPrimary) "إدارة الملف الأساسي متاحة من الحلقة أو البرنامج الأساسي فقط." else if (programScope) "إنهاء عضوية برنامج لا يعني أرشفة الطالب من جميع أنشطته." else "الأرشفة لا تحذف الجلسات أو التسميعات أو التقارير؛ ويمكن استعادة الطالب لاحقًا.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        Text(if (programScope) "آخر أنشطة البرنامج" else "آخر التسميعات", style = MaterialTheme.typography.titleMedium)
                        if (recitations.isEmpty()) {
                            EmptyState(if (programScope) "لا توجد أنشطة مسجلة هذا الشهر" else "لا توجد تسميعات مسجلة هذا الشهر")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                recitations.take(6).forEach { row ->
                                    val session = row.optJSONObject("sessions")
                                    ElevatedCard(Modifier.fillMaxWidth()) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 48.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Column(Modifier.weight(1f)) {
                                                Text(activityTypeAr(row.optString("activity_type")), style = MaterialTheme.typography.labelLarge)
                                                Text(
                                                    "${session?.optString("session_date").orEmpty()} · ${RecitationParityRules.quranRangeLabel(nullableInt(row, "start_surah"), nullableInt(row, "start_ayah"), nullableInt(row, "end_surah"), nullableInt(row, "end_ayah"), nullableInt(row, "start_page"), nullableInt(row, "end_page"))}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Text("${plainNumber(row.optDouble("pages_count", 0.0))} ص", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (canEditPrimary && !programScope) item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("تعديل سريع", style = MaterialTheme.typography.titleMedium)
                                ResponsiveChipGrid(items = listOf("general_hifz" to "الحفظ العام", "tathbit" to "التثبيت"), minItemWidth = 130.dp, maxColumns = 2) { pair, itemModifier ->
                                    ProFilterChip(track == pair.first, { track = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                                }
                                ResponsiveChipGrid(items = listOf("active" to "نشط", "inactive" to "غير نشط", "suspended" to "موقوف", "withdrawn" to "منسحب"), minItemWidth = 105.dp, maxColumns = 2) { pair, itemModifier ->
                                    ProFilterChip(status == pair.first, { status = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                                }
                                Button(
                                    onClick = {
                                        saving = true
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    AppGraph.repo.patch(
                                                        "students",
                                                        "id=eq.${student.id}",
                                                        JSONObject().put("track_code", track).put("status", status),
                                                        dedupe = "student-quick-edit:${student.id}",
                                                    )
                                                }
                                                onChanged()
                                                reloadDetail()
                                            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                                                error = e.message ?: "تعذر تحديث الطالب"
                                            } finally {
                                                saving = false
                                            }
                                        }
                                    },
                                    enabled = !saving,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
                                ) { Text(if (saving) "جاري الحفظ…" else if (online) "حفظ التعديل" else "حفظ محليًا") }
                            }
                        }
                    }
                }

                Surface(shadowElevation = 8.dp, tonalElevation = 2.dp) {
                    Button(
                        onClick = { onStartRecitation(snapshot) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (programScope) "فتح جلسة البرنامج" else if (online) "بدء تسميع ${student.fullName}" else "بدء تسميع أوفلاين · ${student.fullName}")
                    }
                }
            }
        }
    }

    if (showFullEdit && canEditPrimary) {
        StudentFullEditDialog(
            student = student,
            raw = raw,
            close = { showFullEdit = false },
            onSaved = { showFullEdit = false; onChanged(); scope.launch { reloadDetail() } },
        )
    }
    if (showGuardianLink) {
        GuardianLinkDialog(
            close = { showGuardianLink = false },
            onSaved = { showGuardianLink = false; onChanged(); scope.launch { reloadDetail() } },
            initialStudentId = student.id,
        )
    }
    if (showArchiveConfirm && canEditPrimary && !programScope) {
        HalaqatiAlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text(if (status == "withdrawn") "استعادة الطالب" else "أرشفة الطالب") },
            text = { Text(if (status == "withdrawn") "سيعود الطالب إلى قائمة الطلاب الحاليين مع بقاء سجله كاملًا." else "سيُنقل الطالب إلى المنسحبين / الأرشيف دون حذف الجلسات أو التسميعات أو التقارير.") },
            confirmButton = {
                Button(onClick = {
                    showArchiveConfirm = false
                    saving = true
                    scope.launch {
                        try {
                            val newStatus = if (status == "withdrawn") "active" else "withdrawn"
                            withContext(Dispatchers.IO) {
                                AppGraph.repo.patch("students", "id=eq.${student.id}", JSONObject().put("status", newStatus), dedupe = "student-archive:${student.id}:$newStatus")
                            }
                            status = newStatus
                            onChanged(); reloadDetail()
                        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { error = e.message ?: "تعذر تحديث حالة الطالب" }
                        finally { saving = false }
                    }
                }) { Text(if (status == "withdrawn") "استعادة" else "أرشفة") }
            },
            dismissButton = { TextButton(onClick = { showArchiveConfirm = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun StudentFullEditDialog(student: Student, raw: JSONObject?, close: () -> Unit, onSaved: () -> Unit) {
    StudentEditorDialog(student = student, raw = raw, close = close, onSaved = onSaved)
}

@Composable
private fun StudentDialog(
    initialHalaqaId: String?,
    accessibleStudents: List<Student>,
    scopeNames: Map<String, String>,
    close: () -> Unit,
    onSaved: () -> Unit,
) {
    var enrollmentScope by remember { mutableStateOf<String?>(null) }
    val halaqaId = enrollmentScope
    if (halaqaId == null) {
        StudentEditorDialog(
            student = null,
            raw = null,
            close = close,
            onSaved = onSaved,
            initialHalaqaId = initialHalaqaId,
            openExistingEnrollment = { enrollmentScope = it },
        )
    } else {
        val scope = rememberCoroutineScope()
        var selected by remember(halaqaId) { mutableStateOf<String?>(null) }
        var saving by remember(halaqaId) { mutableStateOf(false) }
        var error by remember(halaqaId) { mutableStateOf<String?>(null) }
        val options = accessibleStudents.asSequence().filter { it.status == "active" && it.halaqaId != halaqaId }
            .map { student ->
                val maskedId = student.nationalId?.takeIf { it.length >= 5 }?.takeLast(3)?.let { "•••$it" }
                student.id to listOfNotNull(student.fullName, scopeNames[student.halaqaId], maskedId).joinToString(" · ")
            }.toList()
        HalaqatiAlertDialog(
            onDismissRequest = { if (!saving) enrollmentScope = null },
            title = { Text("تسجيل طالب موجود في ${scopeNames[halaqaId].orEmpty()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر سجل الطالب المسموح لك بعرضه. سيظل ملفه ونشاطه الأساسي كما هما.")
                    if (!AppGraph.repo.online()) StateNotice("سيُحفظ طلب التسجيل في صندوق المزامنة، ولن يصبح معتمدًا قبل تأكيد الخادم.", "warning")
                    HalaqatiLazySelectField("الطالب", selected, options, { selected = it }, Modifier.fillMaxWidth(), searchable = true)
                    error?.let { StateNotice(it, "error") }
                }
            },
            confirmButton = {
                Button(enabled = !saving && selected != null, onClick = {
                    val studentId = selected ?: return@Button
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                AppGraph.repo.enrollExistingStudent(studentId, halaqaId)
                            }
                            onSaved()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "تعذر تسجيل الطالب في البرنامج"
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(if (saving) "جاري التسجيل…" else "تسجيل في البرنامج") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { enrollmentScope = null }) { Text("رجوع") } },
        )
    }
}

private fun enrollmentScopeOf(row: JSONObject) = StudentEnrollmentRules.Scope(
    id = row.optString("id"),
    program = com.mohammedsamir.halaqati.plans.WorkModeRules.isProgramType(row.optString("halaqa_type")),
    active = row.optBoolean("active", true),
    allowSecondary = row.optBoolean("allow_secondary_members", true),
)

private data class StudentEditorMasterData(
    val halaqas: List<JSONObject>,
    val categories: List<JSONObject>,
    val currentSecondaryHalaqaIds: Set<String>,
)

@Composable
private fun StudentEditorDialog(
    student: Student?,
    raw: JSONObject?,
    close: () -> Unit,
    onSaved: () -> Unit,
    initialHalaqaId: String? = null,
    openExistingEnrollment: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val draftStudentId = remember(student?.id) { student?.id ?: java.util.UUID.randomUUID().toString() }
    var masterLoading by remember { mutableStateOf(true) }
    var masterReady by remember { mutableStateOf(false) }
    var fullName by remember(student?.id) { mutableStateOf(raw?.optString("full_name").orEmpty().ifBlank { student?.fullName.orEmpty() }) }
    var nationalId by remember(student?.id) { mutableStateOf(raw?.optString("national_id").orEmpty().ifBlank { student?.nationalId.orEmpty() }) }
    var phone by remember(student?.id) { mutableStateOf(raw?.optString("phone").orEmpty().ifBlank { student?.phone.orEmpty() }) }
    var schoolGrade by remember(student?.id) { mutableStateOf(raw?.optString("school_grade").orEmpty().ifBlank { student?.schoolGrade.orEmpty() }) }
    var dateOfBirth by remember(student?.id) { mutableStateOf(raw?.optString("date_of_birth").orEmpty().ifBlank { student?.dateOfBirth.orEmpty() }) }
    var gender by remember(student?.id) { mutableStateOf(raw?.optString("gender").orEmpty().ifBlank { student?.gender.orEmpty() }) }
    var guardianPhone by remember(student?.id) { mutableStateOf(raw?.optString("guardian_phone").orEmpty().ifBlank { student?.guardianPhone.orEmpty() }) }
    var guardianEmail by remember(student?.id) { mutableStateOf(raw?.optString("guardian_email").orEmpty().ifBlank { student?.guardianEmail.orEmpty() }) }
    var track by remember(student?.id) { mutableStateOf(raw?.optString("track_code").orEmpty().ifBlank { student?.track ?: "general_hifz" }) }
    var halaqaId by remember(student?.id) { mutableStateOf(raw?.optString("halaqa_id").orEmpty().ifBlank { student?.halaqaId.orEmpty() }) }
    var categoryId by remember(student?.id) { mutableStateOf(raw?.optString("category_id").orEmpty().ifBlank { student?.categoryId.orEmpty() }) }
    var status by remember(student?.id) { mutableStateOf(raw?.optString("status").orEmpty().ifBlank { student?.status ?: "active" }) }
    var enrollmentDate by remember(student?.id) { mutableStateOf(raw?.optString("enrollment_date").orEmpty().ifBlank { student?.enrollmentDate ?: LocalDate.now().toString() }) }
    var programName by remember(student?.id) { mutableStateOf(raw?.optString("program_name").orEmpty().ifBlank { student?.programName.orEmpty() }) }
    var address by remember(student?.id) { mutableStateOf(raw?.optString("address").orEmpty().ifBlank { student?.address.orEmpty() }) }
    var notes by remember(student?.id) { mutableStateOf(raw?.optString("notes").orEmpty().ifBlank { student?.notes.orEmpty() }) }
    var photoPath by remember(student?.id) { mutableStateOf(raw?.optString("photo_path").orEmpty().ifBlank { student?.photoPath.orEmpty() }) }
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var categories by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var initialSecondaryHalaqaIds by remember(student?.id) { mutableStateOf(emptySet<String>()) }
    var secondaryHalaqaIds by remember(student?.id) { mutableStateOf(emptySet<String>()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(student?.id) {
        try {
        val master = withContext(Dispatchers.IO) {
            val h = AppGraph.repo.table("halaqas", "select=id,name,active,halaqa_type,starts_on,ends_on,allow_secondary_members&order=name.asc&limit=200")
            val c = AppGraph.repo.table("student_categories", "select=id,halaqa_id,name,active&order=name.asc&limit=500")
            val currentSecondary = student?.id?.let { AppGraph.repo.activeSecondaryHalaqaIds(it) }.orEmpty()
            StudentEditorMasterData(
                halaqas = (0 until h.length()).mapNotNull { h.optJSONObject(it) },
                categories = (0 until c.length()).mapNotNull { c.optJSONObject(it) },
                currentSecondaryHalaqaIds = currentSecondary,
            )
        }
        halaqas = master.halaqas
        categories = master.categories
        initialSecondaryHalaqaIds = master.currentSecondaryHalaqaIds - halaqaId
        secondaryHalaqaIds = initialSecondaryHalaqaIds
        if (halaqaId.isBlank() && student == null) {
            halaqaId = StudentEnrollmentRules.initialScope(initialHalaqaId, halaqas.map(::enrollmentScopeOf)).orEmpty()
        }
        masterReady = true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "تعذر تحميل الحلقات والبرامج" }
        finally { masterLoading = false }
    }

    val currentScope = halaqas.firstOrNull { it.optString("id") == halaqaId }
    val programScope = com.mohammedsamir.halaqati.plans.WorkModeRules.isProgramType(currentScope?.optString("halaqa_type"))
    val visibleCategories = categories.filter { it.optBoolean("active", true) && (halaqaId.isBlank() || it.optString("halaqa_id") == halaqaId) }
    LaunchedEffect(halaqaId, categories.size, masterReady) {
        if (masterReady && categoryId.isNotBlank() && visibleCategories.none { it.optString("id") == categoryId }) categoryId = ""
    }

    HalaqatiAlertDialog(
        onDismissRequest = { if (!saving) close() },
        title = { Text(if (student == null) "إضافة طالب" else "تعديل بيانات الطالب") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                error?.let { StateNotice(it, "error") }
                if (masterLoading) LoadingSkeleton(rows = 2)
                if (!AppGraph.repo.online()) StateNotice("سيُحفظ التعديل على هذا الجهاز ويُرفع تلقائيًا عند عودة الإنترنت.", "warning")
                OutlinedTextField(fullName, { fullName = it }, label = { Text("الاسم الكامل") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(nationalId, { nationalId = it }, label = { Text("رقم الهوية") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("هاتف الطالب") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProFilterChip(gender == "male", { gender = "male" }, "ذكر")
                    ProFilterChip(gender == "female", { gender = "female" }, "أنثى")
                }
                StudentDateField("تاريخ الميلاد", dateOfBirth, onDate = { dateOfBirth = it })
                OutlinedTextField(schoolGrade, { schoolGrade = it }, label = { Text("المرحلة / الصف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (student == null) StudentNamedDropdown(
                    label = "الحلقة / البرنامج",
                    selectedId = halaqaId,
                    items = halaqas.filter { it.optBoolean("active", true) },
                    onSelected = { halaqaId = it; categoryId = ""; secondaryHalaqaIds = secondaryHalaqaIds - it },
                ) else Text("النشاط الأساسي: " + (currentScope?.optString("name") ?: "غير متاح"), style = MaterialTheme.typography.bodyMedium)
                if (programScope && currentScope?.optBoolean("allow_secondary_members", true) == true && openExistingEnrollment != null) {
                    OutlinedButton(enabled = !saving, onClick = { openExistingEnrollment(halaqaId) }) { Text("تسجيل طالب موجود") }
                }
                Text("الحلقات والبرامج الإضافية", style = MaterialTheme.typography.labelLarge)
                Text(
                    "يمكن للطالب البقاء في حلقته الأساسية والمشاركة في التجويد أو التلاوة أو الدورات الأخرى دون تكرار سجله.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val secondaryIds = StudentEnrollmentRules.additionalScopes(halaqaId, halaqas.map(::enrollmentScopeOf)).map { it.id }.toSet()
                val secondaryOptions = halaqas.filter { it.optString("id") in secondaryIds }
                if (secondaryOptions.isEmpty()) {
                    Text("لا توجد برامج إضافية متاحة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ResponsiveChipGrid(items = secondaryOptions, minItemWidth = 145.dp, maxColumns = 2) { row, itemModifier ->
                        val id = row.optString("id")
                        val type = when (row.optString("halaqa_type", "regular")) {
                            "tajweed", "tilawah" -> com.mohammedsamir.halaqati.plans.ProgramKindRules.TILAWAH_TAJWEED_LABEL
                            "training" -> "تأهيل محفظين"
                            "course" -> "دورة"
                            else -> "حلقة"
                        }
                        ProFilterChip(
                            selected = id in secondaryHalaqaIds,
                            onClick = { secondaryHalaqaIds = if (id in secondaryHalaqaIds) secondaryHalaqaIds - id else secondaryHalaqaIds + id },
                            label = "$type · ${row.optString("name", "برنامج")}",
                            modifier = itemModifier.fillMaxWidth(),
                            showCheckIcon = true,
                        )
                    }
                }
                StudentNamedDropdown(
                    label = "الفئة", selectedId = categoryId, items = visibleCategories, allowEmpty = true,
                    onSelected = { categoryId = it },
                )
                if (!programScope) {
                    Text("المسار", style = MaterialTheme.typography.labelLarge)
                    ResponsiveChipGrid(items = listOf("general_hifz" to "الحفظ العام", "tathbit" to "التثبيت"), minItemWidth = 130.dp, maxColumns = 2) { pair, itemModifier ->
                        ProFilterChip(track == pair.first, { track = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                    }
                }
                if (!programScope) {
                    Text("الحالة", style = MaterialTheme.typography.labelLarge)
                    ResponsiveChipGrid(items = listOf("active" to "نشط", "inactive" to "غير نشط", "suspended" to "موقوف", "withdrawn" to "منسحب"), minItemWidth = 105.dp, maxColumns = 2) { pair, itemModifier ->
                        ProFilterChip(status == pair.first, { status = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                    }
                }
                StudentDateField("تاريخ الالتحاق", enrollmentDate, onDate = { enrollmentDate = it })
                OutlinedTextField(programName, { programName = it }, label = { Text("البرنامج") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                StateNotice("ارتباطات أولياء الأمور تُعتمد من طلبات الاعتماد. بيانات التواصل أدناه لا تُنشئ ارتباطًا.", "info")
                OutlinedTextField(guardianPhone, { guardianPhone = it }, label = { Text("جوال ولي الأمر") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(guardianEmail, { guardianEmail = it }, label = { Text("بريد ولي الأمر") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                val photoContext = androidx.compose.ui.platform.LocalContext.current
                val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) com.mohammedsamir.halaqati.documents.DocumentImageEncoder.uriJpeg(photoContext, uri, squareCrop = true)?.let { photoPath = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.mohammedsamir.halaqati.ui.components.StudentAvatar(photoPath, fullName.ifBlank { "طالب" }, 56.dp)
                    OutlinedButton(onClick = { photoPicker.launch("image/*") }) { Text(if (photoPath.isBlank()) "إضافة صورة" else "تغيير الصورة") }
                    if (photoPath.isNotBlank()) TextButton(onClick = { photoPath = "" }) { Text("إزالة") }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && masterReady && currentScope != null && fullName.trim().length >= 2 && halaqaId.isNotBlank(),
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val payload = JSONObject()
                                .put("full_name", fullName.trim())
                                .putNullableUi("national_id", nationalId)
                                .putNullableUi("phone", phone)
                                .putNullableUi("school_grade", schoolGrade)
                                .putNullableUi("date_of_birth", dateOfBirth)
                                .putNullableUi("gender", gender)
                                .putNullableUi("guardian_phone", guardianPhone)
                                .putNullableUi("guardian_email", guardianEmail)
                                .put("halaqa_id", halaqaId)
                                .put("category_id", categoryId.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                                .put("status", status)
                                .putNullableUi("enrollment_date", enrollmentDate)
                                .putNullableUi("program_name", programName)
                                .putNullableUi("address", address)
                                .putNullableUi("notes", notes)
                            if (!programScope) payload.put("track_code", track)
                            else if (student == null) payload.put("track_code", JSONObject.NULL)
                                .put("target_pages_weekly", 0).put("target_review_pages_weekly", 0)
                            payload.put("photo_path", if (photoPath.isNotBlank()) photoPath else JSONObject.NULL)
                            withContext(Dispatchers.IO) {
                                val studentId = draftStudentId
                                val added = secondaryHalaqaIds - initialSecondaryHalaqaIds - halaqaId
                                if (student == null && AppGraph.repo.online()) {
                                    AppGraph.repo.registerStudentConfirmed(payload.put("id", studentId), added)
                                } else {
                                    if (student == null) {
                                        AppGraph.repo.write("students", payload.put("id", studentId), onConflict = "id", dedupe = "student-create:$studentId")
                                    } else {
                                        AppGraph.repo.patch("students", "id=eq.${student.id}", payload, dedupe = "student-full-edit:${student.id}")
                                    }
                                    AppGraph.repo.saveStudentSecondaryMemberships(
                                        studentId, addedHalaqaIds = added,
                                        removedHalaqaIds = initialSecondaryHalaqaIds - secondaryHalaqaIds - halaqaId,
                                        confirmed = student != null,
                                    )
                                }
                            }
                            onSaved()
                        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { error = e.message ?: "تعذر حفظ بيانات الطالب" }
                        finally { saving = false }
                    }
                },
            ) { Text(if (saving) "جاري الحفظ…" else if (AppGraph.repo.online()) "حفظ" else "حفظ محليًا") }
        },
        dismissButton = { TextButton(onClick = close, enabled = !saving) { Text("إلغاء") } },
    )
}

@Composable
private fun StudentNamedDropdown(
    label: String,
    selectedId: String,
    items: List<JSONObject>,
    allowEmpty: Boolean = false,
    onSelected: (String) -> Unit,
) {
    HalaqatiLazySelectField(
        label = label,
        selectedKey = selectedId,
        options = items.map { it.optString("id") to it.optString("name", label) },
        onSelected = onSelected,
        modifier = Modifier.fillMaxWidth(),
        allowEmpty = allowEmpty,
        emptyOptionLabel = "بدون $label",
        searchable = items.size > 12,
    )
}

@Composable
private fun StudentDateField(label: String, value: String, onDate: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
        placeholder = { Text("اختر التاريخ") },
        trailingIcon = {
            IconButton(onClick = { open = true }, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                Icon(Icons.Default.CalendarMonth, contentDescription = label)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (open) {
        val initialMillis = remember(value) {
            runCatching { LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        onDate(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("اختيار") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("إلغاء") } },
        ) { DatePicker(state = picker) }
    }
}

private fun JSONObject.putNullableUi(key: String, value: String): JSONObject = apply {
    put(key, value.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun StudentMiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StudentDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

private fun jsonText(row: JSONObject?, key: String): String {
    if (row == null || !row.has(key) || row.isNull(key)) return "—"
    return row.optString(key).takeUnless { it.isBlank() || it == "null" } ?: "—"
}

private fun activityTypeAr(value: String): String = when (value) {
    "tathbit" -> "تثبيت"
    "new_hifz" -> "حفظ جديد"
    "review_near" -> "مراجعة قريبة"
    "review_far" -> "مراجعة بعيدة"
    "sard" -> "سرد"
    "tilawah" -> "تلاوة"
    "test" -> "اختبار"
    else -> value
}

private fun nullableInt(row: JSONObject, key: String): Int? =
    if (!row.has(key) || row.isNull(key)) null else row.optInt(key).takeIf { it > 0 }

private fun studentStatusAr(status: String): String = when (status) {
    "active" -> "نشط"
    "inactive" -> "غير نشط"
    "suspended" -> "موقوف"
    "withdrawn" -> "منسحب"
    else -> status
}

@Composable
fun SessionsScreen(pending: Int, onRefresh: () -> Unit, openTodayOnLaunch: Boolean = false, role: String = "") {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var rows by remember { mutableStateOf<List<SessionRow>>(emptyList()) }
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sessionMembershipsByHalaqa by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var add by remember { mutableStateOf(false) }
    // RC32: the open session, the list position and the expanded months survive leaving the screen (student profile,
    // another tab and back) so closing a session returns to the same place instead of the top of the list.
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    val addRequested by com.mohammedsamir.halaqati.ui.navigation.AddActionBus.requested.collectAsState()
    LaunchedEffect(addRequested) {
        if (addRequested == "session") {
            com.mohammedsamir.halaqati.ui.navigation.AddActionBus.consume()
            add = true
        }
    }
    val active: SessionRow? = activeId?.let { id -> rows.firstOrNull { it.id == id } }
    val sessionsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var editingSession by remember { mutableStateOf<SessionRow?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<SessionRow?>(null) }
    var todayHalaqaPicker by remember { mutableStateOf(false) }
    var showDeletedSessions by remember { mutableStateOf(false) }
    var showSessionsMenu by remember { mutableStateOf(false) }

    suspend fun reload(force: Boolean = false) {
        loading = rows.isEmpty()
        try {
            val result = withContext(Dispatchers.IO) {
                val sessionRows = AppGraph.repo.sessions(forceRefresh = force, includeArchived = showArchived)
                val studentRows = AppGraph.repo.students(forceRefresh = force)
                val h = AppGraph.repo.table("halaqas", "select=id,name,active,halaqa_type,program_specialization,affects_primary_plan,starts_on,ends_on&order=name.asc&limit=200", forceRefresh = force)
                val m = AppGraph.repo.studentHalaqaMemberships(forceRefresh = force)
                val memberMap = (0 until m.length()).mapNotNull { m.optJSONObject(it) }
                    .filter { it.optString("status", "active") == "active" }
                    .groupBy { it.optString("halaqa_id") }
                    .mapValues { (_, list) -> list.mapNotNull { it.optString("student_id").takeIf(String::isNotBlank) }.toSet() }
                (Triple(sessionRows, studentRows, (0 until h.length()).mapNotNull { h.optJSONObject(it) })) to memberMap
            }
            rows = if (showArchived) result.first.first.filter { it.archived } else result.first.first.filterNot { it.archived }
            students = result.first.second.filter { it.status == "active" }
            halaqas = result.first.third
            sessionMembershipsByHalaqa = result.second
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر تحميل الجلسات"
        } finally {
            loading = false
        }
    }

    var todaySessionChoices by remember { mutableStateOf<List<SessionRow>>(emptyList()) }

    fun openTodayFor(halaqaId: String) {
        val today = LocalDate.now().toString()
        val sessions = rows.filter { it.halaqaId == halaqaId && it.date == today && it.status != "cancelled" && !it.archived }
        when (sessions.size) {
            0 -> message = "لا توجد جلسة اليوم لهذا النشاط. اختر «جلسة جديدة» لإنشائها."
            1 -> {
                activeId = sessions.single().id
                message = null
            }
            else -> todaySessionChoices = sessions
        }
    }

    // RC25: work inside one halaqa, month by month.
    val currentUserId = remember { AppGraph.store.load()?.userId }
    val savedScopes by AppGraph.halaqaScope.selected.collectAsState()
    val activeHalaqaChoices = halaqas.filter { it.optBoolean("active", true) }.map { it.optString("id") to it.optString("name", "حلقة") }
    val halaqaScope = HalaqaScopeRules.resolve(role, activeHalaqaChoices.map { it.first }, savedScopes[currentUserId])
    fun currentScopeId(): String? = HalaqaScopeRules.resolve(
        role,
        halaqas.filter { it.optBoolean("active", true) }.map { it.optString("id") },
        AppGraph.halaqaScope.savedFor(currentUserId),
    ).selectedId
    var monthOverrides by rememberSaveable(
        halaqaScope.selectedId,
        stateSaver = androidx.compose.runtime.saveable.Saver<Map<MonthKey, Boolean>, List<String>>(
            save = { value -> value.map { (k, v) -> "${k.year}-${k.month}-$v" } },
            restore = { list ->
                list.mapNotNull { t ->
                    val p = t.split("-")
                    val y = p.getOrNull(0)?.toIntOrNull()
                    val m = p.getOrNull(1)?.toIntOrNull()
                    if (p.size == 3 && y != null && m != null) MonthKey(y, m) to (p[2] == "true") else null
                }.toMap()
            },
        ),
    ) { mutableStateOf<Map<MonthKey, Boolean>>(emptyMap()) }
    val sessionKind = rememberWorkKind()
    val sessionTypes by com.mohammedsamir.halaqati.data.WorkModeHolder.state.collectAsState()
    val scopedRows = if (halaqaScope.selectedId != null) rows.filter { it.halaqaId == halaqaScope.selectedId } else rows.filter {
        // RC27.10: halaqat and programs are listed apart; a program teacher only has program sessions.
        com.mohammedsamir.halaqati.plans.WorkModeRules.isProgramType(sessionTypes.typeById[it.halaqaId]) ==
            (sessionKind == com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM)
    }
    val monthGroups = remember(scopedRows) { MonthGrouping.group(scopedRows) { it.date } }
    val todayMonth = remember { MonthKey.parse(LocalDate.now().toString()) ?: MonthKey(2026, 1) }
    val defaultOpenMonths = remember(monthGroups, todayMonth) { MonthGrouping.defaultExpanded(monthGroups.map { it.key }, todayMonth) }

    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { if (syncEpoch > 0L) reload() }
    LaunchedEffect(showArchived) { reload(force = false) }

    LaunchedEffect(openTodayOnLaunch) {
        reload()
        if (openTodayOnLaunch && !showArchived) {
            val activeHalaqas = halaqas.filter { it.optBoolean("active", true) }
            val scopedId = currentScopeId()
            when (if (scopedId != null) -1 else activeHalaqas.size) {
                -1 -> runCatching { openTodayFor(scopedId.orEmpty()) }
                    .onFailure { message = it.message ?: "تعذر فتح جلسة اليوم" }
                0 -> message = "لا توجد حلقة مفعلة لفتح جلسة اليوم"
                1 -> runCatching { openTodayFor(activeHalaqas.first().optString("id")) }
                    .onFailure { message = it.message ?: "تعذر فتح جلسة اليوم" }
                else -> todayHalaqaPicker = true
            }
        }
    }

    fun openToday() {
        val activeHalaqas = halaqas.filter { it.optBoolean("active", true) }
        val scopedId = currentScopeId()
        when (if (scopedId != null) -1 else activeHalaqas.size) {
            -1 -> scope.launch {
                try { openTodayFor(scopedId.orEmpty()) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { message = e.message ?: "تعذر فتح جلسة اليوم" }
            }
            0 -> message = "لا توجد حلقة مفعلة لفتح جلسة اليوم"
            1 -> scope.launch {
                try { openTodayFor(activeHalaqas.first().optString("id")) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { message = e.message ?: "تعذر فتح جلسة اليوم" }
            }
            else -> todayHalaqaPicker = true
        }
    }

    active?.let { session ->
        SessionRosterScreen(
            session = session,
            students = students.filter { it.status == "active" && (it.halaqaId == session.halaqaId || it.id in sessionMembershipsByHalaqa[session.halaqaId].orEmpty()) },
            programSession = halaqas.firstOrNull { it.optString("id") == session.halaqaId }
                ?.optString("halaqa_type").let { t -> !com.mohammedsamir.halaqati.plans.SmartPlanEligibility.isHalaqaType(t?.takeIf { it != "null" }) },
            pending = pending,
            close = { activeId = null },
            onEditSession = { editingSession = it; activeId = null },
            onChanged = { scope.launch { reload() } },
        )
        return
    }

    if (showDeletedSessions) {
        DeletedSessionsDialog(
            halaqaNames = halaqas.associate { it.optString("id") to it.optString("name") },
            onDismiss = { showDeletedSessions = false },
            onRestored = { scope.launch { reload(force = true) } },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = "الجلسات",
                kicker = "الجلسات اليومية · سجل اللقاءات اليومية",
                onRefresh = {
                    scope.launch { reload(force = true) }
                    onRefresh()
                },
                pending = pending,
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = HalaqatiUiTokens.ScreenHorizontal, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val unassignedCount = students.count { it.status == "active" && it.halaqaId.isNullOrBlank() }
                val scopeName = activeHalaqaChoices.firstOrNull { it.first == halaqaScope.selectedId }?.second ?: "كل الحلقات"
                // RC27.9: one compact row; every secondary tool lives in a popup menu instead of taking half the page.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = ::openToday,
                        modifier = Modifier.weight(1f).heightIn(min = HalaqatiUiTokens.ActionHeight),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("ابدأ جلسة اليوم", fontWeight = FontWeight.Bold)
                    }
                    BadgedBox(badge = { if (unassignedCount > 0) Badge { Text(unassignedCount.toString()) } }) {
                        FilledTonalIconButton(
                            onClick = { showSessionsMenu = true },
                            modifier = Modifier.size(HalaqatiUiTokens.ActionHeight),
                            shape = RoundedCornerShape(16.dp),
                        ) { Icon(Icons.Default.Tune, contentDescription = "أدوات الجلسات") }
                    }
                }
                AssistChip(
                    onClick = { showSessionsMenu = true },
                    label = { Text("$scopeName · " + if (showArchived) "الأرشيف" else "الجلسات الحالية", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                )
                if (showSessionsMenu) {
                    ModalBottomSheet(onDismissRequest = { showSessionsMenu = false }) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("أدوات الجلسات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (halaqaScope.showSwitcher) {
                                HalaqaScopeBar(
                                    halaqas = activeHalaqaChoices,
                                    selectedId = halaqaScope.selectedId,
                                    onSelect = { AppGraph.halaqaScope.select(currentUserId, it) },
                                )
                            }
                            if (halaqaScope.selectedId == null) WorkKindToggle()
                            ResponsiveChipGrid(
                                items = listOf(false to "الجلسات الحالية", true to "الأرشيف"),
                                minItemWidth = 130.dp,
                                maxColumns = 2,
                            ) { pair, itemModifier ->
                                ProFilterChip(showArchived == pair.first, { showArchived = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                            }
                            FilledTonalButton(
                                onClick = { showSessionsMenu = false; add = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.ActionHeight),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(7.dp))
                                Text("جلسة مخصصة")
                            }
                            if (role == "admin") {
                                OutlinedButton(
                                    onClick = { showSessionsMenu = false; showDeletedSessions = true },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.ActionHeight),
                                ) {
                                    Icon(Icons.Default.RestoreFromTrash, contentDescription = null)
                                    Spacer(Modifier.width(7.dp))
                                    Text("الجلسات المحذوفة (استعادة)")
                                }
                            }
                            if (showArchived) {
                                Text(
                                    "الجلسات المؤرشفة محفوظة ويمكن تعديل بياناتها أو استعادتها دون فقد سجلها.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (unassignedCount > 0) {
                                StateNotice("يوجد $unassignedCount طالب نشط غير مربوط بحلقة. لن يظهروا في أي جلسة حتى يتم ربطهم من بيانات الطالب.", "warning")
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
            message?.let {
                StateNotice(
                    it,
                    kind = if (it.startsWith("تم")) "success" else "error",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (loading && rows.isEmpty()) LoadingSkeleton(rows = 3, modifier = Modifier.padding(HalaqatiUiTokens.ScreenHorizontal))
            if (!loading && scopedRows.isEmpty() && !(halaqaScope.showOverview && !showArchived)) EmptyState(if (showArchived) "لا توجد جلسات مؤرشفة" else "لا توجد جلسات في هذه الحلقة حتى الآن")

            LazyColumn(
                state = sessionsListState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = HalaqatiUiTokens.ScreenHorizontal, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (halaqaScope.showOverview && !showArchived) {
                val overviewRows = HalaqaOverview.build(
                    activeHalaqaChoices.map { it.first },
                    rows.filterNot { it.archived }.map { OverviewSession(it.halaqaId, it.date) },
                    students.map { OverviewStudent(it.halaqaId, it.status == "active") },
                    LocalDate.now().toString(),
                ).map { row ->
                    // RC27.19: participants from other halaqat count toward a program's student number.
                    val own = students.filter { it.status == "active" && it.halaqaId == row.halaqaId }.map { it.id }
                    row.copy(studentCount = (own + sessionMembershipsByHalaqa[row.halaqaId].orEmpty()).toSet().size)
                }
                items(overviewRows, key = { "overview-" + it.halaqaId }, contentType = { "overview" }) { row ->
                    val halaqaJson = halaqas.firstOrNull { it.optString("id") == row.halaqaId }
                    HalaqaOverviewCard(
                        name = halaqaJson?.optString("name", "حلقة") ?: "حلقة",
                        row = row,
                        onOpen = { AppGraph.halaqaScope.select(currentUserId, row.halaqaId) },
                    )
                }
              } else monthGroups.forEach { group ->
                val isOpen = monthOverrides[group.key] ?: (group.key in defaultOpenMonths)
                item(key = "month-" + group.key.year + "-" + group.key.month, contentType = "month") {
                    MonthSectionHeader(
                        label = group.key.label(),
                        summary = "${group.items.size} جلسة",
                        expanded = isOpen,
                        onToggle = { monthOverrides = monthOverrides + (group.key to !isOpen) },
                    )
                }
                if (isOpen) items(group.items, key = { it.id }, contentType = { "session" }) { session ->
                    androidx.compose.foundation.layout.Box(Modifier.animateItem()) {
GoldenSessionCard(
                        session = session,
                        onClick = { if (!session.archived) activeId = session.id else editingSession = session },
                        onEdit = { editingSession = session },
                        onArchive = {
                            scope.launch {
                                com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.setSessionArchived(session, true) } }
                                    .onSuccess { message = "تمت أرشفة الجلسة"; reload(); onRefresh() }
                                    .onFailure { message = it.message ?: "تعذر أرشفة الجلسة" }
                            }
                        },
                        onRestore = {
                            scope.launch {
                                com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.restoreSession(session) } }
                                    .onSuccess { message = "تمت استعادة الجلسة"; reload(); onRefresh() }
                                    .onFailure { message = it.message ?: "تعذر استعادة الجلسة" }
                            }
                        },
                        onDelete = { deleteCandidate = session },
                    )
}
                }
              }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }

    if (todayHalaqaPicker) {
        HalaqatiLazySelectSheet(
            title = "اختر الحلقة لجلسة اليوم",
            selectedKey = null,
            options = halaqas.filter { it.optBoolean("active", true) }.map { it.optString("id") to it.optString("name", "حلقة") },
            searchable = halaqas.size > 10,
            onDismiss = { todayHalaqaPicker = false },
            onSelected = { id ->
                todayHalaqaPicker = false
                scope.launch {
                    try { openTodayFor(id) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { message = e.message ?: "تعذر فتح جلسة اليوم" }
                }
            },
        )
    }

    if (todaySessionChoices.isNotEmpty()) {
        HalaqatiAlertDialog(
            onDismissRequest = { todaySessionChoices = emptyList() },
            title = { Text("اختر جلسة اليوم") },
            text = {
                Column {
                    todaySessionChoices.forEach { session ->
                        TextButton(onClick = {
                            activeId = session.id
                            todaySessionChoices = emptyList()
                        }) { Text(session.title.ifBlank { sessionTypeAr(session.type) }) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { todaySessionChoices = emptyList() }) { Text("إلغاء") } },
        )
    }

    if (add) {
        NewSessionDialog(
            halaqas = halaqas,
            initialHalaqaId = currentScopeId(),
            close = { add = false },
            save = { halaqaId, date, type, title ->
                scope.launch {
                    try {
                        val sessionId = withContext(Dispatchers.IO) {
                            AppGraph.repo.createSession(halaqaId, date, type, title)
                        }
                        add = false
                        showArchived = false
                        reload()
                        activeId = sessionId
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = e.message ?: "تعذر إنشاء الجلسة"
                    }
                }
            },
        )
    }

    editingSession?.let { session ->
        EditSessionDialog(
            session = session,
            halaqas = halaqas,
            close = { editingSession = null },
            save = { halaqaId, date, type, title, notes ->
                scope.launch {
                    try {
                        val updated = withContext(Dispatchers.IO) {
                            AppGraph.repo.updateSessionDetails(session, halaqaId, date, type, title, notes)
                        }
                        rows = rows.map { if (it.id == updated.id) updated else it }
                            .filter { if (showArchived) it.archived else !it.archived }
                            .sortedByDescending { it.date }
                        editingSession = null
                        message = "تم تعديل بيانات الجلسة وتحديث ارتباطها بالخطط الشهرية"
                        onRefresh()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = e.message ?: "تعذر تعديل بيانات الجلسة"
                    }
                }
            },
        )
    }

    deleteCandidate?.let { session ->
        HalaqatiAlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("حذف الجلسة") },
            text = { Text("تبقى الجلسة في القائمة حتى يؤكد الخادم حذفها. إذا كان فيها حضور أو تسميع أو ملاحظات محفوظة، استخدم الأرشفة.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteCandidate = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { AppGraph.repo.deleteSession(session.id) }
                                message = "أُضيف طلب الحذف إلى المزامنة"
                                onRefresh()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = e.message ?: "تعذر إرسال طلب الحذف"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun GoldenStudentCard(
    student: Student,
    halaqaName: String?,
    categoryName: String?,
    secondaryHalaqaNames: List<String>,
    snapshot: PlanSnapshot?,
    monthStats: JSONObject?,
    programContext: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenProfile: () -> Unit,
    onRecitation: () -> Unit,
    onPlan: () -> Unit,
    onEdit: () -> Unit,
    readOnly: Boolean = false,
) {
    // RC27.47: guardians are linked by national id + birth date, so an active student without them is flagged.
    val missingGuardianData = student.status == "active" && (student.nationalId.isNullOrBlank() || student.dateOfBirth.isNullOrBlank())
    val isTathbit = student.track == "tathbit"
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().calmContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = com.mohammedsamir.halaqati.ui.theme.cardHairline()
            ?: androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 80.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                com.mohammedsamir.halaqati.ui.components.StudentAvatar(student.photoPath, student.fullName, 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.mohammedsamir.halaqati.ui.components.OneLineName(
                            student.fullName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (missingGuardianData) {
                            Icon(Icons.Default.Info, contentDescription = "الهوية أو تاريخ الميلاد غير مكتمل", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                        }
                    }
                    // Same three facts as before (track · halaqa · extra programs), now split into a chip row and a quiet line.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val chipBg = if (isTathbit) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                        val chipFg = if (isTathbit) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        Surface(color = chipBg, contentColor = chipFg, shape = RoundedCornerShape(11.dp)) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(if (isTathbit) Icons.Default.PushPin else Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(if (programContext) "مشارك في برنامج" else if (isTathbit) "التثبيت" else "الحفظ العام", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                        if (secondaryHalaqaNames.isNotEmpty()) {
                            Text(
                                "+${secondaryHalaqaNames.size} برنامج",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val quietLine = buildList {
                        halaqaName?.takeIf(String::isNotBlank)?.let(::add)
                    }.joinToString(" · ")
                    if (quietLine.isNotBlank()) {
                        Text(quietLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (student.status != "active") StatusPill(studentStatusAr(student.status), kind = "info")
                }
                snapshot?.progress?.let { progress -> StudentPlanRing(progress.percentage) }
                IconButton(onClick = onToggle, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "طي تفاصيل الطالب" else "فتح تفاصيل الطالب")
                }
            }

            AnimatedVisibility(visible = expanded, enter = motionEnter(fadeIn() + expandVertically()), exit = motionExit(fadeOut() + shrinkVertically())) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (missingGuardianData) {
                        StatusPill(
                            listOfNotNull("الهوية".takeIf { student.nationalId.isNullOrBlank() }, "تاريخ الميلاد".takeIf { student.dateOfBirth.isNullOrBlank() })
                                .joinToString(" و", prefix = "ناقص: "),
                            kind = "warning",
                        )
                    }

                    snapshot?.progress?.let { progress ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("المنجز ${plainNumber(progress.completed)} ص", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                                Text("المطلوب حتى اليوم ${plainNumber(progress.plannedByToday)} ص", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            com.mohammedsamir.halaqati.ui.components.NourProgress((progress.percentage / 100.0).coerceIn(0.0, 1.0).toFloat())
                        }
                    }

                    Text(
                        buildList {
                            categoryName?.takeIf(String::isNotBlank)?.let { add("الفئة: $it") }
                            studentAge(student.dateOfBirth)?.let { add("العمر: $it سنة") }
                            if (secondaryHalaqaNames.isNotEmpty()) add("البرامج: ${secondaryHalaqaNames.joinToString("، ")}")
                        }.joinToString(" · ").ifBlank { "لا توجد تفاصيل إضافية" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    monthStats?.let { stats ->
                        val hifz = stats.optDouble("hifz_pages", 0.0)
                        val tathbit = stats.optDouble("tathbit_pages", 0.0)
                        val review = stats.optDouble("review_pages", 0.0)
                        val absence = stats.optInt("absent", 0)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!programContext) {
                                StudentStatTile(if (isTathbit) "تثبيت" else "حفظ", "${plainNumber(if (isTathbit) tathbit else hifz)} ص", Modifier.weight(1f))
                                StudentStatTile("مراجعة", "${plainNumber(review)} ص", Modifier.weight(1f))
                            } else {
                                val pages = stats.optDouble("tilawah_pages", 0.0)
                                StudentStatTile("التلاوة", if (pages > 0) "${plainNumber(pages)} ص" else "—", Modifier.weight(1f))
                            }
                            StudentStatTile("غياب", if (programContext && stats.optInt("sessions", 0) == 0) "—" else "$absence", Modifier.weight(1f))
                        }
                    }
                    // RC53: the supervisor only follows students; recitation and editing belong to the teacher.
                    if (!readOnly) Button(onClick = onRecitation, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (programContext) "جلسة التلاوة أو الاختبار" else "تسميع اليوم") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        TextButton(onClick = onOpenProfile, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("الملف الشخصي", maxLines = 1)
                        }
                        if (!readOnly && !programContext) TextButton(onClick = onPlan, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("الخطة", maxLines = 1)
                        }
                    }
                    if (!readOnly) TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تعديل بيانات الطالب والانتساب")
                    }
                }
            }
        }
    }
}

/** Purely visual: the plan completion percentage (progress.percentage) drawn as a ring, with the "الخطة" caption. */
@Composable
private fun StudentPlanRing(percentage: Double) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = MaterialTheme.colorScheme.primary
    val fraction = (percentage / 100.0).coerceIn(0.0, 1.0).toFloat()
    // The ring grows with the system font scale (capped) so the percentage never overflows it.
    val ringSize = 44.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 1.4f)
    val label = "الخطة ${plainNumber(percentage)}%"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 52.dp).studentCardSemantics(label),
    ) {
        Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2)
                val area = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
                drawArc(track, 0f, 360f, false, topLeft, area, style = stroke)
                if (fraction > 0f) drawArc(color, -90f, 360f * fraction, false, topLeft, area, style = stroke)
            }
            Text("${plainNumber(percentage)}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text("الخطة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

private fun Modifier.studentCardSemantics(description: String): Modifier =
    this.then(Modifier.semantics(mergeDescendants = true) { contentDescription = description })

@Composable
private fun StudentStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun studentAge(dateOfBirth: String?): Int? {
    val dob = dateOfBirth?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return null
    return Period.between(dob, LocalDate.now()).years.takeIf { it in 3..100 }
}

@Composable
private fun GoldenSessionCard(
    session: SessionRow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(session.id) { mutableStateOf(false) }
    val displayStatus = SessionWorkflowRules.displayStatus(session.status, session.date, session.archived, LocalDate.now(GazaTime.zone))
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            com.mohammedsamir.halaqati.ui.components.GoldenNavIcon(
                key = "sessions",
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session.title.ifBlank { "جلسة اليوم — ${sessionTypeAr(session.type)}" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    buildList {
                        add(session.date)
                        add(sessionTypeAr(session.type))
                        add(displayStatus)
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.notes.isNotBlank()) {
                    Text(session.notes, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "خيارات الجلسة")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("تعديل بيانات الجلسة") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    if (session.archived) {
                        DropdownMenuItem(
                            text = { Text("استعادة الجلسة") },
                            leadingIcon = { Icon(Icons.Default.Restore, null) },
                            onClick = { menuExpanded = false; onRestore() },
                        )
                    } else {
                        DropdownMenuItem(text = { Text("أرشفة الجلسة") }, leadingIcon = { Icon(Icons.Default.Archive, null) }, onClick = { menuExpanded = false; onArchive() })
                    }
                    DropdownMenuItem(text = { Text("حذف الجلسة") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

fun sessionTypeAr(type: String) = when (type) {
    "tasmee" -> "تسميع"
    "sard" -> "سرد"
    "review" -> "مراجعة"
    "test" -> "اختبار"
    "tajweed", "tilawah" -> com.mohammedsamir.halaqati.plans.ProgramKindRules.TILAWAH_TAJWEED_LABEL
    "lesson" -> "نشاط تربوي"
    "other" -> "أخرى"
    else -> type
}

@Composable
private fun NewSessionDialog(
    halaqas: List<JSONObject>,
    initialHalaqaId: String?,
    close: () -> Unit,
    save: (String, String, String, String) -> Unit,
) {
    val halaqaOptions = halaqas.filter { it.optBoolean("active", true) }
        .map { it.optString("id") to it.optString("name").ifBlank { "حلقة" } }
    var halaqaId by remember(halaqas, initialHalaqaId) {
        mutableStateOf(if (initialHalaqaId != null) initialHalaqaId.takeIf { id -> halaqaOptions.any { it.first == id } }.orEmpty() else halaqaOptions.singleOrNull()?.first.orEmpty())
    }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var preferredType by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val selectedHalaqaType = halaqas.firstOrNull { it.optString("id") == halaqaId }?.optString("halaqa_type")
    val types = SessionWorkflowRules.manualTypesForScope(selectedHalaqaType)
    val type = preferredType?.takeIf { it in types } ?: types.first()

    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text("جلسة جديدة") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HalaqatiLazySelectField(
                    label = "الحلقة / البرنامج",
                    selectedKey = halaqaId,
                    options = halaqaOptions,
                    onSelected = { halaqaId = it; preferredType = null },
                    modifier = Modifier.fillMaxWidth(),
                    searchable = halaqaOptions.size > 8,
                )
                HalaqatiDateField("تاريخ الجلسة", date, { showDatePicker = true }, Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("العنوان (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("نوع الجلسة", style = MaterialTheme.typography.labelLarge)
                Text(if (com.mohammedsamir.halaqati.plans.WorkModeRules.isProgramType(selectedHalaqaType))
                    "أنشطة البرنامج مستقلة عن خطة التحفيظ." else "اختر نشاطًا من أنشطة الحلقة.", style = MaterialTheme.typography.bodySmall)
                ResponsiveChipGrid(items = types, minItemWidth = 125.dp, maxColumns = 2) { value, itemModifier ->
                    ProFilterChip(type == value, { preferredType = value }, sessionTypeAr(value), itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
                if (date != LocalDate.now()) StateNotice(if (date.isAfter(LocalDate.now())) "تاريخ مستقبلي؛ تُحفظ الجلسة في السجل وفق حالة الخادم." else "جلسة بتاريخ سابق؛ راجع التاريخ قبل الحفظ.", "info")
            }
        },
        confirmButton = {
            Button(onClick = { save(halaqaId, date.toString(), type, title.trim()) }, enabled = halaqaId.isNotBlank()) { Text("حفظ وفتح") }
        },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
    if (showDatePicker) {
        HalaqatiDatePickerDialog(date, "اختر تاريخ الجلسة", { showDatePicker = false }) { date = it; showDatePicker = false }
    }
}

@Composable
private fun EditSessionDialog(
    session: SessionRow,
    halaqas: List<JSONObject>,
    close: () -> Unit,
    save: (String, String, String, String, String) -> Unit,
) {
    var halaqaId by remember(session.id) { mutableStateOf(session.halaqaId) }
    var date by remember(session.id) { mutableStateOf(runCatching { LocalDate.parse(session.date) }.getOrDefault(LocalDate.now())) }
    var type by remember(session.id) { mutableStateOf(session.type) }
    var title by remember(session.id) { mutableStateOf(session.title) }
    var notes by remember(session.id) { mutableStateOf(session.notes) }
    var showDatePicker by remember(session.id) { mutableStateOf(false) }
    val selectedType = halaqas.firstOrNull { it.optString("id") == halaqaId }?.optString("halaqa_type")
    val baseTypes = SessionWorkflowRules.manualTypesForScope(selectedType)
    val types = if (halaqaId == session.halaqaId) (baseTypes + session.type).distinct() else baseTypes
    val selectedTypeValue = type.takeIf { it in types } ?: types.first()
    val halaqaOptions = halaqas.filter { it.optBoolean("active", true) || it.optString("id") == session.halaqaId }
        .map { it.optString("id") to it.optString("name").ifBlank { "حلقة" } }

    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text(if (session.archived) "تعديل جلسة مؤرشفة" else "تعديل بيانات الجلسة") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HalaqatiLazySelectField("الحلقة / البرنامج", halaqaId, halaqaOptions, { halaqaId = it }, Modifier.fillMaxWidth(), searchable = halaqaOptions.size > 8)
                HalaqatiDateField("تاريخ الجلسة", date, { showDatePicker = true }, Modifier.fillMaxWidth())
                Text("نوع الجلسة", style = MaterialTheme.typography.labelLarge)
                ResponsiveChipGrid(items = types, minItemWidth = 125.dp, maxColumns = 2) { value, itemModifier ->
                    ProFilterChip(selectedTypeValue == value, { type = value }, sessionTypeAr(value), itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان الجلسة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات الجلسة") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 180.dp), minLines = 3, maxLines = 6)
                StateNotice("تغيير نوع الجلسة أو نشاطها بعد تسجيل بيانات يحتاج مراجعة الخادم. لا تُنقل بيانات الطلاب تلقائيًا إلى نشاط آخر.", kind = "info")
            }
        },
        confirmButton = { Button(onClick = { save(halaqaId, date.toString(), selectedTypeValue, title.trim(), notes.trim()) }, enabled = halaqaId.isNotBlank()) { Text("حفظ التعديلات") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
    if (showDatePicker) {
        HalaqatiDatePickerDialog(date, "اختر تاريخ الجلسة", { showDatePicker = false }) { date = it; showDatePicker = false }
    }
}

private data class SessionRecitationTarget(
    val student: Student,
    val activityType: String? = null,
    val snapshot: PlanSnapshot? = null,
    val existing: RecitationRow? = null,
)

private data class SessionRosterLoad(
    val attendance: Map<String, AttendanceRow>,
    val notes: Map<String, DailyNoteRow>,
    val recitations: Map<String, List<RecitationRow>>,
    val queue: List<OfflineQueue.SnapshotItem>,
    val plans: Map<String, List<PlanSnapshot>>,
)

@Composable
private fun SessionRosterScreen(
    session: SessionRow,
    students: List<Student>,
    programSession: Boolean = false,
    pending: Int,
    close: () -> Unit,
    onEditSession: (SessionRow) -> Unit,
    onChanged: () -> Unit,
) {
    // RC27.9: the halaqa decides the kind of session; programs never offer hifz/sard/review or smart plans.
    val educational = session.type == "lesson"
    val usesRecitation = SessionWorkflowRules.usesRecitationTools(session.type)
    val isProgramSession = programSession || session.type in setOf("tajweed", "tilawah") || !usesRecitation
    val today = LocalDate.now(GazaTime.zone)
    val displayStatus = SessionWorkflowRules.displayStatus(session.status, session.date, session.archived, today)
    val editable = SessionWorkflowRules.canRecord(session.status, session.archived, session.date, today)
    val activityDoneLabel = if (educational) "تم تقييمه" else if (isProgramSession) "نشاط مسجل" else "سُمّع"
    val activityMissingLabel = if (educational) "بانتظار التقييم" else if (isProgramSession) "لم يسجل نشاطًا" else "لم يُسمّع"
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<SessionRecitationTarget?>(null) }
    var noteStudent by remember { mutableStateOf<Student?>(null) }
    var attendanceDetails by remember { mutableStateOf<Pair<Student, String>?>(null) }
    var attendance by remember { mutableStateOf<Map<String, AttendanceRow>>(emptyMap()) }
    var educationScores by remember(session.id) { mutableStateOf<Map<String, Map<String, Int>>>(emptyMap()) }
    var educationReady by remember(session.id) { mutableStateOf(false) }
    var educationError by remember(session.id) { mutableStateOf<String?>(null) }
    var savingEducation by remember(session.id) { mutableStateOf<Set<String>>(emptySet()) }
    var bulkEducationIds by remember(session.id) { mutableStateOf<Set<String>?>(null) }
    var bulkEducationScore by remember(session.id) { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf<Map<String, DailyNoteRow>>(emptyMap()) }
    var recitations by remember { mutableStateOf<Map<String, List<RecitationRow>>>(emptyMap()) }
    var planSnapshotsByStudent by remember { mutableStateOf<Map<String, List<PlanSnapshot>>>(emptyMap()) }
    var queuedStudents by remember { mutableStateOf<Set<String>>(emptySet()) }
    var blockedStudents by remember { mutableStateOf<Set<String>>(emptySet()) }
    var track by remember { mutableStateOf("all") }
    var statusFilter by remember { mutableStateOf("all") }
    var sortMode by remember { mutableStateOf("priority") }
    var query by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var closeConfirm by remember { mutableStateOf(false) }
    var transitionTarget by remember(session.id) { mutableStateOf<String?>(null) }
    var transitioning by remember(session.id) { mutableStateOf(false) }
    var expandedStudentId by remember { mutableStateOf<String?>(null) }
    var sessionFiltersOpen by remember { mutableStateOf(false) }
    var deleteRecitationTarget by remember { mutableStateOf<Pair<Student, RecitationRow>?>(null) }
    var hiddenRecitationIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var rosterReady by remember(session.id) { mutableStateOf(false) }
    suspend fun loadRosterLocal(includePlans: Boolean = false) {
        try {
            val result = withContext(Dispatchers.IO) {
                val q = AppGraph.repo.queueSnapshot(1000)
                val a = runCatching { AppGraph.repo.attendanceForSession(session.id, allowNetwork = false) }.getOrDefault(emptyMap())
                val n = runCatching { AppGraph.repo.notesForSession(session.id, allowNetwork = false) }.getOrDefault(emptyMap())
                val r = runCatching { AppGraph.repo.recitationsForSession(session.id, allowNetwork = false) }.getOrDefault(emptyMap())
                val plans = if (includePlans) {
                    val sessionDay = runCatching { LocalDate.parse(session.date) }.getOrDefault(LocalDate.now())
                    runCatching { AppGraph.repo.planSnapshots(sessionDay, forceRefresh = false) }
                        .getOrDefault(emptyList())
                        .filter { !isProgramSession && it.plan.halaqaId == session.halaqaId }
                        .groupBy { it.student.id }
                } else emptyMap()
                SessionRosterLoad(a, n, r, q, plans)
            }
            val attendanceOverlay = result.attendance.toMutableMap()
            val notesOverlay = result.notes.toMutableMap()
            val queued = mutableSetOf<String>()
            val blocked = mutableSetOf<String>()
            result.queue.forEach { item ->
                val d = item.dedupe.orEmpty()
                val studentId = when {
                    d.startsWith("attendance:${session.id}:") -> d.substringAfter("attendance:${session.id}:").substringBefore(':')
                    d.startsWith("note:${session.id}:") -> d.substringAfter("note:${session.id}:").substringBefore(':')
                    d.startsWith("recitation:${session.id}:") -> d.substringAfter("recitation:${session.id}:").substringBefore(':')
                    d.startsWith("education:${session.id}:") -> d.substringAfter("education:${session.id}:").substringBefore(':')
                    else -> ""
                }
                if (studentId.isNotBlank()) {
                    if (item.blocked) blocked += studentId else queued += studentId
                    val body = item.body?.let { runCatching { JSONObject(it) }.getOrNull() }
                    when {
                        d.startsWith("attendance:${session.id}:") && body != null -> attendanceOverlay[studentId] = AttendanceRow.from(body)
                        d.startsWith("note:${session.id}:") && body != null -> notesOverlay[studentId] = DailyNoteRow.from(body)
                    }
                }
            }
            attendance = attendanceOverlay
            notes = notesOverlay
            recitations = result.recitations
            if (includePlans) planSnapshotsByStudent = result.plans
            queuedStudents = queued
            blockedStudents = blocked
            message = null
            rosterReady = true
        } catch (_: Exception) {
            message = "تعذر تحديث بعض تفاصيل الجلسة الآن؛ البيانات المحلية ما زالت متاحة"
        }
    }

    suspend fun refreshRosterInBackground() {
        if (!AppGraph.repo.online()) return
        refreshing = true
        try {
            withContext(Dispatchers.IO) { AppGraph.repo.refreshSessionDetails(session.id) }
            loadRosterLocal(includePlans = false)
        } finally {
            refreshing = false
        }
    }

    fun saveStatus(student: Student, status: String, reason: String = "", lateMinutes: Int = 0) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { AppGraph.repo.saveAttendance(session.id, student.id, status, reason, lateMinutes) }
                attendance = attendance + (student.id to AttendanceRow(
                    id = "local:${session.id}:${student.id}", sessionId = session.id, studentId = student.id,
                    status = status, excuseReason = reason, arrivalMinutesLate = lateMinutes,
                ))
                if (!AppGraph.repo.online()) queuedStudents = queuedStudents + student.id
                message = null
                onChanged()
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر حفظ الحضور" }
        }
    }

    fun saveBehavior(student: Student, behavior: String) {
        val current = notes[student.id]
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    AppGraph.repo.saveDailyNote(
                        sessionId = session.id,
                        studentId = student.id,
                        behavior = behavior,
                        behaviorScore = current?.behaviorScore,
                        teacherComment = current?.teacherComment.orEmpty(),
                        homework = current?.homework.orEmpty(),
                        parentVisible = current?.parentVisible ?: true,
                        participation = current?.participation.orEmpty(),
                    )
                }
                notes = notes + (student.id to DailyNoteRow(
                    id = current?.id ?: "local-note:${session.id}:${student.id}",
                    sessionId = session.id,
                    studentId = student.id,
                    behavior = behavior,
                    behaviorScore = current?.behaviorScore,
                    teacherComment = current?.teacherComment.orEmpty(),
                    homework = current?.homework.orEmpty(),
                    parentVisible = current?.parentVisible ?: true,
                    participation = current?.participation.orEmpty(),
                ))
                if (!AppGraph.repo.online()) queuedStudents = queuedStudents + student.id
                onChanged()
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر حفظ السلوك" }
        }
    }

    fun markNotRecited(student: Student) {
        val currentNote = notes[student.id]
        val currentAttendance = attendance[student.id]
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (currentAttendance == null) {
                        AppGraph.repo.saveAttendance(session.id, student.id, "present")
                    }
                    AppGraph.repo.saveDailyNote(
                        sessionId = session.id,
                        studentId = student.id,
                        behavior = currentNote?.behavior ?: "normal",
                        behaviorScore = currentNote?.behaviorScore,
                        teacherComment = currentNote?.teacherComment.orEmpty(),
                        homework = currentNote?.homework.orEmpty(),
                        parentVisible = currentNote?.parentVisible ?: true,
                        participation = SessionWorkflowRules.NOT_RECITED_MARKER,
                    )
                }
                if (currentAttendance == null) {
                    attendance = attendance + (student.id to AttendanceRow(
                        id = "local:${session.id}:${student.id}", sessionId = session.id,
                        studentId = student.id, status = "present",
                    ))
                }
                notes = notes + (student.id to DailyNoteRow(
                    id = currentNote?.id ?: "local-note:${session.id}:${student.id}",
                    sessionId = session.id,
                    studentId = student.id,
                    behavior = currentNote?.behavior ?: "normal",
                    behaviorScore = currentNote?.behaviorScore,
                    teacherComment = currentNote?.teacherComment.orEmpty(),
                    homework = currentNote?.homework.orEmpty(),
                    parentVisible = currentNote?.parentVisible ?: true,
                    participation = SessionWorkflowRules.NOT_RECITED_MARKER,
                ))
                if (!AppGraph.repo.online()) queuedStudents = queuedStudents + student.id
                message = "تم تسجيل ${student.fullName}: لم يُسمّع"
                onChanged()
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تسجيل حالة لم يُسمّع" }
        }
    }

    LaunchedEffect(session.id) {
        loadRosterLocal(includePlans = false)
        launch { loadRosterLocal(includePlans = true) }
        launch { refreshRosterInBackground() }
    }
    val rosterSyncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(rosterSyncEpoch) { if (rosterSyncEpoch > 0L) loadRosterLocal(includePlans = false) }

    suspend fun refreshEducation() {
        if (!educational) return
        try {
            educationScores = withContext(Dispatchers.IO) { AppGraph.repo.educationScores(session.id, refresh = AppGraph.repo.online()) }
            educationReady = true
            educationError = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            educationReady = false
            educationError = "تعذر تحميل التقييمات التربوية. حدّث الجلسة بعد التحقق من اتصال الخادم."
        }
    }

    LaunchedEffect(session.id, rosterSyncEpoch) { refreshEducation() }

    fun saveEducation(ids: Set<String>, metric: String, score: Int, onlyUnrated: Boolean = false) {
        if (!educational || !editable || !educationReady || transitioning) return
        val eligible = ids.filter { EducationScoreRules.eligible(attendance[it]?.status) &&
            "$it:$metric" !in savingEducation &&
            (!onlyUnrated || educationScores[it]?.containsKey(metric) != true) }.toSet()
        if (eligible.isEmpty()) return
        val keys = eligible.map { "$it:$metric" }.toSet()
        savingEducation = savingEducation + keys
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    eligible.forEach { AppGraph.repo.saveEducationScore(session.id, it, metric, score, onlyUnrated) }
                }
                val updated = educationScores.toMutableMap()
                eligible.forEach { id -> updated[id] = updated[id].orEmpty() + (metric to score) }
                educationScores = updated
                queuedStudents = queuedStudents + eligible
                onChanged()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                educationError = e.message ?: "تعذر حفظ التقييم"
                refreshEducation()
            } finally { savingEducation = savingEducation - keys }
        }
    }

    fun stateFor(student: Student): SessionWorkflowRules.StudentState = SessionWorkflowRules.studentState(
        attendanceStatus = attendance[student.id]?.status,
        participation = if (educational && EducationScoreRules.eligible(attendance[student.id]?.status) &&
            educationScores[student.id]?.containsKey(EducationScoreRules.GENERAL) != true) SessionWorkflowRules.NOT_RECITED_MARKER
            else notes[student.id]?.participation.takeIf { usesRecitation },
        hasRecitations = if (educational) educationScores[student.id]?.containsKey(EducationScoreRules.GENERAL) == true
            else usesRecitation && recitations[student.id].orEmpty().any { it.id !in hiddenRecitationIds },
    )

    // The session editor exits without assigning a status to students who have not been observed.
    fun snapshotForActivity(studentId: String, activityType: String): PlanSnapshot? {
        // Tilawah is an independent quality assessment. It must never inherit a Hifz/Tathbit
        // assignment merely because the student also has an active smart plan.
        if (isProgramSession || activityType == "tilawah") return null
        val key = when (activityType) {
            "sard" -> "sard"
            "review_near", "review_far", "review" -> "review"
            "tathbit" -> "tathbit"
            // A generic milestone/test does not advance the independent Sard anchor.
            "test" -> "hifz"
            else -> "hifz"
        }
        val snapshots = planSnapshotsByStudent[studentId].orEmpty()
        return snapshots.firstOrNull { snapshot -> snapshot.activities.any { it.key == key && it.scheduledToday } }
            ?: snapshots.firstOrNull { snapshot -> snapshot.activities.any { it.key == key } }
    }

    val queryNormalized = query.trim()
    val trackAndQuery = students.filter { student ->
        (isProgramSession || track == "all" || student.track == track) &&
            (queryNormalized.isBlank() || SessionWorkflowRules.matchesSearch(student.fullName, queryNormalized) || student.id.contains(queryNormalized, ignoreCase = true))
    }
    // RC27.24: the priority order (not yet marked first...) is frozen when the roster loads or the sort changes. Before, marking
    // attendance re-sorted the list live, so the card you were working on jumped to the bottom and you had to scroll back to it.
    val frozenRank = remember(rosterReady, sortMode, session.id, students.size) {
        students.associate { it.id to SessionWorkflowRules.priority(stateFor(it)) }
    }
    val filtered = trackAndQuery
        .filter { SessionWorkflowRules.matchesFilter(stateFor(it), statusFilter) }
        .let { list ->
            when (sortMode) {
                "alpha" -> list.sortedBy { it.fullName }
                else -> list.sortedWith(compareBy<Student> { frozenRank[it.id] ?: SessionWorkflowRules.priority(stateFor(it)) }.thenBy { it.fullName })
            }
        }
    val sessionSummary = SessionWorkflowRules.summary(students.map(::stateFor))
    val present = sessionSummary.present
    val absent = sessionSummary.absent
    val recited = sessionSummary.recited
    val notRecited = sessionSummary.notRecited
    val unmarked = sessionSummary.unmarked

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = close, enabled = !transitioning, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(session.title.ifBlank { sessionTypeAr(session.type) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${session.date} · ${sessionTypeAr(session.type)} · $displayStatus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onEditSession(session) }, enabled = !transitioning, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل بيانات الجلسة")
                    }
                }
            }

            if (refreshing || transitioning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            if (!editable) StateNotice("هذه الجلسة للعرض فقط حاليًا.", "info", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("ابحث عن طالب", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotBlank()) { { IconButton(onClick = { query = "" }, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) { Icon(Icons.Default.Clear, contentDescription = "مسح البحث") } } } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        SessionMiniTile(present, "حاضر", "success", Modifier.weight(1f))
                        SessionMiniTile(absent, "غائب", "danger", Modifier.weight(1f))
                        SessionMiniTile(sessionSummary.excused, "معذور", "warning", Modifier.weight(1f))
                        SessionMiniTile(if (educational || usesRecitation) recited else unmarked, if (educational) "مقيّم" else if (usesRecitation) "نشاط" else "غير مرصود", "neutral", Modifier.weight(1f))
                    }
                    IconButton(onClick = { sessionFiltersOpen = true }, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                        Icon(Icons.Default.Tune, contentDescription = "تصفية وترتيب")
                    }
                }
            }

            if (sessionFiltersOpen) {
                ModalBottomSheet(onDismissRequest = { sessionFiltersOpen = false }) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("حالة الجلسة والفلترة · ${sessionSummary.total} طالب", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        SessionSummaryStrip(summary = sessionSummary, programSession = isProgramSession, educational = educational)
                        Text("حالة الطالب", style = MaterialTheme.typography.labelLarge)
                        ResponsiveChipGrid(
                            items = listOf("all" to "الكل", "unmarked" to "لم يُرصد", "absent" to "غائب", "excused" to "معذور") +
                                (if (educational) listOf("not_recited" to activityMissingLabel, "recited" to activityDoneLabel)
                                    else if (usesRecitation) listOf("not_recited" to activityMissingLabel, "present_pending" to (if (isProgramSession) "حاضر بلا نشاط" else "حاضر بلا تسميع"), "recited" to activityDoneLabel) else emptyList()),
                            minItemWidth = 112.dp, maxColumns = 2,
                        ) { pair, itemModifier ->
                            ProFilterChip(statusFilter == pair.first, { statusFilter = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                        }
                        Text(if (isProgramSession) "الترتيب" else "المسار والترتيب", style = MaterialTheme.typography.labelLarge)
                        ResponsiveChipGrid(
                            items = (if (isProgramSession) emptyList() else listOf("track_all" to "كل المسارات", "track_hifz" to "الحفظ العام", "track_tathbit" to "التثبيت")) + listOf("sort_priority" to "الأولوية", "sort_alpha" to "أبجدي"),
                            minItemWidth = 112.dp, maxColumns = 2,
                        ) { pair, itemModifier ->
                            ProFilterChip(
                                selected = when (pair.first) { "track_all" -> track == "all"; "track_hifz" -> track == "general_hifz"; "track_tathbit" -> track == "tathbit"; "sort_priority" -> sortMode == "priority"; else -> sortMode == "alpha" },
                                onClick = { when (pair.first) { "track_all" -> track = "all"; "track_hifz" -> track = "general_hifz"; "track_tathbit" -> track = "tathbit"; "sort_priority" -> sortMode = "priority"; else -> sortMode = "alpha" } },
                                label = pair.second, modifier = itemModifier.fillMaxWidth(), showCheckIcon = false,
                            )
                        }
                        if (educational && editable && educationReady) {
                            val candidates = EducationScoreRules.bulkCandidates(attendance.mapValues { it.value.status }, educationScores)
                                .intersect(filtered.map { it.id }.toSet())
                            OutlinedButton(onClick = {
                                bulkEducationIds = candidates; bulkEducationScore = null; sessionFiltersOpen = false
                            }, enabled = candidates.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                Text("تقييم الحاضرين غير المقيّمين (${candidates.size})")
                            }
                        }
                        Button(onClick = { sessionFiltersOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("تطبيق وعرض الطلاب") }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            // RC40: the global sync banner already reports waiting operations; only blocked ones need a notice here.
            if (blockedStudents.isNotEmpty()) {
                StateNotice(
                    text = "${blockedStudents.size} طالب لديهم عملية مزامنة تحتاج مراجعة",
                    kind = "error",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            message?.let { StateNotice(it, kind = if (it.startsWith("تم")) "success" else "warning", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
            educationError?.let { StateNotice(it, "warning", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshRosterInBackground()
                        loadRosterLocal(includePlans = true)
                        refreshEducation()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
            if (filtered.isEmpty()) {
                EmptyState("لا يوجد طلاب يطابقون البحث أو الفلتر")
            } else {
                LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(filtered, key = { it.id }, contentType = { "session_student_rc92" }) { student ->
                        var quickActionsOpen by remember(student.id) { mutableStateOf(false) }
                        val status = attendance[student.id]?.status
                        val behavior = notes[student.id]?.behavior ?: "normal"
                        val state = stateFor(student)
                        val syncLabel = when {
                            student.id in blockedStudents -> "خطأ مزامنة"
                            student.id in queuedStudents -> "بانتظار المزامنة"
                            else -> null
                        }
                        // RC53: every student card has a clear outline whose colour tells the state, on a surface that differs
                        // from the page background, so neighbouring cards no longer melt into each other.
                        val cardAccent = when (state) {
                            SessionWorkflowRules.StudentState.ABSENT -> MaterialTheme.colorScheme.error
                            SessionWorkflowRules.StudentState.EXCUSED -> MaterialTheme.colorScheme.tertiary
                            SessionWorkflowRules.StudentState.NOT_RECITED -> MaterialTheme.colorScheme.outline
                            SessionWorkflowRules.StudentState.RECITED -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        }
                        val cardColor = cardAccent.copy(alpha = .08f).compositeOver(MaterialTheme.colorScheme.surface)
                        Card(
                            onClick = { expandedStudentId = if (expandedStudentId == student.id) null else student.id },
                            modifier = Modifier.fillMaxWidth().calmContentSize(),
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, cardAccent.copy(alpha = .75f)),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    com.mohammedsamir.halaqati.ui.components.StudentAvatar(student.photoPath, student.fullName, 38.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        OneLineName(student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(
                                                when (state) {
                                                    SessionWorkflowRules.StudentState.UNMARKED -> "لم يُرصد بعد"
                                                    SessionWorkflowRules.StudentState.NOT_RECITED -> activityMissingLabel
                                                    SessionWorkflowRules.StudentState.RECITED -> if (educational) "$activityDoneLabel · ${educationScores[student.id]?.get(EducationScoreRules.GENERAL) ?: "—"}/5" else "$activityDoneLabel ${recitations[student.id].orEmpty().size}"
                                                    SessionWorkflowRules.StudentState.ABSENT -> "غائب"
                                                    SessionWorkflowRules.StudentState.EXCUSED -> "غائب بعذر"
                                                    SessionWorkflowRules.StudentState.LATE -> "متأخر"
                                                    SessionWorkflowRules.StudentState.PRESENT -> "حاضر"
                                                },
                                                behaviorLabel(behavior).takeUnless { educational }, syncLabel,
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (student.id in blockedStudents) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (syncLabel != null) Icon(
                                        if (student.id in blockedStudents) Icons.Default.ErrorOutline else Icons.Default.CloudUpload,
                                        contentDescription = syncLabel,
                                        tint = if (student.id in blockedStudents) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                    IconButton(
                                        onClick = { expandedStudentId = if (expandedStudentId == student.id) null else student.id },
                                        modifier = Modifier.size(HalaqatiUiTokens.TouchTarget),
                                    ) {
                                        Icon(
                                            if (expandedStudentId == student.id) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (expandedStudentId == student.id) "طي تفاصيل الطالب" else "فتح تفاصيل الطالب",
                                        )
                                    }
                                }

                                if (educational && EducationScoreRules.eligible(status)) {
                                    EducationRatingSlider(
                                        label = "التقييم العام",
                                        score = educationScores[student.id]?.get(EducationScoreRules.GENERAL),
                                        enabled = editable && educationReady && !transitioning && "${student.id}:general" !in savingEducation,
                                        onCommit = { saveEducation(setOf(student.id), EducationScoreRules.GENERAL, it) },
                                    )
                                }

                                // RC58: an open card already shows the last recitation inside the smart-plan block, so it is not repeated here.
                                (if (usesRecitation) recitations[student.id].orEmpty() else emptyList()).filterNot { it.id in hiddenRecitationIds }.lastOrNull()
                                    ?.takeUnless { expandedStudentId == student.id && !isProgramSession && planSnapshotsByStudent[student.id]?.isNotEmpty() == true }
                                    ?.let { latest ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                            Text(recitationActivityAr(latest.activityType), Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                                        }
                                        Text(
                                            recitationRangeText(latest),
                                            Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text("${plainNumber(latest.pages)} ص", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }

                                AnimatedVisibility(
                                    visible = expandedStudentId == student.id,
                                    enter = motionEnter(fadeIn() + expandVertically()),
                                    exit = motionExit(fadeOut() + shrinkVertically()),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (editable && !transitioning) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SessionChoiceMenu(
                                        label = "الحضور",
                                        selected = status ?: "unmarked",
                                        options = listOf("present" to "حاضر", "absent" to "غائب", "excused" to "غائب بعذر", "late" to "متأخر"),
                                        leadingIcon = when (status) {
                                            "absent" -> Icons.Default.Cancel
                                            "late" -> Icons.Default.Schedule
                                            "excused" -> Icons.Default.Info
                                            else -> Icons.Default.CheckCircle
                                        },
                                        onSelected = { value ->
                                            when (value) {
                                                "late", "excused" -> attendanceDetails = student to value
                                                else -> saveStatus(student, value)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!educational) SessionChoiceMenu(
                                        label = "السلوك",
                                        selected = behavior,
                                        options = listOf("excellent" to "ممتاز", "good" to "جيد", "normal" to "طبيعي", "needs_attention" to "يحتاج انتباه", "warning" to "تنبيه"),
                                        leadingIcon = Icons.Default.EmojiEmotions,
                                        onSelected = { saveBehavior(student, it) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                if (educational && EducationScoreRules.eligible(status)) {
                                    EducationScoreRules.detailed.forEach { (metric, label) ->
                                        EducationRatingSlider(label, educationScores[student.id]?.get(metric),
                                            editable && educationReady && !transitioning && "${student.id}:$metric" !in savingEducation,
                                            onCommit = { saveEducation(setOf(student.id), metric, it) })
                                    }
                                }

                                planSnapshotsByStudent[student.id]?.takeIf { it.isNotEmpty() }?.let { snapshots ->
                                    if (!isProgramSession && editable && !transitioning) {
                                        SessionSmartPlanCard(snapshots, session.date) { activity, snapshot ->
                                            selected = SessionRecitationTarget(student, activity, snapshot)
                                        }
                                    } else if (isProgramSession) {
                                        StateNotice("جلسة ${sessionTypeAr(session.type)} مستقلة عن تقدم خطة الحفظ الأساسية.", "info")
                                    }
                                }

                                (if (usesRecitation) recitations[student.id].orEmpty() else emptyList()).filterNot { it.id in hiddenRecitationIds }.forEach { rec ->
                                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .88f), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        ) {
                                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text("${recitationActivityAr(rec.activityType)} · ${recitationRangeText(rec)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                                val tilawahMeta = rec.tilawahAssessment
                                                Text(
                                                    if (rec.activityType == "tilawah" && tilawahMeta != null) {
                                                        "${plainNumber(rec.pages)} ص · ${recitationEvaluationAr(rec.evaluation)} · لحن جلي ${tilawahMeta.optInt("lahn_jali", 0)} · لحن خفي ${tilawahMeta.optInt("lahn_khafi", 0)} · نتيجة ${plainNumber(tilawahMeta.optDouble("overall_score", rec.safetyPercentage ?: 0.0))}%"
                                                    } else {
                                                        "${plainNumber(rec.pages)} ص · ${recitationEvaluationAr(rec.evaluation)} · ${rec.mistakes} خطأ · ${rec.prompts} تلقين · سلامة ${plainNumber(rec.safetyPercentage ?: 0.0)}%"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (editable && !transitioning) IconButton(
                                                onClick = {
                                                    val snapshot = planSnapshotsByStudent[student.id]?.firstOrNull { it.plan.id == rec.smartPlanId }
                                                        ?: planSnapshotsByStudent[student.id]?.firstOrNull()
                                                    selected = SessionRecitationTarget(student, rec.activityType, snapshot, rec)
                                                },
                                                modifier = Modifier.size(HalaqatiUiTokens.TouchTarget),
                                            ) { Icon(Icons.Default.Edit, contentDescription = "تعديل التسميع") }
                                            if (editable && !transitioning) IconButton(
                                                onClick = { deleteRecitationTarget = student to rec },
                                                modifier = Modifier.size(HalaqatiUiTokens.TouchTarget),
                                            ) { Icon(Icons.Default.DeleteOutline, contentDescription = "حذف التسميع", tint = MaterialTheme.colorScheme.error) }
                                        }
                                    }
                                }

                                if (editable && !transitioning) OutlinedButton(
                                    onClick = { quickActionsOpen = true },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Icon(Icons.Default.MoreHoriz, null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("إجراءات الطالب السريعة")
                                }
                                if (quickActionsOpen && editable && !transitioning) {
                                    QuickActionSheet(onDismissRequest = { quickActionsOpen = false }) {
                                        Column(
                                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                                                .padding(horizontal = 16.dp).navigationBarsPadding(),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("إجراءات الطالب السريعة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Text(student.fullName, style = MaterialTheme.typography.titleMedium)
                                            val quickActivities = if (!usesRecitation) emptyList() else com.mohammedsamir.halaqati.plans.SessionQuickActivityRules.activities(
                                                programSession = isProgramSession,
                                                tathbitStudent = com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(student.track),
                                                planActivityKeys = planSnapshotsByStudent[student.id].orEmpty().flatMap { snap -> snap.activities.map { it.key } }.toSet(),
                                            )
                                            ResponsiveChipGrid(
                                                items = quickActivities,
                                                minItemWidth = 100.dp,
                                                maxColumns = 2,
                                            ) { pair, itemModifier ->
                                                OutlinedButton(
                                                    onClick = { quickActionsOpen = false; selected = SessionRecitationTarget(student, pair.first, snapshotForActivity(student.id, pair.first)) },
                                                    modifier = itemModifier.fillMaxWidth().heightIn(min = 48.dp),
                                                ) { Text(pair.second) }
                                            }
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (usesRecitation) {
                                                    OutlinedButton(
                                                        onClick = { quickActionsOpen = false; markNotRecited(student) },
                                                        enabled = status !in setOf("absent", "excused") && state != SessionWorkflowRules.StudentState.RECITED,
                                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                                    ) {
                                                        Icon(Icons.Default.VoiceOverOff, null, Modifier.size(18.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(activityMissingLabel)
                                                    }
                                                }
                                                FilledTonalButton(
                                                    onClick = { quickActionsOpen = false; noteStudent = student },
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                                ) {
                                                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("ملاحظة")
                                                }
                                            }

                                            TextButton(onClick = { quickActionsOpen = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("إغلاق") }
                                            Spacer(Modifier.height(16.dp))
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

            Surface(shadowElevation = 2.dp, tonalElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if ((session.status == "open" && editable) || (session.status == "planned" && session.date == today.toString() && !session.archived)) {
                        Button(onClick = { transitionTarget = if (session.status == "open") "closed" else "open" },
                            enabled = rosterReady && !transitioning && savingEducation.isEmpty(),
                            modifier = Modifier.weight(1f).heightIn(min = HalaqatiUiTokens.TouchTarget)) {
                            if (transitioning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.DoneAll, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (session.status == "open") "إنهاء الجلسة" else "بدء الجلسة")
                        }
                    }
                    TextButton(onClick = { closeConfirm = true }, enabled = !transitioning, modifier = Modifier.weight(1f).heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text(if (displayStatus == "جارية") "خروج مؤقت" else "رجوع") }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }

    bulkEducationIds?.let { ids ->
        HalaqatiAlertDialog(
            onDismissRequest = { bulkEducationIds = null },
            title = { Text("تقييم جماعي · ${ids.size} طالب") },
            text = {
                Column {
                    Text("يشمل الحاضرين والمتأخرين غير المقيّمين ضمن القائمة المعروضة. لن يستبدل درجة موجودة على الخادم.")
                    EducationRatingSlider("الدرجة العامة", bulkEducationScore, true, onCommit = { bulkEducationScore = it })
                }
            },
            confirmButton = {
                Button(enabled = bulkEducationScore != null, onClick = {
                    bulkEducationScore?.let { saveEducation(ids, EducationScoreRules.GENERAL, it, onlyUnrated = true) }
                    bulkEducationIds = null
                }) { Text("تأكيد التقييم") }
            },
            dismissButton = { TextButton(onClick = { bulkEducationIds = null }) { Text("إلغاء") } },
        )
    }

    deleteRecitationTarget?.let { (student, recitation) ->
        HalaqatiAlertDialog(
            onDismissRequest = { deleteRecitationTarget = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("حذف التسميع") },
            text = {
                Text("سيُحذف تسميع ${student.fullName} فورًا وتُعاد حسبة الخطة.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteRecitationTarget = null
                        hiddenRecitationIds = hiddenRecitationIds + recitation.id
                        // RC27.5: delete immediately; nothing waits on a snackbar, so leaving the screen cannot skip it.
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    AppGraph.repo.deleteRecitation(session.id, student.id, recitation.id)
                                }
                                recitations = recitations + (student.id to recitations[student.id].orEmpty().filterNot { it.id == recitation.id })
                                hiddenRecitationIds = hiddenRecitationIds - recitation.id
                                loadRosterLocal(includePlans = true)
                                message = "تم حذف التسميع وإعادة حساب الخطة"
                                onChanged()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                hiddenRecitationIds = hiddenRecitationIds - recitation.id
                                message = e.message ?: "تعذر حذف التسميع؛ تمت استعادته"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteRecitationTarget = null }) { Text("إلغاء") } },
        )
    }

    transitionTarget?.let { target ->
        HalaqatiAlertDialog(
            onDismissRequest = { if (!transitioning) transitionTarget = null },
            title = { Text(if (target == "closed") "إنهاء الجلسة" else "بدء الجلسة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (target == "closed") "ستصبح الجلسة منتهية بعد تأكيد الخادم. يمكنك تعديل بياناتها لاحقًا، ولن تُسجل غيابات تلقائيًا." else "ستصبح الجلسة قابلة لتسجيل الحضور والأنشطة مجددًا.")
                    if (target == "closed") SessionSummaryStrip(summary = sessionSummary, programSession = isProgramSession, educational = educational)
                    Text("تحتاج هذه العملية اتصالًا بالإنترنت وإتمام مزامنة عمليات هذه الجلسة.", style = MaterialTheme.typography.bodySmall)
                    if (!AppGraph.repo.online()) StateNotice("أنت غير متصل. لا تُغلق الجلسة محليًا حتى لا تختلف حالتها بين الأجهزة.", "warning")
                    message?.let { StateNotice(it, "warning") }
                }
            },
            confirmButton = {
                Button(enabled = !transitioning && AppGraph.repo.online(), onClick = {
                    transitioning = true
                    message = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { AppGraph.repo.transitionSession(session, target) }
                            transitionTarget = null
                            if (target == "closed") HalaqatiMotionEvents.celebrate()
                            onChanged()
                            close()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message = e.message ?: "تعذر تغيير حالة الجلسة" }
                        finally { transitioning = false }
                    }
                }) { Text(if (transitioning) "جارٍ التأكيد…" else "تأكيد") }
            },
            dismissButton = { TextButton(onClick = { transitionTarget = null }, enabled = !transitioning) { Text("إلغاء") } },
        )
    }

    if (closeConfirm) {
        val unmarkedCount = students.count { stateFor(it) == SessionWorkflowRules.StudentState.UNMARKED }
        HalaqatiAlertDialog(
            onDismissRequest = { closeConfirm = false },
            title = { Text("خروج دون تغيير الحالة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (unmarkedCount > 0) Text("عدد الطلاب الذين لم يُرصد حضورهم: $unmarkedCount. ستبقى حالتهم غير مرصودة.")
                    SessionSummaryStrip(summary = sessionSummary, programSession = isProgramSession, educational = educational)
                }
            },
            confirmButton = {
                Button(onClick = {
                    closeConfirm = false
                    close()
                }) { Text("خروج") }
            },
            dismissButton = { TextButton(onClick = { closeConfirm = false }) { Text("متابعة الرصد") } },
        )
    }

    attendanceDetails?.let { (student, status) ->
        AttendanceDetailsDialog(
            student = student,
            status = status,
            close = { attendanceDetails = null },
            save = { reason, minutes -> saveStatus(student, status, reason, minutes); attendanceDetails = null },
        )
    }

    noteStudent?.let { student ->
        val existing = notes[student.id]
        StudentNoteDialog(
            student = student,
            existing = existing,
            close = { noteStudent = null },
            save = { behavior, score, comment, homework, visible ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            AppGraph.repo.saveDailyNote(
                                sessionId = session.id,
                                studentId = student.id,
                                behavior = behavior,
                                behaviorScore = score,
                                teacherComment = comment,
                                homework = homework,
                                parentVisible = visible,
                                participation = existing?.participation.orEmpty(),
                            )
                        }
                        notes = notes + (student.id to DailyNoteRow(
                            id = existing?.id ?: "local-note:${session.id}:${student.id}",
                            sessionId = session.id,
                            studentId = student.id,
                            behavior = behavior,
                            behaviorScore = score,
                            teacherComment = comment,
                            homework = homework,
                            parentVisible = visible,
                            participation = existing?.participation.orEmpty(),
                        ))
                        if (!AppGraph.repo.online()) queuedStudents = queuedStudents + student.id
                        noteStudent = null
                        onChanged()
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر حفظ الملاحظة" }
                }
            },
        )
    }

    selected?.let { target ->
        val existing = target.existing
        val snapshot = target.snapshot
        val activityKey = when (target.activityType) {
            "tilawah" -> null
            "sard" -> "sard"
            "review_near", "review_far" -> "review"
            "tathbit" -> "tathbit"
            else -> "hifz"
        }
        val assignment = activityKey?.let { key ->
            snapshot?.activities?.firstOrNull { it.key == key && it.scheduledToday }?.assignment
                ?: snapshot?.activities?.firstOrNull { it.key == key }?.let { it.assignment ?: it.extraAssignment }
        }
        RecitationDialog(
            student = target.student,
            session = session,
            close = { selected = null },
            onSaved = { savedId ->
                val wasEdit = existing != null
                selected = null
                scope.launch {
                    val currentNote = notes[target.student.id]
                    if (currentNote?.participation == SessionWorkflowRules.NOT_RECITED_MARKER) {
                        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                            withContext(Dispatchers.IO) {
                                AppGraph.repo.saveDailyNote(
                                    sessionId = session.id,
                                    studentId = target.student.id,
                                    behavior = currentNote.behavior,
                                    behaviorScore = currentNote.behaviorScore,
                                    teacherComment = currentNote.teacherComment,
                                    homework = currentNote.homework,
                                    parentVisible = currentNote.parentVisible,
                                    participation = "",
                                )
                            }
                        }
                        notes = notes + (target.student.id to currentNote.copy(participation = ""))
                    }
                    loadRosterLocal(includePlans = true)
                    // RC27.5: tell the teacher when a new hifz jumps over whole surahs (lost session / typo).
                    if (!wasEdit && target.activityType == "new_hifz") {
                        val saved = recitations[target.student.id].orEmpty().firstOrNull { it.id == savedId }
                        val prev = snapshot?.activities?.firstOrNull { it.key == "hifz" }?.lastRecitation
                        val gap = com.mohammedsamir.halaqati.plans.RecitationGapRules.gap(
                            previousEnd = if (prev?.endSurah != null && prev.endAyah != null) com.mohammedsamir.halaqati.quran.QuranLocation(prev.endSurah, prev.endAyah) else null,
                            newStart = if (saved?.startSurah != null && saved.startAyah != null) com.mohammedsamir.halaqati.quran.QuranLocation(saved.startSurah, saved.startAyah) else null,
                            route = snapshot?.activities?.firstOrNull { it.key == "hifz" }?.routeDirection ?: "reverse_surahs_forward_ayahs",
                        )
                        if (gap != null) message = com.mohammedsamir.halaqati.plans.RecitationGapRules.message(gap)
                    }
                    if (wasEdit) {
                        snackbarHostState.showSnackbar("تم تعديل التسميع وإعادة حساب الخطة")
                    } else {
                        val action = snackbarHostState.showSnackbar(
                            message = "تم حفظ التسميع",
                            actionLabel = "تراجع",
                            duration = SnackbarDuration.Long,
                        )
                        if (action == SnackbarResult.ActionPerformed) {
                            val undone = withContext(Dispatchers.IO) {
                                AppGraph.repo.undoPendingRecitation(session.id, target.student.id, savedId)
                            }
                            if (undone) {
                                loadRosterLocal(includePlans = true)
                                snackbarHostState.showSnackbar("تم التراجع عن التسميع")
                            } else {
                                snackbarHostState.showSnackbar("بدأت المزامنة؛ استخدم زر التعديل لتصحيح التسميع")
                            }
                        }
                    }
                }
                onChanged()
            },
            existing = existing,
            prefillStartPage = existing?.startPage ?: assignment?.startPage,
            prefillEndPage = existing?.endPage ?: assignment?.endPage,
            prefillPages = existing?.pages ?: assignment?.pages,
            smartPlanId = existing?.smartPlanId ?: if (target.activityType == "tilawah") null else snapshot?.plan?.id,
            smartPlanPhaseId = existing?.smartPlanPhaseId ?: if (target.activityType == "tilawah") null else snapshot?.phase?.id,
            smartPlanType = existing?.smartPlanType ?: if (target.activityType == "tilawah") null else snapshot?.plan?.type,
            smartPlanDate = existing?.smartPlanDate ?: session.date,
            qcfLineKeys = existing?.qcfLineKeys ?: assignment?.lineKeys.orEmpty(),
            activityTypeOverride = existing?.activityType ?: target.activityType,
        )
    }
}

@Composable
private fun AttendanceDetailsDialog(
    student: Student,
    status: String,
    close: () -> Unit,
    save: (String, Int) -> Unit,
) {
    var reason by remember(student.id, status) { mutableStateOf("") }
    var minutes by remember(student.id, status) { mutableStateOf("5") }
    QuickActionSheet(onDismissRequest = close) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (status == "late") "تسجيل التأخر" else "تسجيل غياب بعذر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.mohammedsamir.halaqati.ui.components.StudentAvatar(student.photoPath, student.fullName, 48.dp)
                Text(student.fullName, style = MaterialTheme.typography.titleMedium)
            }
            if (status == "late") {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text("دقائق التأخر") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب العذر") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(onClick = { save(reason.trim(), minutes.toIntOrNull() ?: 0) }, modifier = Modifier.fillMaxWidth()) { Text("حفظ الحضور") }
            TextButton(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("إلغاء") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StudentNoteDialog(
    student: Student,
    existing: DailyNoteRow?,
    close: () -> Unit,
    save: (String, Int?, String, String, Boolean) -> Unit,
) {
    var behavior by remember { mutableStateOf(existing?.behavior ?: "normal") }
    var score by remember { mutableStateOf(existing?.behaviorScore?.toString().orEmpty()) }
    var comment by remember { mutableStateOf(existing?.teacherComment.orEmpty()) }
    var homework by remember { mutableStateOf(existing?.homework.orEmpty()) }
    var visible by remember { mutableStateOf(existing?.parentVisible ?: true) }
    val behaviors = listOf(
        "excellent" to "ممتاز",
        "good" to "جيد",
        "normal" to "طبيعي",
        "needs_attention" to "يحتاج متابعة",
        "warning" to "تنبيه",
    )
    QuickActionSheet(onDismissRequest = close, modifier = Modifier.fillMaxHeight(.9f)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("ملاحظة الطالب", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.mohammedsamir.halaqati.ui.components.StudentAvatar(student.photoPath, student.fullName, 48.dp)
                Text(student.fullName, style = MaterialTheme.typography.titleMedium)
            }
            ResponsiveChipGrid(items = behaviors, minItemWidth = 112.dp, maxColumns = 2) { pair, itemModifier ->
                ProFilterChip(behavior == pair.first, { behavior = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
            }
            OutlinedTextField(
                value = score,
                onValueChange = { score = it.filter(Char::isDigit).take(1) },
                label = { Text("درجة السلوك 1–5 (اختياري)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("ملاحظة المحفظ") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = homework,
                onValueChange = { homework = it },
                label = { Text("الواجب") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = visible, onCheckedChange = { visible = it })
                Spacer(Modifier.width(8.dp))
                Text("تظهر الملاحظة لولي الأمر")
            }
            Button(
                onClick = { save(behavior, score.toIntOrNull()?.takeIf { it in 1..5 }, comment.trim(), homework.trim(), visible) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("حفظ الملاحظة") }
            TextButton(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("إلغاء") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun recitationActivityAr(type: String): String = when (type) {
    "tathbit" -> "تثبيت"
    "new_hifz" -> "حفظ"
    "review_near" -> "مراجعة قريبة"
    "review_far" -> "تثبيت"
    "sard" -> "سرد"
    "tilawah" -> "تلاوة"
    "test" -> "اختبار"
    else -> type
}

private fun recitationEvaluationAr(value: String): String = when (value) {
    "excellent" -> "ممتاز"
    "very_good" -> "جيد جدًا"
    "good" -> "جيد"
    "repeat" -> "يحتاج إعادة"
    else -> "غير مقيّم"
}

private fun recitationRangeText(row: RecitationRow): String = RecitationParityRules.quranRangeLabel(
    row.startSurah, row.startAyah, row.endSurah, row.endAyah, row.startPage, row.endPage,
)

private fun planAssignmentText(assignment: SmartPlanEngine.Assignment): String {
    val kind = when (assignment.kind) {
        "hifz" -> "حفظ"
        "review" -> "مراجعة"
        "sard" -> "سرد"
        "tathbit" -> "إعادة / تثبيت"
        else -> "مقرر"
    }
    val exact = assignment.lineKeys.takeIf { it.isNotEmpty() }?.let { keys ->
        runCatching { QuranCore.engine().endpointsForLineKeys(keys) }.getOrNull()
    }
    val range = exact?.let { (a, b) ->
        RecitationParityRules.quranRangeLabel(a.surah, a.ayah, b.surah, b.ayah, assignment.startPage, assignment.endPage)
    } ?: when {
        assignment.startPage != null || assignment.endPage != null -> pageRangeLabel(assignment.startPage, assignment.endPage)
        else -> "لا يوجد مقدار اليوم"
    }
    return "$kind: $range"
}

private fun smartRecitationRangeText(rec: SmartPlanEngine.Recitation): String = RecitationParityRules.quranRangeLabel(
    rec.startSurah, rec.startAyah, rec.endSurah, rec.endAyah, rec.startPage, rec.endPage,
)

private fun smartWeekRangeText(snapshot: PlanSnapshot): String {
    val week = snapshot.weekRequirement ?: return "لا يوجد مقرر أسبوعي"
    val planKeys = snapshot.plan.settings.memorizationLineKeys
    if (planKeys.isNotEmpty() && week.amount > 0.0) {
        val consumed = kotlin.math.round(snapshot.progress.completed * 15.0).toInt().coerceIn(0, planKeys.size)
        val needed = kotlin.math.ceil(week.amount * 15.0).toInt().coerceAtLeast(1)
        val keys = planKeys.drop(consumed).take(needed)
        val endpoints = keys.takeIf { it.isNotEmpty() }?.let { runCatching { QuranCore.engine().endpointsForLineKeys(it) }.getOrNull() }
        if (endpoints != null) {
            return RecitationParityRules.quranRangeLabel(
                endpoints.first.surah, endpoints.first.ayah, endpoints.second.surah, endpoints.second.ayah,
                week.startPage, week.endPage,
            )
        }
    }
    return pageRangeLabel(week.startPage, week.endPage)
}

@Composable
private fun SessionSmartPlanCard(
    snapshots: List<PlanSnapshot>,
    sessionDate: String,
    onRecite: (String, PlanSnapshot?) -> Unit,
) {
    val activities = snapshots.flatMap { snapshot ->
        snapshot.activities.map { activity -> snapshot to activity }
    }
    val hifz = activities.firstOrNull { (_, a) -> a.key == "hifz" && a.scheduledToday }
    val sard = activities.firstOrNull { (_, a) -> a.key == "sard" && a.scheduledToday }
    val review = activities.firstOrNull { (_, a) -> a.key == "review" && a.scheduledToday }
    // RC27.8: tathbit students' duty has the activity key "tathbit" (not "hifz").
    val tathbit = activities.firstOrNull { (_, a) -> a.key == "tathbit" && a.scheduledToday }
    val hasTathbitPlan = activities.any { (_, a) -> a.key == "tathbit" }
    val anyScheduled = listOfNotNull(hifz, tathbit, sard, review)
    val primaryKey = when {
        tathbit != null -> "tathbit"
        hifz != null -> "hifz"
        sard != null -> "sard"
        review != null -> "review"
        else -> listOf("hifz", "tathbit", "sard", "review").firstOrNull { key ->
            activities.any { (_, state) -> state.key == key && !state.scheduledToday && state.extraAssignment != null }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .30f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("المقرر الذكي اليوم", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(sessionDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            (snapshots.mapNotNull { it.lastRecitation } + activities.mapNotNull { (_, activity) -> activity.lastRecitation })
                .distinctBy { it.id }
                .maxWithOrNull(compareBy<SmartPlanEngine.Recitation> { it.date }.thenBy { it.createdAt })?.let { last ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("آخر تسميع · ${last.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${assignmentKindAr(last.activityType)} · ${smartRecitationRangeText(last)}", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }

            if (tathbit != null) {
                val (tSnapshot, tState) = tathbit
                SmartActivityDutyRow(
                    title = "مقرر التثبيت اليوم",
                    text = tState.assignment?.let(::planAssignmentText)
                        ?: if (tState.needsStart) "التثبيت مقرر اليوم ويحتاج تحديد نقطة البداية أول مرة."
                        else "التثبيت مقرر اليوم؛ اختر النطاق المطلوب.",
                    icon = Icons.Default.MenuBook,
                    actionLabel = if (tState.needsStart) "تحديد بداية التثبيت" else "تسميع التثبيت",
                    primary = primaryKey == "tathbit",
                    onClick = { onRecite("tathbit", tSnapshot) },
                )
            }
            if (hifz == null && tathbit == null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .86f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(if (hasTathbitPlan) "لا يوجد تثبيت مقرر اليوم" else "لا يوجد حفظ مقرر اليوم", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            val nextHifz = activities.firstOrNull { it.second.key == (if (hasTathbitPlan) "tathbit" else "hifz") }?.second?.nextDuty
                            Text(
                                nextHifz?.let { "غدًا/القادم ${it.date}: ${planAssignmentText(it.assignment)}" }
                                    ?: "سيظهر الحفظ في يومه المعتمد ضمن الخطة الذكية.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                }
            } else if (hifz != null) {
                val (snapshot, state) = hifz
                state.assignment?.let { assignment ->
                    SmartActivityDutyRow(
                        title = "مقرر الحفظ اليوم",
                        text = planAssignmentText(assignment),
                        icon = Icons.Default.MenuBook,
                        actionLabel = "تسميع الحفظ",
                        primary = primaryKey == "hifz",
                        onClick = { onRecite("new_hifz", snapshot) },
                    )
                } ?: SmartActivityDutyRow(
                    title = "الحفظ اليوم",
                    text = com.mohammedsamir.halaqati.plans.PlanRangeRebaseRules.emptyHifzDutyMessage(
                        latestEnd = state.lastRecitation?.let { r ->
                            if (r.endSurah != null && r.endAyah != null) com.mohammedsamir.halaqati.quran.QuranLocation(r.endSurah, r.endAyah) else null
                        },
                        rangeEnd = snapshot.plan.let { pl ->
                            if (pl.rangeEndSurah != null && pl.rangeEndAyah != null) com.mohammedsamir.halaqati.quran.QuranLocation(pl.rangeEndSurah, pl.rangeEndAyah) else null
                        },
                        route = state.routeDirection,
                        terminal = com.mohammedsamir.halaqati.plans.SmartPlanActivityRules.terminalSurah("hifz", state.routeDirection).let { ts ->
                            com.mohammedsamir.halaqati.quran.QuranLocation(ts, com.mohammedsamir.halaqati.quran.QuranMetadata.maxAyah(ts))
                        },
                    ),
                    icon = Icons.Default.MenuBook,
                    actionLabel = "تسميع حفظ",
                    primary = primaryKey == "hifz",
                    onClick = { onRecite("new_hifz", snapshot) },
                )
            }

            sard?.let { (snapshot, state) ->
                val text = state.assignment?.let(::planAssignmentText)
                    ?: if (state.needsStart) "السرد مقرر اليوم ويحتاج تحديد نقطة البداية أول مرة."
                    else "السرد مقرر اليوم؛ اختر النطاق المطلوب."
                SmartActivityDutyRow(
                    title = "مقرر السرد اليوم",
                    text = text,
                    icon = Icons.Default.RecordVoiceOver,
                    actionLabel = if (state.needsStart) "تحديد بداية ${state.label}" else "تسميع السرد",
                    primary = primaryKey == "sard",
                    onClick = { onRecite("sard", snapshot) },
                )
            }

            review?.let { (snapshot, state) ->
                SmartActivityDutyRow(
                    title = "مقرر المراجعة اليوم",
                    text = state.assignment?.let(::planAssignmentText) ?: "المراجعة مقررة اليوم؛ اختر النطاق.",
                    icon = Icons.Default.Replay,
                    actionLabel = "تسجيل المراجعة",
                    primary = primaryKey == "review",
                    onClick = { onRecite("review_near", snapshot) },
                )
            }

            // RC27.9: reciting on an unscheduled day: show what the student would recite and let him start at once.
            listOf("hifz", "tathbit", "sard", "review").forEach { key ->
                val extra = activities.firstOrNull { (_, a) -> a.key == key && !a.scheduledToday && a.extraAssignment != null }
                extra?.let { (xSnapshot, xState) ->
                    SmartActivityDutyRow(
                        title = "ورد إضافي اليوم — ${xState.label}",
                        text = xState.extraAssignment?.let(::planAssignmentText).orEmpty(),
                        icon = Icons.Default.Add,
                        actionLabel = "سمّع الآن",
                        primary = anyScheduled.isEmpty() && primaryKey == key,
                        onClick = { onRecite(when (key) { "hifz" -> "new_hifz"; "review" -> "review_near"; else -> key }, xSnapshot) },
                    )
                }
            }

            if (anyScheduled.isEmpty() && hifz == null) {
                Text(
                    "لا توجد أنشطة أخرى مجدولة لهذا اليوم.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SmartActivityDutyRow(
    title: String,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Column {
        Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 3)
                }
            }
            if (primary) {
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Mic, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel)
                }
            } else {
                TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Mic, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun RecitationCounter(
    label: String,
    value: Int,
    decrement: () -> Unit,
    increment: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalIconButton(onClick = decrement, enabled = value > 0) { Icon(Icons.Default.Remove, contentDescription = "إنقاص") }
                Text(value.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.Bold)
                FilledTonalIconButton(onClick = increment) { Icon(Icons.Default.Add, contentDescription = "زيادة") }
            }
        }
    }
}

@Composable
private fun TilawahRatingRow(label: String, value: Int, minValue: Int = 1, onValue: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$value/5", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(kotlin.math.round(it).toInt().coerceIn(minValue, 5)) },
            valueRange = minValue.toFloat()..5f,
            steps = 4 - minValue,
        )
    }
}

@Composable
private fun SessionSummaryStrip(
    summary: SessionWorkflowRules.Summary,
    modifier: Modifier = Modifier,
    programSession: Boolean = false,
    educational: Boolean = false,
) {
    ResponsiveChipGrid(
        items = listOf(
            Triple("غير مرصود", summary.unmarked.toString(), Icons.Default.PendingActions),
            Triple(if (educational) "بانتظار التقييم" else if (programSession) "بلا نشاط" else "لم يُسمّع", summary.notRecited.toString(), Icons.Default.PendingActions),
            Triple(if (educational) "تم تقييمه" else if (programSession) "نشاط مسجل" else "سمّع", summary.recited.toString(), Icons.Default.CheckCircle),
            Triple("حاضر / متأخر", summary.present.toString(), Icons.Default.CheckCircle),
            Triple("غائب", summary.absent.toString(), Icons.Default.Cancel),
            Triple("معذور", summary.excused.toString(), Icons.Default.Info),
        ),
        minItemWidth = 108.dp,
        maxColumns = 3,
        modifier = modifier,
    ) { item, itemModifier ->
        SessionMetricChip(item.first, item.second, item.third, itemModifier.fillMaxWidth())
    }
}

@Composable
private fun SessionMetricChip(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionChoiceMenu(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == selected }?.second
        ?: if (selected == "unmarked") "لم يُرصد" else selected
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(currentLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        if (expanded) {
            QuickActionSheet(onDismissRequest = { expanded = false }) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    options.forEach { (key, text) ->
                        TextButton(
                            onClick = { expanded = false; onSelected(key) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(if (key == selected) Icons.Default.CheckCircle else leadingIcon, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(text, modifier = Modifier.weight(1f), fontWeight = if (key == selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun behaviorLabel(value: String): String = when (value) {
    "excellent" -> "ممتاز"
    "good" -> "جيد"
    "needs_attention" -> "يحتاج انتباه"
    "warning" -> "تنبيه"
    else -> "طبيعي"
}

private fun attendanceStatusAr(status: String?): String = when (status) {
    "present" -> "حاضر"
    "absent" -> "غائب"
    "late" -> "متأخر"
    "excused" -> "بعذر"
    else -> "لم يُرصد بعد"
}

@Composable
private fun RecitationDialog(
    student: Student,
    session: SessionRow,
    close: () -> Unit,
    onSaved: (String) -> Unit,
    existing: RecitationRow? = null,
    prefillStartPage: Int? = null,
    prefillEndPage: Int? = null,
    prefillPages: Double? = null,
    smartPlanId: String? = null,
    smartPlanPhaseId: String? = null,
    smartPlanType: String? = null,
    smartPlanDate: String? = null,
    qcfLineKeys: List<String> = emptyList(),
    activityTypeOverride: String? = null,
) {
    val scope = rememberCoroutineScope()
    var start by remember(existing?.id, prefillStartPage) { mutableStateOf((existing?.startPage ?: prefillStartPage)?.toString().orEmpty()) }
    var end by remember(existing?.id, prefillEndPage) { mutableStateOf((existing?.endPage ?: prefillEndPage)?.toString().orEmpty()) }
    var pages by remember(existing?.id, prefillPages) { mutableStateOf((existing?.pages ?: prefillPages)?.let(::plainNumber) ?: "0") }
    var mistakes by remember(existing?.id) { mutableStateOf((existing?.mistakes ?: 0).toString()) }
    var prompts by remember(existing?.id) { mutableStateOf((existing?.prompts ?: 0).toString()) }
    var note by remember(existing?.id) { mutableStateOf(existing?.teacherNote.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showQuranPicker by remember { mutableStateOf(false) }
    // interactive mushaf: the marks live here while the form is open; a new range starts a fresh session (as on the web)
    var showMushaf by remember { mutableStateOf(false) }
    var mushafEpoch by remember { mutableIntStateOf(0) }
    var mushafTick by remember { mutableIntStateOf(0) }
    var mushafStartPage by remember(existing?.id) { mutableIntStateOf(1) }
    val mushafSession = remember(student.id, session.id, existing?.id, mushafEpoch) { com.mohammedsamir.halaqati.quran.MushafEngine.Session(mushafStartPage) }
    var selectedQuranRange by remember(student.id, session.id, existing?.id) {
        mutableStateOf(existing?.let { row ->
            if (row.qcfLineKeys.isNotEmpty() || row.startSurah == null || row.startAyah == null || row.endSurah == null || row.endAyah == null) null
            else runCatching {
                QuranCore.engine().calculate(
                    QuranRangeRequest(
                        track = student.track,
                        activityType = row.activityType,
                        selection = QuranSelection.Ayahs(
                            com.mohammedsamir.halaqati.quran.QuranLocation(row.startSurah, row.startAyah),
                            com.mohammedsamir.halaqati.quran.QuranLocation(row.endSurah, row.endAyah),
                        ),
                    ),
                )
            }.getOrNull()
        })
    }
    var effectiveQcfLineKeys by remember(existing?.id, qcfLineKeys) { mutableStateOf(existing?.qcfLineKeys?.takeIf { it.isNotEmpty() } ?: qcfLineKeys) }
    var resolvedActivityType by remember(student.id, session.id, activityTypeOverride, existing?.id) {
        mutableStateOf(existing?.activityType?.let { old -> runCatching { com.mohammedsamir.halaqati.data.TrackActivityPolicy.recitationActivityFor(student.track, old) }.getOrDefault(old) }
            ?: activityTypeOverride?.let { runCatching { com.mohammedsamir.halaqati.data.TrackActivityPolicy.recitationActivityFor(student.track, it) }.getOrNull() }
            ?: if (com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(student.track)) "tathbit" else when (session.type) {
            "sard" -> "sard"
            "review" -> "review_near"
            "test" -> "test"
            "tajweed", "tilawah" -> "tilawah"
            else -> "new_hifz"
        })
    }

    val existingTilawah = existing?.tilawahAssessment
    // Do not silently convert old assessments to the new scoring contract during an edit.
    val legacyTilawah = existing != null && (existingTilawah?.optInt("version", 1) ?: 1) < 2
    var jaliWords by remember(existing?.id) { mutableIntStateOf(existingTilawah?.optInt("jali_words", 0)?.coerceAtLeast(0) ?: 0) }
    var jaliLetters by remember(existing?.id) { mutableIntStateOf(existingTilawah?.optInt("jali_letters", 0)?.coerceAtLeast(0) ?: 0) }
    var jaliHarakat by remember(existing?.id) { mutableIntStateOf(existingTilawah?.optInt("jali_harakat", 0)?.coerceAtLeast(0) ?: 0) }
    var wardReviewed by remember(existing?.id) { mutableStateOf(existingTilawah?.takeIf { it.has("ward_reviewed") && !it.isNull("ward_reviewed") }?.optBoolean("ward_reviewed")) }
    var correctionAdherence by remember(existing?.id) { mutableStateOf(existingTilawah?.takeIf { it.has("correction_adherence") && !it.isNull("correction_adherence") }?.optInt("correction_adherence")?.coerceIn(0, 5)) }
    val issueOptions = listOf("madd" to "المدود", "ghunnah" to "الغنة", "ikhfa_idgham" to "الإخفاء والإدغام", "qalqalah" to "القلقلة", "makharij" to "المخارج", "waqf" to "الوقف والابتداء")
    var previousIssues by remember(student.id, session.id, existing?.id) {
        val arr = existingTilawah?.optJSONArray("previous_issue_keys")
        mutableStateOf(if (arr == null) emptySet<String>() else (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }.toSet())
    }
    var previousLoading by remember(student.id, session.id, existing?.id) { mutableStateOf(false) }
    LaunchedEffect(student.id, session.id, existing?.id, resolvedActivityType) {
        if (resolvedActivityType == "tilawah" && existing == null) {
            previousLoading = true
            try {
                // Narrow, account-scoped cached projection, one lookup on entry, never on a rating change.
                val validIds = listOf(student.id, session.halaqaId).all { id ->
                    runCatching { java.util.UUID.fromString(id).toString().equals(id, ignoreCase = true) }.getOrDefault(false)
                }
                val date = runCatching { LocalDate.parse(session.date).toString() }.getOrNull()
                if (validIds && date != null) {
                    val rows = withContext(Dispatchers.IO) {
                        AppGraph.repo.table(
                            "recitation_entries",
                            "select=selection_data,sessions!inner(session_date,halaqa_id,status)&student_id=eq.${student.id}&activity_type=eq.tilawah&sessions.halaqa_id=eq.${session.halaqaId}&sessions.session_date=lt.$date&sessions.status=neq.cancelled&order=sessions(session_date).desc,created_at.desc&limit=1",
                        )
                    }
                    val arr = rows.optJSONObject(0)?.optJSONObject("selection_data")?.optJSONObject("tilawah_assessment")?.optJSONArray("issue_keys")
                    previousIssues = if (arr == null) emptySet() else (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }.toSet()
                }
            } finally {
                previousLoading = false
            }
        }
    }
    val majorErrors = if (legacyTilawah) 0 else (jaliWords.toLong() + jaliLetters + jaliHarakat).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    var tilawahRiwayah by remember(existing?.id) { mutableStateOf(existingTilawah?.optString("riwayah", "hafs")?.takeIf { it.isNotBlank() } ?: "hafs") }
    var lahnJali by remember(existing?.id) { mutableStateOf(existingTilawah?.optInt("lahn_jali", 0)?.coerceAtLeast(0) ?: 0) }
    var lahnKhafi by remember(existing?.id) { mutableStateOf(existingTilawah?.optInt("lahn_khafi", 0)?.coerceAtLeast(0) ?: 0) }
    var makharijRating by remember(existing?.id) { mutableStateOf(existingTilawah?.optInt("makharij", 5)?.coerceIn(1, 5) ?: 5) }
    var tajweedRating by remember(existing?.id) { mutableStateOf(existingTilawah?.optInt("tajweed", 5)?.coerceIn(1, 5) ?: 5) }
    var waqfRating by remember(existing?.id) { mutableStateOf(existingTilawah?.optInt("waqf_ibtida", 5)?.coerceIn(1, 5) ?: 5) }
    var fluencyRating by remember(existing?.id) { mutableStateOf(existingTilawah?.optInt("fluency", 5)?.coerceIn(1, 5) ?: 5) }
    var tilawahIssues by remember(existing?.id) {
        val arr = existingTilawah?.optJSONArray("issue_keys")
        mutableStateOf(
            if (arr == null) emptySet() else (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }.toSet()
        )
    }

    val manualPageRange = remember(start, end, resolvedActivityType, student.track, effectiveQcfLineKeys) {
        if (effectiveQcfLineKeys.isNotEmpty()) null else runCatching {
            val a = start.toIntOrNull() ?: return@runCatching null
            val b = end.toIntOrNull() ?: return@runCatching null
            QuranCore.engine().calculate(
                QuranRangeRequest(student.track, resolvedActivityType, QuranSelection.Pages(a, b)),
            )
        }.getOrNull()
    }
    val pageValue = when {
        effectiveQcfLineKeys.isNotEmpty() -> effectiveQcfLineKeys.distinct().size.toDouble() / 15.0
        selectedQuranRange != null -> selectedQuranRange?.exactPages ?: 0.0
        manualPageRange != null -> manualPageRange?.exactPages ?: 0.0
        else -> 0.0
    }
    val mistakeValue = mistakes.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val promptValue = prompts.toIntOrNull()?.coerceAtLeast(0) ?: 0
    // a page the marks put over the per-page limit makes the whole recitation "repeat"; without a mushaf session an old entry keeps its own flag
    val overLimitPages = remember(mushafTick, mushafSession, existing?.id) {
        if (!mushafSession.isEmpty) mushafSession.overLimitPages().map { it.page } else existing?.overLimitPages.orEmpty()
    }
    val safety = SafetyScoring.calculate(pageValue, mistakeValue, promptValue, overLimitPage = overLimitPages.isNotEmpty())
    val evaluation = SafetyScoring.inferredEvaluation(safety, pageValue > 0.0)
    val isTilawah = resolvedActivityType == "tilawah"
    val tilawahBaseScore = ((makharijRating + tajweedRating + waqfRating + fluencyRating) / 20.0) * 100.0
    val tilawahScore = if (legacyTilawah) (tilawahBaseScore - (lahnJali * 8.0) - (lahnKhafi * 3.0)).coerceIn(0.0, 100.0)
        else com.mohammedsamir.halaqati.plans.TilawahScoreV2.score(makharijRating, tajweedRating, waqfRating,
            correctionAdherence.takeIf { previousIssues.isNotEmpty() }, wardReviewed == true, majorErrors)
    val effectiveSafety = if (isTilawah) tilawahScore else safety
    val effectiveEvaluation = if (isTilawah) when {
        tilawahScore >= 90.0 -> "excellent"
        tilawahScore >= 80.0 -> "very_good"
        tilawahScore >= 70.0 -> "good"
        else -> "repeat"
    } else evaluation
    val prefillEndpoints = remember(effectiveQcfLineKeys) {
        effectiveQcfLineKeys.takeIf { it.isNotEmpty() }?.let { keys ->
            runCatching { QuranCore.engine().endpointsForLineKeys(keys) }.getOrNull()
        }
    }
    val mushafRangeStartPage = selectedQuranRange?.startPage
        ?: prefillEndpoints?.first?.let { runCatching { QuranMetadata.pageFor(it.surah, it.ayah) }.getOrNull() }
        ?: manualPageRange?.startPage ?: start.toIntOrNull()?.takeIf { it in 1..604 }
    val visibleRangeLabel = selectedQuranRange?.displayLabel ?: prefillEndpoints?.let { (a, b) ->
        RecitationParityRules.quranRangeLabel(a.surah, a.ayah, b.surah, b.ayah, start.toIntOrNull(), end.toIntOrNull())
    } ?: manualPageRange?.displayLabel

    val recitationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!saving) close() },
        sheetState = recitationSheetState,
        modifier = Modifier.fillMaxHeight(0.97f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (existing == null) "تسميع ${student.fullName}" else "تعديل تسميع ${student.fullName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${session.date} · ${sessionTypeAr(session.type)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { if (!saving) close() }, enabled = !saving, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { StateNotice(it, kind = "error") }
                Text("نوع التسميع", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

                val recitationTypes = if (com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(student.track)) listOf(
                    Triple("tathbit", "تثبيت", Icons.Default.Replay), Triple("tilawah", "تلاوة", Icons.Default.AutoStories),
                ) else listOf(
                    Triple("new_hifz", "حفظ", Icons.Default.MenuBook), Triple("review_near", "مراجعة", Icons.Default.Replay),
                    Triple("sard", "سرد", Icons.Default.RecordVoiceOver), Triple("tilawah", "تلاوة", Icons.Default.AutoStories),
                )
                ResponsiveChipGrid(items = recitationTypes, minItemWidth = 105.dp, maxColumns = 4) { option, itemModifier ->
                    val (value, label, icon) = option
                    ProFilterChip(
                        selected = resolvedActivityType == value,
                        onClick = { resolvedActivityType = value; selectedQuranRange = null; effectiveQcfLineKeys = emptyList(); pages = "0" },
                        label = label,
                        modifier = itemModifier.fillMaxWidth(),
                        showCheckIcon = false,
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .85f), modifier = Modifier.size(42.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("المقرر القرآني", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(visibleRangeLabel ?: "لم يتم اختيار نطاق بعد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3)
                            }
                        }
                        if (pageValue > 0.0001) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                StatusPill("${plainNumber(pageValue)} صفحة", kind = "info")
                                selectedQuranRange?.let {
                                    StatusPill("${it.ayahCount} آية", kind = "info")
                                }
                            }
                        }
                        Button(
                            onClick = { showQuranPicker = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
                        ) {
                            Icon(Icons.Default.AutoStories, contentDescription = "اختيار النطاق من المصحف")
                            Spacer(Modifier.width(7.dp))
                            Text(if (visibleRangeLabel == null) "اختيار المقرر من المصحف" else "تعديل المقرر")
                        }
                        if (!isTilawah && pageValue > 0.0001) {
                            OutlinedButton(
                                onClick = { mushafStartPage = mushafRangeStartPage ?: 1; if (mushafSession.isEmpty) mushafEpoch++; showMushaf = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(7.dp))
                                Text("تعليم الأخطاء والتلقين على المصحف")
                            }
                        }
                    }
                }

                if (isTilawah) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .46f)),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("تقييم التلاوة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text("تقييم يدوي من المحفّظ؛ لا يعتمد على كشف آلي لأحكام التجويد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (legacyTilawah) {
                                Text("الرواية / القراءة", style = MaterialTheme.typography.labelLarge)
                                ResponsiveChipGrid(
                                    items = listOf("hafs" to "حفص عن عاصم", "warsh" to "ورش عن نافع", "qalun" to "قالون عن نافع"),
                                    minItemWidth = 118.dp,
                                    maxColumns = 2,
                                ) { pair, itemModifier ->
                                    ProFilterChip(
                                        selected = tilawahRiwayah == pair.first,
                                        onClick = { tilawahRiwayah = pair.first },
                                        label = pair.second,
                                        modifier = itemModifier.fillMaxWidth(),
                                        showCheckIcon = false,
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.weight(1f)) {
                                        RecitationCounter("لحن جلي", lahnJali, { lahnJali = (lahnJali - 1).coerceAtLeast(0) }, { lahnJali += 1 })
                                    }
                                    Box(Modifier.weight(1f)) {
                                        RecitationCounter("لحن خفي", lahnKhafi, { lahnKhafi = (lahnKhafi - 1).coerceAtLeast(0) }, { lahnKhafi += 1 })
                                    }
                                }
                                TilawahRatingRow("المخارج والصفات", makharijRating) { makharijRating = it }
                                TilawahRatingRow("أحكام التجويد", tajweedRating) { tajweedRating = it }
                                TilawahRatingRow("الوقف والابتداء", waqfRating) { waqfRating = it }
                                TilawahRatingRow("الطلاقة والترتيل", fluencyRating) { fluencyRating = it }
                            } else {
                                Text("أخطاء اللحن الجلي", style = MaterialTheme.typography.labelLarge)
                                ResponsiveChipGrid(items = listOf("word", "letter", "harakah"), minItemWidth = 104.dp, maxColumns = 3) { key, itemModifier ->
                                    Box(itemModifier.fillMaxWidth()) {
                                        when (key) {
                                            "word" -> RecitationCounter("الكلمات", jaliWords, { jaliWords = (jaliWords - 1).coerceAtLeast(0) }, { jaliWords++ })
                                            "letter" -> RecitationCounter("الحروف", jaliLetters, { jaliLetters = (jaliLetters - 1).coerceAtLeast(0) }, { jaliLetters++ })
                                            else -> RecitationCounter("الحركات", jaliHarakat, { jaliHarakat = (jaliHarakat - 1).coerceAtLeast(0) }, { jaliHarakat++ })
                                        }
                                    }
                                }
                                TilawahRatingRow("المخارج والصفات", makharijRating) { makharijRating = it }
                                TilawahRatingRow("أحكام التجويد", tajweedRating) { tajweedRating = it }
                                TilawahRatingRow("الوقف والابتداء", waqfRating) { waqfRating = it }
                                Text("التصحيحات السابقة", style = MaterialTheme.typography.labelLarge)
                                when {
                                    previousLoading -> Text("جارٍ تحميل التصحيحات السابقة…", style = MaterialTheme.typography.bodySmall)
                                    previousIssues.isEmpty() -> Text("لم تتوفر تصحيحات سابقة · لا يدخل المعيار في النسبة", style = MaterialTheme.typography.bodySmall)
                                    else -> {
                                        Text(previousIssues.map { key -> issueOptions.firstOrNull { it.first == key }?.second ?: key }.joinToString("، "), style = MaterialTheme.typography.bodyMedium)
                                        Text("مدى الالتزام بتصحيحات الشيخ السابقة", style = MaterialTheme.typography.labelLarge)
                                        if (correctionAdherence == null) TextButton(onClick = { correctionAdherence = 5 }) { Text("بدء تقييم الالتزام") }
                                        correctionAdherence?.let { rating ->
                                            TilawahRatingRow("الالتزام", rating, minValue = 0) { correctionAdherence = it }
                                        }
                                    }
                                }
                                Text("هل راجع الطالب الورد؟", style = MaterialTheme.typography.labelLarge)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ProFilterChip(wardReviewed == true, { wardReviewed = true }, "نعم", Modifier.weight(1f), showCheckIcon = true)
                                    ProFilterChip(wardReviewed == false, { wardReviewed = false }, "لا", Modifier.weight(1f), showCheckIcon = true)
                                }
                            }
                            Text("مواضع تحتاج متابعة", style = MaterialTheme.typography.labelLarge)
                            val tilawahIssueOptions = listOf(
                                "madd" to "المدود",
                                "ghunnah" to "الغنة",
                                "ikhfa_idgham" to "الإخفاء والإدغام",
                                "qalqalah" to "القلقلة",
                                "makharij" to "المخارج",
                                "waqf" to "الوقف والابتداء",
                            )
                            ResponsiveChipGrid(items = tilawahIssueOptions, minItemWidth = 126.dp, maxColumns = 2) { pair, itemModifier ->
                                ProFilterChip(
                                    selected = pair.first in tilawahIssues,
                                    onClick = { tilawahIssues = if (pair.first in tilawahIssues) tilawahIssues - pair.first else tilawahIssues + pair.first },
                                    label = pair.second,
                                    modifier = itemModifier.fillMaxWidth(),
                                    showCheckIcon = false,
                                )
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            RecitationCounter("الأخطاء", mistakeValue, { mistakes = (mistakeValue - 1).coerceAtLeast(mushafSession.totals().mistakes).toString() }, { mistakes = (mistakeValue + 1).toString() })
                        }
                        Box(Modifier.weight(1f)) {
                            RecitationCounter("التلقين", promptValue, { prompts = (promptValue - 1).coerceAtLeast(mushafSession.totals().prompts).toString() }, { prompts = (promptValue + 1).toString() })
                        }
                    }
                }

                Surface(
                    color = if (effectiveEvaluation == "repeat") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (effectiveEvaluation == "repeat") Icons.Default.WarningAmber else Icons.Default.Verified, contentDescription = null)
                            Column {
                                Text(if (isTilawah) "التقييم العام للتلاوة" else "تقييم تلقائي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(recitationEvaluationAr(effectiveEvaluation), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (isTilawah) "النتيجة" else "السلامة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${plainNumber(effectiveSafety)}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة المحفظ (اختياري)") },
                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = close,
                    enabled = !saving,
                    modifier = Modifier.weight(.8f).heightIn(min = 52.dp),
                ) { Text("إلغاء") }
                Button(
                    enabled = !saving && pageValue > 0.0 && (!isTilawah || legacyTilawah || (wardReviewed != null && !previousLoading && (previousIssues.isEmpty() || correctionAdherence != null))),
                    modifier = Modifier.weight(1.4f).heightIn(min = 52.dp),
                    onClick = {
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                val startPage = start.toIntOrNull()
                                val endPage = end.toIntOrNull()
                                if (startPage != null && startPage !in 1..604) error("صفحة البداية يجب أن تكون بين 1 و604")
                                if (endPage != null && endPage !in 1..604) error("صفحة النهاية يجب أن تكون بين 1 و604")
                                val authoritativeQuranRange = selectedQuranRange ?: manualPageRange ?: runCatching {
                                    if (startPage != null && endPage != null) {
                                        QuranCore.engine().calculate(
                                            QuranRangeRequest(
                                                track = student.track,
                                                activityType = resolvedActivityType,
                                                selection = QuranSelection.Pages(startPage, endPage),
                                            ),
                                        )
                                    } else null
                                }.getOrElse { throw it }
                                val authoritativeQcfLineKeys = authoritativeQuranRange?.qcfLineKeys ?: effectiveQcfLineKeys
                                val authoritativePages = if (authoritativeQcfLineKeys.isNotEmpty()) authoritativeQcfLineKeys.distinct().size.toDouble() / 15.0 else pageValue
                                val savedId = withContext(Dispatchers.IO) {
                                    AppGraph.repo.saveRecitation(
                                        RecitationDraft(
                                            studentId = student.id,
                                            sessionId = session.id,
                                            activityType = resolvedActivityType,
                                            startPage = authoritativeQuranRange?.startPage ?: startPage,
                                            endPage = authoritativeQuranRange?.endPage ?: endPage,
                                            pages = authoritativePages,
                                            mistakes = if (isTilawah) (if (legacyTilawah) lahnJali + lahnKhafi else majorErrors) else mistakeValue,
                                            prompts = if (isTilawah) 0 else promptValue,
                                            evaluation = effectiveEvaluation,
                                            notes = note.trim(),
                                            smartPlanId = smartPlanId,
                                            smartPlanPhaseId = smartPlanPhaseId,
                                            smartPlanType = smartPlanType,
                                            smartPlanDate = smartPlanDate,
                                            qcfLineKeys = authoritativeQuranRange?.qcfLineKeys ?: effectiveQcfLineKeys,
                                            inputMethod = authoritativeQuranRange?.inputMethod ?: existing?.inputMethod ?: "pages",
                                            clientId = existing?.id,
                                            startSurah = authoritativeQuranRange?.start?.surah,
                                            startAyah = authoritativeQuranRange?.start?.ayah,
                                            endSurah = authoritativeQuranRange?.end?.surah,
                                            endAyah = authoritativeQuranRange?.end?.ayah,
                                            ayahsCount = authoritativeQuranRange?.ayahCount ?: 0,
                                            surahsCount = authoritativeQuranRange?.surahCount ?: 0,
                                            selectedSurahs = authoritativeQuranRange?.selectedSurahs.orEmpty(),
                                            selectedJuz = authoritativeQuranRange?.selectedJuz.orEmpty(),
                                            traversalDirection = authoritativeQuranRange?.resolvedTraversal?.name,
                                            quranSegments = authoritativeQuranRange?.orderedSegments?.map { it.compactKey() }.orEmpty(),
                                            tilawahAssessment = if (isTilawah && legacyTilawah) JSONObject()
                                                .put("version", 1)
                                                .put("riwayah", tilawahRiwayah)
                                                .put("lahn_jali", lahnJali)
                                                .put("lahn_khafi", lahnKhafi)
                                                .put("makharij", makharijRating)
                                                .put("tajweed", tajweedRating)
                                                .put("waqf_ibtida", waqfRating)
                                                .put("fluency", fluencyRating)
                                                .put("issue_keys", org.json.JSONArray(tilawahIssues.sorted()))
                                                .put("overall_score", kotlin.math.round(tilawahScore * 10.0) / 10.0)
                                                .put("manual_teacher_assessment", true)
                                            else if (isTilawah) JSONObject(existingTilawah?.toString() ?: "{}")
                                                .put("version", 2)
                                                .put("riwayah", "hafs")
                                                .put("lahn_jali", majorErrors)
                                                .put("lahn_khafi", 0)
                                                .put("jali_words", jaliWords)
                                                .put("jali_letters", jaliLetters)
                                                .put("jali_harakat", jaliHarakat)
                                                .put("makharij", makharijRating)
                                                .put("tajweed", tajweedRating)
                                                .put("waqf_ibtida", waqfRating)
                                                .put("ward_reviewed", wardReviewed == true)
                                                .put("correction_adherence", correctionAdherence.takeIf { previousIssues.isNotEmpty() } ?: JSONObject.NULL)
                                                .put("previous_issue_keys", org.json.JSONArray(previousIssues.sorted()))
                                                .put("issue_keys", org.json.JSONArray(tilawahIssues.sorted()))
                                                .put("overall_score", kotlin.math.round(tilawahScore * 10.0) / 10.0)
                                                .put("manual_teacher_assessment", true)
                                            else null,
                                            overLimitPages = if (isTilawah) emptyList() else overLimitPages,
                                            createdAt = existing?.createdAt,
                                        ),
                                    )
                                }
                                onSaved(savedId)
                            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                                error = e.message ?: "تعذر حفظ التسميع"
                            } finally { saving = false }
                        }
                    },
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (saving) "جارٍ الحفظ…" else if (existing == null) "حفظ التسميع" else "حفظ التعديل")
                }
            }
        }
    }

    if (showQuranPicker) {
        QuranRangePickerDialog(
            initialStartPage = start.toIntOrNull(),
            initialEndPage = end.toIntOrNull(),
            initialStartSurah = selectedQuranRange?.start?.surah ?: prefillEndpoints?.first?.surah,
            initialStartAyah = selectedQuranRange?.start?.ayah ?: prefillEndpoints?.first?.ayah,
            initialEndSurah = selectedQuranRange?.end?.surah ?: prefillEndpoints?.second?.surah,
            initialEndAyah = selectedQuranRange?.end?.ayah ?: prefillEndpoints?.second?.ayah,
            track = student.track,
            activityType = resolvedActivityType,
            onDismiss = { showQuranPicker = false },
            onConfirm = { selected ->
                selectedQuranRange = selected
                effectiveQcfLineKeys = selected.qcfLineKeys
                start = selected.startPage?.toString().orEmpty()
                end = selected.endPage?.toString().orEmpty()
                pages = plainNumber(selected.exactPages)
                showQuranPicker = false
                mushafEpoch++; mushafTick++   // a new range starts a fresh mushaf session
            },
        )
    }
    if (showMushaf) {
        com.mohammedsamir.halaqati.ui.screens.MushafMarkingDialog(
            session = mushafSession,
            rangeStartPage = mushafRangeStartPage,
            rangeEndPage = selectedQuranRange?.endPage ?: end.toIntOrNull(),
            counters = { mistakeValue to promptValue },
            score = { MushafScore(safety, recitationEvaluationAr(evaluation), pageValue) },
            inRange = { s, a, pg ->
                val rng = selectedQuranRange
                val rs = rng?.start; val re = rng?.end
                when {
                    rng != null && rng.selectedSurahs.isNotEmpty() -> s in rng.selectedSurahs
                    rs != null && re != null -> (s > rs.surah || (s == rs.surah && a >= rs.ayah)) && (s < re.surah || (s == re.surah && a <= re.ayah))
                    else -> {
                        val sp = start.toIntOrNull(); val ep = end.toIntOrNull()
                        sp != null && ep != null && pg in kotlin.math.min(sp, ep)..kotlin.math.max(sp, ep)
                    }
                }
            },
            onMark = { dm, dp ->
                mistakes = ((mistakes.toIntOrNull() ?: 0) + dm).coerceAtLeast(0).toString()
                prompts = ((prompts.toIntOrNull() ?: 0) + dp).coerceAtLeast(0).toString()
                mushafTick++
            },
            onDismiss = { showMushaf = false; mushafTick++ },
        )
    }
}

private data class QuickMemorizationEntry(val student: Student, val snapshot: PlanSnapshot?)
private data class QuickUndoToken(
    val sessionId: String,
    val studentId: String,
    val recitationId: String?,
    val studentIndex: Int,
    val studentName: String,
)

@Composable
fun QuickMemorizationScreen(pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<QuickMemorizationEntry>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var track by remember { mutableStateOf("general_hifz") }
    var attendance by remember { mutableStateOf("present") }
    var mistakes by remember { mutableIntStateOf(0) }
    var prompts by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var undoToken by remember { mutableStateOf<QuickUndoToken?>(null) }
    var undoing by remember { mutableStateOf(false) }
    var fullRecitation by remember { mutableStateOf<Pair<QuickMemorizationEntry, SessionRow>?>(null) }

    suspend fun reload(force: Boolean = false) {
        loading = true
        try {
            val loaded = withContext(Dispatchers.IO) {
                val students = AppGraph.repo.students(forceRefresh = force).filter { it.status == "active" }
                val snapshots = AppGraph.repo.planSnapshots(LocalDate.now(), forceRefresh = force).groupBy { it.student.id }
                students.map { QuickMemorizationEntry(it, snapshots[it.id]?.firstOrNull()) }
            }
            entries = loaded
            index = index.coerceIn(0, (loaded.count { it.student.track == track } - 1).coerceAtLeast(0))
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر تجهيز التحفيظ السريع"
        } finally { loading = false }
    }

    LaunchedEffect(Unit) { reload() }
    val visible = entries.filter { it.student.track == track }
    val current = visible.getOrNull(index)
    val snapshot = current?.snapshot
    val assignment = snapshot?.progress?.assignment
    val duty = when {
        assignment != null && (assignment.pages > .001 || assignment.lineKeys.isNotEmpty()) -> "${planAssignmentText(assignment)} · ${plainNumber(assignment.pages)} ص"
        snapshot?.nextDuty != null -> "المقرر القادم ${snapshot.nextDuty.date} · ${planAssignmentText(snapshot.nextDuty.assignment)}"
        else -> "لا يوجد مقرر ذكي قابل للتسجيل الآن"
    }

    fun resetAndMove(delta: Int) {
        mistakes = 0; prompts = 0; attendance = "present"
        index = (index + delta).coerceIn(0, (visible.size - 1).coerceAtLeast(0))
    }

    fun ensureSession(entry: QuickMemorizationEntry, done: (SessionRow) -> Unit) {
        scope.launch {
            try {
                val a = entry.snapshot?.progress?.assignment
                val h = entry.student.halaqaId ?: entry.snapshot?.plan?.halaqaId ?: error("الطالب غير مرتبط بحلقة")
                val session = withContext(Dispatchers.IO) {
                    AppGraph.repo.ensureTodaySession(h, LocalDate.now().toString(), a?.kind ?: "hifz")
                }
                done(session)
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر فتح جلسة اليوم" }
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("التحفيظ السريع", onRefresh = { scope.launch { reload(true) }; onRefresh() }, pending = pending)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProFilterChip(track == "general_hifz", { track = "general_hifz"; index = 0 }, "الحفظ العام", Modifier.weight(1f))
            ProFilterChip(track == "tathbit", { track = "tathbit"; index = 0 }, "التثبيت", Modifier.weight(1f))
        }
        if (loading) {
            LoadingSkeleton(rows = 3, modifier = Modifier.padding(16.dp))
        } else if (current == null) {
            EmptyState("لا يوجد طلاب نشطون في هذا المسار")
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("الطالب ${index + 1} من ${visible.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlanHealthBadge(snapshot?.progress?.health ?: "green", snapshot?.progress?.healthLabel ?: "بدون خطة")
                        OneLineName(current.student.fullName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(if ((assignment?.pages ?: 0.0) > .001) "مقرر اليوم" else "الخطة الذكية", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(duty, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { attendance = "present" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (attendance == "present") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)) { Text("✓ حاضر") }
                    Button(onClick = { attendance = "absent" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (attendance == "absent") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant)) { Text("غائب") }
                    OutlinedButton(onClick = { attendance = "excused" }, modifier = Modifier.weight(1f)) { Text("بعذر") }
                }
                if (attendance == "present") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCounter("الأخطاء", mistakes, { mistakes = (mistakes - 1).coerceAtLeast(0) }, { mistakes += 1 }, Modifier.weight(1f))
                        QuickCounter("التلقين", prompts, { prompts = (prompts - 1).coerceAtLeast(0) }, { prompts += 1 }, Modifier.weight(1f))
                    }
                } else {
                    StateNotice(if (attendance == "excused") "سيُسجل غياب بعذر وتعيد الخطة توزيع الورد." else "سيُسجل غياب بدون عذر وتعيد الخطة توزيع الورد.", "warning")
                }
                message?.let { StateNotice(it, if (it.startsWith("تم")) "success" else "error") }
                undoToken?.let { token ->
                    OutlinedButton(
                        onClick = {
                            if (!undoing) scope.launch {
                                undoing = true
                                try {
                                    val undone = withContext(Dispatchers.IO) {
                                        AppGraph.repo.undoPendingQuickSave(token.sessionId, token.studentId, token.recitationId)
                                    }
                                    if (undone) {
                                        index = token.studentIndex.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
                                        mistakes = 0; prompts = 0; attendance = "present"
                                        message = "تم التراجع عن آخر حفظ للطالب ${token.studentName}"
                                        onRefresh()
                                    } else {
                                        message = "تعذر التراجع الفوري لأن المزامنة بدأت؛ استخدم نموذج الجلسة الكامل للتعديل"
                                    }
                                    undoToken = null
                                } finally { undoing = false }
                            }
                        },
                        enabled = !saving && !undoing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(if (undoing) "جارٍ التراجع…" else "↶ تراجع عن آخر حفظ") }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { resetAndMove(-1) }, enabled = index > 0 && !saving, modifier = Modifier.weight(.8f).heightIn(min = 52.dp)) { Text("السابق") }
                    Button(
                        onClick = {
                            val entry = current
                            saving = true
                            ensureSession(entry) { session ->
                                scope.launch {
                                    try {
                                        val savedIndex = index
                                        val savedAttendance = attendance
                                        val recitationId = withContext(Dispatchers.IO) {
                                            AppGraph.repo.saveAttendance(session.id, entry.student.id, savedAttendance, if (savedAttendance == "excused") "غياب بعذر" else "", 0)
                                            if (savedAttendance == "present") {
                                                val snap = entry.snapshot ?: error("لا توجد خطة ذكية للطالب")
                                                val a = snap.progress.assignment
                                                require(a.pages > .001) { "لا يوجد مقرر ذكي قابل للتسجيل الآن" }
                                                val safety = SafetyScoring.calculate(a.pages, mistakes, prompts)
                                                AppGraph.repo.saveRecitation(RecitationDraft(
                                                    studentId = entry.student.id,
                                                    sessionId = session.id,
                                                    activityType = com.mohammedsamir.halaqati.data.TrackActivityPolicy.quickSaveActivity(entry.student.track, a.kind, session.type),
                                                    startPage = a.startPage,
                                                    endPage = a.endPage,
                                                    pages = a.pages,
                                                    mistakes = mistakes,
                                                    prompts = prompts,
                                                    evaluation = SafetyScoring.inferredEvaluation(safety, true),
                                                    smartPlanId = snap.plan.id,
                                                    smartPlanPhaseId = snap.phase?.id,
                                                    smartPlanType = snap.plan.type,
                                                    smartPlanDate = snap.nextDuty?.date?.takeIf { (assignment?.pages ?: 0.0) <= .001 }?.toString() ?: LocalDate.now().toString(),
                                                    qcfLineKeys = a.lineKeys,
                                                ))
                                            } else null
                                        }
                                        undoToken = QuickUndoToken(session.id, entry.student.id, recitationId, savedIndex, entry.student.fullName)
                                        message = if (savedAttendance == "present") "تم حفظ التسميع محليًا" else "تم حفظ الغياب محليًا"
                                        com.mohammedsamir.halaqati.ui.components.HalaqatiMotionEvents.saved()
                                        resetAndMove(1)
                                        onRefresh()
                                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر الحفظ" }
                                    finally { saving = false }
                                }
                            }
                        },
                        enabled = !saving && (attendance != "present" || (assignment?.pages ?: 0.0) > .001),
                        modifier = Modifier.weight(1.5f).heightIn(min = 52.dp),
                    ) { Text(if (saving) "جارٍ الحفظ…" else if (attendance == "present") "✓ تمّ التسميع وحفظ" else "حفظ الغياب والتالي") }
                    OutlinedButton(onClick = { resetAndMove(1) }, enabled = index < visible.lastIndex && !saving, modifier = Modifier.weight(.8f).heightIn(min = 52.dp)) { Text("التالي") }
                }
                OutlinedButton(
                    onClick = { ensureSession(current) { fullRecitation = current to it } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("نموذج التسميع الكامل") }
            }
        }
    }

    fullRecitation?.let { (entry, session) ->
        val a = entry.snapshot?.progress?.assignment
        RecitationDialog(
            student = entry.student,
            session = session,
            close = { fullRecitation = null },
            onSaved = { _ -> fullRecitation = null; scope.launch { reload(false) }; onRefresh() },
            prefillStartPage = a?.startPage,
            prefillEndPage = a?.endPage,
            prefillPages = a?.pages?.takeIf { it > 0.0 },
            smartPlanId = entry.snapshot?.plan?.id,
            smartPlanPhaseId = entry.snapshot?.phase?.id,
            smartPlanType = entry.snapshot?.plan?.type,
            smartPlanDate = LocalDate.now().toString(),
            qcfLineKeys = a?.lineKeys.orEmpty(),
        )
    }
}

@Composable
private fun QuickCounter(label: String, value: Int, minus: () -> Unit, plus: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = minus, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) { Icon(Icons.Default.Remove, contentDescription = "إنقاص $label") }
                Text(value.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.Bold)
                IconButton(onClick = plus, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) { Icon(Icons.Default.Add, contentDescription = "زيادة $label") }
            }
        }
    }
}

@Composable
fun PlansScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    val canManage = profile.role !in setOf(UserRole.GUARDIAN, UserRole.STUDENT)
    var rows by remember { mutableStateOf<List<PlanSnapshot>>(emptyList()) }
    var allPlans by remember { mutableStateOf<List<PlanRow>>(emptyList()) }
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var categories by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshingPlans by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageKind by remember { mutableStateOf("info") }
    var query by remember { mutableStateOf("") }
    var track by remember { mutableStateOf("all") }
    var performanceFilter by remember { mutableStateOf("all") }
    var showFilters by remember { mutableStateOf(false) }
    var guardianStudentId by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<PlanSnapshot?>(null) }
    var planRecitation by remember { mutableStateOf<Pair<PlanSnapshot, SessionRow>?>(null) }
    var preparing by remember { mutableStateOf<String?>(null) }
    var showManager by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editPlan by remember { mutableStateOf<PlanRow?>(null) }
    var overrideParent by remember { mutableStateOf<PlanRow?>(null) }
    var phasePlan by remember { mutableStateOf<PlanRow?>(null) }
    var phaseDirect by remember { mutableStateOf(false) }
    var showPhasePlanChooser by remember { mutableStateOf(false) }
    var showBulkCycleManager by remember { mutableStateOf(false) }
    var historyStudent by remember { mutableStateOf<Student?>(null) }
    var monthArchives by remember { mutableStateOf<List<PlanHistoryRules.ArchiveInput>>(emptyList()) }
    var rollingMonth by remember { mutableStateOf(false) }
    var confirmRollMonth by remember { mutableStateOf(false) }
    var rollNext by remember { mutableStateOf(false) }
    var rollError by remember { mutableStateOf<String?>(null) }
    var showRangeRebase by remember { mutableStateOf(false) }
    var showPlanActions by remember { mutableStateOf(false) }
    var expandedPlanKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun reload(force: Boolean = false) {
        loading = rows.isEmpty()
        if (force && rows.isNotEmpty()) refreshingPlans = true
        try {
            online = withContext(Dispatchers.IO) { AppGraph.repo.online() }
            val loadedSnapshots = withContext(Dispatchers.IO) { AppGraph.repo.planSnapshots(forceRefresh = force) }
            rows = if (profile.role == UserRole.GUARDIAN) {
                // RC18 privacy: the UI is filtered by the authoritative guardian_children RPC even
                // when the database migration has not yet been applied. Server RLS is a second layer.
                val allowed = withContext(Dispatchers.IO) { AppGraph.repo.guardianChildren(forceRefresh = force).map { it.id }.toSet() }
                loadedSnapshots.filter { it.student.id in allowed }
            } else loadedSnapshots
            if (canManage) {
                allPlans = withContext(Dispatchers.IO) { AppGraph.repo.plans(forceRefresh = force) }
                students = withContext(Dispatchers.IO) { AppGraph.repo.students(forceRefresh = force) }
                halaqas = withContext(Dispatchers.IO) {
                    val arr = AppGraph.repo.table("halaqas", "select=id,name,active&order=name.asc&limit=200")
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                }
                categories = withContext(Dispatchers.IO) {
                    val arr = AppGraph.repo.table("student_categories", "select=id,halaqa_id,name,active&order=name.asc&limit=500")
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                }
                // RC26.2: finished months live in the archive table once a month rolls; show them too.
                monthArchives = withContext(Dispatchers.IO) {
                    runCatching {
                        val arr = AppGraph.repo.table(
                            "smart_plan_month_archives",
                            "select=id,source_plan_id,student_id,halaqa_id,period_month,plan_title,target_pages,done_pages&order=period_month.desc&limit=2000",
                            forceRefresh = force,
                        )
                        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { o ->
                            PlanHistoryRules.ArchiveInput(
                                id = o.optString("id"),
                                sourcePlanId = o.optString("source_plan_id").takeIf { it.isNotBlank() && it != "null" },
                                studentId = o.optString("student_id"),
                                halaqaId = o.optString("halaqa_id").takeIf { it.isNotBlank() && it != "null" },
                                periodMonth = o.optString("period_month").takeIf { it.isNotBlank() && it != "null" },
                                title = o.optString("plan_title").let { if (it == "null") "" else it },
                                target = o.optDouble("target_pages", 0.0),
                                done = o.optDouble("done_pages", 0.0),
                            )
                        }
                    }.getOrDefault(monthArchives)
                }
            }
            message = null
            messageKind = "info"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر تحميل تقدم الخطط"
            messageKind = "error"
        } finally {
            loading = false
            refreshingPlans = false
        }
    }

    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { if (syncEpoch > 0L) reload() }

    LaunchedEffect(Unit) { reload() }

    val guardianChildren = rows.map { it.student }.distinctBy { it.id }
    LaunchedEffect(profile.role, guardianChildren.map { it.id }) {
        if (profile.role == UserRole.GUARDIAN && guardianStudentId !in guardianChildren.map { it.id }) {
            guardianStudentId = guardianChildren.firstOrNull()?.id
        }
    }
    // RC25: plans follow the chosen halaqa; earlier months are listed below the current month.
    val plansUserId = remember { AppGraph.store.load()?.userId }
    val plansScopes by AppGraph.halaqaScope.selected.collectAsState()
    val planHalaqaChoices = halaqas.filter { it.optBoolean("active", true) }.map { it.optString("id") to it.optString("name", "حلقة") }
    val planScope = if (canManage) {
        HalaqaScopeRules.resolve(profile.role.name.lowercase(), planHalaqaChoices.map { it.first }, plansScopes[plansUserId])
    } else HalaqaScope(null, false, false)
    var planMonthOverrides by remember(planScope.selectedId) { mutableStateOf<Map<MonthKey, Boolean>>(emptyMap()) }
    val todayPlanMonth = remember { MonthKey.parse(LocalDate.now().toString()) ?: MonthKey(2026, 1) }
    val previousMonthGroups = remember(allPlans, monthArchives, planScope.selectedId, todayPlanMonth) {
        val currentKey = "%04d-%02d".format(java.util.Locale.US, todayPlanMonth.year, todayPlanMonth.month)
        MonthGrouping.group(
            PlanHistoryRules.merge(
                plans = allPlans.map { PlanHistoryRules.PlanInput(it.id, it.studentId.orEmpty(), it.halaqaId, it.startDate, it.title, it.status) },
                archives = monthArchives,
                currentMonth = currentKey,
                halaqaId = planScope.selectedId,
            ),
        ) { it.month }
    }
    val filtered = rows.filter { snapshot ->
        val performance = snapshot.progress.healthLabel
        (planScope.selectedId == null || (snapshot.student.halaqaId ?: snapshot.plan.halaqaId) == planScope.selectedId) &&
        (profile.role != UserRole.GUARDIAN || guardianStudentId == null || snapshot.student.id == guardianStudentId) &&
            (track == "all" || snapshot.student.track == track) &&
            (performanceFilter == "all" || performanceFilter == performance) &&
            (query.isBlank() || SessionWorkflowRules.matchesSearch(snapshot.student.fullName, query) ||
                SessionWorkflowRules.matchesSearch(snapshot.plan.title, query))
    }
    val healthCounts = rows.groupingBy { it.progress.health }.eachCount()

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = if (profile.role == UserRole.GUARDIAN) "خطط الأبناء" else "الخطط الذكية",
            onRefresh = {
                scope.launch { reload(force = true) }
                onRefresh()
            },
            pending = pending,
            kicker = if (canManage) "${todayPlanMonth.label()} · ${filtered.size} خطة" else null,
        )

        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (profile.role == UserRole.GUARDIAN) {
                if (guardianChildren.isNotEmpty()) {
                    ResponsiveChipGrid(items = guardianChildren, minItemWidth = 145.dp, maxColumns = 2) { child, itemModifier ->
                        ProFilterChip(
                            selected = guardianStudentId == child.id,
                            onClick = { guardianStudentId = child.id },
                            label = child.fullName,
                            modifier = itemModifier.fillMaxWidth(),
                            showCheckIcon = false,
                        )
                    }
                }
                filtered.firstOrNull()?.let { snap ->
                    val a = snap.progress.assignment
                    val required = snap.progress.dailyTarget
                    val completed = snap.progress.completedToday
                    val remaining = (required - completed).coerceAtLeast(0.0)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("مقرر اليوم", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                                Spacer(Modifier.weight(1f))
                                Text("${plainNumber(completed)}/${plainNumber(required)} ص", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                when {
                                    a.startPage != null || a.endPage != null || a.lineKeys.isNotEmpty() -> "${planAssignmentText(a)} · ${plainNumber(a.pages)} ص"
                                    a.pages > 0 -> "${assignmentKindAr(a.kind)} · ${plainNumber(a.pages)} ص"
                                    snap.progress.remaining <= 0.0 -> "اكتملت خطة هذا الشهر"
                                    else -> "لا يوجد مقرر اليوم"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (remaining > 0.001) {
                                LinearProgressIndicator(
                                    drawStopIndicator = {}, progress = { if (required > .001) (completed / required).toFloat().coerceIn(0f,1f) else 0f },
                                    modifier = Modifier.fillMaxWidth().height(5.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (canManage) {
                CompactSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "بحث باسم الطالب أو الخطة",
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BadgedBox(badge = { if (track != "all" || performanceFilter != "all" || planScope.selectedId != null) Badge() }) {
                                IconButton(onClick = { showFilters = true }, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                                    Icon(Icons.Default.FilterAlt, contentDescription = "تصفية الخطط")
                                }
                            }
                            IconButton(onClick = { showPlanActions = true }, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                                Icon(Icons.Default.Tune, contentDescription = "إدارة الخطط والإعدادات المتقدمة")
                            }
                        }
                    },
                )
            }
            if (track != "all" || performanceFilter != "all") {
                val activeFilters = buildList {
                    if (track != "all") add("track" to if (track == "general_hifz") "الحفظ العام ×" else "التثبيت ×")
                    if (performanceFilter != "all") add("performance" to "$performanceFilter ×")
                    add("clear" to "مسح الفلاتر")
                }
                ResponsiveChipGrid(items = activeFilters, minItemWidth = 118.dp, maxColumns = 3) { pair, itemModifier ->
                    AssistChip(
                        onClick = { when (pair.first) { "track" -> track = "all"; "performance" -> performanceFilter = "all"; else -> { track = "all"; performanceFilter = "all" } } },
                        label = { Text(pair.second, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = itemModifier.fillMaxWidth(),
                    )
                }
            }
        }

        message?.let { StateNotice(it, kind = messageKind, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (loading && rows.isEmpty()) LoadingSkeleton(rows = 3, modifier = Modifier.padding(12.dp))
        if (!loading && filtered.isEmpty()) EmptyState("لا توجد خطط فعالة مطابقة")

        PullToRefreshBox(
            isRefreshing = refreshingPlans,
            onRefresh = { scope.launch { reload(force = true); onRefresh() } },
            modifier = Modifier.weight(1f),
        ) {
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val planGroupsByHalaqa: List<Pair<String?, List<PlanSnapshot>>> = if (canManage && planScope.selectedId == null && planHalaqaChoices.size > 1) {
                val halaqaNames = planHalaqaChoices.toMap()
                filtered.groupBy { it.student.halaqaId ?: it.plan.halaqaId }.entries
                    .sortedBy { halaqaNames[it.key].orEmpty() }
                    .map { entry -> ((halaqaNames[entry.key] ?: "بدون حلقة") + " · " + entry.value.size + " خطة") to entry.value }
            } else listOf(null to filtered)
            planGroupsByHalaqa.forEach { (groupTitle, groupRows) ->
              if (groupTitle != null) {
                item(key = "plan-halaqa-header-" + groupTitle, contentType = "halaqa-header") {
                    com.mohammedsamir.halaqati.ui.components.NourSectionTitle(groupTitle, entity = com.mohammedsamir.halaqati.ui.theme.Entity.HALAQA)
                }
              }
              items(groupRows, key = { "${it.student.id}:${it.plan.id}" }, contentType = { "smart_plan" }) { snapshot ->
                val key = "${snapshot.student.id}:${snapshot.plan.id}"
                val expanded = key in expandedPlanKeys
                SmartPlanAccordionCard(
                    snapshot = snapshot,
                    expanded = expanded,
                    canManage = canManage,
                    busy = preparing == snapshot.plan.id,
                    onToggle = { expandedPlanKeys = if (expanded) expandedPlanKeys - key else expandedPlanKeys + key },
                    onDetails = { details = snapshot },
                    onHistory = { historyStudent = snapshot.student },
                    onEdit = { overrideParent = null; editPlan = snapshot.plan; showEditor = true },
                    onPhases = { phaseDirect = false; phasePlan = snapshot.plan },
                    onEditPhase = { phaseDirect = true; phasePlan = snapshot.plan },
                    onRecite = {
                        val assignment = snapshot.progress.assignment
                        preparing = snapshot.plan.id
                        scope.launch {
                            try {
                                val session = withContext(Dispatchers.IO) {
                                    AppGraph.repo.ensureTodaySession(
                                        halaqaId = snapshot.student.halaqaId ?: snapshot.plan.halaqaId,
                                        date = LocalDate.now().toString(),
                                        assignmentKind = assignment.kind,
                                    )
                                }
                                planRecitation = snapshot to session
                                message = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = e.message ?: "تعذر تجهيز جلسة التسميع"
                                messageKind = "error"
                            } finally { preparing = null }
                        }
                    },
                )
              }
            }
            if (canManage && previousMonthGroups.isNotEmpty()) {
                item(key = "plan-previous-title", contentType = "section-title") {
                    Text(
                        "الأشهر السابقة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
                previousMonthGroups.forEach { group ->
                    val isOpen = planMonthOverrides[group.key] ?: (group.key == todayPlanMonth.previous())
                    item(key = "plan-month-" + group.key.year + "-" + group.key.month, contentType = "month") {
                        MonthSectionHeader(
                            label = group.key.label(),
                            summary = "${group.items.size} خطة",
                            expanded = isOpen,
                            onToggle = { planMonthOverrides = planMonthOverrides + (group.key to !isOpen) },
                        )
                    }
                    if (isOpen) items(group.items, key = { "plan-history-" + it.key }, contentType = { "plan-history" }) { entry ->
                        val studentName = students.firstOrNull { it.id == entry.studentId }?.fullName.orEmpty()
                        PlanHistoryLine(
                            title = entry.title,
                            subtitle = studentName,
                            status = entry.status,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
        }
    }

    historyStudent?.let { student ->
        PlanMonthHistorySheet(student = student, close = { historyStudent = null })
    }

    details?.let { snapshot ->
        PlanDetailDialog(snapshot = snapshot, close = { details = null })
    }

    planRecitation?.let { (snapshot, session) ->
        val assignment = snapshot.progress.assignment
        RecitationDialog(
            student = snapshot.student,
            session = session,
            close = { planRecitation = null },
            onSaved = { _ ->
                planRecitation = null
                // The local Outbox/cache is already authoritative; recompute immediately from it.
                // Network reconciliation continues in the background and must not block closing the editor.
                scope.launch { reload(force = false) }
                onRefresh()
            },
            prefillStartPage = assignment.startPage,
            prefillEndPage = assignment.endPage,
            prefillPages = assignment.pages.takeIf { it > 0.0 },
            smartPlanId = snapshot.plan.id,
            smartPlanPhaseId = snapshot.phase?.id,
            smartPlanType = snapshot.plan.type,
            smartPlanDate = LocalDate.now().toString(),
            qcfLineKeys = assignment.lineKeys,
            activityTypeOverride = when (assignment.kind) {
                "sard" -> "sard"
                "review" -> "review_near"
                "tathbit" -> "tathbit"
                "test" -> "test"
                else -> "new_hifz"
            },
        )
    }

    if (showRangeRebase) {
        PlanRangeRebaseDialog(
            halaqaId = planScope.selectedId,
            onDismiss = { showRangeRebase = false },
            onApplied = { count ->
                message = "تم ضبط نطاق $count خطة"
                scope.launch { reload(force = true) }
            },
        )
    }
    if (confirmRollMonth) {
        HalaqatiAlertDialog(
            onDismissRequest = { if (!rollingMonth) confirmRollMonth = false },
            title = { Text("بدء دورة شهر جديدة") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rollMonth = LocalDate.now().withDayOfMonth(1).plusMonths(if (rollNext) 1 else 0)
                    Text("اختر الشهر الذي تريد بدءه. سيغلق النظام مراحل الشهر السابق ويحفظها في أرشيف كل طالب، ثم ينشئ أو يصلح خطة الشهر المختار لكل طالب بشكل فردي.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProFilterChip(!rollNext, { rollNext = false }, "هذا الشهر (${LocalDate.now().year}-${LocalDate.now().monthValue.toString().padStart(2, '0')})", showCheckIcon = false)
                        val nextMonth = LocalDate.now().withDayOfMonth(1).plusMonths(1)
                        ProFilterChip(rollNext, { rollNext = true }, "الشهر القادم (${nextMonth.year}-${nextMonth.monthValue.toString().padStart(2, '0')})", showCheckIcon = false)
                    }
                    Text("الشهر المحدد: ${rollMonth.year}-${rollMonth.monthValue.toString().padStart(2, '0')}", fontWeight = FontWeight.Bold, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                    rollError?.let { StateNotice("تعذر التنفيذ: $it", kind = "error") }
                    StateNotice("الحفظ: 1–22 · هدف كل طالب = هدفه الشخصي السابق. السرد: 23–نهاية الشهر · 25 صفحة للجميع.", kind = "info")
                    val preview = rows.filter { it.student.track != "tathbit" }.distinctBy { it.student.id }.take(8)
                    if (preview.isNotEmpty()) {
                        Text("معاينة الأهداف", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        preview.forEach { item ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                OneLineName(item.student.fullName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("حفظ ${plainNumber(item.plan.target)} ص · سرد 25 ص", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (rows.distinctBy { it.student.id }.size > preview.size) Text("… وباقي الطلاب حسب أهدافهم الشخصية", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("بعد التنفيذ سيتم فحص كل طالب؛ لن تعتبر العملية ناجحة لمجرد وجود خطة، بل يجب وجود مرحلتي الحفظ والسرد بالتواريخ الصحيحة. طلاب التثبيت يبقون على محرك التثبيت الشهري المستقل.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!rollingMonth) scope.launch {
                            rollingMonth = true
                            try {
                                rollError = null
                                val result = withContext(Dispatchers.IO) { AppGraph.repo.rollMonthlySmartPlans(LocalDate.now().withDayOfMonth(1).plusMonths(if (rollNext) 1 else 0)) }
                                reload(force = true)
                                messageKind = "success"
                                message = "تم فحص ${result.optInt("students_checked",0)} طالب · ${result.optInt("verified_students",0)} خطة سليمة · ${result.optInt("repaired_phases",0)} مرحلة تم إصلاحها · ${result.optInt("archived_duplicate_plans",0)} تعارض مؤرشف"
                                confirmRollMonth = false
                                onRefresh()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                messageKind = "error"
                                rollError = e.message ?: "تعذر بدء دورة الشهر الجديدة"
                                message = rollError
                            } finally { rollingMonth = false }
                        }
                    },
                    enabled = !rollingMonth,
                ) { Text(if (rollingMonth) "جاري التنفيذ…" else "تأكيد وإنشاء الشهر") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRollMonth = false }, enabled = !rollingMonth) { Text("إلغاء") }
            },
        )
    }

    if (showPlanActions) {
        ModalBottomSheet(onDismissRequest = { showPlanActions = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("إدارة الخطط", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { showPlanActions = false; editPlan = null; showEditor = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("خطة جديدة")
                }
                FilledTonalButton(onClick = { showPlanActions = false; showManager = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Inventory2, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("إدارة وتعديل الخطط")
                }
                FilledTonalButton(onClick = { showPlanActions = false; showBulkCycleManager = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Groups, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("إدارة جماعية للحفظ والسرد")
                }
                FilledTonalButton(onClick = { showPlanActions = false; showRangeRebase = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Tune, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("ضبط النطاق")
                }
                FilledTonalButton(onClick = { showPlanActions = false; rollError = null; rollNext = LocalDate.now().dayOfMonth >= 26; confirmRollMonth = true }, enabled = !rollingMonth, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Autorenew, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("إدارة الشهر (بدء شهر جديد)")
                }
                OutlinedButton(onClick = {
                    showPlanActions = false
                    val choices = allPlans.filter { it.status != "archived" }.sortedByDescending { it.status == "active" }
                    when (choices.size) {
                        0 -> { message = "لا توجد خطة يمكن إدارة مراحلها"; messageKind = "info" }
                        1 -> phasePlan = choices.first()
                        else -> showPhasePlanChooser = true
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Timeline, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("تخصيص مراحل طالب/خطة")
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("تصفية الخطط", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (planScope.showSwitcher) {
                    Text("الحلقة", style = MaterialTheme.typography.labelLarge)
                    HalaqaScopeBar(
                        halaqas = planHalaqaChoices,
                        selectedId = planScope.selectedId,
                        onSelect = { AppGraph.halaqaScope.select(plansUserId, it) },
                        allLabel = "كل الحلقات (مجمّعة)",
                    )
                }
                Text("المسار", style = MaterialTheme.typography.labelLarge)
                ResponsiveChipGrid(items = listOf("all" to "الكل", "general_hifz" to "الحفظ العام", "tathbit" to "التثبيت"), minItemWidth = 112.dp, maxColumns = 3) { pair, itemModifier ->
                    ProFilterChip(track == pair.first, { track = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
                Text("مستوى المتابعة", style = MaterialTheme.typography.labelLarge)
                ResponsiveChipGrid(items = listOf("all", "متقدم", "على المسار", "يحتاج انتباه", "يحتاج متابعة"), minItemWidth = 118.dp, maxColumns = 2) { label, itemModifier ->
                    val n = if (label == "all") rows.size else rows.count { it.progress.healthLabel == label }
                    ProFilterChip(performanceFilter == label, { performanceFilter = label }, (if (label == "all") "الكل" else label) + " ($n)", itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
                Button(
                    onClick = { showFilters = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("تطبيق") }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showBulkCycleManager) {
        SmartPlanBulkCycleManagerSheet(
            plans = allPlans,
            students = students,
            snapshots = rows,
            onDismiss = { showBulkCycleManager = false },
            onChanged = {
                scope.launch { reload(force = true) }
                onRefresh()
            },
        )
    }

    if (showManager) {
        PlansManagerSheet(
            plans = allPlans,
            students = students,
            halaqas = halaqas,
            categories = categories,
            onDismiss = { showManager = false },
            onAdd = { showManager = false; editPlan = null; showEditor = true },
            onEdit = { plan ->
                showManager = false
                overrideParent = null
                editPlan = plan
                showEditor = true
            },
            onOverride = { plan -> showManager = false; editPlan = null; overrideParent = plan; showEditor = true },
            onPhases = { plan -> showManager = false; phasePlan = plan },
            onStatus = { plan, status ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { AppGraph.repo.setPlanStatus(plan.id, status) }
                        reload()
                        messageKind = "success"
                        message = when (status) {
                            "archived" -> "تمت أرشفة الخطة"
                            "paused" -> "تم إيقاف الخطة مؤقتاً"
                            "active" -> "تم تفعيل الخطة"
                            else -> "تم تحديث حالة الخطة"
                        }
                        onRefresh()
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                        message = e.message ?: "تعذر تحديث الخطة"
                        messageKind = "error"
                    }
                }
            },
        )
    }


    if (showPhasePlanChooser) {
        ModalBottomSheet(onDismissRequest = { showPhasePlanChooser = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("اختر الخطة لإدارة المراحل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("من هنا تعدّل مرحلة الحفظ، تضيف السرد، وتنهي الحفظ أو تنقل الطلاب للسرد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                allPlans.filter { it.status != "archived" }.sortedByDescending { it.status == "active" }.forEach { plan ->
                    OutlinedButton(
                        onClick = { showPhasePlanChooser = false; phasePlan = plan },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.Timeline, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text(plan.title.ifBlank { planTypeAr(plan.type) }, fontWeight = FontWeight.SemiBold)
                            Text("${planStatusAr(plan.status)} · ${plan.startDate} ← ${plan.endDate}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    phasePlan?.let { plan ->
        SmartPlanPhasesManagerSheet(
            plan = plan,
            snapshots = rows.filter { it.plan.id == plan.id },
            directEdit = phaseDirect,
            onDismiss = { phasePlan = null; phaseDirect = false },
            onChanged = {
                scope.launch { reload(false) }
                onRefresh()
            },
        )
    }

    if (showEditor) {
        SmartPlanEditorDialog(
            current = editPlan,
            template = overrideParent,
            students = students,
            halaqas = halaqas,
            categories = categories,
            onDismiss = { showEditor = false; editPlan = null; overrideParent = null },
            onSave = { draft ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { AppGraph.repo.saveSmartPlan(draft) }
                        showEditor = false
                        editPlan = null
                        overrideParent = null
                        reload()
                        message = "تم حفظ الخطة الذكية"
                        com.mohammedsamir.halaqati.ui.components.HalaqatiMotionEvents.saved()
                        messageKind = "success"
                        onRefresh()
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                        message = e.message ?: "تعذر حفظ الخطة"
                        messageKind = "error"
                    }
                }
            },
        )
    }
}

@Composable
private fun PlanMonthHistorySheet(student: Student, close: () -> Unit) {
    var rows by remember(student.id) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember(student.id) { mutableStateOf(true) }
    var message by remember(student.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(student.id) {
        loading = true
        try {
            val arr = withContext(Dispatchers.IO) { AppGraph.repo.studentPlanMonthHistory(student.id) }
            rows = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            message = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر تحميل أرشيف الخطط"
        } finally { loading = false }
    }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("أرشيف خطط ${student.fullName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("كل شهر محفوظ كما انتهى، ولا يتغير عند بدء الخطة التالية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = close, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
            }
            Spacer(Modifier.height(8.dp))
            message?.let { StateNotice(it, kind = "error") }
            if (loading) LoadingSkeleton(rows = 3)
            if (!loading && rows.isEmpty()) EmptyState("لا يوجد شهر مؤرشف بعد؛ سيظهر الأرشيف بعد إغلاق أول خطة شهرية.")
            LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                items(rows, key = { it.optString("id") }) { row ->
                    val month = row.optString("period_month").take(7).replace('-', '/')
                    val target = row.optDouble("target_pages", 0.0)
                    val done = row.optDouble("done_pages", 0.0)
                    val sardTarget = row.optDouble("sard_target_pages", 0.0)
                    val sardDone = row.optDouble("sard_done_pages", 0.0)
                    val planType = row.optString("plan_type")
                    val percent = if (target > .001) (done * 100.0 / target).coerceIn(0.0, 100.0) else 0.0
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(month.ifBlank { "شهر سابق" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(row.optString("title", if (planType == "tathbit") "خطة التثبيت" else "الخطة الذكية"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(if (done + .001 >= target && (sardTarget <= .001 || sardDone + .001 >= sardTarget)) "مكتملة" else "منتهية", if (done + .001 >= target) "success" else "warning")
                            }
                            if (target > .001) {
                                AnimatedLinearProgress(progress = (percent / 100.0).toFloat(), modifier = Modifier.fillMaxWidth().height(6.dp))
                                Text("${if (planType == "tathbit") "التثبيت" else "الحفظ"}: ${plainNumber(done)} / ${plainNumber(target)} ص · ${plainNumber(percent)}٪", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (sardTarget > .001 || sardDone > .001) {
                                Text("السرد: ${plainNumber(sardDone)} / ${plainNumber(sardTarget)} ص", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PlanStatTile("الحضور", row.optInt("present",0).toString(), Modifier.weight(1f))
                                PlanStatTile("الغياب", row.optInt("absent",0).toString(), Modifier.weight(1f))
                                PlanStatTile("الجودة", if (row.has("quality") && !row.isNull("quality")) "${plainNumber(row.optDouble("quality"))}%" else "—", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlansManagerSheet(
    plans: List<PlanRow>,
    students: List<Student>,
    halaqas: List<JSONObject>,
    categories: List<JSONObject>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PlanRow) -> Unit,
    onOverride: (PlanRow) -> Unit,
    onPhases: (PlanRow) -> Unit,
    onStatus: (PlanRow, String) -> Unit,
) {
    var status by remember { mutableStateOf("all") }
    val filtered = plans.filter { status == "all" || it.status == status }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("إدارة الخطط", style = MaterialTheme.typography.titleLarge)
                    Text("إنشاء وتعديل وإيقاف وأرشفة واستعادة الخطط", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledIconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "خطة جديدة")
                }
            }
            Spacer(Modifier.height(8.dp))
            ResponsiveChipGrid(items = listOf("all" to "الكل", "active" to "فعالة", "paused" to "موقفة", "completed" to "مكتملة", "archived" to "الأرشيف"), minItemWidth = 105.dp, maxColumns = 3) { pair, itemModifier ->
                ProFilterChip(status == pair.first, { status = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                EmptyState("لا توجد خطط في هذه الحالة")
            } else {
                LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                    modifier = Modifier.heightIn(max = 610.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    items(filtered, key = { it.id }) { plan ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(plan.title.ifBlank { planTypeAr(plan.type) }, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        Text(
                                            planScopeLabel(plan, students, halaqas, categories),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        shape = MaterialTheme.shapes.extraLarge,
                                    ) {
                                        Text(
                                            planStatusAr(plan.status),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                                Text("${planTypeAr(plan.type)} · ${plainNumber(plan.target)} صفحة · من ${plan.startDate} إلى ${plan.endDate}", style = MaterialTheme.typography.bodySmall)
                                val planActions = buildList {
                                    add("phases")
                                    add("edit")
                                    if (plan.scopeType != "student" && plan.status != "archived") add("override")
                                    if (plan.status == "active") add("pause") else if (plan.status in setOf("paused", "archived", "draft")) add("activate")
                                    if (plan.status != "archived") add("archive")
                                }
                                ResponsiveChipGrid(items = planActions, minItemWidth = 118.dp, maxColumns = 2) { action, itemModifier ->
                                    OutlinedButton(
                                        onClick = {
                                            when (action) {
                                                "phases" -> onPhases(plan)
                                                "edit" -> onEdit(plan)
                                                "override" -> onOverride(plan)
                                                "pause" -> onStatus(plan, "paused")
                                                "activate" -> onStatus(plan, "active")
                                                else -> onStatus(plan, "archived")
                                            }
                                        },
                                        modifier = itemModifier.fillMaxWidth().heightIn(min = 48.dp),
                                    ) {
                                        Icon(when (action) { "phases" -> Icons.Default.Timeline; "edit" -> Icons.Default.Edit; "override" -> Icons.Default.PersonAddAlt; "archive" -> Icons.Default.Archive; "pause" -> Icons.Default.PauseCircle; else -> Icons.Default.PlayCircle }, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(when (action) { "phases" -> "المراحل"; "edit" -> "تعديل"; "override" -> "تجاوز للطالب"; "pause" -> "إيقاف"; "activate" -> if (plan.status == "archived") "استعادة" else "تفعيل"; else -> "أرشفة" })
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

private fun planStatusAr(value: String) = when (value) {
    "draft" -> "مسودة"
    "active" -> "فعالة"
    "paused" -> "موقفة"
    "completed" -> "مكتملة"
    "archived" -> "مؤرشفة"
    else -> value
}

private fun planScopeLabel(
    plan: PlanRow,
    students: List<Student>,
    halaqas: List<JSONObject>,
    categories: List<JSONObject>,
): String = when (plan.scopeType) {
    "student" -> students.firstOrNull { it.id == plan.studentId }?.fullName ?: "طالب محدد"
    "category" -> categories.firstOrNull { it.optString("id") == plan.categoryId }?.optString("name") ?: "فئة محددة"
    else -> halaqas.firstOrNull { it.optString("id") == plan.halaqaId }?.optString("name") ?: "الحلقة كلها"
}

@Composable
private fun SmartPlanSearchChoiceField(
    label: String,
    selectedId: String?,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember(open) { mutableStateOf("") }
    val selected = options.firstOrNull { it.first == selectedId }?.second ?: "اختر $label"
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Icon(Icons.Default.Search, contentDescription = null)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }
    if (open) {
        val filtered = options.filter { (_, name) -> query.isBlank() || name.contains(query, ignoreCase = true) }.take(100)
        HalaqatiAlertDialog(
            onDismissRequest = { open = false },
            title = { Text("اختيار $label") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("ابحث عن $label") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filtered, key = { it.first }) { (id, name) ->
                            TextButton(onClick = { onSelected(id); open = false }, modifier = Modifier.fillMaxWidth()) {
                                Icon(if (id == selectedId) Icons.Default.CheckCircle else Icons.Default.Circle, null, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (filtered.isEmpty()) item { Text("لا توجد نتيجة مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("إغلاق") } },
        )
    }
}

@Composable
private fun SmartPlanEditorDialog(
    current: PlanRow?,
    template: PlanRow? = null,
    students: List<Student>,
    halaqas: List<JSONObject>,
    categories: List<JSONObject>,
    onDismiss: () -> Unit,
    onSave: (SmartPlanDraft) -> Unit,
) {
    val today = LocalDate.now()
    val source = current ?: template
    val isStudentOverride = current == null && template != null
    val planKey = current?.id ?: template?.id?.let { "student_override:$it" }
    var scopeType by remember(planKey) { mutableStateOf(if (isStudentOverride) "student" else (source?.scopeType ?: "student")) }
    var halaqaId by remember(planKey) { initialPlanHalaqaId(source, halaqas) }
    var studentId by remember(planKey) { initialPlanStudentId(source, students, halaqaId) }
    var categoryId by remember(planKey) { initialPlanCategoryId(source, categories, halaqaId) }
    var type by remember(planKey) { initialPlanType(source) }
    var title by remember(planKey) { mutableStateOf(if (isStudentOverride) "${source?.title.orEmpty()} · تخصيص" else initialPlanTitle(source).value) }
    var target by remember(planKey) { initialPlanTarget(source) }
    var startDate by remember(planKey) { initialPlanStartDate(source, today) }
    var endDate by remember(planKey) { initialPlanEndDate(source, today) }
    var weekdays by remember(planKey) { initialPlanWeekdays(source) }
    var catchUp by remember(planKey) { initialPlanCatchUp(source) }
    var rangeStartPage by remember(planKey) { initialPlanRangeStartPage(source) }
    var rangeEndPage by remember(planKey) { initialPlanRangeEndPage(source) }
    var rangeStartSurah by remember(planKey) { initialPlanRangeStartSurah(source) }
    var rangeStartAyah by remember(planKey) { initialPlanRangeStartAyah(source) }
    var rangeEndSurah by remember(planKey) { initialPlanRangeEndSurah(source) }
    var rangeEndAyah by remember(planKey) { initialPlanRangeEndAyah(source) }
    var rangeLineKeys by remember(planKey) { mutableStateOf(source?.settings?.memorizationLineKeys.orEmpty()) }
    var reviewTier by remember(planKey) { initialPlanReviewTier(source) }
    var nearPages by remember(planKey) { initialPlanNearPages(source) }
    var farPages by remember(planKey) { initialPlanFarPages(source) }
    var minEvaluation by remember(planKey) { initialPlanMinEvaluation(source) }
    var dailyCap by remember(planKey) { initialPlanDailyCap(source) }
    val milestone = "none"
    var delayDays by remember(planKey) { initialPlanDelayDays(source) }
    var qualityGate by remember(planKey) { initialPlanQualityGate(source) }
    var autoExtend by remember(planKey) { initialPlanAutoExtend(source) }
    val milestoneApproval = false
    var homePrepPush by remember(planKey) { initialPlanHomePrepPush(source) }
    var note by remember(planKey) { initialPlanNote(source) }
    var routeDirection by remember(planKey) { initialPlanRouteDirection(source) }
    var enabledActivities by remember(planKey) {
        mutableStateOf(SmartPlanActivityRules.enabledActivities(source?.type ?: type, source?.settings?.enabledActivities.orEmpty()).toSet())
    }
    var sardTarget by remember(planKey) { mutableStateOf(source?.settings?.sardTargetPages?.takeIf { it > 0.0 }?.let(::plainNumber).orEmpty()) }
    var sardRouteDirection by remember(planKey) { mutableStateOf(source?.settings?.sardRouteDirection ?: if ((source?.type ?: type) == "hifz") "reverse_surahs_forward_ayahs" else "forward_surahs_forward_ayahs") }
    var showRangePicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var phaseManaged by remember(planKey) { mutableStateOf(false) }
    var latestPlanAnchor by remember(planKey, studentId, type) { mutableStateOf<SmartPlanEngine.Recitation?>(null) }
    var loadingPlanAnchor by remember(planKey, studentId, type) { mutableStateOf(false) }

    LaunchedEffect(current?.id) {
        phaseManaged = current?.id?.let { id ->
            withContext(Dispatchers.IO) { AppGraph.repo.planPhases(id).any { it.status != "archived" } }
        } ?: false
    }

    val scopedStudents = students.filter { st ->
        st.halaqaId == halaqaId && st.status == "active" && AppGraph.repo.isSmartPlanEligible(st.halaqaId) &&
            (!isStudentOverride || template?.scopeType != "category" || st.categoryId == template.categoryId)
    }
    val scopedCategories = categories.filter { it.optString("halaqa_id") == halaqaId && it.optBoolean("active", true) }
    val selectedStudent = students.firstOrNull { it.id == studentId }
    val selectedStudentIsTathbit = selectedStudent?.let { com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(it.track) } == true
    val effectiveStudentTrack = if (selectedStudentIsTathbit || (scopeType != "student" && type == "tathbit")) "tathbit" else "general_hifz"
    val weekdayLabels = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

    LaunchedEffect(scopeType, studentId, selectedStudentIsTathbit, phaseManaged) {
        if (!phaseManaged && scopeType == "student" && studentId != null) {
            when {
                selectedStudentIsTathbit && type != "tathbit" -> {
                    type = "tathbit"
                    enabledActivities = setOf("tathbit")
                    if (current == null) routeDirection = defaultSmartPlanRouteDirection("tathbit")
                }
                !selectedStudentIsTathbit && type == "tathbit" -> {
                    type = "hifz"
                    enabledActivities = setOf("hifz")
                    if (current == null) routeDirection = defaultSmartPlanRouteDirection("hifz")
                }
            }
        }
    }

    LaunchedEffect(scopeType, studentId, type, current?.id) {
        latestPlanAnchor = null
        if (scopeType == "student" && !studentId.isNullOrBlank() && type in setOf("hifz", "tathbit")) {
            loadingPlanAnchor = true
            latestPlanAnchor = withContext(Dispatchers.IO) {
                AppGraph.repo.latestAcceptedActivityRecitation(
                    studentId = studentId!!,
                    activity = if (type == "tathbit") "tathbit" else "hifz",
                    planId = current?.id,
                    forceRefresh = true,
                )
            }
            loadingPlanAnchor = false
        } else {
            loadingPlanAnchor = false
        }
    }

    fun applyLatestPlanAnchor() {
        val last = latestPlanAnchor ?: error("لا يوجد تسميع سابق معتمد لهذا الطالب")
        val surah = last.endSurah ?: error("آخر تسميع لا يحتوي نهاية آية دقيقة")
        val ayah = last.endAyah ?: error("آخر تسميع لا يحتوي نهاية آية دقيقة")
        val next = SmartPlanActivityRules.nextLocation(QuranLocation(surah, ayah), routeDirection)
            ?: error("وصل الطالب إلى نهاية المسار المحدد")
        val traversal = planTraversalDirection(routeDirection)
        val activityType = if (type == "tathbit") "tathbit" else "new_hifz"
        val end = when {
            rangeEndSurah != null && rangeEndAyah != null -> QuranLocation(rangeEndSurah!!, rangeEndAyah!!)
            rangeEndPage != null -> QuranMetadata.pageEnd(rangeEndPage!!)
            else -> null
        }
        val calculated = runCatching {
            if (end != null) {
                QuranCore.engine().calculate(
                    QuranRangeRequest(
                        track = effectiveStudentTrack,
                        activityType = activityType,
                        selection = QuranSelection.Ayahs(next, end),
                        direction = traversal,
                    )
                )
            } else {
                val pages = target.toDoubleOrNull()?.takeIf { it > 0.0 } ?: error("حدد هدف الخطة أولاً")
                QuranCore.engine().previewPagesFrom(next, pages, effectiveStudentTrack, activityType, traversal)
            }
        }.getOrElse { throw IllegalArgumentException("تعذر بناء النطاق من آخر تسميع: ${it.message.orEmpty()}") }
        val calcStart = calculated.start ?: error("تعذر تحديد بداية النطاق")
        val calcEnd = calculated.end ?: error("تعذر تحديد نهاية النطاق")
        rangeStartPage = calculated.startPage
        rangeEndPage = calculated.endPage
        rangeStartSurah = calcStart.surah
        rangeStartAyah = calcStart.ayah
        rangeEndSurah = calcEnd.surah
        rangeEndAyah = calcEnd.ayah
        rangeLineKeys = calculated.qcfLineKeys
    }

    fun buildDraft(): SmartPlanDraft {
        val parsedTarget = target.toDoubleOrNull() ?: error("أدخل هدفاً صحيحاً بالصفحات")
        val parsedDailyCap = dailyCap.toDoubleOrNull() ?: error("أقصى زيادة يومية غير صالحة")
        val parsedDelay = delayDays.toIntOrNull() ?: error("أيام تنبيه التأخر غير صالحة")
        val parsedNear = nearPages.toDoubleOrNull() ?: 0.0
        val parsedFar = farPages.toDoubleOrNull() ?: 0.0
        val settings = editorPlanSettings(source)
        return SmartPlanDraft(
            id = current?.id,
            scopeType = if (isStudentOverride) "student" else scopeType,
            halaqaId = halaqaId,
            studentId = studentId,
            categoryId = categoryId,
            parentPlanId = if (isStudentOverride) template?.id else current?.parentPlanId,
            type = type,
            reviewTier = if (type == "review") reviewTier else "none",
            title = title,
            targetPages = parsedTarget,
            startDate = startDate,
            endDate = endDate,
            scheduleWeekdays = weekdays.sorted(),
            catchUpWeekday = catchUp,
            maxDailyMultiplier = parsedDailyCap,
            autoExtend = autoExtend,
            qualityGateEnabled = qualityGate,
            minimumEvaluation = minEvaluation,
            rangeStartPage = rangeStartPage,
            rangeEndPage = rangeEndPage,
            rangeStartSurah = rangeStartSurah,
            rangeStartAyah = rangeStartAyah,
            rangeEndSurah = rangeEndSurah,
            rangeEndAyah = rangeEndAyah,
            nearRevisionPages = parsedNear,
            farRevisionPages = parsedFar,
            milestoneMode = milestone,
            milestoneRequiresApproval = milestoneApproval,
            delayAlertDays = parsedDelay,
            homePrepPush = homePrepPush,
            status = current?.status?.takeUnless { it == "archived" } ?: "active",
            note = note,
            minimumDailyMode = settings.minimumDailyMode,
            minimumDailyPages = settings.minimumDailyPages,
            intensiveWeekFrom = settings.intensiveWeekFrom,
            intensiveWeekTo = settings.intensiveWeekTo,
            intensiveWeekdays = settings.intensiveWeekdays,
            memorizationLineKeys = rangeLineKeys,
            routeDirection = routeDirection,
            enabledActivities = SmartPlanActivityRules.enabledActivities(type, enabledActivities.toList()),
            sardTargetPages = sardTarget.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
            sardRouteDirection = sardRouteDirection,
        )
    }

    HalaqatiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when { isStudentOverride -> "تجاوز خاص بالطالب"; current == null -> "خطة ذكية جديدة"; phaseManaged -> "تعديل بيانات الخطة"; else -> "تعديل الخطة الذكية" }) },
        text = {
            LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (phaseManaged) item {
                    StateNotice(
                        "هذه الشاشة تعدّل بيانات الخطة العامة مثل الاسم والسياسات. أهداف الحفظ/السرد والتواريخ والأيام والنطاقات تُعدّل من «مراحل الخطة» بشكل مستقل.",
                        "info",
                    )
                }
                item {
                    Text("نطاق التطبيق", style = MaterialTheme.typography.titleSmall)
                    if (isStudentOverride) {
                        StateNotice("هذه خطة ابن مرتبطة بالخطة الأصلية ${template?.title.orEmpty()}. يرث الطالب الإعدادات ويمكن تخصيص هدفه أو نطاقه دون تغيير الخطة الجماعية.", "info")
                    } else {
                        ResponsiveChipGrid(
                            items = listOf("student" to "طالب", "category" to "فئة", "halaqa" to "الحلقة"),
                            minItemWidth = 104.dp,
                            maxColumns = 3,
                        ) { pair, itemModifier ->
                            ProFilterChip(scopeType == pair.first, { if (!phaseManaged) scopeType = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                        }
                    }
                }
                item {
                    SmartPlanSearchChoiceField(
                        label = "الحلقة",
                        selectedId = halaqaId,
                        options = halaqas.filter { it.optBoolean("active", true) || it.optString("id") == halaqaId }
                            .map { it.optString("id") to it.optString("name", "حلقة") },
                        onSelected = { id ->
                            if (!phaseManaged) {
                                halaqaId = id
                                studentId = students.firstOrNull { it.halaqaId == id && it.status == "active" }?.id
                                categoryId = categories.firstOrNull { it.optString("halaqa_id") == id && it.optBoolean("active", true) }?.optString("id")
                            }
                        },
                        enabled = !phaseManaged,
                    )
                }
                if (scopeType == "student") item {
                    SmartPlanSearchChoiceField(
                        label = "الطالب",
                        selectedId = studentId,
                        options = scopedStudents.map { it.id to it.fullName },
                        onSelected = { studentId = it },
                        enabled = !phaseManaged,
                    )
                    if (scopedStudents.isEmpty()) Text("لا يوجد طلاب فعالون في الحلقة", color = MaterialTheme.colorScheme.error)
                }
                if (scopeType == "category") item {
                    HalaqatiLazySelectField(
                        label = "الفئة",
                        selectedKey = categoryId,
                        options = scopedCategories.map { it.optString("id") to it.optString("name", "فئة") },
                        onSelected = { if (!phaseManaged) categoryId = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !phaseManaged,
                        searchable = scopedCategories.size > 8,
                    )
                    if (scopedCategories.isEmpty()) Text("لا توجد فئات فعالة في الحلقة", color = MaterialTheme.colorScheme.error)
                }
                item {
                    Text("نوع الخطة", style = MaterialTheme.typography.labelLarge)
                    val legacySard = current?.type == "sard"
                    val typeOptions = when {
                        scopeType == "student" && selectedStudentIsTathbit -> listOf("tathbit" to "تثبيت")
                        scopeType == "student" -> buildList {
                            add("hifz" to "حفظ")
                            add("review" to "مراجعة")
                            if (legacySard) add("sard" to "سرد (قديم)")
                        }
                        else -> buildList {
                            add("hifz" to "حفظ")
                            add("review" to "مراجعة")
                            add("tathbit" to "تثبيت")
                            if (legacySard) add("sard" to "سرد (قديم)")
                        }
                    }
                    ResponsiveChipGrid(items = typeOptions, minItemWidth = 110.dp, maxColumns = 3) { pair, itemModifier ->
                        ProFilterChip(
                            selected = type == pair.first,
                            onClick = {
                                if (!phaseManaged) {
                                    type = pair.first
                                    if (current == null && template == null) routeDirection = defaultSmartPlanRouteDirection(pair.first)
                                }
                            },
                            label = pair.second,
                            modifier = itemModifier.fillMaxWidth(),
                            showCheckIcon = false,
                        )
                    }
                    if (scopeType == "student") {
                        Text(
                            if (selectedStudentIsTathbit) "طالب التثبيت يملك خطة تثبيت فقط؛ السرد ليس مساراً مستقلاً."
                            else "طالب الحفظ العام يستخدم الحفظ، ويمكن إضافة السرد كنشاط/مرحلة داخل خطة الحفظ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (type == "hifz") item {
                    Text("أنشطة الخطة", style = MaterialTheme.typography.labelLarge)
                    ResponsiveChipGrid(items = listOf("hifz" to "حفظ", "sard" to "سرد"), minItemWidth = 118.dp, maxColumns = 2) { pair, itemModifier ->
                        ProFilterChip(
                            selected = pair.first == "hifz" || "sard" in enabledActivities,
                            onClick = {
                                if (pair.first == "sard" && !phaseManaged) enabledActivities = if ("sard" in enabledActivities) enabledActivities - "sard" else enabledActivities + "sard"
                            },
                            label = pair.second,
                            modifier = itemModifier.fillMaxWidth(),
                            showCheckIcon = false,
                        )
                    }
                    Text("الحفظ والسرد يتقدمان من آخر تسميع معتمد لكل نشاط بشكل مستقل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if ("sard" in enabledActivities) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = sardTarget,
                            onValueChange = { sardTarget = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("هدف السرد خلال الخطة بالصفحات") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !phaseManaged,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("اتجاه السرد", style = MaterialTheme.typography.labelMedium)
                        ResponsiveChipGrid(
                            items = listOf("forward_surahs_forward_ayahs" to "من البقرة إلى الناس", "reverse_surahs_forward_ayahs" to "من الناس إلى البقرة"),
                            minItemWidth = 160.dp,
                            maxColumns = 2,
                        ) { pair, itemModifier ->
                            ProFilterChip(
                                selected = sardRouteDirection == pair.first,
                                onClick = { if (!phaseManaged) sardRouteDirection = pair.first },
                                label = pair.second,
                                modifier = itemModifier.fillMaxWidth(),
                                showCheckIcon = false,
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان الخطة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                item {
                    OutlinedTextField(target, { target = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("المطلوب خلال الخطة بالصفحات") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !phaseManaged)
                }
                if (scopeType == "student" && type in setOf("hifz", "tathbit")) item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f)),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (type == "tathbit") "الاستمرار من آخر تثبيت" else "الاستمرار من آخر حفظ", fontWeight = FontWeight.Bold)
                            when {
                                loadingPlanAnchor -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                latestPlanAnchor != null -> {
                                    Text("آخر موضع: ${recitationRangeLabel(latestPlanAnchor!!)} · ${latestPlanAnchor!!.date}")
                                    val loc = latestPlanAnchor?.let { r ->
                                        if (r.endSurah != null && r.endAyah != null) SmartPlanActivityRules.nextLocation(QuranLocation(r.endSurah, r.endAyah), routeDirection) else null
                                    }
                                    loc?.let { Text("البداية المقترحة: ${quranLocationLabel(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Button(
                                        onClick = {
                                            try { error = null; applyLatestPlanAnchor() }
                                            catch (e: Exception) { error = e.message ?: "تعذر استخدام آخر تسميع" }
                                        },
                                        enabled = !phaseManaged,
                                    ) { Text("استخدم النقطة التالية") }
                                }
                                else -> Text("لا يوجد تسميع سابق معتمد لهذا النشاط؛ يمكنك تحديد البداية يدويًا من نطاق القرآن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { showRangePicker = true }, enabled = !phaseManaged, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Default.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("نطاق القرآن: ${RecitationParityRules.quranRangeLabel(rangeStartSurah, rangeStartAyah, rangeEndSurah, rangeEndAyah, rangeStartPage, rangeEndPage)}")
                    }
                }
                if (type in setOf("hifz", "tathbit", "sard")) item {
                    Text("اتجاه الحفظ / التقدم", style = MaterialTheme.typography.labelLarge)
                    ResponsiveChipGrid(
                        items = listOf("reverse_surahs_forward_ayahs" to "من الناس إلى البقرة", "forward_surahs_forward_ayahs" to "من البقرة إلى الناس"),
                        minItemWidth = 160.dp,
                        maxColumns = 2,
                    ) { pair, itemModifier ->
                        ProFilterChip(
                            selected = routeDirection == pair.first,
                            onClick = { if (!phaseManaged) routeDirection = pair.first },
                            label = pair.second,
                            modifier = itemModifier.fillMaxWidth(),
                            showCheckIcon = false,
                        )
                    }
                    Text(
                        if (routeDirection == "reverse_surahs_forward_ayahs") "داخل كل سورة تبقى الآيات من الأصغر إلى الأكبر، ثم ننتقل إلى السورة السابقة رقمًا."
                        else "داخل كل سورة تبقى الآيات من الأصغر إلى الأكبر، ثم ننتقل إلى السورة التالية في ترتيب المصحف.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HalaqatiDateField(
                            label = "تاريخ البدء",
                            value = runCatching { LocalDate.parse(startDate) }.getOrDefault(today),
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.weight(1f),
                            enabled = !phaseManaged,
                        )
                        HalaqatiDateField(
                            label = "تاريخ الانتهاء",
                            value = runCatching { LocalDate.parse(endDate) }.getOrDefault(today.plusMonths(1)),
                            onClick = { showEndDatePicker = true },
                            modifier = Modifier.weight(1f),
                            enabled = !phaseManaged,
                        )
                    }
                }
                item {
                    Text("أيام الدوام", style = MaterialTheme.typography.labelLarge)
                    ResponsiveChipGrid(items = weekdayLabels.mapIndexed { index, label -> index to label }, minItemWidth = 104.dp, maxColumns = 3) { pair, itemModifier ->
                        ProFilterChip(
                            selected = pair.first in weekdays,
                            onClick = { if (!phaseManaged) weekdays = if (pair.first in weekdays) weekdays - pair.first else weekdays + pair.first },
                            label = pair.second,
                            modifier = itemModifier.fillMaxWidth(),
                            showCheckIcon = false,
                        )
                    }
                }
                item {
                    Text("يوم الاستدراك", style = MaterialTheme.typography.labelLarge)
                    val catchupOptions = listOf(-1 to "بدون") + weekdayLabels.mapIndexed { index, label -> index to label }
                    ResponsiveChipGrid(items = catchupOptions, minItemWidth = 104.dp, maxColumns = 3) { pair, itemModifier ->
                        ProFilterChip(
                            selected = if (pair.first == -1) catchUp == null else catchUp == pair.first,
                            onClick = { if (!phaseManaged) catchUp = pair.first.takeIf { it >= 0 } },
                            label = pair.second,
                            modifier = itemModifier.fillMaxWidth(),
                            showCheckIcon = false,
                        )
                    }
                }
                if (type == "review") item {
                    Text("مسار المراجعة", style = MaterialTheme.typography.labelLarge)
                    ResponsiveChipGrid(items = listOf("near" to "قريبة", "far" to "بعيدة", "both" to "قريبة + بعيدة"), minItemWidth = 110.dp, maxColumns = 3) { pair, itemModifier ->
                        ProFilterChip(reviewTier == pair.first, { reviewTier = pair.first }, pair.second, modifier = itemModifier.fillMaxWidth(), showCheckIcon = false)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(nearPages, { nearPages = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("قريبة يومياً") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(farPages, { farPages = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("دورة بعيدة") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                item {
                    TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (showAdvanced) "إخفاء قواعد الخطة" else "قواعد الخطة الذكية")
                        Spacer(Modifier.width(6.dp))
                        Icon(if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                }
                if (showAdvanced) {
                    item {
                        Text("أقل تقييم للاجتياز", style = MaterialTheme.typography.labelLarge)
                        ResponsiveChipGrid(items = listOf("excellent" to "ممتاز", "very_good" to "جيد جداً", "good" to "جيد"), minItemWidth = 105.dp, maxColumns = 3) { pair, itemModifier ->
                            ProFilterChip(minEvaluation == pair.first, { minEvaluation = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                        }
                    }
                    item {
                        Text("أقصى زيادة يومية", style = MaterialTheme.typography.labelLarge)
                        ResponsiveChipGrid(items = listOf("1.25", "1.5", "1.75", "2.0"), minItemWidth = 92.dp, maxColumns = 4) { value, itemModifier ->
                            ProFilterChip(dailyCap == value || dailyCap == value.removeSuffix(".0"), { dailyCap = value }, "${(value.toDouble() * 100).toInt()}%", itemModifier.fillMaxWidth(), showCheckIcon = false)
                        }
                    }
                    item { OutlinedTextField(delayDays, { delayDays = it.filter(Char::isDigit) }, label = { Text("تنبيه التأخر بعد أيام دوام") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { SwitchRow("لا يتقدم قبل اجتياز التقييم", qualityGate) { qualityGate = it } }
                    item { SwitchRow("تنبيه التحضير المنزلي لولي الأمر", homePrepPush) { homePrepPush = it } }
                }
                item { OutlinedTextField(note, { note = it }, label = { Text("ملاحظة للمحفّظ وولي الأمر") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        error = null
                        onSave(buildDraft())
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                        error = e.message ?: "تحقق من بيانات الخطة"
                    }
                },
            ) { Text("حفظ الخطة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
        modifier = Modifier.fillMaxWidth(.94f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    )

    if (showStartDatePicker) {
        HalaqatiDatePickerDialog(
            initial = runCatching { LocalDate.parse(startDate) }.getOrDefault(today),
            title = "اختر تاريخ بدء الخطة",
            onDismiss = { showStartDatePicker = false },
            onConfirm = { selected -> startDate = selected.toString(); if (runCatching { LocalDate.parse(endDate) }.getOrDefault(selected) < selected) endDate = selected.toString(); showStartDatePicker = false },
        )
    }
    if (showEndDatePicker) {
        HalaqatiDatePickerDialog(
            initial = runCatching { LocalDate.parse(endDate) }.getOrDefault(today.plusMonths(1)),
            title = "اختر تاريخ انتهاء الخطة",
            onDismiss = { showEndDatePicker = false },
            onConfirm = { selected -> endDate = maxOf(selected, runCatching { LocalDate.parse(startDate) }.getOrDefault(today)).toString(); showEndDatePicker = false },
        )
    }

    if (showRangePicker) {
        QuranRangePickerDialog(
            initialStartPage = rangeStartPage,
            initialEndPage = rangeEndPage,
            initialStartSurah = rangeStartSurah,
            initialStartAyah = rangeStartAyah,
            initialEndSurah = rangeEndSurah,
            initialEndAyah = rangeEndAyah,
            track = effectiveStudentTrack,
            activityType = when (type) {
                "hifz" -> "new_hifz"
                "sard" -> "sard"
                "tathbit" -> "tathbit"
                else -> "review_near"
            },
            direction = planTraversalDirection(routeDirection),
            onDismiss = { showRangePicker = false },
            onConfirm = { selected ->
                rangeStartPage = selected.startPage
                rangeEndPage = selected.endPage
                rangeStartSurah = selected.start?.surah
                rangeStartAyah = selected.start?.ayah
                rangeEndSurah = selected.end?.surah
                rangeEndAyah = selected.end?.ayah
                rangeLineKeys = selected.qcfLineKeys
                showRangePicker = false
            },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SmartPlanOverviewCard(snapshot: PlanSnapshot) {
    val core = snapshot.coreState
    val schedulePercent = SmartPlanUiRules.schedulePerformancePercent(core.completedPages, core.requiredByToday)
    val performanceBand = if (core.status == com.mohammedsamir.halaqati.plans.SmartPlanCoreStateEngine.StatusCode.NOT_STARTED) {
        SmartPlanUiRules.PerformanceBand("لم تبدأ", SmartPlanUiRules.Tone.NEUTRAL)
    } else SmartPlanUiRules.performanceBand(schedulePercent)
    val toneContainer = when (performanceBand.tone) {
        SmartPlanUiRules.Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        SmartPlanUiRules.Tone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
        SmartPlanUiRules.Tone.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
        SmartPlanUiRules.Tone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val toneContent = when (performanceBand.tone) {
        SmartPlanUiRules.Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        SmartPlanUiRules.Tone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
        SmartPlanUiRules.Tone.SUCCESS -> MaterialTheme.colorScheme.onSecondaryContainer
        SmartPlanUiRules.Tone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val statusIcon = when (core.status) {
        com.mohammedsamir.halaqati.plans.SmartPlanCoreStateEngine.StatusCode.COMPLETE_EARLY,
        com.mohammedsamir.halaqati.plans.SmartPlanCoreStateEngine.StatusCode.COMPLETE -> Icons.Default.Verified
        com.mohammedsamir.halaqati.plans.SmartPlanCoreStateEngine.StatusCode.BEHIND -> Icons.Default.WarningAmber
        com.mohammedsamir.halaqati.plans.SmartPlanCoreStateEngine.StatusCode.AHEAD -> Icons.Default.TrendingUp
        com.mohammedsamir.halaqati.plans.SmartPlanCoreStateEngine.StatusCode.NOT_STARTED -> Icons.Default.Schedule
        else -> Icons.Default.TrackChanges
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(15.dp), color = toneContainer, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(statusIcon, contentDescription = null, tint = toneContent, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    snapshot.student.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    snapshot.plan.title.ifBlank { planTypeAr(snapshot.plan.type) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = toneContainer, contentColor = toneContent, shape = RoundedCornerShape(100.dp)) {
                Text(
                    performanceBand.label,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                when {
                    core.completedEarly -> "أتم الخطة مبكرًا${if (core.surplusPages > 0.001) " · +${plainNumber(core.surplusPages)} ص" else ""}"
                    core.behindBySchedule > 0.001 -> "متأخر ${plainNumber(core.behindBySchedule)} ص"
                    core.aheadBySchedule > 0.001 -> "متقدم +${plainNumber(core.aheadBySchedule)} ص"
                    core.status == com.mohammedsamir.halaqati.plans.SmartPlanCoreStateEngine.StatusCode.NOT_STARTED -> "لم تبدأ الخطة"
                    else -> "على الخطة"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LinearProgressIndicator(
            drawStopIndicator = {}, progress = { (core.completionPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(9.dp).graphicsLayer(scaleX = -1f),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PlanStatTile("الهدف", "${plainNumber(core.targetPages)} ص", Modifier.weight(1f))
            PlanStatTile("المنجز", "${plainNumber(core.completedPages)} ص", Modifier.weight(1f))
            PlanStatTile(
                if (core.surplusPages > 0.001) "الإنجاز الإضافي" else "المتبقي",
                if (core.surplusPages > 0.001) "+${plainNumber(core.surplusPages)} ص" else "${plainNumber(core.remainingPages)} ص",
                Modifier.weight(1f),
            )
        }

        val requiredToday = snapshot.progress.dailyTarget.coerceAtLeast(0.0)
        val completedToday = snapshot.progress.completedToday.coerceAtLeast(0.0)
        val remainingToday = (requiredToday - completedToday).coerceAtLeast(0.0)
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
            shape = RoundedCornerShape(15.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PlanMetric("مطلوب اليوم", "${plainNumber(requiredToday)} ص")
                PlanMetric("المنجز اليوم", "${plainNumber(completedToday)} ص")
                PlanMetric("المتبقي اليوم", "${plainNumber(remainingToday)} ص")
            }
        }
        // RC44: say where the daily figure comes from so a manager can spot a wrong setting.
        if (snapshot.progress.plannedToday > 0.001) {
            Text(
                "أساسي ${plainNumber(snapshot.progress.plannedToday)} ص" +
                    (if (snapshot.progress.recoveryShare > 0.001) " + استدراك ${plainNumber(snapshot.progress.recoveryShare)} ص" else "") +
                    " = ${plainNumber(requiredToday)} ص",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (core.completedEarly) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .72f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text("أتم الخطة مبكرًا", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "بقي ${core.daysRemainingInPlan} يومًا على نهاية الخطة · الإنجاز الإضافي ${if (core.surplusPages > 0.001) "+${plainNumber(core.surplusPages)} ص" else "يبدأ من التسميع التالي"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        val week = core.weekly
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .50f), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    Text("هذا الأسبوع", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${plainNumber(week.completed)} / ${plainNumber(week.target)} ص", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                }
                val weekProgress = if (week.target <= 0.001) 0f else (week.completed / week.target).toFloat().coerceIn(0f, 1f)
                AnimatedLinearProgress(progress = weekProgress, modifier = Modifier.fillMaxWidth().height(6.dp).graphicsLayer(scaleX = -1f))
                Text(
                    when {
                        week.behindBy > 0.001 -> "متبقي على المطلوب حتى اليوم ${plainNumber(week.behindBy)} ص"
                        week.aheadBy > 0.001 -> "متقدم هذا الأسبوع ${plainNumber(week.aheadBy)} ص"
                        else -> "التقدم الأسبوعي متوافق مع الخطة"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlanHealthSummaryChip(health: String, count: Int, label: String) {
    val (bg, fg) = planHealthColors(health)
    Surface(shape = RoundedCornerShape(999.dp), color = bg, contentColor = fg) {
        Row(
            Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(7.dp).background(fg, RoundedCornerShape(50)))
            Text("$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun planHealthColors(health: String): Pair<Color, Color> = com.mohammedsamir.halaqati.ui.theme.statusColors(
    when (health) {
        SmartPlanHealthRules.BLUE -> "info"
        SmartPlanHealthRules.GREEN -> "success"
        SmartPlanHealthRules.YELLOW -> "warning"
        SmartPlanHealthRules.RED -> "error"
        else -> "neutral"
    },
)

private fun planCurrentPhaseLabel(snapshot: PlanSnapshot, today: LocalDate = LocalDate.now()): String {
    if (snapshot.student.track == "tathbit" || snapshot.plan.type == "tathbit") return "التثبيت الآن"
    snapshot.phase?.let { phase ->
        return when (phase.phaseType) {
            "sard" -> "السرد الآن"
            "hifz" -> "الحفظ الآن"
            else -> "${phase.title} الآن"
        }
    }
    val month = runCatching { YearMonth.from(today) }.getOrNull()
    if (month != null && today.dayOfMonth <= minOf(22, month.lengthOfMonth())) {
        return if (snapshot.progress.remaining <= .001) "الحفظ مكتمل · السرد يبدأ 23" else "الحفظ الآن"
    }
    return "بانتظار المرحلة الحالية"
}

@Composable
private fun MonthlyPhaseMiniCard(
    title: String,
    dates: String,
    target: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .68f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
                if (active) Spacer(Modifier.width(5.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(dates, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(target, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SmartPlanAccordionCard(
    snapshot: PlanSnapshot,
    expanded: Boolean,
    canManage: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onDetails: () -> Unit,
    onHistory: () -> Unit,
    onEdit: () -> Unit,
    onPhases: () -> Unit,
    onRecite: () -> Unit,
    onEditPhase: () -> Unit = onPhases,
) {
    // While the student is in the Sard phase the card shows the Sard phase itself (its own target and scheduled days), not the month's
    // Hifz progress measured against the Sard target (that showed "25 / 15.73 · 0.63 behind" for a student who had not started).
    val sardPhase = snapshot.phase?.phaseType == "sard"
    val phaseProgress = snapshot.phaseProgress?.takeIf { sardPhase }
    val sardPace = phaseProgress?.let {
        com.mohammedsamir.halaqati.plans.PhaseDaysRules.sardPace(it.targetPages, it.completedPages, it.scheduledDays, it.remainingScheduledDays)
    }
    val progress = if (sardPace != null && phaseProgress != null) snapshot.progress.copy(
        completed = phaseProgress.completedPages,
        remaining = phaseProgress.remainingPages ?: 0.0,
        plannedByToday = sardPace.expected,
        behindBy = sardPace.behind,
        aheadBy = sardPace.ahead,
    ) else snapshot.progress
    val health = SmartPlanHealthRules.fromProgress(
        completed = progress.completed,
        requiredByToday = progress.plannedByToday,
        paused = snapshot.plan.status == "paused",
    )
    val monthEndDay = runCatching { LocalDate.parse(snapshot.plan.endDate).dayOfMonth }.getOrDefault(YearMonth.now().lengthOfMonth())
    val currentPhase = planCurrentPhaseLabel(snapshot)
    val hifzMonthlyTarget = snapshot.plan.settings.monthlyHifzTargetPages.takeIf { it > .001 } ?: snapshot.plan.target
    val activeTarget = if (sardPhase) (phaseProgress?.targetPages ?: snapshot.plan.target) else snapshot.plan.target
    val monthlyProgress = if (activeTarget <= .001) 0f else (progress.completed / activeTarget).toFloat().coerceIn(0f, 1f)
    val assignment = progress.assignment
    val assignmentText = when {
        assignment.blockedByRetry -> "إعادة تثبيت · ${planAssignmentText(assignment).substringAfter(": ", planAssignmentText(assignment))}"
        assignment.pages <= 0.0 -> if (progress.remaining <= 0.0) "تم إكمال الهدف الحالي" else "لا يوجد مقرر اليوم"
        else -> planAssignmentText(assignment)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().calmContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().nourPattern(PatternLevel.Subtle, MaterialTheme.colorScheme.primary), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (dotBg, dotFg) = planHealthColors(health.code)
                val statusSymbol = when (health.code) {
                    SmartPlanHealthRules.BLUE -> "↑"
                    SmartPlanHealthRules.GREEN -> "✓"
                    SmartPlanHealthRules.YELLOW -> "!"
                    SmartPlanHealthRules.RED -> "!!"
                    else -> "–"
                }
                Surface(shape = RoundedCornerShape(999.dp), color = dotBg, contentColor = dotFg, modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(statusSymbol, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { contentDescription = health.label })
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(snapshot.student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, softWrap = true)
                    if (expanded) {
                        Text(currentPhase, style = MaterialTheme.typography.labelMedium, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"), fontWeight = FontWeight.SemiBold)
                        PlanHealthBadge(health.code, health.label)
                    }
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (expanded) "طي الخطة" else "عرض الخطة")
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${plainNumber(progress.completed)} / ${plainNumber(activeTarget)} ص", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        progress.behindBy > .001 -> "متأخر ${plainNumber(progress.behindBy)} ص"
                        progress.aheadBy > .001 -> "متقدم ${plainNumber(progress.aheadBy)} ص"
                        else -> "مطابق للمطلوب حتى اليوم"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedLinearProgress(progress = monthlyProgress, modifier = Modifier.fillMaxWidth().height(5.dp))

            if (expanded) {
                if (snapshot.student.track == "tathbit" || snapshot.plan.type == "tathbit") {
                    MonthlyPhaseMiniCard("التثبيت", "${snapshot.plan.startDate} – ${snapshot.plan.endDate}", "الهدف ${plainNumber(snapshot.plan.target)} ص", true, Modifier.fillMaxWidth())
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        MonthlyPhaseMiniCard("الحفظ", "1–22", "${plainNumber(hifzMonthlyTarget)} ص", snapshot.phase?.phaseType == "hifz" || (snapshot.phase == null && LocalDate.now().dayOfMonth <= 22), Modifier.weight(1f))
                        MonthlyPhaseMiniCard("السرد", "23–$monthEndDay", "25 ص", snapshot.phase?.phaseType == "sard", Modifier.weight(1f))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("مقرر اليوم", style = MaterialTheme.typography.labelMedium, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                Text(assignmentText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            }
            AnimatedVisibility(visible = expanded, enter = motionEnter(expandVertically() + fadeIn()), exit = motionExit(shrinkVertically() + fadeOut())) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    snapshot.lastRecitation?.let { last ->
                        PlanInfoBlock(
                            "آخر تسميع · ${last.date}",
                            "${assignmentKindAr(last.activityType)} · ${smartRecitationRangeText(last)} · ${plainNumber(last.pagesCount)} ص",
                            Icons.Default.History,
                        )
                    } ?: PlanInfoBlock("آخر تسميع", "لا يوجد تسميع مرتبط بالخطة بعد", Icons.Default.History)

                    if (health.code == SmartPlanHealthRules.RED || health.code == SmartPlanHealthRules.YELLOW) {
                        val remainingDays = (22 - LocalDate.now().dayOfMonth).coerceAtLeast(1)
                        val recovery = progress.behindBy / remainingDays
                        StateNotice(
                            if (health.code == SmartPlanHealthRules.RED)
                                "يحتاج متابعة: متأخر أكثر من 25% عن المطلوب حتى اليوم. للعودة للمسار يحتاج تقريبًا ${plainNumber(recovery)} ص إضافية في كل يوم حفظ متبقٍ."
                            else "يحتاج انتباه: يوجد تأخر عن المسار المتوقع حتى اليوم.",
                            kind = if (health.code == SmartPlanHealthRules.RED) "error" else "warning",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("الأرشيف")
                        }
                        OutlinedButton(onClick = onDetails, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("التفاصيل")
                        }
                    }
                    if (canManage) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("تعديل المقرر", maxLines = 1)
                            }
                            OutlinedButton(onClick = onEditPhase, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("تعديل المرحلة", maxLines = 1)
                            }
                        }
                        TextButton(onClick = onPhases, modifier = Modifier.fillMaxWidth()) { Text("كل المراحل والأدوات") }
                        Button(
                            onClick = onRecite,
                            enabled = !busy && assignment.pages > 0.0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null); Spacer(Modifier.width(7.dp)); Text(if (busy) "جاري التجهيز…" else "تسميع المقرر")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanHealthBadge(health: String, label: String) {
    val colors = planHealthColors(health)
    Surface(color = colors.first, contentColor = colors.second, shape = MaterialTheme.shapes.extraLarge) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlanStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun PlanInfoBlock(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val container = when {
        title.startsWith("آخر") -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .45f)
        title.contains("القادم") -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
        title.startsWith("هذا الأسبوع") -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .70f)
        title.startsWith("لماذا") -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .42f)
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun planWhyText(snapshot: PlanSnapshot): String {
    val progress = snapshot.progress
    val core = snapshot.coreState
    val phaseProgress = snapshot.phaseProgress
    if (snapshot.phase?.phaseType == "sard") {
        return when {
            phaseProgress?.targetPages == null -> "مرحلة السرد مفتوحة؛ المقرر يتابع من آخر Sard Anchor من دون فرض هدف يومي ثابت."
            phaseProgress.completedEarly -> "أتم الطالب هدف السرد مبكرًا؛ يمكنه الاستمرار ويُحسب ما بعد الهدف كإنجاز إضافي، أو إنهاء المرحلة."
            (phaseProgress.remainingPages ?: 0.0) <= .001 -> "اكتمل هدف مرحلة السرد الحالية."
            phaseProgress.suggestedTodayPages != null -> "استكمالًا من آخر سرد + توزيع ${plainNumber(phaseProgress.remainingPages ?: 0.0)} ص المتبقية على أيام السرد المتبقية بنمط ${if (snapshot.phase?.distributionMode == "equal") "متساوٍ" else "مرن"}."
            else -> "يستكمل السرد من آخر Sard Anchor المسجل لهذه المرحلة، مستقلًا عن موضع الحفظ."
        }
    }
    return when {
        core.completedEarly -> "أتم الطالب الهدف الشهري مبكرًا؛ المقرر الحالي استمرار طبيعي من آخر موضع، وما بعد الهدف يُحسب إنجازًا إضافيًا دون رفع هدف الشهر."
        core.surplusPages > 0.001 -> "الهدف مكتمل، والطالب متقدم +${plainNumber(core.surplusPages)} ص كإنجاز إضافي؛ يستمر التسلسل من آخر موضع محفوظ."
        progress.assignment.blockedByRetry -> "يوجد مقرر سابق يحتاج إعادة؛ بوابة الجودة تمنع التقدم حتى تثبيته."
        progress.behindBy > 0.01 && progress.recoveryShare > 0.001 -> "أنت متأخر ${plainNumber(progress.behindBy)} ص عن المسار؛ تعويض اليوم +${plainNumber(progress.recoveryShare)} ص ضمن الحد اليومي للخطة."
        progress.behindBy > 0.01 -> "أنت متأخر ${plainNumber(progress.behindBy)} ص عن المسار؛ سيُوزّع التعويض على أيام الخطة القادمة دون تجاوز الحد اليومي."
        progress.assignment.isCatchUp -> "هذا يوم تعويض مخصص لاستعادة المتأخر من الأيام السابقة."
        progress.aheadBy > 0.01 -> "الطالب متقدم ${plainNumber(progress.aheadBy)} ص؛ المقرر يحافظ على تسلسل الخطة دون مضاعفة غير لازمة."
        progress.assignment.pages <= 0.001 && progress.remaining <= 0.001 -> "اكتملت الخطة الحالية."
        progress.assignment.pages <= 0.001 -> "اليوم ليس يوم دوام للخطة؛ سيظهر المقرر في يوم الخطة التالي."
        else -> "المطلوب حتى اليوم ${plainNumber(progress.plannedByToday)} ص، والمنجز ${plainNumber(progress.completed)} ص؛ ورد اليوم محسوب وفق أيام الدوام والحد اليومي والتقدم الفعلي."
    }
}


@Composable
private fun PlanMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanDetailDialog(snapshot: PlanSnapshot, close: () -> Unit) {
    val p = snapshot.progress
    ModalBottomSheet(
        onDismissRequest = close,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("تفاصيل الخطة الذكية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text("${snapshot.plan.startDate} ← ${snapshot.plan.endDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = close, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }
            LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ElevatedCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) { SmartPlanOverviewCard(snapshot) }
                    }
                }
                if (snapshot.phase != null) item { PlanPhaseTimelineInline(snapshot) }
                item {
                    PlanInfoBlock(
                        title = "مقرر اليوم",
                        value = when {
                            p.assignment.pages > 0.001 -> planAssignmentText(p.assignment)
                            snapshot.coreState.completedEarly -> "أُنجز الهدف الشهري؛ أي تسميع جديد يُحسب إنجازًا إضافيًا"
                            p.remaining <= 0.001 -> "الخطة مكتملة"
                            else -> if (snapshot.phase?.phaseType == "sard") "لا يوجد سرد مقرر اليوم" else "لا يوجد حفظ مقرر اليوم"
                        },
                        icon = Icons.Default.MenuBook,
                    )
                }
                snapshot.nextDuty?.let { next ->
                    item { PlanInfoBlock("المقرر القادم · ${next.date}", planAssignmentText(next.assignment), Icons.Default.EventAvailable) }
                }
                item { PlanInfoBlock("لماذا هذا المقرر؟", planWhyText(snapshot), Icons.Default.Lightbulb) }
                item { PlanInfoBlock("الحد اليومي", "${plainNumber(p.dailyCap)} صفحة · الحد الأدنى ${plainNumber(p.minimumDaily)} صفحة", Icons.Default.Speed) }
                item { PlanInfoBlock("المطلوب حتى اليوم", "${plainNumber(p.plannedByToday)} صفحة · العجز ${plainNumber(p.deficit)} صفحة", Icons.Default.TrackChanges) }
                item {
                    PlanInfoBlock(
                        "النطاق",
                        RecitationParityRules.quranRangeLabel(
                            snapshot.plan.rangeStartSurah, snapshot.plan.rangeStartAyah,
                            snapshot.plan.rangeEndSurah, snapshot.plan.rangeEndAyah,
                            snapshot.plan.rangeStartPage, snapshot.plan.rangeEndPage,
                        ),
                        Icons.Default.AutoStories,
                    )
                }
                if (p.retryPages.isNotEmpty()) item { PlanInfoBlock("صفحات تحتاج إعادة", p.retryPages.joinToString(", "), Icons.Default.Replay) }
                snapshot.plan.note.takeIf { it.isNotBlank() && it != "null" }?.let { note -> item { PlanInfoBlock("ملاحظة الخطة", note, Icons.Default.StickyNote2) } }
                item {
                    Button(onClick = close, modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget)) {
                        Icon(Icons.Default.Done, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("إغلاق التفاصيل")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun assignmentKindAr(value: String) = when (value) {
    "hifz", "new_hifz" -> "حفظ جديد"
    "review", "review_near" -> "مراجعة قريبة"
    "review_far", "tathbit" -> "تثبيت"
    "sard" -> "سرد"
    "tilawah" -> "تلاوة"
    "test" -> "اختبار"
    else -> value
}

private fun pageRangeLabel(start: Int?, end: Int?): String = when {
    start == null && end == null -> "يُحدد حسب الخطة"
    else -> RecitationParityRules.pageRangeQuranLabel(start, end)
}

internal fun plainNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (kotlin.math.abs(rounded - rounded.toInt()) < 0.000001) rounded.toInt().toString()
    else rounded.toString().trimEnd('0').trimEnd('.')
}

fun planTypeAr(value: String) = when (value) {
    "hifz" -> "حفظ"
    "review" -> "مراجعة"
    "sard" -> "سرد"
    "tathbit" -> "تثبيت"
    else -> value
}


@Composable
fun UsersScreen(pending: Int, onRefresh: () -> Unit, openApprovals: () -> Unit, tabs: @Composable () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("approved") }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var rejectUser by remember { mutableStateOf<JSONObject?>(null) }
    var roleUser by remember { mutableStateOf<JSONObject?>(null) }
    var deleteUser by remember { mutableStateOf<JSONObject?>(null) }
    var linkGuardian by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }

    suspend fun reload(force: Boolean = false) {
        loading = rows.isEmpty()
        try {
            val arr = withContext(Dispatchers.IO) { AppGraph.repo.accountProfiles(forceRefresh = force) }
            rows = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر تحميل الحسابات"
        } finally {
            loading = false
        }
    }

    fun runAccountAction(userId: String, action: () -> Unit, success: String) {
        busyId = userId
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                message = success
                if (success.contains("اعتماد")) com.mohammedsamir.halaqati.ui.components.HalaqatiMotionEvents.celebrate()
                reload(force = true)
                onRefresh()
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                message = e.message ?: "تعذر تنفيذ العملية"
            } finally {
                busyId = null
            }
        }
    }

    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { if (syncEpoch > 0L) reload() }

    LaunchedEffect(Unit) { reload() }
    val filtered = rows.filter { row ->
        (status == "all" || row.optString("approval_status") == status) &&
            (query.isBlank() || row.optString("full_name").contains(query, true) || row.optString("email").contains(query, true))
    }

    val photoContext = androidx.compose.ui.platform.LocalContext.current
    var photoTarget by remember { mutableStateOf<String?>(null) }
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        val target = photoTarget
        photoTarget = null
        if (uri != null && target != null) {
            val data = com.mohammedsamir.halaqati.documents.DocumentImageEncoder.uriJpeg(photoContext, uri, maxDimension = 256, quality = 80, squareCrop = true)
            if (data != null) runAccountAction(target, { AppGraph.repo.setAvatar(target, data) }, "تم تحديث الصورة")
            else message = "تعذر قراءة الصورة"
        }
    }
    var expandedUser by remember { mutableStateOf<String?>(null) }
    var freezeUser by remember { mutableStateOf<JSONObject?>(null) }

    Column {
        AppTopBar(
            title = "الحسابات والصلاحيات",
            onRefresh = { scope.launch { reload(force = true) }; onRefresh() },
            pending = pending,
        )
        tabs()
        // RC53: one slim search row + one scrolling chip strip (the old 2x2 chip grid and the big link button took a third of the screen).
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.mohammedsamir.halaqati.ui.components.CompactSearchField(
                value = query, onValueChange = { query = it }, placeholder = "بحث بالاسم أو البريد",
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { linkGuardian = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.FamilyRestroom, contentDescription = "إدارة ارتباطات أولياء الأمور")
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("approved" to "معتمدة", "rejected" to "مرفوضة", "all" to "الكل").forEach { pair ->
                ProFilterChip(status == pair.first, { status = pair.first }, pair.second, showCheckIcon = false)
            }
        }
        message?.let {
            StateNotice(
                it,
                kind = if (it.startsWith("تعذر") || it.contains("غير صالح")) "error" else "success",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (loading && rows.isEmpty()) LoadingSkeleton(rows = 4, modifier = Modifier.padding(12.dp))
        if (!loading && filtered.isEmpty()) EmptyState("لا توجد حسابات في هذا التصنيف")
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.optString("id") }) { row ->
                val id = row.optString("id")
                val isAdmin = row.optString("role") == "admin"
                val approval = row.optString("approval_status")
                val active = row.optBoolean("active", true)
                val frozen = row.optBoolean("frozen", false)
                val staff = row.optString("role") in setOf("teacher", "supervisor")
                val requested = row.optString("requested_role").ifBlank { row.optString("role", "guardian") }
                val open = expandedUser == id
                val photo = row.optString("avatar_path").takeIf { it.isNotBlank() && it != "null" }
                val reducedMotion = com.mohammedsamir.halaqati.ui.theme.LocalReducedMotion.current
                val chevronTurn by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (open) 180f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(if (reducedMotion) 0 else 260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    label = "accountChevron",
                )
                com.mohammedsamir.halaqati.ui.components.BorderedCard(
                    modifier = (if (reducedMotion) Modifier else Modifier.animateItem(
                        fadeInSpec = androidx.compose.animation.core.tween(240),
                        placementSpec = androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 380f),
                        fadeOutSpec = androidx.compose.animation.core.tween(160),
                    )).then(
                        if (reducedMotion) Modifier else Modifier.animateContentSize(
                            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 420f),
                        ),
                    ),
                    accent = if (frozen) MaterialTheme.colorScheme.error else null,
                    onClick = { expandedUser = if (open) null else id },
                    padding = 8.dp,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.mohammedsamir.halaqati.ui.components.StudentAvatar(photo, row.optString("full_name").ifBlank { "؟" }, 38.dp)
                        Column(Modifier.weight(1f)) {
                            Text(row.optString("full_name").ifBlank { "مستخدم" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (isAdmin) (if (com.mohammedsamir.halaqati.ui.components.PlatformOwnerState.isOwner) "مسؤول النظام" else "مسؤول الجمعية") else roleAr(row.optString("role")), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (frozen) StatusPill("مجمّد", kind = "danger")
                        AccountStatusBadge(approval, active, isAdmin)
                        Icon(Icons.Default.ExpandMore, contentDescription = if (open) "طي" else "فتح", modifier = Modifier.graphicsLayer { rotationZ = chevronTurn })
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = open,
                        enter = com.mohammedsamir.halaqati.ui.theme.motionEnter(
                            androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)),
                        ),
                        exit = com.mohammedsamir.halaqati.ui.theme.motionExit(
                            androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
                        ),
                    ) {
                      Column {
                        Spacer(Modifier.height(6.dp))
                        Text(row.optString("email").ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("الطلب: ${roleAr(requested)}", style = MaterialTheme.typography.labelMedium)
                        if (approval == "rejected") row.optString("rejection_reason").takeIf { it.isNotBlank() && it != "null" }?.let { reason ->
                            Text("سبب الرفض: $reason", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!isAdmin) {
                            val guardianRequest = requested == "guardian"
                            val accountActions = buildList {
                                when (approval) {
                                    "pending" -> add("review_link")
                                    "rejected" -> add(if (guardianRequest) "review_link" else "approve_again")
                                    else -> { add("toggle"); add("freeze"); add("role"); if (staff) add("photo") }
                                }
                                add("delete")
                            }
                            Spacer(Modifier.height(6.dp))
                            ResponsiveChipGrid(items = accountActions, minItemWidth = 110.dp, maxColumns = 3) { action, itemModifier ->
                                OutlinedButton(
                                    onClick = {
                                        when (action) {
                                            "review_link" -> openApprovals()
                                            "approve" -> runAccountAction(id, { AppGraph.repo.approveAccount(id, requested) }, "تم اعتماد الحساب")
                                            "reject" -> rejectUser = row
                                            "approve_again" -> runAccountAction(id, { AppGraph.repo.approveAccount(id, requested) }, "تم اعتماد الحساب من جديد")
                                            "toggle" -> runAccountAction(id, { AppGraph.repo.setAccountActive(id, !active) }, if (active) "تم إيقاف الحساب" else "تم تفعيل الحساب")
                                            "freeze" -> if (frozen) runAccountAction(id, { AppGraph.repo.setAccountFrozen(id, false) }, "تم فك تجميد الحساب") else freezeUser = row
                                            "role" -> roleUser = row
                                            "photo" -> { photoTarget = id; photoPicker.launch("image/*") }
                                            else -> deleteUser = row
                                        }
                                    },
                                    enabled = busyId == null,
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = itemModifier.fillMaxWidth().heightIn(min = 44.dp),
                                ) {
                                    if (action == "delete") Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Text(
                                        when (action) {
                                            "review_link" -> "طلبات الاعتماد"
                                            "approve" -> if (busyId == id) "جاري…" else "اعتماد"
                                            "reject" -> "رفض"
                                            "approve_again" -> "اعتماد من جديد"
                                            "toggle" -> if (active) "إيقاف" else "تفعيل"
                                            "freeze" -> if (frozen) "فك التجميد" else "تجميد"
                                            "role" -> "الصلاحية"
                                            "photo" -> if (photo == null) "إضافة صورة" else "تغيير الصورة"
                                            else -> "حذف"
                                        },
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                      }
                    }
                }
            }
        }
    }

    freezeUser?.let { row ->
        var reason by remember(row) { mutableStateOf("") }
        HalaqatiAlertDialog(
            onDismissRequest = { freezeUser = null },
            title = { Text("تجميد حساب ${row.optString("full_name")}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("عند الدخول سيرى صاحب الحساب أن حسابه مجمّد ولن يصل لأي بيانات حتى تفك التجميد أنت.")
                    OutlinedTextField(reason, { reason = it }, label = { Text("ملاحظة داخلية (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = { val id = row.optString("id"); val r = reason.trim(); freezeUser = null; runAccountAction(id, { AppGraph.repo.setAccountFrozen(id, true, r) }, "تم تجميد الحساب") },
                    modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget),
                ) { Text("تجميد") }
            },
            dismissButton = { TextButton(onClick = { freezeUser = null }, modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text("إلغاء") } },
        )
    }
    rejectUser?.let { row ->
        RejectAccountDialog(
            name = row.optString("full_name"),
            close = { rejectUser = null },
            save = { reason ->
                val id = row.optString("id")
                rejectUser = null
                runAccountAction(id, { AppGraph.repo.rejectAccount(id, reason) }, "تم رفض طلب الحساب")
            },
        )
    }
    roleUser?.let { row ->
        RoleAccountDialog(
            name = row.optString("full_name"),
            current = row.optString("role", "guardian"),
            close = { roleUser = null },
            save = { role ->
                val id = row.optString("id")
                roleUser = null
                runAccountAction(id, { AppGraph.repo.changeAccountRole(id, role) }, "تم تغيير الصلاحية")
            },
        )
    }
    deleteUser?.let { row ->
        DeleteAccountDialog(
            name = row.optString("full_name"),
            close = { deleteUser = null },
            confirm = {
                val id = row.optString("id")
                deleteUser = null
                runAccountAction(id, { AppGraph.repo.deleteAccountPermanently(id) }, "تم حذف الحساب نهائيًا")
            },
        )
    }
    if (linkGuardian) {
        GuardianLinkDialog(
            close = { linkGuardian = false },
            onSaved = {
                linkGuardian = false
                message = "تم تحديث ربط ولي الأمر"
                onRefresh()
            },
        )
    }
}

@Composable
private fun AccountStatusBadge(approval: String, active: Boolean, admin: Boolean) {
    val (label, container, content) = when {
        admin -> Triple("مسؤول", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        approval == "pending" -> Triple("بانتظار الاعتماد", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        approval == "rejected" -> Triple("مرفوض", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        !active -> Triple("موقوف", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> Triple("نشط", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraLarge) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

private fun roleAr(value: String) = when (value) {
    "admin" -> if (com.mohammedsamir.halaqati.ui.components.PlatformOwnerState.isOwner) "مسؤول النظام" else "مسؤول الجمعية"
    "supervisor" -> "مشرف الحلقات"
    "teacher" -> "محفّظ الحلقة"
    "guardian" -> "ولي أمر"
    "student" -> "طالب"
    "observer" -> "مانح"
    else -> "غير محدد"
}

@Composable
private fun RejectAccountDialog(name: String, close: () -> Unit, save: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text("رفض حساب $name") },
        text = { OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("سبب الرفض (اختياري)") }, minLines = 2, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { save(reason.trim()) }, modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text("رفض الحساب") } },
        dismissButton = { TextButton(onClick = close, modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text("إلغاء") } },
    )
}

@Composable
private fun RoleAccountDialog(name: String, current: String, close: () -> Unit, save: (String) -> Unit) {
    val isOwner = com.mohammedsamir.halaqati.ui.components.PlatformOwnerState.isOwner
    val roles = buildList { add("guardian"); add("teacher"); add("supervisor"); add("observer"); if (isOwner) add("admin") }
    var role by remember { mutableStateOf(current.takeIf { it in roles } ?: "guardian") }
    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text("صلاحية $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                roles.forEach { value ->
                    ProFilterChip(role == value, { role = value }, if (value == "admin") "مسؤول جمعية" else roleAr(value), modifier = Modifier.fillMaxWidth())
                }
                androidx.compose.animation.AnimatedVisibility(visible = role == "admin") {
                    Text(
                        "يدير جمعية كاملة: مشرفوها ومحفّظوها وحلقاتها. بعد الحفظ عيّنه على جمعية من شاشة «الهيكل الإداري».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { save(role) }, modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close, modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text("إلغاء") } },
    )
}

@Composable
private fun DeleteAccountDialog(name: String, close: () -> Unit, confirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text("حذف الحساب نهائيًا") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سيتم حذف حساب «$name» نهائيًا. اكتب حذف للتأكيد.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(value = typed, onValueChange = { typed = it }, label = { Text("اكتب: حذف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = confirm, enabled = typed.trim() == "حذف", modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text("حذف نهائي") } },
        dismissButton = { TextButton(onClick = close, modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text("إلغاء") } },
    )
}


@Composable
private fun GuardianLinkDialog(close: () -> Unit, onSaved: () -> Unit, initialStudentId: String? = null) {
    val scope = rememberCoroutineScope()
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var studentId by remember { mutableStateOf<String?>(null) }
    var links by remember { mutableStateOf<List<StudentGuardianRules.GuardianOption>>(emptyList()) }
    var loadedFor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<StudentGuardianRules.GuardianOption?>(null) }
    val admin = remember { AppGraph.repo.cachedProfile()?.role == UserRole.ADMIN }

    LaunchedEffect(initialStudentId) {
        try {
            students = withContext(Dispatchers.IO) { AppGraph.repo.students() }
            studentId = if (initialStudentId != null) initialStudentId.takeIf { id -> students.any { it.id == id } } else students.singleOrNull()?.id
            if (initialStudentId != null && studentId == null) error = "الطالب غير متاح ضمن صلاحياتك"
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "تعذر تحميل الطلاب" }
        finally { loading = false }
    }
    LaunchedEffect(studentId) {
        val id = studentId ?: return@LaunchedEffect
        loading = true
        error = null
        links = emptyList()
        loadedFor = null
        try {
            links = withContext(Dispatchers.IO) { AppGraph.repo.guardianLinks(id, forceRefresh = true) }
            loadedFor = id
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "تعذر تحميل الارتباطات" }
        finally { loading = false }
    }

    HalaqatiAlertDialog(
        onDismissRequest = { if (!saving) close() },
        title = { Text("أولياء الأمور المرتبطون") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StateNotice("يمكن ربط عدة أولياء أمور بالطالب. إضافة ارتباط جديد تحتاج طلبًا معتمدًا؛ جميع الارتباطات متساوية.", "info")
                if (!saving && removing == null) CompactFilterSelect("الطالب", studentId.orEmpty(), students.map { it.id to it.fullName }, { studentId = it })
                else Text(students.firstOrNull { it.id == studentId }?.fullName.orEmpty())
                if (loading) LoadingSkeleton(rows = 2)
                error?.let { StateNotice(it, "error") }
                if (!loading && error == null && loadedFor == studentId && studentId != null) {
                    if (links.isEmpty()) Text(if (AppGraph.repo.online()) "لا توجد ارتباطات معتمدة لهذا الطالب." else "لا توجد بيانات ارتباطات محفوظة.")
                    links.forEach { link ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(link.fullName, fontWeight = FontWeight.SemiBold)
                                val contact = listOfNotNull(link.phone, link.email).joinToString(" · ")
                                if (contact.isNotBlank()) Text(contact, style = MaterialTheme.typography.bodySmall)
                            }
                            if (admin && AppGraph.repo.online() && link.metadataAvailable) TextButton(onClick = { removing = link }, enabled = !saving) { Text("إزالة") }
                        }
                        HorizontalDivider()
                    }
                }
                if (links.any { !it.metadataAvailable }) Text("تعذر إظهار بيانات بعض الحسابات؛ حدّث البيانات قبل إزالة أي ارتباط غير محدد.", style = MaterialTheme.typography.bodySmall)
                if (!AppGraph.repo.online()) Text("اتصل بالإنترنت للتحقق من الارتباطات وإدارتها.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = close, enabled = !saving) { Text("إغلاق") } },
    )
    removing?.let { link ->
        val id = studentId ?: return@let
        var reason by remember(id, link.id) { mutableStateOf("") }
        HalaqatiAlertDialog(
            onDismissRequest = { if (!saving) removing = null },
            title = { Text("إزالة ارتباط ولي أمر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إزالة ارتباط ${link.fullName} فقط. لن تُحذف ارتباطات أولياء الأمور الآخرين أو بيانات الطالب.")
                    OutlinedTextField(reason, { reason = it }, label = { Text("سبب الإزالة") }, modifier = Modifier.fillMaxWidth())
                    error?.let { StateNotice(it, "error") }
                }
            },
            confirmButton = {
                Button(enabled = !saving && reason.isNotBlank(), onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { AppGraph.repo.unlinkGuardian(id, link.id, reason) }
                            removing = null
                            onSaved()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "تعذر إزالة الارتباط" }
                        finally { saving = false }
                    }
                }) { Text(if (saving) "جارٍ الإزالة…" else "تأكيد الإزالة") }
            },
            dismissButton = { TextButton(onClick = { removing = null }, enabled = !saving) { Text("إلغاء") } },
        )
    }
}

// Keep nullable field initialization in separate FIR control-flow graphs.
private fun initialPlanScopeType(current: PlanRow?): MutableState<String> =
    mutableStateOf(current?.scopeType ?: "student")

private fun initialPlanHalaqaId(current: PlanRow?, halaqas: List<JSONObject>): MutableState<String> =
    mutableStateOf(current?.halaqaId ?: halaqas.firstOrNull()?.optString("id").orEmpty())

private fun initialPlanStudentId(current: PlanRow?, students: List<Student>, halaqaId: String): MutableState<String?> =
    mutableStateOf(current?.studentId ?: students.firstOrNull { it.halaqaId == halaqaId }?.id)

private fun initialPlanCategoryId(current: PlanRow?, categories: List<JSONObject>, halaqaId: String): MutableState<String?> =
    mutableStateOf(current?.categoryId ?: categories.firstOrNull { it.optString("halaqa_id") == halaqaId }?.optString("id"))

private fun initialPlanType(current: PlanRow?): MutableState<String> =
    mutableStateOf(current?.type ?: "hifz")

private fun initialPlanTitle(current: PlanRow?): MutableState<String> =
    mutableStateOf(current?.title.orEmpty())

private fun initialPlanTarget(current: PlanRow?): MutableState<String> =
    mutableStateOf((current?.target ?: 20.0).toString())

private fun initialPlanStartDate(current: PlanRow?, today: LocalDate): MutableState<String> =
    mutableStateOf(current?.startDate ?: today.toString())

private fun initialPlanEndDate(current: PlanRow?, today: LocalDate): MutableState<String> =
    mutableStateOf(current?.endDate ?: today.plusMonths(1).toString())

private fun initialPlanWeekdays(current: PlanRow?): MutableState<Set<Int>> =
    mutableStateOf((current?.scheduleWeekdays?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf(0, 1, 2, 3, 4)))

private fun initialPlanCatchUp(current: PlanRow?): MutableState<Int?> =
    mutableStateOf(current?.catchUpWeekday)

private fun initialPlanRangeStartPage(current: PlanRow?): MutableState<Int?> =
    mutableStateOf(current?.rangeStartPage)

private fun initialPlanRangeEndPage(current: PlanRow?): MutableState<Int?> =
    mutableStateOf(current?.rangeEndPage)

private fun initialPlanRangeStartSurah(current: PlanRow?): MutableState<Int?> =
    mutableStateOf(current?.rangeStartSurah)

private fun initialPlanRangeStartAyah(current: PlanRow?): MutableState<Int?> =
    mutableStateOf(current?.rangeStartAyah)

private fun initialPlanRangeEndSurah(current: PlanRow?): MutableState<Int?> =
    mutableStateOf(current?.rangeEndSurah)

private fun initialPlanRangeEndAyah(current: PlanRow?): MutableState<Int?> =
    mutableStateOf(current?.rangeEndAyah)

private fun initialPlanReviewTier(current: PlanRow?): MutableState<String> =
    mutableStateOf(current?.reviewTier ?: "none")

private fun initialPlanNearPages(current: PlanRow?): MutableState<String> =
    mutableStateOf((current?.nearRevisionPages ?: 7.0).toString())

private fun initialPlanFarPages(current: PlanRow?): MutableState<String> =
    mutableStateOf((current?.farRevisionPages ?: 20.0).toString())

private fun initialPlanMinEvaluation(current: PlanRow?): MutableState<String> =
    mutableStateOf(current?.minimumEvaluation ?: "good")

private fun initialPlanDailyCap(current: PlanRow?): MutableState<String> =
    mutableStateOf((current?.maxDailyMultiplier ?: 1.5).toString())

private fun initialPlanMilestone(current: PlanRow?): MutableState<String> =
    mutableStateOf(current?.milestoneMode ?: "juz")

private fun initialPlanDelayDays(current: PlanRow?): MutableState<String> =
    mutableStateOf((current?.delayAlertDays ?: 3).toString())

private fun initialPlanQualityGate(current: PlanRow?): MutableState<Boolean> =
    mutableStateOf(current?.qualityGateEnabled ?: true)

private fun initialPlanAutoExtend(current: PlanRow?): MutableState<Boolean> =
    mutableStateOf(current?.autoExtend ?: true)

private fun initialPlanMilestoneApproval(current: PlanRow?): MutableState<Boolean> =
    mutableStateOf(current?.milestoneRequiresApproval ?: true)

private fun initialPlanHomePrepPush(current: PlanRow?): MutableState<Boolean> =
    mutableStateOf(current?.homePrepPush ?: true)

private fun initialPlanNote(current: PlanRow?): MutableState<String> =
    mutableStateOf(current?.note.orEmpty())

private fun defaultSmartPlanRouteDirection(planType: String): String = when (planType) {
    "hifz" -> "reverse_surahs_forward_ayahs"
    "tathbit", "review" -> "forward_surahs_forward_ayahs"
    else -> "forward_surahs_forward_ayahs"
}

private fun planTraversalDirection(routeDirection: String): QuranTraversalDirection = when (routeDirection) {
    "reverse_surahs_forward_ayahs" -> QuranTraversalDirection.REVERSE_SURAH_ORDER
    "forward_surahs_forward_ayahs" -> QuranTraversalDirection.FORWARD_QURAN
    else -> QuranTraversalDirection.AUTO_BY_ACTIVITY
}

private fun initialPlanRouteDirection(current: PlanRow?): MutableState<String> =
    mutableStateOf(current?.settings?.routeDirection ?: defaultSmartPlanRouteDirection(current?.type ?: "hifz"))

private fun editorPlanSettings(current: PlanRow?): PlanSettingsRow =
    current?.settings ?: PlanSettingsRow()


/** RC90: one small coloured number tile of the session summary row. */
@Composable
private fun SessionMiniTile(value: Int, label: String, kind: String, modifier: Modifier = Modifier) {
    val (bg, _) = com.mohammedsamir.halaqati.ui.theme.statusColors(kind)
    Column(modifier.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(bg))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
