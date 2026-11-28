@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.OneLineName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.mohammedsamir.halaqati.ui.components.AnimatedLinearProgress
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.PlanSnapshot
import com.mohammedsamir.halaqati.data.StudentGuardianRules
import com.mohammedsamir.halaqati.data.StudentInsightEngine
import com.mohammedsamir.halaqati.model.Student
import com.mohammedsamir.halaqati.model.PlanRow
import com.mohammedsamir.halaqati.model.SmartPlanPhaseRow
import com.mohammedsamir.halaqati.model.SmartPlanPhaseStateRow
import com.mohammedsamir.halaqati.plans.SmartPlanEngine
import com.mohammedsamir.halaqati.quran.RecitationParityRules
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.dashboard.DailyOperationStateEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

private data class StudentProfileActivity(val id: String, val name: String, val program: Boolean, val primary: Boolean)

@Composable
fun StudentProfileScreen(studentId: String, pending: Int, onBack: () -> Unit, navigate: (String) -> Unit) {
    var activities by remember(studentId) { mutableStateOf<List<StudentProfileActivity>>(emptyList()) }
    var selected by rememberSaveable(studentId) { mutableStateOf<String?>(null) }
    var loadingScopes by remember(studentId) { mutableStateOf(true) }
    var scopeError by remember(studentId) { mutableStateOf<String?>(null) }
    var refreshScopes by remember(studentId) { mutableIntStateOf(0) }
    val epoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(studentId, epoch, refreshScopes) {
        try {
            val available = withContext(Dispatchers.IO) {
                val repo = AppGraph.repo
                val online = repo.online()
                val st = repo.students(forceRefresh = online).firstOrNull { it.id == studentId }
                    ?: error("ملف الطالب غير متاح")
                val ids = repo.activeSecondaryHalaqaIds(studentId, forceRefresh = online) + listOfNotNull(st.halaqaId)
                val rows = repo.table("halaqas", "select=id,name,halaqa_type,active&order=name.asc&limit=400", forceRefresh = online)
                (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.filter { it.optString("id") in ids }
                    .map { row -> StudentProfileActivity(row.optString("id"), row.optString("name"),
                        com.mohammedsamir.halaqati.plans.WorkModeRules.isProgramType(row.optString("halaqa_type")), row.optString("id") == st.halaqaId) }
                    .sortedByDescending { it.primary }
            }
            activities = available
            selected = selected?.takeIf { id -> available.any { it.id == id } }
                ?: available.singleOrNull()?.id ?: available.firstOrNull { it.primary }?.id
            scopeError = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { activities = emptyList(); selected = null; scopeError = e.message ?: "تعذر تحميل العضويات" }
        finally { loadingScopes = false }
    }
    val activity = activities.firstOrNull { it.id == selected }
    if (activity == null) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("رجوع") }
            if (loadingScopes) LoadingSkeleton(rows = 3)
            else {
                scopeError?.let { StateNotice(it,"warning") }
                if (activities.isEmpty()) EmptyState("لا يوجد نشاط متاح لهذا الطالب")
                else {
                    Text("اختر الحلقة أو البرنامج", style = MaterialTheme.typography.titleMedium)
                    activities.forEach { a -> OutlinedButton(onClick = { selected = a.id }, modifier = Modifier.fillMaxWidth()) { Text(a.name) } }
                }
                TextButton(onClick = { refreshScopes++ }) { Text("تحديث العضويات") }
            }
        }
        return
    }
    key(studentId, activity.id) {
        ScopedStudentProfile(studentId, pending, onBack, navigate, activity, activities, { selected = it })
    }
}

@Composable
private fun ScopedStudentProfile(
    studentId: String,
    pending: Int,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    activity: StudentProfileActivity,
    activities: List<StudentProfileActivity>,
    onSelect: (String) -> Unit,
) {
    var profileTab by rememberSaveable(studentId) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var student by remember(studentId) { mutableStateOf<Student?>(null) }
    var snapshots by remember(studentId) { mutableStateOf<List<PlanSnapshot>>(emptyList()) }
    var phases by remember(studentId) { mutableStateOf<List<SmartPlanPhaseRow>>(emptyList()) }
    var phaseStates by remember(studentId) { mutableStateOf<List<SmartPlanPhaseStateRow>>(emptyList()) }
    var recitations by remember(studentId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var attendance by remember(studentId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var weekSessions by remember(studentId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var guardians by remember(studentId) { mutableStateOf<List<StudentGuardianRules.GuardianOption>>(emptyList()) }
    var monthlyComment by remember(studentId) { mutableStateOf<JSONObject?>(null) }
    var editFollowUpNote by remember(studentId) { mutableStateOf(false) }
    var loading by remember(studentId) { mutableStateOf(true) }
    // Header shown at once from the copy already on the device, while the full profile loads.
    var earlyStudent by remember(studentId) { mutableStateOf<Student?>(null) }
    var refreshing by remember(studentId) { mutableStateOf(false) }
    var error by remember(studentId) { mutableStateOf<String?>(null) }

    suspend fun reload(forceRequested: Boolean) {
        // RC79: opening the profile reads the copy stored on the device. The server is asked once per cycle
        // (after 20:00 Gaza time, or after this device saved new session data) or when the user pulls to refresh.
        val gateKey = "student:$studentId:${activity.id}"
        val force = forceRequested || com.mohammedsamir.halaqati.data.RefreshGate.due(gateKey)
        if (forceRequested) refreshing = true else loading = student == null
        try {
            val result = withContext(Dispatchers.IO) {
                val today = LocalDate.now()
                val monthStartDate = YearMonth.from(today).atDay(1)
                val currentWeekStart = today.minusDays(SmartPlanEngine.weekday(today).toLong())
                val historyFrom = minOf(monthStartDate, currentWeekStart.minusWeeks(3)).toString()
                val monthStart = monthStartDate.toString()
                val students = AppGraph.repo.students(forceRefresh = force)
                val st = students.firstOrNull { it.id == studentId } ?: error("لم يتم العثور على الطالب")
                val allPlans = if (activity.program) emptyList() else AppGraph.repo.plans(forceRefresh = force)
                val scopePriority = mapOf("halaqa" to 1, "category" to 2, "student" to 3)
                val currentPlan = allPlans.filter { p ->
                    val ps = runCatching { LocalDate.parse(p.startDate) }.getOrNull()
                    val pe = runCatching { LocalDate.parse(p.endDate) }.getOrNull()
                    ps != null && pe != null && ps <= today && pe >= monthStartDate && p.halaqaId == activity.id &&
                        when (p.scopeType) {
                            "student" -> p.studentId == st.id
                            "category" -> !p.categoryId.isNullOrBlank() && p.categoryId == st.categoryId
                            else -> true
                        }
                }.maxByOrNull { scopePriority[it.scopeType] ?: 0 }
                currentPlan?.let { runCatching { AppGraph.repo.reconcilePlanPhaseLifecycle(it.id, today) } }
                val studentSnapshots = if (activity.program) emptyList() else AppGraph.repo.planSnapshots(today, forceRefresh = force).filter { it.student.id == studentId && it.plan.halaqaId == activity.id }
                val studentPhases = currentPlan?.let { AppGraph.repo.planPhases(it.id, forceRefresh = force) }.orEmpty()
                val studentPhaseStates = if (activity.program) emptyList() else AppGraph.repo.planPhaseStates(studentId, forceRefresh = force)
                    .filter { state -> studentPhases.any { it.id == state.phaseId } }
                val recitationArray = AppGraph.repo.table(
                    "recitation_entries",
                    "select=id,phase_id,activity_type,start_surah,start_ayah,end_surah,end_ayah,start_page,end_page,pages_count,evaluation,mistakes,prompts,safety_percentage,created_at," +
                        "sessions!inner(session_date,session_type,title,halaqa_id)" +
                        "&student_id=eq.$studentId&sessions.halaqa_id=eq.${activity.id}&sessions.session_date=gte.$historyFrom&sessions.session_date=lte.${today}" +
                        "&order=created_at.desc&limit=1000",
                    forceRefresh = force, preferCache = !force,
                )
                val attendanceArray = AppGraph.repo.table(
                    "attendance_records",
                    "select=status,arrival_minutes_late,excuse_reason,recorded_at,sessions!inner(session_date,halaqa_id)" +
                        "&student_id=eq.$studentId&sessions.halaqa_id=eq.${activity.id}&sessions.session_date=gte.$historyFrom&sessions.session_date=lte.${today}" +
                        "&order=recorded_at.desc&limit=1000",
                    forceRefresh = force, preferCache = !force,
                )
                val currentWeekEnd = currentWeekStart.plusDays(6)
                val sessionsArray = AppGraph.repo.table(
                    "sessions",
                    "select=id,session_date,session_type,title,status&halaqa_id=eq.${activity.id}" +
                        "&session_date=gte.${currentWeekStart}&session_date=lte.${currentWeekEnd}&order=session_date.asc&limit=100",
                    forceRefresh = force,
                )
                val commentArray = if (!activity.primary) org.json.JSONArray() else AppGraph.repo.table(
                    "monthly_comments",
                    "select=id,student_id,month_start,teacher_comment,supervisor_comment,overall_rating,updated_at" +
                        "&student_id=eq.$studentId&month_start=eq.$monthStart&limit=1",
                    forceRefresh = force,
                )
                val guardianOptions = if (activity.primary) AppGraph.repo.guardianLinks(studentId, forceRefresh = force) else emptyList()
                val pendingComment = AppGraph.repo.queueSnapshot(500)
                    .filter { it.dedupe == "student-followup:$studentId:$monthStart" }
                    .maxByOrNull { it.created }
                    ?.body
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ProfilePayload(
                    st,
                    studentSnapshots,
                    studentPhases,
                    studentPhaseStates,
                    (0 until recitationArray.length()).mapNotNull { recitationArray.optJSONObject(it) },
                    (0 until attendanceArray.length()).mapNotNull { attendanceArray.optJSONObject(it) },
                    (0 until sessionsArray.length()).mapNotNull { sessionsArray.optJSONObject(it) },
                    guardianOptions,
                    if (activity.primary) pendingComment ?: commentArray.optJSONObject(0) else null,
                )
            }
            student = result.student
            snapshots = result.snapshots
            phases = result.phases
            phaseStates = result.phaseStates
            recitations = result.recitations
            attendance = result.attendance
            weekSessions = result.weekSessions
            guardians = result.guardians
            monthlyComment = result.monthlyComment
            if (force) com.mohammedsamir.halaqati.data.RefreshGate.mark(gateKey)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "تعذر تحميل ملف الطالب"
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(studentId) {
        earlyStudent = withContext(Dispatchers.IO) {
            runCatching { AppGraph.repo.store.load()?.userId?.let { AppGraph.repo.cachedStudents(it) }?.firstOrNull { it.id == studentId } }.getOrNull()
        }
    }
    LaunchedEffect(studentId) { reload(false) }
    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { if (syncEpoch > 0L) reload(false) }

    val primarySnapshot = snapshots.firstOrNull { it.plan.type in setOf("hifz", "tathbit") } ?: snapshots.firstOrNull()
    val monthStartForMetrics = YearMonth.from(LocalDate.now()).atDay(1)
    val monthRecitations = recitations.filter { row ->
        row.optJSONObject("sessions")?.optString("session_date")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { !it.isBefore(monthStartForMetrics) } == true
    }
    val qualityRows = monthRecitations.filter {
        it.has("safety_percentage") && !it.isNull("safety_percentage") &&
            !com.mohammedsamir.halaqati.plans.RecitationQualityRules.isPlaceholder(it.optString("evaluation"), it.optDouble("safety_percentage"), it.optDouble("pages_count", 0.0))
    }
    val averageSafety = qualityRows.takeIf { it.isNotEmpty() }?.map { it.optDouble("safety_percentage") }?.average()
    val mistakes = monthRecitations.sumOf { it.optInt("mistakes", 0) }
    val prompts = monthRecitations.sumOf { it.optInt("prompts", 0) }
    // RC79: the loaded attendance reaches back to the previous weeks for the timeline; the monthly counts must only use this month.
    val monthAttendance = attendance.filter { row ->
        row.optJSONObject("sessions")?.optString("session_date")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { !it.isBefore(monthStartForMetrics) } == true
    }
    val present = monthAttendance.count { it.optString("status") == "present" }
    val late = monthAttendance.count { it.optString("status") == "late" }
    val absent = monthAttendance.count { it.optString("status") == "absent" }
    val excused = monthAttendance.count { it.optString("status") == "excused" }
    val insight = StudentInsightEngine.build(
        requiredToDate = primarySnapshot?.progress?.plannedByToday ?: 0.0,
        completed = primarySnapshot?.progress?.completed ?: 0.0,
        averageSafety = averageSafety,
        mistakes = mistakes,
        prompts = prompts,
        present = present,
        absent = absent,
        excused = excused,
        late = late,
        missedActivityDays = primarySnapshot?.progress?.consecutiveMissed ?: 0,
    )

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Column {
                        Text(student?.fullName ?: earlyStudent?.fullName ?: "ملف الطالب", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (pending > 0) Text("$pending عملية بانتظار المزامنة", style = MaterialTheme.typography.labelSmall, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
                actions = {
                    IconButton(onClick = { scope.launch { reload(true) } }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Refresh, contentDescription = "تحديث") }
                },
            )
            com.mohammedsamir.halaqati.ui.components.LatticeStrip()
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { reload(true) } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (loading && student == null) {
                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    earlyStudent?.let { es ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            com.mohammedsamir.halaqati.ui.components.StudentAvatar(es.photoPath, es.fullName, 72.dp)
                            Text(es.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        }
                    }
                    LoadingSkeleton(rows = if (earlyStudent != null) 3 else 6)
                }
            } else {
                LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    error?.let { item { StateNotice(it, "warning") } }
                    student?.let { st ->
                        item { StudentHero(st, insight, primarySnapshot, activity.program, activity.name) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("النشاط المعروض", style = MaterialTheme.typography.labelMedium)
                                com.mohammedsamir.halaqati.ui.components.ResponsiveChipGrid(items = activities, minItemWidth = 140.dp, maxColumns = 2) { a, itemModifier ->
                                    com.mohammedsamir.halaqati.ui.components.ProFilterChip(a.id == activity.id, { onSelect(a.id) }, a.name, itemModifier.fillMaxWidth(), showCheckIcon = true)
                                }
                            }
                        }
                        item {
                            val tabs = if (activity.program) listOf(0 to "نظرة عامة", 2 to "السجل", 3 to "العضوية") else listOf(0 to "نظرة عامة", 1 to "الخطة والتقدم", 2 to "السجل", 3 to "البيانات")
                            ScrollableTabRow(
                                selectedTabIndex = tabs.indexOfFirst { it.first == profileTab }.coerceAtLeast(0),
                                edgePadding = 0.dp,
                                containerColor = MaterialTheme.colorScheme.background,
                                divider = {},
                            ) {
                                tabs.forEach { (index, label) ->
                                    Tab(selected = profileTab == index, onClick = { profileTab = index },
                                        text = { Text(label, style = MaterialTheme.typography.labelLarge) })
                                }
                            }
                        }
                        when (profileTab) {
                            0 -> {
                                if (!activity.program) item { ProgressOverview(primarySnapshot, AppGraph.repo.isSmartPlanEligible(activity.id)) }
                                item { QualityAttendanceCard(insight, activity.program) }
                                if (!activity.program) item { FollowUpCard(insight) }
                                item { LastMeaningfulActivityCard(recitations, attendance, activity.program) }
                            }
                            1 -> if (!activity.program) {
                                item { MonthlyPhaseSummaryCard(phases, phaseStates, snapshots, recitations) }
                                item { ProgressOverview(primarySnapshot, AppGraph.repo.isSmartPlanEligible(activity.id)) }
                                item { WeeklyTimeline(phases, phaseStates, recitations, attendance, weekSessions) }
                                item { FourWeekProgress(primarySnapshot, recitations) }
                                item {
                                    Button(onClick = { navigate("plans") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                        Icon(Icons.Default.AutoGraph, null, Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp)); Text("إدارة الخطة الذكية")
                                    }
                                }
                            }
                            2 -> {
                                item { com.mohammedsamir.halaqati.ui.components.NourSectionTitle(if (activity.program) "آخر أنشطة البرنامج" else "آخر التسميعات") }
                                if (recitations.isEmpty()) item { EmptyState(if (activity.program) "لا توجد أنشطة مسجلة في هذا النطاق" else "لا توجد تسميعات مسجلة هذا الشهر") }
                                items(recitations.take(8), key = { it.optString("id") }) { row -> RecitationCompactRow(row) }
                                item { QualityAttendanceCard(insight, activity.program) }
                            }
                            3 -> if (activity.primary) {
                                item { GuardianCard(guardians, st) }
                                item { DataHealthCard(st, guardians, primarySnapshot, attendance, recitations) }
                                item { FollowUpNoteCard(comment = monthlyComment?.optString("teacher_comment").orEmpty(), onEdit = { editFollowUpNote = true }) }
                                item {
                                    OutlinedButton(onClick = { navigate("students:${st.id}") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp)); Text("إدارة بيانات الطالب")
                                    }
                                }
                            } else {
                                item { StateNotice("هذا نشاط إضافي. تُدار بيانات الطالب الشخصية وملاحظته العامة من ملف النشاط الأساسي.", "info") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editFollowUpNote && activity.primary) {
        FollowUpNoteDialog(
            initial = monthlyComment?.optString("teacher_comment").orEmpty(),
            close = { editFollowUpNote = false },
            save = { value ->
                editFollowUpNote = false
                scope.launch {
                    try {
                        val saved = withContext(Dispatchers.IO) {
                            val monthStart = YearMonth.now().atDay(1).toString()
                            val id = monthlyComment?.optString("id")?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
                            AppGraph.repo.write(
                                table = "monthly_comments",
                                obj = JSONObject()
                                    .put("id", id)
                                    .put("student_id", studentId)
                                    .put("month_start", monthStart)
                                    .put("teacher_comment", value.trim()),
                                onConflict = "student_id,month_start",
                                dedupe = "student-followup:$studentId:$monthStart",
                            )
                            JSONObject()
                                .put("id", id)
                                .put("student_id", studentId)
                                .put("month_start", monthStart)
                                .put("teacher_comment", value.trim())
                        }
                        monthlyComment = saved
                        error = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "تعذر حفظ ملاحظة المتابعة"
                    }
                }
            },
        )
    }
}

@Composable
private fun StudentHero(student: Student, insight: StudentInsightEngine.State, snapshot: PlanSnapshot?, program: Boolean, activityName: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.mohammedsamir.halaqati.ui.components.StudentAvatar(student.photoPath, student.fullName, 72.dp)
                Column(Modifier.weight(1f)) {
                    Text(student.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(activityName, if (program) "برنامج تعليمي" else if (student.track == "tathbit") "التثبيت" else "الحفظ العام", studentStatusLabel(student.status)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!program) FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(Icons.Default.Star, insight.performanceLevel.label)
                val delay = snapshot?.progress?.behindBy ?: 0.0
                val planLabel = when {
                    snapshot == null -> if (AppGraph.repo.isSmartPlanEligible(student.halaqaId)) "لا توجد خطة" else "برنامج بلا خطة ذكية"
                    snapshot.coreState.completedEarly -> "أتم الخطة"
                    snapshot.progress.aheadBy > 0.01 -> "متقدم +${n(snapshot.progress.aheadBy)} ص"
                    delay > 0.01 -> "متأخر ${n(delay)} ص"
                    else -> "على الخطة"
                }
                StatusBadge(Icons.Default.Timeline, planLabel)
            }
        }
    }
}


@Composable
private fun StatusBadge(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ProgressOverview(snapshot: PlanSnapshot?, eligible: Boolean = true) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("الخطة الشهرية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (snapshot == null) {
                Text(if (eligible) "لا توجد خطة فعالة لهذا الطالب" else "هذا الطالب في برنامج غير حلقي، ولا تُطبَّق عليه الخطط الذكية", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val values = listOf(
                "الهدف" to snapshot.plan.target,
                "المطلوب" to snapshot.progress.plannedByToday,
                "المنجز" to snapshot.progress.completed,
                (if (snapshot.progress.aheadBy > 0.01) "الزائد" else "المتبقي") to (if (snapshot.progress.aheadBy > 0.01) snapshot.progress.aheadBy else snapshot.progress.remaining),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                values.forEach { (label, value) -> MiniMetric(label, "${n(value)} ص", Modifier.weight(1f)) }
            }
            val fraction = if (snapshot.plan.target <= 0.0) 0f else (snapshot.progress.completed / snapshot.plan.target).coerceIn(0.0, 1.0).toFloat()
            AnimatedLinearProgress(progress = fraction, modifier = Modifier.fillMaxWidth().height(6.dp))
            snapshot.progress.originalDaily.takeIf { it > 0.001 && snapshot.progress.plannedByToday > 0.0 }?.let { daily ->
                val totalDays = snapshot.plan.target / daily
                val elapsed = snapshot.progress.plannedByToday / daily
                Text(
                    "المطلوب = ${n(daily)} ص يوميًا × ${n(elapsed)} يوم دوام مضى (من ${n(totalDays)} يوم في الخطة)" +
                        if (snapshot.progress.aheadBy > 0.01) "، والزائد = المنجز − المطلوب" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            snapshot.progress.assignment.takeIf { it.pages > 0.0 }?.let { a ->
                Text("المقرر القادم: ${assignmentRangeLabel(a)} · ${n(a.pages)} ص", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeeklyTimeline(
    phases: List<SmartPlanPhaseRow>,
    states: List<SmartPlanPhaseStateRow>,
    recitations: List<JSONObject>,
    attendance: List<JSONObject>,
    sessions: List<JSONObject>,
) {
    val today = LocalDate.now()
    val weekStart = today.minusDays(SmartPlanEngine.weekday(today).toLong())
    val recitationByDate = recitations.groupBy { it.optJSONObject("sessions")?.optString("session_date").orEmpty() }
    val attendanceByDate = attendance.groupBy { it.optJSONObject("sessions")?.optString("session_date").orEmpty() }
    val sessionByDate = sessions.groupBy { it.optString("session_date") }
    val stateByPhase = states.associateBy { it.phaseId }

    fun effectivePhase(date: LocalDate): SmartPlanPhaseRow? = phases
        .asSequence()
        .filter { it.status != "archived" }
        .filter { stateByPhase[it.id]?.status != "skipped" }
        .filter { phase ->
            val state = stateByPhase[phase.id]
            val start = state?.startDateOverride?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: runCatching { LocalDate.parse(phase.startDate) }.getOrNull()
            val end = state?.endDateOverride?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: runCatching { LocalDate.parse(phase.endDate) }.getOrNull()
            start != null && end != null && date in start..end
        }
        .sortedBy { it.phaseOrder }
        .firstOrNull()

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("هذا الأسبوع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("الإجازة لا تُستنتج من غياب التسميع. السبت إجازة ثابتة؛ وبقية الأيام بلا جلسة تظهر «لا توجد جلسة» بدل «إجازة».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (0..6).forEach { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val phase = effectivePhase(date)
                val scheduled = phase != null && (date.dayOfWeek.value % 7) in phase.scheduleWeekdays
                val sessionRows = sessionByDate[date.toString()].orEmpty()
                val rows = recitationByDate[date.toString()].orEmpty()
                val attendanceRows = attendanceByDate[date.toString()].orEmpty()
                val completed = rows.sumOf { it.optDouble("pages_count", 0.0).coerceAtLeast(0.0) }
                val activity = when {
                    rows.isNotEmpty() -> activityLabel(rows.first().optString("activity_type"))
                    phase?.phaseType == "sard" && scheduled -> "سرد"
                    phase?.phaseType == "hifz" && scheduled -> "حفظ"
                    sessionRows.isNotEmpty() -> activityLabel(sessionRows.first().optString("session_type"))
                    else -> "—"
                }
                val attendanceStatus = attendanceRows.firstOrNull()?.optString("status")
                val status = when {
                    date > today && (scheduled || sessionRows.isNotEmpty()) -> "قادم"
                    date > today -> "غير مجدول"
                    rows.isNotEmpty() -> "تم التسميع"
                    attendanceStatus == "absent" -> "غائب"
                    attendanceStatus == "excused" -> "بعذر"
                    attendanceStatus == "late" -> "حاضر متأخر ولم يسمّع"
                    attendanceStatus == "present" -> "حاضر لم يسمّع"
                    sessionRows.isNotEmpty() -> "لا يوجد سجل حضور/تسميع"
                    scheduled -> "لا توجد جلسة"
                    (date.dayOfWeek.value % 7) == 6 -> "إجازة" // السبت هو يوم الإجازة الثابت في الحلقة
                    else -> "لا توجد جلسة"
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel(date), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                    Text(activity, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (completed <= 0.0) "—" else "${n(completed)} ص", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(58.dp))
                    Text(status, style = MaterialTheme.typography.labelSmall, modifier = Modifier.widthIn(min = 88.dp), color = when {
                        status == "تم التسميع" -> MaterialTheme.colorScheme.primary
                        status in setOf("غائب", "لا توجد جلسة") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    })
                }
                if (offset < 6) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }
        }
    }
}

@Composable
private fun FourWeekProgress(snapshot: PlanSnapshot?, recitations: List<JSONObject>) {
    if (snapshot == null) return
    val today = LocalDate.now()
    val currentStart = today.minusDays(SmartPlanEngine.weekday(today).toLong())
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("آخر 4 أسابيع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            (3 downTo 0).forEach { back ->
                val start = currentStart.minusWeeks(back.toLong())
                val end = start.plusDays(6)
                val completed = recitations.filter { row ->
                    val d = row.optJSONObject("sessions")?.optString("session_date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    d != null && d in start..end
                }.sumOf { it.optDouble("pages_count", 0.0) }
                val target = snapshot.coreState.weekly.target.takeIf { it > 0.0 } ?: snapshot.progress.weekly.target
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (back == 0) "الحالي" else "قبل $back", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(58.dp))
                    LinearProgressIndicator(
                        drawStopIndicator = {}, progress = { if (target <= 0.0) 0f else (completed / target).coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.weight(1f).height(6.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${n(completed)} / ${n(target)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
                }
            }
        }
    }
}

@Composable
private fun QualityAttendanceCard(insight: StudentInsightEngine.State, program: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ElevatedCard(Modifier.weight(1f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Default.Verified, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (program) "جودة التلاوة" else "جودة التسميع", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(insight.averageSafety?.let { "${n(it)}%" } ?: "—", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily))
                Text("${insight.mistakes} خطأ · ${insight.prompts} تلقين", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ElevatedCard(Modifier.weight(1f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Default.EventAvailable, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("الحضور", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("${insight.present + insight.late} حاضر", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily))
                Text("${insight.absent} غائب · ${insight.excused} بعذر · ${insight.late} متأخر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


@Composable
private fun GuardianCard(guardians: List<StudentGuardianRules.GuardianOption>, student: Student) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SupervisorAccount, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("أولياء الأمور المرتبطون (${guardians.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            if (guardians.isEmpty()) Text("لا توجد ارتباطات معروضة", style = MaterialTheme.typography.bodyMedium)
            guardians.forEach { guardian ->
                Text(guardian.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val details = listOfNotNull(guardian.phone, guardian.email).joinToString(" · ")
                if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val contact = listOfNotNull(student.guardianPhone, student.guardianEmail).joinToString(" · ")
            if (contact.isNotBlank()) Text("بيانات التواصل المسجلة: $contact", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FollowUpCard(insight: StudentInsightEngine.State) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("لماذا يحتاج متابعة؟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (insight.followUpReasons.isEmpty()) {
                Text("لا توجد أسباب متابعة حرجة حاليًا", color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
            } else insight.followUpReasons.forEach { reason ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.WarningAmber, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text(reason.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun FollowUpNoteCard(comment: String, onEdit: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.StickyNote2, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("ملاحظة متابعة الشهر", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(comment.ifBlank { "لا توجد ملاحظة متابعة" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onEdit) { Text(if (comment.isBlank()) "إضافة" else "تعديل") }
        }
    }
}

@Composable
private fun FollowUpNoteDialog(initial: String, close: () -> Unit, save: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    QuickActionSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ملاحظة متابعة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                label = { Text("ملاحظة المحفّظ لهذا الشهر") })
            Button(onClick = { save(value) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("حفظ الملاحظة") }
            TextButton(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("إلغاء") }
        }
    }
}

@Composable
private fun LastMeaningfulActivityCard(recitations: List<JSONObject>, attendance: List<JSONObject>, program: Boolean = false) {
    val lastRecitation = recitations.firstOrNull()?.optJSONObject("sessions")?.optString("session_date").orEmpty()
    val lastAttendance = attendance.firstOrNull()?.optJSONObject("sessions")?.optString("session_date").orEmpty()
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("آخر نشاط فعلي", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("${if (program) "آخر نشاط" else "آخر تسميع"}: ${lastRecitation.ifBlank { "لا يوجد" }}", style = MaterialTheme.typography.bodySmall)
            Text("آخر حضور: ${lastAttendance.ifBlank { "لا يوجد" }}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MonthlyPhaseSummaryCard(
    phases: List<SmartPlanPhaseRow>,
    states: List<SmartPlanPhaseStateRow>,
    snapshots: List<PlanSnapshot>,
    recitations: List<JSONObject>,
) {
    val ordered = phases.filter { it.status != "archived" }.sortedBy { it.phaseOrder }
    if (ordered.isEmpty()) return
    val stateByPhase = states.associateBy { it.phaseId }

    fun targetFor(phase: SmartPlanPhaseRow): Double? {
        val state = stateByPhase[phase.id]
        state?.frozenTargetPages?.let { return it }
        val base = state?.targetPagesOverride ?: phase.targetPages
            ?: if (phase.goalMode == "juz") phase.targetUnits?.times(20.0) else null
        return base?.plus(state?.carryShortfallPages ?: 0.0)
    }
    fun recitationPagesFor(phase: SmartPlanPhaseRow): Double {
        val start = runCatching { LocalDate.parse(phase.startDate) }.getOrNull() ?: return 0.0
        val end = runCatching { LocalDate.parse(phase.endDate) }.getOrNull() ?: return 0.0
        return recitations.filter { row ->
            val date = row.optJSONObject("sessions")?.optString("session_date")
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val tagged = row.optString("phase_id")
            val activity = row.optString("activity_type")
            val typeMatches = if (phase.phaseType == "sard") activity == "sard" else activity in setOf("new_hifz", "hifz")
            typeMatches && (tagged == phase.id || (tagged.isBlank() && date != null && date >= start && date <= end))
        }.sumOf { it.optDouble("pages_count", 0.0).coerceAtLeast(0.0) }
    }
    fun completedFor(phase: SmartPlanPhaseRow): Double {
        val state = stateByPhase[phase.id]
        state?.frozenCompletedPages?.let { return it }
        snapshots.firstOrNull { it.phase?.id == phase.id }?.phaseProgress?.completedPages?.let { return it }
        return recitationPagesFor(phase)
    }
    fun statusFor(phase: SmartPlanPhaseRow): String {
        val state = stateByPhase[phase.id]
        return when (state?.status) {
            "completed" -> "مكتملة"
            "active" -> "نشطة"
            "skipped" -> "مستثناة"
            else -> {
                val today = LocalDate.now()
                val start = runCatching { LocalDate.parse(state?.startDateOverride ?: phase.startDate) }.getOrNull()
                val end = runCatching { LocalDate.parse(state?.endDateOverride ?: phase.endDate) }.getOrNull()
                when {
                    snapshots.any { it.phase?.id == phase.id && start != null && end != null && today in start..end } -> "نشطة"
                    end != null && today > end -> "منتهية"
                    start != null && today < start -> "مجدولة"
                    else -> "غير نشطة"
                }
            }
        }
    }

    val hifz = ordered.firstOrNull { it.phaseType == "hifz" }
    val sard = ordered.firstOrNull { it.phaseType == "sard" }
    val lastSard = recitations.filter { it.optString("activity_type") == "sard" }.maxWithOrNull(
        compareBy<JSONObject> { row -> row.optJSONObject("sessions")?.optString("session_date").orEmpty() }
            .thenBy { it.optString("created_at") }
    )

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Text("مراحل الشهر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            hifz?.let { phase ->
                val target = targetFor(phase)
                val completed = completedFor(phase)
                PhaseProfileLine(
                    icon = Icons.Default.MenuBook,
                    title = "الحفظ · ${statusFor(phase)}",
                    value = if (target != null) "${n(completed)} / ${n(target)} ص · ${n((completed / target.coerceAtLeast(.001) * 100.0))}%" else "${n(completed)} ص",
                )
            }
            sard?.let { phase ->
                val target = targetFor(phase)
                val completed = completedFor(phase)
                PhaseProfileLine(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "السرد · ${statusFor(phase)}",
                    value = if (target != null) "${n(completed)} / ${n(target)} ص · ${n((completed / target.coerceAtLeast(.001) * 100.0))}%" else "سرد مفتوح · ${n(completed)} ص",
                )
                lastSard?.let { row ->
                    val range = RecitationParityRules.quranRangeLabel(
                        nullableInt(row, "start_surah"), nullableInt(row, "start_ayah"),
                        nullableInt(row, "end_surah"), nullableInt(row, "end_ayah"),
                        nullableInt(row, "start_page"), nullableInt(row, "end_page"),
                    )
                    val date = row.optJSONObject("sessions")?.optString("session_date").orEmpty()
                    Text("آخر سرد: $range${date.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PhaseProfileLine(icon: ImageVector, title: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DataHealthCard(student: Student, guardians: List<StudentGuardianRules.GuardianOption>, snapshot: PlanSnapshot?, attendance: List<JSONObject>, recitations: List<JSONObject>) {
    val issues = buildList {
        if (guardians.isEmpty()) add("لا توجد ارتباطات معروضة لأولياء الأمور")
        if (snapshot == null && student.status == "active" && AppGraph.repo.isSmartPlanEligible(student.halaqaId)) add("لا توجد خطة ذكية فعالة")
        if (attendance.isEmpty()) add("لا توجد بيانات حضور هذا الشهر")
        if (recitations.isEmpty()) add("لا توجد تسميعات هذا الشهر")
    }
    if (issues.isEmpty()) return
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("صحة البيانات", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            issues.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun RecitationCompactRow(row: JSONObject) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.MenuBook, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                val sessionDate = row.optJSONObject("sessions")?.optString("session_date").orEmpty()
                Text("$sessionDate · ${activityLabel(row.optString("activity_type"))}", style = MaterialTheme.typography.labelLarge)
                Text(
                    RecitationParityRules.quranRangeLabel(
                        nullableInt(row, "start_surah"), nullableInt(row, "start_ayah"), nullableInt(row, "end_surah"), nullableInt(row, "end_ayah"), nullableInt(row, "start_page"), nullableInt(row, "end_page")
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${n(row.optDouble("pages_count", 0.0))} ص", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (row.has("safety_percentage") && !row.isNull("safety_percentage")) Text("${n(row.optDouble("safety_percentage"))}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private data class ProfilePayload(
    val student: Student,
    val snapshots: List<PlanSnapshot>,
    val phases: List<SmartPlanPhaseRow>,
    val phaseStates: List<SmartPlanPhaseStateRow>,
    val recitations: List<JSONObject>,
    val attendance: List<JSONObject>,
    val weekSessions: List<JSONObject>,
    val guardians: List<StudentGuardianRules.GuardianOption>,
    val monthlyComment: JSONObject?,
)


private fun isPlanScheduledOn(plan: PlanRow, date: LocalDate): Boolean {
    val start = runCatching { LocalDate.parse(plan.startDate) }.getOrNull() ?: return false
    val end = runCatching { LocalDate.parse(plan.endDate) }.getOrNull() ?: return false
    val pauses = plan.settings.pauseRanges.mapNotNull { (fromText, toText) ->
        val from = runCatching { LocalDate.parse(fromText) }.getOrNull() ?: return@mapNotNull null
        val to = runCatching { LocalDate.parse(toText) }.getOrNull() ?: return@mapNotNull null
        from to to
    }
    val intensiveFrom = plan.settings.intensiveWeekFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val intensiveTo = plan.settings.intensiveWeekTo?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val intensiveRange = if (intensiveFrom != null && intensiveTo != null) intensiveFrom to intensiveTo else null
    return DailyOperationStateEngine.isScheduledDay(
        date = date,
        start = start,
        end = end,
        scheduleWeekdays = plan.scheduleWeekdays.toSet(),
        catchUpWeekday = plan.catchUpWeekday,
        pauseRanges = pauses,
        intensiveRange = intensiveRange,
        intensiveWeekdays = plan.settings.intensiveWeekdays.toSet(),
        intensiveEnabled = plan.type == "sard",
    )
}

private fun nullableInt(row: JSONObject, key: String): Int? = if (!row.has(key) || row.isNull(key)) null else row.optInt(key)
private fun assignmentRangeLabel(a: SmartPlanEngine.Assignment): String = when {
    a.startPage != null -> com.mohammedsamir.halaqati.quran.RecitationParityRules.pageRangeQuranLabel(
        minOf(a.startPage, a.endPage ?: a.startPage), maxOf(a.startPage, a.endPage ?: a.startPage),
    )
    else -> "المقرر الذكي"
}
private fun n(v: Double): String = if (kotlin.math.abs(v - v.toInt()) < 0.005) v.toInt().toString() else "%.1f".format(java.util.Locale.US, v)
private fun studentStatusLabel(value: String) = when(value) { "active" -> "نشط"; "inactive" -> "غير نشط"; "suspended" -> "موقوف"; "withdrawn" -> "منسحب"; else -> value }
private fun planActivityLabel(value: String) = when(value) { "hifz" -> "حفظ"; "sard" -> "سرد"; "review" -> "مراجعة"; "tathbit" -> "تثبيت"; else -> value }
private fun activityLabel(value: String) = when(value) { "new_hifz" -> "حفظ"; "sard" -> "سرد"; "review_near", "review_far" -> "مراجعة"; "test" -> "اختبار"; else -> value }
private fun dayLabel(date: LocalDate) = when(date.dayOfWeek.value) { 7 -> "الأحد"; 1 -> "الإثنين"; 2 -> "الثلاثاء"; 3 -> "الأربعاء"; 4 -> "الخميس"; 5 -> "الجمعة"; else -> "السبت" }
