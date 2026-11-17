@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import com.mohammedsamir.halaqati.ui.components.OneLineName
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.PlanSnapshot
import com.mohammedsamir.halaqati.data.PhaseTransitionRequest
import com.mohammedsamir.halaqati.model.PlanRow
import com.mohammedsamir.halaqati.model.SmartPlanPhaseDraft
import com.mohammedsamir.halaqati.model.SmartPlanPhaseRow
import com.mohammedsamir.halaqati.model.SmartPlanPhaseStateRow
import com.mohammedsamir.halaqati.model.SmartPlanPhaseTemplateRow
import com.mohammedsamir.halaqati.plans.SmartPlanActivityRules
import com.mohammedsamir.halaqati.plans.SmartPlanEngine
import com.mohammedsamir.halaqati.plans.SmartPlanPhaseEngine
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.HalaqatiLazySelectField
import com.mohammedsamir.halaqati.ui.components.ResponsiveChipGrid
import com.mohammedsamir.halaqati.quran.QuranCore
import com.mohammedsamir.halaqati.quran.QuranLocation
import com.mohammedsamir.halaqati.quran.QuranMetadata
import com.mohammedsamir.halaqati.quran.QuranRangeRequest
import com.mohammedsamir.halaqati.quran.QuranSelection
import com.mohammedsamir.halaqati.quran.QuranTraversalDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.max

@Composable
fun PlanPhaseTimelineInline(snapshot: PlanSnapshot) {
    // RC15.2: tathbit is a monthly plan without phases.
    if (snapshot.plan.type == "tathbit") return
    var phases by remember(snapshot.plan.id) { mutableStateOf<List<SmartPlanPhaseRow>>(emptyList()) }
    LaunchedEffect(snapshot.plan.id) {
        phases = withContext(Dispatchers.IO) { AppGraph.repo.planPhases(snapshot.plan.id) }
    }
    if (phases.isEmpty()) return

    val tathbitPlan = snapshot.plan.type == "tathbit"
    // RC15.1: tathbit plans show tathbit phases only; hifz→sard (and bulk transfer) belong to general-hifz plans.
    val ordered = phases.filter { it.status != "archived" && (if (tathbitPlan) it.phaseType == "tathbit" else it.phaseType != "tathbit") }.sortedBy { it.phaseOrder }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("الخط الزمني للخطة", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                snapshot.phase?.let {
                    val actuallyActive = snapshot.phaseProgress != null
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (it.phaseType == "sard") Icons.Default.RecordVoiceOver else Icons.Default.MenuBook, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (actuallyActive) { if (it.phaseType == "sard") "السرد نشط" else "الحفظ نشط" }
                                else { if (LocalDate.now() < LocalDate.parse(it.startDate)) "المرحلة القادمة" else "آخر مرحلة" },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ordered.forEachIndexed { index, phase ->
                    val active = snapshot.phase?.id == phase.id
                    Surface(
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                            Text("${index + 1}. ${phaseTitle(phase)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Text("${phase.startDate} ← ${phase.endDate}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (index < ordered.lastIndex) Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
            snapshot.phaseProgress?.let { progress ->
                val goal = progress.targetPages?.let { "${phaseNumber(progress.completedPages)} / ${phaseNumber(it)} ص" } ?: "سرد مفتوح · ${phaseNumber(progress.completedPages)} ص منجزة"
                Text(goal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                progress.suggestedTodayPages?.takeIf { it > .001 }?.let {
                    Text("المقترح اليوم ${phaseNumber(it)} ص · توزيع ${if (snapshot.phase?.distributionMode == "equal") "متساوٍ" else "مرن"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (snapshot.phase?.phaseType == "sard") {
                    val sardActivity = snapshot.activities.firstOrNull { it.key == "sard" }
                    sardActivity?.lastRecitation?.let { last ->
                        val anchor = if (last.endSurah != null && last.endAyah != null) "${QuranMetadata.surahName(last.endSurah)} ${last.endAyah}" else last.endPage?.let { "ص $it" } ?: "—"
                        Text("آخر سرد: $anchor · ${last.date}", style = MaterialTheme.typography.bodySmall)
                    }
                    sardActivity?.assignment?.let { assignment ->
                        val range = when {
                            assignment.startPage != null -> com.mohammedsamir.halaqati.quran.RecitationParityRules.pageRangeQuranLabel(
                                minOf(assignment.startPage, assignment.endPage ?: assignment.startPage),
                                maxOf(assignment.startPage, assignment.endPage ?: assignment.startPage),
                            )
                            else -> "${phaseNumber(assignment.pages)} ص"
                        }
                        Text("المقرر التالي: $range · ${phaseNumber(assignment.pages)} ص", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                    if (sardActivity?.needsStart == true) {
                        Text("يلزم تحديد نقطة بداية السرد لأن آخر Sard Anchor غير متاح.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun SmartPlanPhasesManagerSheet(
    plan: PlanRow,
    snapshots: List<PlanSnapshot>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    directEdit: Boolean = false,
) {
    // RC15.2 Tathbit Core: a tathbit plan is a monthly plan. No phases, no hifz/sard, no bulk
    // transfer, no halaqa template — those concepts do not exist in its logic at all.
    if (plan.type == "tathbit") {
        TathbitMonthlyPlanSheet(plan = plan, snapshots = snapshots, onDismiss = onDismiss)
        return
    }
    val scope = rememberCoroutineScope()
    var phases by remember(plan.id) { mutableStateOf<List<SmartPlanPhaseRow>>(emptyList()) }
    var states by remember(plan.id) { mutableStateOf<List<SmartPlanPhaseStateRow>>(emptyList()) }
    var templates by remember(plan.halaqaId) { mutableStateOf<List<SmartPlanPhaseTemplateRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var schemaIssue by remember(plan.id) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SmartPlanPhaseRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addingType by remember { mutableStateOf<String?>(null) }
    var excluded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var exceptionEndDate by remember { mutableStateOf("") }
    var showExceptionDatePicker by remember { mutableStateOf(false) }
    var showBulkStudents by remember { mutableStateOf(false) }
    var customizeSardTargets by remember { mutableStateOf(false) }
    var perStudentSardTargets by remember(plan.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showStudentActions by remember { mutableStateOf(true) }
    var transitionOne by remember { mutableStateOf<PlanSnapshot?>(null) }
    var closePhaseOne by remember { mutableStateOf<PlanSnapshot?>(null) }
    var showBulkEndSard by remember { mutableStateOf(false) }
    var bulkEndSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bulkEndRunning by remember { mutableStateOf(false) }
    var previousShortfalls by remember(plan.id) { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var carryPreviousShortfallBulk by remember(plan.id) { mutableStateOf(false) }
    var carryPreviousShortfallOne by remember { mutableStateOf(false) }
    var showBulkConfirm by remember { mutableStateOf(false) }

    suspend fun reload(force: Boolean = false) {
        loading = true
        try {
            withContext(Dispatchers.IO) { runCatching { AppGraph.repo.reconcilePlanPhaseLifecycle(plan.id) } }
            val loadedPhases = withContext(Dispatchers.IO) { AppGraph.repo.planPhases(plan.id, forceRefresh = force) }
            phases = loadedPhases
            states = withContext(Dispatchers.IO) { AppGraph.repo.planPhaseStates(forceRefresh = force) }
            templates = withContext(Dispatchers.IO) { AppGraph.repo.planPhaseTemplates(plan.halaqaId, forceRefresh = force) }
            schemaIssue = withContext(Dispatchers.IO) { AppGraph.repo.smartPlanPhaseSchemaIssue() }
            val sardStart = loadedPhases.firstOrNull { it.phaseType == "sard" }
                ?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            previousShortfalls = if (sardStart != null) withContext(Dispatchers.IO) {
                AppGraph.repo.previousSardShortfalls(snapshots.filterNot { com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(it.student.track) }.map { it.student.id }.toSet(), sardStart, forceRefresh = force)
            } else emptyMap()
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر تحميل مراحل الخطة"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(plan.id) { reload() }
    LaunchedEffect(transitionOne?.student?.id) { carryPreviousShortfallOne = false }
    // RC53: "تعديل المرحلة" on the plan card opens the editor of the current phase straight away.
    var showAdvanced by remember(plan.id) { mutableStateOf(false) }
    var directDone by remember(plan.id) { mutableStateOf(false) }
    LaunchedEffect(phases, directEdit) {
        if (directEdit && !directDone && phases.isNotEmpty()) {
            val currentId = snapshots.firstOrNull { it.plan.id == plan.id }?.phase?.id
            val live = phases.filter { it.status != "archived" }.sortedBy { it.phaseOrder }
            editing = live.firstOrNull { it.id == currentId } ?: live.firstOrNull()
            directDone = true
        }
    }

    val tathbitPlan = plan.type == "tathbit"
    val ordered = phases.filter { it.status != "archived" && (if (tathbitPlan) it.phaseType == "tathbit" else it.phaseType != "tathbit") }.sortedBy { it.phaseOrder }
    val hifz = ordered.firstOrNull { it.phaseType == "hifz" }
    val sard = ordered.firstOrNull { it.phaseType == "sard" }
    val timelineMessages = ordered.zipWithNext().mapNotNull { (a, b) ->
        val ae = runCatching { LocalDate.parse(a.endDate) }.getOrNull() ?: return@mapNotNull null
        val bs = runCatching { LocalDate.parse(b.startDate) }.getOrNull() ?: return@mapNotNull null
        when {
            bs <= ae -> "تداخل غير صالح بين ${phaseTitle(a)} و${phaseTitle(b)}"
            bs > ae.plusDays(1) -> "تنبيه: لا توجد مرحلة من ${ae.plusDays(1)} إلى ${bs.minusDays(1)}؛ الخطة ستبقى ظاهرة لكن لن يُولد مقرر تلقائي في هذه الفجوة."
            else -> null
        }
    }
    // RC15.1: hifz→sard phases and bulk transfer never include tathbit students.
    val planStudents = snapshots.distinctBy { it.student.id }
        .filter { snap -> if (plan.type == "tathbit") com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(snap.student.track) else !com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(snap.student.track) }
        .sortedBy { it.student.fullName }
    var bulkSardTarget by remember(plan.id, sard?.id, sard?.targetPages, sard?.goalMode) {
        mutableStateOf(phaseNumber(sard?.targetPages?.takeIf { it > .001 } ?: 25.0))
    }

    fun hifzStateFor(studentId: String): SmartPlanPhaseStateRow? = hifz?.let { hp -> states.firstOrNull { it.phaseId == hp.id && it.studentId == studentId } }
    fun hifzTargetFor(snap: PlanSnapshot): Double {
        val st = hifzStateFor(snap.student.id)
        return st?.frozenTargetPages ?: st?.targetPagesOverride ?: hifz?.targetPages ?: snap.plan.target
    }
    fun hifzCompletedFor(snap: PlanSnapshot): Double {
        val st = hifzStateFor(snap.student.id)
        st?.frozenCompletedPages?.let { return it }
        if (snap.phase?.id == hifz?.id) return snap.phaseProgress?.completedPages ?: snap.progress.completed
        return snap.progress.completed
    }

    val transitionCandidates = if (hifz == null) emptyList() else planStudents.filter { snap ->
        hifzStateFor(snap.student.id)?.status != "completed"
    }
    val completedCount = transitionCandidates.count { snap -> hifzCompletedFor(snap) + .001 >= hifzTargetFor(snap) }
    val avgPercent = if (transitionCandidates.isEmpty()) 0.0 else transitionCandidates.map { snap ->
        val target = hifzTargetFor(snap)
        if (target <= .001) 0.0 else (hifzCompletedFor(snap) / target * 100.0).coerceAtMost(200.0)
    }.average()
    val totalPreviousShortfall = transitionCandidates.sumOf { previousShortfalls[it.student.id] ?: 0.0 }
    val canMutate = schemaIssue == null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("مراحل الخطة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("اضغط «تعديل» على أي مرحلة لتغيير تواريخها وهدفها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { scope.launch { reload(true) } }) { Icon(Icons.Default.Refresh, "تحديث") }
                }
            }

            schemaIssue?.let { issue ->
                item {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.error)
                                Text("تحديث قاعدة البيانات مطلوب", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Text(issue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("تم تعطيل إنشاء/تعديل المراحل مؤقتًا حتى لا تتكرر عمليات فاشلة في قائمة المزامنة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            message?.let { text ->
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(text, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (loading && phases.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            if (!loading && ordered.isEmpty()) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("تهيئة الخطة كمراحل", fontWeight = FontWeight.Bold)
                            Text("سيتم إنشاء مرحلة حفظ ثم مرحلة سرد. السرد الافتراضي: 25 صفحة، توزيع مرن، والبداية من آخر سرد مستقل لكل طالب.", style = MaterialTheme.typography.bodySmall)
                            Button(
                                enabled = canMutate,
                                onClick = {
                                    scope.launch {
                                        try {
                                            val start = LocalDate.parse(plan.startDate)
                                            val end = LocalDate.parse(plan.endDate)
                                            val sardStart = maxOf(start.plusDays(1), end.minusDays(6))
                                            val hifzEnd = sardStart.minusDays(1).coerceAtLeast(start)
                                            if (plan.type == "tathbit") withContext(Dispatchers.IO) {
                                                // RC15: a tathbit plan has one tathbit phase only — never hifz/sard.
                                                AppGraph.repo.savePlanPhase(
                                                    SmartPlanPhaseDraft(
                                                        planId = plan.id, phaseOrder = 1, phaseType = "tathbit", title = "مرحلة التثبيت",
                                                        startDate = start.toString(), endDate = end.toString(),
                                                        scheduleWeekdays = plan.scheduleWeekdays.ifEmpty { listOf(0, 1, 2, 3, 4) },
                                                        goalMode = "pages", goalUnit = "pages", targetPages = plan.target,
                                                        distributionMode = "flexible", startMode = "plan_start",
                                                        rangeStartPage = plan.rangeStartPage, rangeEndPage = plan.rangeEndPage,
                                                        rangeStartSurah = plan.rangeStartSurah, rangeStartAyah = plan.rangeStartAyah,
                                                        rangeEndSurah = plan.rangeEndSurah, rangeEndAyah = plan.rangeEndAyah,
                                                    )
                                                )
                                            } else withContext(Dispatchers.IO) {
                                                AppGraph.repo.savePlanPhase(
                                                    SmartPlanPhaseDraft(
                                                        planId = plan.id, phaseOrder = 1, phaseType = "hifz", title = "مرحلة الحفظ",
                                                        startDate = start.toString(), endDate = hifzEnd.toString(),
                                                        scheduleWeekdays = plan.scheduleWeekdays.ifEmpty { listOf(0, 1, 2, 3, 4) },
                                                        goalMode = "pages", goalUnit = "pages", targetPages = plan.target,
                                                        distributionMode = "flexible", startMode = "plan_start",
                                                        rangeStartPage = plan.rangeStartPage, rangeEndPage = plan.rangeEndPage,
                                                        rangeStartSurah = plan.rangeStartSurah, rangeStartAyah = plan.rangeStartAyah,
                                                        rangeEndSurah = plan.rangeEndSurah, rangeEndAyah = plan.rangeEndAyah,
                                                    )
                                                )
                                                AppGraph.repo.savePlanPhase(
                                                    SmartPlanPhaseDraft(
                                                        planId = plan.id, phaseOrder = 2, phaseType = "sard", title = "مرحلة السرد",
                                                        startDate = sardStart.toString(), endDate = end.toString(),
                                                        scheduleWeekdays = listOf(0, 1, 2, 3, 4, 5),
                                                        goalMode = "pages", goalUnit = "pages", targetPages = SmartPlanPhaseEngine.DEFAULT_SARD_TARGET_PAGES,
                                                        distributionMode = "flexible", startMode = "last_activity",
                                                        settings = JSONObject().put("route_direction", SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH),
                                                    )
                                                )
                                            }
                                            reload(false); onChanged(); message = "تم تجهيز المرحلتين محليًا وسيتم رفعهما بالمزامنة."
                                        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                                            message = e.message ?: "تعذر إنشاء المراحل"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            ) {
                                Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(7.dp)); Text("إنشاء حفظ + سرد (25 صفحة)")
                            }
                        }
                    }
                }
                if (templates.isNotEmpty()) {
                    item { Text("قوالب الحلقة", fontWeight = FontWeight.Bold) }
                    items(templates.take(6), key = { it.id }) { template ->
                        OutlinedButton(
                            enabled = canMutate,
                            onClick = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { AppGraph.repo.applyPlanPhaseTemplate(plan, template) }
                                        reload(false); onChanged(); message = "تم تطبيق قالب ${template.name}"
                                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تطبيق القالب" }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Default.ContentPasteGo, null); Spacer(Modifier.width(6.dp)); Text("${template.name} · ${templateModeAr(template.templateMode)}")
                        }
                    }
                }
            } else if (ordered.isNotEmpty()) {
                item { com.mohammedsamir.halaqati.ui.components.NourSectionTitle("مراحل الخطة") }
                if (timelineMessages.isNotEmpty()) {
                    item {
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("تحقق الخط الزمني", fontWeight = FontWeight.Bold)
                                timelineMessages.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                items(ordered, key = { it.id }) { phase ->
                    val activeCount = states.count { it.phaseId == phase.id && it.status == "active" }
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalIconButton(onClick = { editing = phase }, enabled = canMutate) {
                                    Icon(Icons.Default.Edit, "تعديل المرحلة")
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(phaseTitle(phase), fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("المرحلة #${phase.phaseOrder} · ${when (phase.phaseType) { "sard" -> "سرد"; "tathbit" -> "تثبيت"; else -> "حفظ" }}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(if (phase.phaseType == "sard") Icons.Default.RecordVoiceOver else Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                "${phase.startDate} ← ${phase.endDate} · ${phaseGoalText(phase)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    append("الأيام: ${phase.scheduleWeekdays.sorted().joinToString("، ") { phaseWeekdayName(it) }}")
                                    if (phase.phaseType in setOf("sard", "hifz", "tathbit")) {
                                        append(" · البداية: ${phaseStartModeAr(phase.startMode)}")
                                        append(" · ${if (phase.distributionMode == "equal") "متساوٍ" else "مرن"}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (activeCount > 0) Text("$activeCount طالب بحالة نشطة", style = MaterialTheme.typography.labelMedium, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                            OutlinedButton(enabled = canMutate, onClick = { editing = phase }, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                                Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("تعديل إعدادات المرحلة")
                            }
                        }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = canMutate, onClick = { addingType = if (tathbitPlan) "tathbit" else null; adding = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text(if (tathbitPlan) "إضافة مرحلة تثبيت" else "إضافة مرحلة")
                        }
                        if (tathbitPlan) {
                            // RC15.1: no hifz/sard shortcuts for tathbit students.
                        } else if (sard == null) {
                            Button(enabled = canMutate, onClick = { addingType = "sard"; adding = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Icon(Icons.Default.RecordVoiceOver, null); Spacer(Modifier.width(5.dp)); Text("إضافة السرد")
                            }
                        } else if (hifz == null) {
                            Button(enabled = canMutate, onClick = { addingType = "hifz"; adding = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(5.dp)); Text("إضافة الحفظ")
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (showAdvanced) "إخفاء الأدوات المتقدمة" else "أدوات متقدمة: إنهاء جماعي، نقل الطلاب، القوالب")
                    }
                }
                if (showAdvanced) {
                if (!tathbitPlan && sard != null) item {
                    val inSard = snapshots.distinctBy { it.student.id }.count { it.phase?.phaseType == "sard" && it.phaseProgress != null }
                    FilledTonalButton(
                        enabled = canMutate && inSard > 0,
                        onClick = {
                            bulkEndSelected = snapshots.distinctBy { it.student.id }.filter { it.phase?.phaseType == "sard" && it.phaseProgress != null }.map { it.student.id }.toSet()
                            showBulkEndSard = true
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(6.dp)); Text("إنهاء السرد لعدة طلاب ($inSard في مرحلة السرد)") }
                }

                if (!tathbitPlan && hifz != null && sard != null && transitionCandidates.isNotEmpty()) {
                    item {
                        ElevatedCard(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text("الانتقال الجماعي إلى السرد", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                        Text("هدف الحفظ لا يتغير؛ يبقى لكل طالب حسب خطته الحالية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text("${transitionCandidates.size} طالب في الحفظ · $completedCount أكملوا الهدف · ${transitionCandidates.size - completedCount} لم يكملوا · متوسط ${phaseNumber(avgPercent)}%", style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = bulkSardTarget,
                                    onValueChange = { bulkSardTarget = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("هدف السرد الموحد للمنقولين (صفحة)") },
                                    supportingText = { Text("الافتراضي 25 صفحة. يمكن تخصيص طالب بعينه أدناه.") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = canMutate,
                                )
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = customizeSardTargets, onCheckedChange = { customizeSardTargets = it }, enabled = canMutate)
                                    Text("تخصيص هدف سرد لبعض الطلاب", modifier = Modifier.weight(1f))
                                    TextButton(onClick = { showBulkStudents = !showBulkStudents }) {
                                        Text(if (showBulkStudents) "إخفاء الطلاب" else "الطلاب والاستثناءات")
                                    }
                                }
                                if (showBulkStudents) {
                                    Text("أزل علامة الطالب ليبقى في الحفظ. عند التخصيص اكتب هدف سرد مختلفًا له فقط.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(transitionCandidates, key = { it.student.id }) { snap ->
                                            val included = snap.student.id !in excluded
                                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
                                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                                        Checkbox(
                                                            checked = included,
                                                            onCheckedChange = { checked -> excluded = if (checked) excluded - snap.student.id else excluded + snap.student.id },
                                                            enabled = canMutate,
                                                        )
                                                        Column(Modifier.weight(1f)) {
                                                            OneLineName(snap.student.fullName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                            Text("الحفظ: ${phaseNumber(hifzCompletedFor(snap))} / ${phaseNumber(hifzTargetFor(snap))} ص", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                    if (included && customizeSardTargets) {
                                                        OutlinedTextField(
                                                            value = perStudentSardTargets[snap.student.id].orEmpty(),
                                                            onValueChange = { value -> perStudentSardTargets = perStudentSardTargets + (snap.student.id to value.filter { ch -> ch.isDigit() || ch == '.' }) },
                                                            label = { Text("هدف سرد خاص (اتركه فارغًا للهدف الموحد)") },
                                                            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = canMutate,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (excluded.isNotEmpty()) {
                                    Text("${excluded.size} طالب سيبقون في الحفظ", fontWeight = FontWeight.SemiBold)
                                    OutlinedButton(onClick = { showExceptionDatePicker = true }, modifier = Modifier.fillMaxWidth(), enabled = canMutate) {
                                        Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(6.dp));
                                        Text("نهاية استثناء الحفظ: ${exceptionEndDate.ifBlank { hifz.endDate }}")
                                    }
                                }
                                if (totalPreviousShortfall > .001) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = carryPreviousShortfallBulk, onCheckedChange = { carryPreviousShortfallBulk = it }, enabled = canMutate)
                                        Column(Modifier.weight(1f)) {
                                            Text("ترحيل نقص السرد السابق اختياريًا", style = MaterialTheme.typography.labelLarge)
                                            Text("إجمالي النقص السابق: ${phaseNumber(totalPreviousShortfall)} ص · الافتراضي تجاهل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Button(
                                    enabled = canMutate && bulkSardTarget.toDoubleOrNull()?.let { it > 0.0 } == true,
                                    onClick = { showBulkConfirm = true },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                ) {
                                    Icon(Icons.Default.Preview, null); Spacer(Modifier.width(7.dp)); Text("معاينة الانتقال الجماعي")
                                }
                            }
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("حالة الطلاب داخل المراحل", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { showStudentActions = !showStudentActions }) { Text(if (showStudentActions) "إخفاء" else "عرض") }
                            }
                            if (showStudentActions) {
                                LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),modifier = Modifier.fillMaxWidth().heightIn(max = 310.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(planStudents, key = { it.student.id }) { snap ->
                                        val current = snap.phase
                                        val pp = snap.phaseProgress
                                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                                            Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                                Column(Modifier.weight(1f)) {
                                                    OneLineName(snap.student.fullName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                    Text(
                                                        when {
                                                            current == null -> "لا توجد مرحلة نشطة"
                                                            current.phaseType == "hifz" -> "حفظ · ${phaseNumber(pp?.completedPages ?: snap.progress.completed)} / ${phaseNumber(pp?.targetPages ?: current.targetPages ?: plan.target)} ص"
                                                            pp?.targetPages == null -> "سرد مفتوح · ${phaseNumber(pp?.completedPages ?: snap.progress.completed)} ص"
                                                            else -> "سرد · ${phaseNumber(pp.completedPages)} / ${phaseNumber(pp.targetPages)} ص"
                                                        },
                                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                when {
                                                    current?.phaseType == "hifz" && sard != null -> FilledTonalButton(enabled = canMutate, onClick = { transitionOne = snap }) { Text("إنهاء الحفظ") }
                                                    current?.phaseType == "sard" && pp?.goalComplete == true -> {
                                                        Column(horizontalAlignment = Alignment.End) {
                                                            if (current.allowSurplus && snap.phaseState?.settings?.optBoolean("continue_surplus", false) != true) {
                                                                TextButton(enabled = canMutate, onClick = {
                                                                    scope.launch {
                                                                        try {
                                                                            withContext(Dispatchers.IO) {
                                                                                AppGraph.repo.upsertPlanPhaseState(
                                                                                    phaseId = current.id, studentId = snap.student.id, status = "active",
                                                                                    targetPagesOverride = snap.phaseState?.targetPagesOverride,
                                                                                    startDateOverride = snap.phaseState?.startDateOverride, endDateOverride = snap.phaseState?.endDateOverride,
                                                                                    carryShortfallPages = snap.phaseState?.carryShortfallPages ?: 0.0,
                                                                                    settings = JSONObject(snap.phaseState?.settings?.toString() ?: "{}").put("continue_surplus", true),
                                                                                )
                                                                            }
                                                                            reload(false); onChanged(); message = "سيستمر ${snap.student.fullName} بسرد إضافي"
                                                                        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تفعيل الاستمرار الإضافي" }
                                                                    }
                                                                }) { Text("استمرار إضافي") }
                                                            }
                                                            Button(enabled = canMutate, onClick = { closePhaseOne = snap }) { Text("إنهاء السرد") }
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

                if (!tathbitPlan) item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("قالب الحلقة", fontWeight = FontWeight.Bold)
                            Text("احفظ دورة الحفظ ← السرد بالتاريخ أو بالأسابيع أو يدويًا.", style = MaterialTheme.typography.bodySmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(listOf("dates" to "بالتاريخ", "weeks" to "بالأسابيع", "manual" to "يدوي")) { pair ->
                                    val mode = pair.first
                                    val label = pair.second
                                    OutlinedButton(
                                        enabled = canMutate,
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    val start = LocalDate.parse(plan.startDate)
                                                    val h = hifz
                                                    val sd = sard
                                                    val definition = when (mode) {
                                                        "dates" -> JSONObject()
                                                            .put("hifz_start_offset_days", h?.startDate?.let { ChronoUnit.DAYS.between(start, LocalDate.parse(it)) } ?: 0L)
                                                            .put("hifz_end_offset_days", h?.endDate?.let { ChronoUnit.DAYS.between(start, LocalDate.parse(it)) } ?: 23L)
                                                            .put("sard_start_offset_days", sd?.startDate?.let { ChronoUnit.DAYS.between(start, LocalDate.parse(it)) } ?: 24L)
                                                            .put("sard_end_offset_days", sd?.endDate?.let { ChronoUnit.DAYS.between(start, LocalDate.parse(it)) } ?: ChronoUnit.DAYS.between(start, LocalDate.parse(plan.endDate)))
                                                            .put("sard_goal_mode", sd?.goalMode ?: "pages")
                                                            .put("sard_target_units", sd?.targetUnits ?: JSONObject.NULL)
                                                            .put("sard_target_pages", sd?.targetPages ?: 25.0)
                                                            .put("sard_distribution", sd?.distributionMode ?: "flexible")
                                                            .put("sard_start_mode", sd?.startMode ?: "last_activity")
                                                            .put("sard_route_direction", sd?.settings?.optString("route_direction")?.takeIf { it.isNotBlank() } ?: SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH)
                                                        "weeks" -> JSONObject()
                                                            .put("hifz_weeks", 3)
                                                            .put("sard_weeks", maxOf(1, kotlin.math.ceil(((sd?.let { ChronoUnit.DAYS.between(LocalDate.parse(it.startDate), LocalDate.parse(it.endDate)) + 1 } ?: 7L).toDouble()) / 7.0).toInt()))
                                                            .put("sard_goal_mode", sd?.goalMode ?: "pages")
                                                            .put("sard_target_units", sd?.targetUnits ?: JSONObject.NULL)
                                                            .put("sard_target_pages", sd?.targetPages ?: 25.0)
                                                            .put("sard_distribution", sd?.distributionMode ?: "flexible")
                                                            .put("sard_start_mode", sd?.startMode ?: "last_activity")
                                                            .put("sard_route_direction", sd?.settings?.optString("route_direction")?.takeIf { it.isNotBlank() } ?: SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH)
                                                        else -> JSONObject().put("phases", JSONArray().also { array ->
                                                            ordered.forEach { p ->
                                                                val ps = LocalDate.parse(p.startDate); val pe = LocalDate.parse(p.endDate)
                                                                array.put(JSONObject()
                                                                    .put("phase_order", p.phaseOrder).put("phase_type", p.phaseType).put("title", p.title)
                                                                    .put("start_offset_days", ChronoUnit.DAYS.between(start, ps))
                                                                    .put("end_offset_days", ChronoUnit.DAYS.between(start, pe))
                                                                    .put("schedule_weekdays", JSONArray(p.scheduleWeekdays))
                                                                    .put("goal_mode", p.goalMode).put("goal_unit", p.goalUnit)
                                                                    .put("target_units", p.targetUnits ?: JSONObject.NULL).put("target_pages", p.targetPages ?: JSONObject.NULL)
                                                                    .put("distribution_mode", p.distributionMode).put("start_mode", p.startMode).put("allow_surplus", p.allowSurplus)
                                                                    .put("settings", JSONObject(p.settings.toString()))
                                                                    .put("range_start_page", p.rangeStartPage ?: JSONObject.NULL).put("range_end_page", p.rangeEndPage ?: JSONObject.NULL)
                                                                    .put("range_start_surah", p.rangeStartSurah ?: JSONObject.NULL).put("range_start_ayah", p.rangeStartAyah ?: JSONObject.NULL)
                                                                    .put("range_end_surah", p.rangeEndSurah ?: JSONObject.NULL).put("range_end_ayah", p.rangeEndAyah ?: JSONObject.NULL))
                                                            }
                                                        })
                                                    }
                                                    withContext(Dispatchers.IO) { AppGraph.repo.savePlanPhaseTemplate(plan.halaqaId, "${plan.title} · $label", mode, definition) }
                                                    reload(false); message = "تم حفظ القالب $label"
                                                } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر حفظ القالب" }
                                            }
                                        },
                                        modifier = Modifier.heightIn(min = 44.dp),
                                    ) { Icon(Icons.Default.BookmarkAdd, null); Spacer(Modifier.width(5.dp)); Text(label) }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    if (showExceptionDatePicker && hifz != null) {
        Rc11DatePickerDialog(
            initialDate = exceptionEndDate.ifBlank { hifz.endDate },
            onDismiss = { showExceptionDatePicker = false },
            onConfirm = { exceptionEndDate = it; showExceptionDatePicker = false },
        )
    }

    if (showBulkConfirm && hifz != null && sard != null) {
        val commonTarget = bulkSardTarget.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 25.0
        val included = transitionCandidates.filterNot { it.student.id in excluded }
        val excludedCount = transitionCandidates.size - included.size
        val carryTotal = if (carryPreviousShortfallBulk) included.sumOf { previousShortfalls[it.student.id] ?: 0.0 } else 0.0
        val customCount = if (customizeSardTargets) included.count { snap ->
            perStudentSardTargets[snap.student.id]?.toDoubleOrNull()?.let { kotlin.math.abs(it - commonTarget) > .001 } == true
        } else 0
        HalaqatiAlertDialog(
            onDismissRequest = { showBulkConfirm = false },
            title = { Text("معاينة الانتقال الجماعي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("سيتم نقل ${included.size} طالب إلى السرد.", fontWeight = FontWeight.Bold)
                    Text("هدف السرد الموحد: ${phaseNumber(commonTarget)} صفحة${if (customCount > 0) " · $customCount أهداف فردية" else ""}.")
                    Text("أهداف الحفظ لكل طالب ستُجمّد كما هي ولن تُستبدل بهدف جماعي.")
                    Text("${included.count { hifzCompletedFor(it) + .001 >= hifzTargetFor(it) }} أكملوا هدف الحفظ، و${included.count { hifzCompletedFor(it) + .001 < hifzTargetFor(it) }} سيُغلق حفظهم عند النسبة الحالية.")
                    if (excludedCount > 0) Text("سيبقى $excludedCount طالب في الحفظ حتى ${exceptionEndDate.ifBlank { hifz.endDate }}.")
                    Text("متوسط إنجاز الحفظ للمنقولين: ${phaseNumber(if (included.isEmpty()) 0.0 else included.map { (hifzCompletedFor(it) / hifzTargetFor(it).coerceAtLeast(.001) * 100.0).coerceAtMost(200.0) }.average())}%")
                    if (carryPreviousShortfallBulk && carryTotal > .001) Text("سيُضاف نقص سرد سابق بإجمالي ${phaseNumber(carryTotal)} ص للطلاب المعنيين.")
                    else Text("لن يُرحّل أي نقص سابق تلقائيًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showBulkConfirm = false
                    scope.launch {
                        try {
                            val exceptionEnd = exceptionEndDate.takeIf { it.isNotBlank() } ?: hifz.endDate
                            withContext(Dispatchers.IO) {
                                val sardStarted = states.any { it.phaseId == sard.id && it.status in setOf("active", "completed") }
                                val lockedDefault = sard.targetPages ?: if (sard.goalMode == "juz") sard.targetUnits?.times(20.0) else null
                                if (!sardStarted) {
                                    AppGraph.repo.savePlanPhase(sard.toDraftWithPagesTarget(commonTarget))
                                }
                                transitionCandidates.filter { it.student.id in excluded }.forEach { snap ->
                                    AppGraph.repo.upsertPlanPhaseState(phaseId = hifz.id, studentId = snap.student.id, status = "active", endDateOverride = exceptionEnd)
                                    AppGraph.repo.upsertPlanPhaseState(phaseId = sard.id, studentId = snap.student.id, status = "skipped")
                                }
                                val transitionRows = included.map { snap ->
                                    val requestedTarget = if (customizeSardTargets) perStudentSardTargets[snap.student.id]?.toDoubleOrNull()?.takeIf { it > 0.0 } else null
                                    val effectiveRequested = requestedTarget ?: commonTarget
                                    val override = if (sardStarted) effectiveRequested.takeIf { lockedDefault == null || kotlin.math.abs(it - lockedDefault) > .001 }
                                        else requestedTarget?.takeIf { kotlin.math.abs(it - commonTarget) > .001 }
                                    PhaseTransitionRequest(
                                        studentId = snap.student.id,
                                        completedPages = hifzCompletedFor(snap),
                                        targetPages = hifzTargetFor(snap),
                                        carryShortfallPages = if (carryPreviousShortfallBulk) previousShortfalls[snap.student.id] ?: 0.0 else 0.0,
                                        sardTargetPagesOverride = override,
                                    )
                                }
                                if (transitionRows.isNotEmpty()) AppGraph.repo.transitionPlanStudentsBulk(plan.id, hifz.id, sard.id, transitionRows)
                            }
                            excluded = emptySet(); perStudentSardTargets = emptyMap(); customizeSardTargets = false
                            reload(false); onChanged(); message = "تم تجهيز نقل ${included.size} طالب إلى السرد مع تجميد أهداف الحفظ الفردية."
                        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تنفيذ الانتقال الجماعي" }
                    }
                }) { Text("اعتماد وبدء السرد") }
            },
            dismissButton = { TextButton(onClick = { showBulkConfirm = false }) { Text("رجوع") } },
        )
    }

    transitionOne?.let { snap ->
        val target = hifzTargetFor(snap)
        val completed = hifzCompletedFor(snap)
        val percent = if (target > .001) completed / target * 100.0 else 0.0
        val priorShortfall = previousShortfalls[snap.student.id] ?: 0.0
        val lastHifz = snap.activities.firstOrNull { it.key == "hifz" }?.lastRecitation
        HalaqatiAlertDialog(
            onDismissRequest = { transitionOne = null },
            title = { Text("إنهاء مرحلة الحفظ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OneLineName(snap.student.fullName, fontWeight = FontWeight.Bold)
                    Text("الهدف: ${phaseNumber(target)} صفحة")
                    Text("المنجز: ${phaseNumber(completed)} صفحة")
                    Text("نسبة الإنجاز: ${phaseNumber(percent)}%")
                    lastHifz?.let { last -> Text("آخر حفظ: ${recitationEndLabel(last)}") }
                    Text("تاريخ الإنهاء: ${LocalDate.now()}")
                    if (priorShortfall > .001) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = carryPreviousShortfallOne, onCheckedChange = { carryPreviousShortfallOne = it })
                            Column(Modifier.weight(1f)) {
                                Text("ترحيل نقص السرد السابق: ${phaseNumber(priorShortfall)} ص", style = MaterialTheme.typography.labelLarge)
                                Text("اختياري؛ الافتراضي تجاهل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text("بعد الاعتماد يتجمّد إنجاز الحفظ ويبدأ Sard Anchor المستقل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val targetSnap = transitionOne ?: return@Button
                    val from = hifz ?: return@Button
                    val to = sard ?: return@Button
                    transitionOne = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                AppGraph.repo.transitionPlanStudentPhase(plan.id, targetSnap.student.id, from.id, to.id, completed, target, if (carryPreviousShortfallOne) priorShortfall else 0.0)
                            }
                            reload(false); onChanged(); message = "تم إنهاء حفظ ${targetSnap.student.fullName} وبدء السرد"
                        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر إنهاء مرحلة الحفظ" }
                    }
                }) { Text("اعتماد وإنهاء الحفظ") }
            },
            dismissButton = { TextButton(onClick = { transitionOne = null }) { Text("إلغاء") } },
        )
    }

    if (showBulkEndSard) {
        val candidates = snapshots.distinctBy { it.student.id }.filter { it.phase?.phaseType == "sard" && it.phaseProgress != null }.sortedBy { it.student.fullName }
        HalaqatiAlertDialog(
            onDismissRequest = { if (!bulkEndRunning) showBulkEndSard = false },
            title = { Text("إنهاء السرد لعدة طلاب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("يُجمَّد تقدّم كل طالب محدد كما هو الآن وتُنهى مرحلة السرد له. لا تُحذف تسميعات، ولا يُنشأ شيء جديد؛ لبدء دورة جديدة استخدم «بدء دورة شهر جديدة».", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { bulkEndSelected = candidates.map { it.student.id }.toSet() }) { Text("تحديد الكل") }
                        TextButton(onClick = { bulkEndSelected = emptySet() }) { Text("إلغاء الكل") }
                    }
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(candidates.size) { i ->
                            val snap = candidates[i]
                            val pp = snap.phaseProgress
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = snap.student.id in bulkEndSelected,
                                    onCheckedChange = { on -> bulkEndSelected = if (on) bulkEndSelected + snap.student.id else bulkEndSelected - snap.student.id },
                                )
                                com.mohammedsamir.halaqati.ui.components.StudentAvatar(snap.student.photoPath, snap.student.fullName, 36.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(snap.student.fullName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(
                                        "${phaseNumber(pp?.completedPages ?: 0.0)}${pp?.targetPages?.let { " / ${phaseNumber(it)}" } ?: ""} ص",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !bulkEndRunning && bulkEndSelected.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            bulkEndRunning = true
                            var done = 0
                            var failed = 0
                            for (snap in candidates.filter { it.student.id in bulkEndSelected }) {
                                val phase = snap.phase
                                val pp = snap.phaseProgress
                                if (phase == null || pp == null) { failed++; continue }
                                try {
                                    withContext(Dispatchers.IO) { AppGraph.repo.completePlanPhaseForStudent(plan.id, phase.id, snap.student.id, pp.completedPages, pp.targetPages) }
                                    done++
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) { failed++ }
                            }
                            bulkEndRunning = false
                            showBulkEndSard = false
                            reload(false); onChanged()
                            message = "تم إنهاء السرد لـ $done طالب" + if (failed > 0) " · تعذّر $failed" else ""
                        }
                    },
                ) { Text(if (bulkEndRunning) "جاري الإنهاء…" else "إنهاء السرد (${bulkEndSelected.size})") }
            },
            dismissButton = { TextButton(enabled = !bulkEndRunning, onClick = { showBulkEndSard = false }) { Text("رجوع") } },
        )
    }

    closePhaseOne?.let { snap ->
        val phase = snap.phase
        val pp = snap.phaseProgress
        if (phase != null && pp != null) {
            HalaqatiAlertDialog(
                onDismissRequest = { closePhaseOne = null },
                title = { Text("إنهاء مرحلة السرد") },
                text = { Text("${snap.student.fullName}: ${phaseNumber(pp.completedPages)}${pp.targetPages?.let { " / ${phaseNumber(it)} صفحة · ${phaseNumber(pp.percentage ?: 0.0)}%" } ?: " صفحة"}. سيتم تجميد النتيجة ولن يُولد مقرر جديد.") },
                confirmButton = {
                    Button(onClick = {
                        closePhaseOne = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { AppGraph.repo.completePlanPhaseForStudent(plan.id, phase.id, snap.student.id, pp.completedPages, pp.targetPages) }
                                reload(false); onChanged(); message = "تم إنهاء مرحلة السرد لـ${snap.student.fullName}"
                            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر إنهاء مرحلة السرد" }
                        }
                    }) { Text("إنهاء المرحلة") }
                },
                dismissButton = { TextButton(onClick = { closePhaseOne = null }) { Text("إلغاء") } },
            )
        }
    }

    val currentEdit = editing
    if ((adding || currentEdit != null) && canMutate) {
        PhaseEditorDialog(
            plan = plan,
            current = currentEdit,
            initialType = addingType,
            nextOrder = (ordered.maxOfOrNull { it.phaseOrder } ?: 0) + 1,
            states = states,
            snapshots = snapshots,
            onDismiss = { adding = false; addingType = null; editing = null },
            onSave = { draft ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { AppGraph.repo.savePlanPhase(draft) }
                        adding = false; addingType = null; editing = null; reload(false); onChanged(); message = "تم حفظ المرحلة محليًا وستُزامن تلقائيًا."
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر حفظ المرحلة" }
                }
            },
        )
    }
}


@Composable
private fun PhaseEditorDialog(
    plan: PlanRow,
    current: SmartPlanPhaseRow?,
    initialType: String? = null,
    nextOrder: Int,
    states: List<SmartPlanPhaseStateRow>,
    snapshots: List<PlanSnapshot>,
    onDismiss: () -> Unit,
    onSave: (SmartPlanPhaseDraft) -> Unit,
) {
    val source = current
    var type by remember(source?.id, initialType) { mutableStateOf(source?.phaseType ?: initialType ?: if (plan.type == "tathbit") "tathbit" else if (nextOrder == 1) "hifz" else "sard") }
    var title by remember(source?.id, initialType) { mutableStateOf(source?.title ?: when (type) { "sard" -> "مرحلة السرد"; "tathbit" -> "مرحلة التثبيت"; else -> "مرحلة الحفظ" }) }
    var startDate by remember(source?.id) { mutableStateOf(source?.startDate ?: plan.startDate) }
    var endDate by remember(source?.id) { mutableStateOf(source?.endDate ?: plan.endDate) }
    var goalMode by remember(source?.id, initialType) { mutableStateOf(source?.goalMode ?: "pages") }
    var target by remember(source?.id, initialType) {
        mutableStateOf(
            when {
                source != null -> (source.targetUnits ?: source.targetPages ?: if (source.phaseType == "sard") 25.0 else plan.target).let(::phaseNumber)
                type == "sard" -> "25"
                else -> phaseNumber(plan.target)
            }
        )
    }
    var distribution by remember(source?.id) { mutableStateOf(source?.distributionMode ?: "flexible") }
    var startMode by remember(source?.id, initialType) {
        mutableStateOf(
            source?.startMode?.let { if (it == "last_sard") "last_activity" else it }
                ?: if (type == "sard") "last_activity" else "plan_start"
        )
    }
    var weekdays by remember(source?.id, initialType) {
        mutableStateOf((source?.scheduleWeekdays ?: if (type == "sard") listOf(0, 1, 2, 3, 4, 5) else plan.scheduleWeekdays.ifEmpty { listOf(0, 1, 2, 3, 4) }).toSet())
    }
    var allowSurplus by remember(source?.id) { mutableStateOf(source?.allowSurplus ?: true) }
    var rangeStartPage by remember(source?.id) { mutableStateOf(source?.rangeStartPage) }
    var rangeEndPage by remember(source?.id) { mutableStateOf(source?.rangeEndPage) }
    var rangeStartSurah by remember(source?.id) { mutableStateOf(source?.rangeStartSurah) }
    var rangeStartAyah by remember(source?.id) { mutableStateOf(source?.rangeStartAyah) }
    var rangeEndSurah by remember(source?.id) { mutableStateOf(source?.rangeEndSurah) }
    var rangeEndAyah by remember(source?.id) { mutableStateOf(source?.rangeEndAyah) }
    var showRange by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDraft by remember(source?.id) { mutableStateOf<SmartPlanPhaseDraft?>(null) }

    val previewSnapshots = remember(snapshots) { snapshots.distinctBy { it.student.id }.sortedBy { it.student.fullName } }
    var previewStudentId by remember(plan.id, source?.id) {
        mutableStateOf(plan.studentId?.takeIf { id -> previewSnapshots.any { it.student.id == id } } ?: previewSnapshots.firstOrNull()?.student?.id.orEmpty())
    }
    var latestActivity by remember(previewStudentId, type) { mutableStateOf<SmartPlanEngine.Recitation?>(null) }
    var loadingActivityAnchor by remember(previewStudentId, type) { mutableStateOf(false) }
    var sardRoute by remember(source?.id, plan.id) {
        mutableStateOf(
            source?.settings?.optString("route_direction")?.takeIf { it in setOf("reverse_surahs_forward_ayahs", "forward_surahs_forward_ayahs") }
                ?: plan.settings.sardRouteDirection
                ?: if (plan.type == "hifz") "reverse_surahs_forward_ayahs" else "forward_surahs_forward_ayahs"
        )
    }
    val phaseRoute = if (type == "sard") sardRoute else (plan.settings.routeDirection ?: "reverse_surahs_forward_ayahs")
    val traversal = phaseTraversalForRoute(phaseRoute)

    val anchorActivity = when (type) { "sard" -> "sard"; "tathbit" -> "tathbit"; else -> "hifz" }
    LaunchedEffect(type, startMode, previewStudentId, startDate) {
        if (startMode == "last_activity" && previewStudentId.isNotBlank()) {
            loadingActivityAnchor = true
            val anchorDate = runCatching { LocalDate.parse(startDate).minusDays(1) }.getOrElse { LocalDate.now() }
            latestActivity = withContext(Dispatchers.IO) {
                AppGraph.repo.latestAcceptedActivityRecitation(
                    studentId = previewStudentId,
                    activity = anchorActivity,
                    planId = plan.id,
                    today = anchorDate,
                    forceRefresh = true,
                )
            }
            loadingActivityAnchor = false
        } else {
            latestActivity = null
            loadingActivityAnchor = false
        }
    }

    val previewStart: QuranLocation? = when {
        startMode == "last_activity" -> latestActivity?.let { last ->
            val s = last.endSurah
            val a = last.endAyah
            if (s != null && a != null) SmartPlanActivityRules.nextLocation(QuranLocation(s, a), phaseRoute) else null
        }
        rangeStartSurah != null && rangeStartAyah != null -> QuranLocation(rangeStartSurah!!, rangeStartAyah!!)
        rangeStartPage != null -> QuranMetadata.pageStart(rangeStartPage!!)
        startMode == "plan_start" -> when {
            plan.rangeStartSurah != null && plan.rangeStartAyah != null -> QuranLocation(plan.rangeStartSurah, plan.rangeStartAyah)
            plan.rangeStartPage != null -> QuranMetadata.pageStart(plan.rangeStartPage)
            else -> null
        }
        else -> null
    }
    val numericTarget = target.toDoubleOrNull()
    val previewTargetPages = when (goalMode) {
        "pages" -> numericTarget
        "juz" -> numericTarget?.times(20.0)
        else -> null
    }
    val phasePreview = remember(type, startMode, previewStart, previewTargetPages, goalMode, rangeStartSurah, rangeStartAyah, rangeEndSurah, rangeEndAyah, phaseRoute) {
        runCatching {
            val previewTrack = if (type == "tathbit") "tathbit" else "general_hifz"
            val previewActivity = SmartPlanActivityRules.activityTypeFor(anchorActivity)
            when {
                goalMode == "quran_range" && rangeStartSurah != null && rangeStartAyah != null && rangeEndSurah != null && rangeEndAyah != null ->
                    QuranCore.engine().calculate(
                        QuranRangeRequest(
                            track = previewTrack,
                            activityType = previewActivity,
                            selection = QuranSelection.Ayahs(QuranLocation(rangeStartSurah!!, rangeStartAyah!!), QuranLocation(rangeEndSurah!!, rangeEndAyah!!)),
                            direction = traversal,
                        )
                    )
                previewStart != null && previewTargetPages != null && previewTargetPages > 0.0 ->
                    QuranCore.engine().previewPagesFrom(previewStart, previewTargetPages, previewTrack, previewActivity, traversal)
                else -> null
            }
        }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.92f),
            shape = RoundedCornerShape(26.dp),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (source == null && type == "sard") "إعداد مرحلة السرد" else if (source == null) "إضافة مرحلة" else "تعديل المرحلة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text("عدّل الإعدادات ثم راجع المعاينة قبل الاعتماد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "إغلاق") }
                }
                HorizontalDivider()

                LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("نوع المرحلة", style = MaterialTheme.typography.labelLarge)
                        ResponsiveChipGrid(items = listOf("hifz" to "حفظ", "sard" to "سرد"), minItemWidth = 120.dp, maxColumns = 2) { pair, itemModifier ->
                            ProFilterChip(
                                selected = type == pair.first,
                                onClick = {
                                    if (source == null && pair.first == "hifz") { type = "hifz"; title = "مرحلة الحفظ"; goalMode = "pages"; target = phaseNumber(plan.target); startMode = "plan_start" }
                                    if (source == null && pair.first == "sard") { type = "sard"; title = "مرحلة السرد"; goalMode = "pages"; target = "25"; startMode = "last_activity"; distribution = "flexible" }
                                },
                                label = pair.second,
                                modifier = itemModifier.fillMaxWidth(),
                                showCheckIcon = false,
                            )
                        }
                        if (source != null) Text("نوع المرحلة ثابت بعد إنشائها لحماية التسميعات المرتبطة بها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    item { OutlinedTextField(title, { title = it }, label = { Text("اسم المرحلة") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }

                    item {
                        Text("الفترة", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                                Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Column { Text("البداية", style = MaterialTheme.typography.labelSmall); Text(startDate) }
                            }
                            OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                                Icon(Icons.Default.Event, null); Spacer(Modifier.width(6.dp)); Column { Text("النهاية", style = MaterialTheme.typography.labelSmall); Text(endDate) }
                            }
                        }
                    }

                    item {
                        Text("أيام المرحلة", style = MaterialTheme.typography.labelLarge)
                        ResponsiveChipGrid(items = (0..6).map { it to phaseWeekdayName(it) }, minItemWidth = 104.dp, maxColumns = 3) { pair, itemModifier ->
                            ProFilterChip(
                                selected = pair.first in weekdays,
                                onClick = { weekdays = if (pair.first in weekdays) weekdays - pair.first else weekdays + pair.first },
                                label = pair.second,
                                modifier = itemModifier.fillMaxWidth(),
                                showCheckIcon = false,
                            )
                        }
                    }

                    item {
                        Text("نوع الهدف", style = MaterialTheme.typography.labelLarge)
                        val goalOptions = if (type == "sard")
                            listOf("pages" to "صفحات", "juz" to "أجزاء", "quran_range" to "نطاق قرآني", "open" to "سرد مفتوح")
                        else listOf("pages" to "صفحات", "juz" to "أجزاء", "quran_range" to "نطاق قرآني")
                        ResponsiveChipGrid(items = goalOptions, minItemWidth = 122.dp, maxColumns = 2) { pair, itemModifier ->
                            ProFilterChip(
                                selected = goalMode == pair.first,
                                onClick = {
                                    goalMode = pair.first
                                    if (pair.first == "pages" && target.toDoubleOrNull() == null) target = if (type == "sard") "25" else phaseNumber(plan.target)
                                    if (pair.first == "quran_range") startMode = "range_start"
                                },
                                label = pair.second,
                                modifier = itemModifier.fillMaxWidth(),
                                showCheckIcon = false,
                            )
                        }
                    }
                    if (goalMode != "open" && goalMode != "quran_range") item {
                        OutlinedTextField(
                            target,
                            { target = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text(if (goalMode == "juz") "عدد الأجزاء" else "الهدف بالصفحات") },
                            supportingText = { if (type == "sard" && goalMode == "pages") Text("الافتراضي في RC11.3 هو 25 صفحة") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                        )
                    }

                    if (type in setOf("hifz", "sard", "tathbit")) {
                        item {
                            Text("نقطة بداية المرحلة", style = MaterialTheme.typography.labelLarge)
                            val latestLabel = when (type) { "sard" -> "آخر سرد"; "tathbit" -> "آخر تثبيت"; else -> "آخر تسميع حفظ" }
                            val startOptions = listOf("last_activity" to latestLabel, "plan_start" to "بداية الخطة", "manual" to "تحديد يدوي", "range_start" to "بداية نطاق")
                            ResponsiveChipGrid(items = startOptions, minItemWidth = 150.dp, maxColumns = 2) { pair, itemModifier ->
                                ProFilterChip(
                                    selected = startMode == pair.first,
                                    onClick = { startMode = pair.first },
                                    label = pair.second,
                                    modifier = itemModifier.fillMaxWidth(),
                                    showCheckIcon = false,
                                )
                            }
                        }

                        if (startMode == "last_activity") {
                            item {
                                if (previewSnapshots.size > 1) {
                                    Rc11SearchChoiceField(
                                        label = "طالب لمعاينة آخر موضع",
                                        selectedId = previewStudentId,
                                        options = previewSnapshots.map { it.student.id to it.student.fullName },
                                        onSelected = { previewStudentId = it },
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f))) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("آخر موضع معتمد قبل بداية المرحلة", fontWeight = FontWeight.Bold)
                                        when {
                                            loadingActivityAnchor -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                            latestActivity != null -> {
                                                val label = when (type) { "sard" -> "آخر سرد"; "tathbit" -> "آخر تثبيت"; else -> "آخر حفظ" }
                                                Text("$label: ${recitationRangeLabel(latestActivity!!)}")
                                                Text("التاريخ: ${latestActivity!!.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                previewStart?.let { Text("بداية المرحلة التالية: ${quranLocationLabel(it)}", fontWeight = FontWeight.SemiBold) }
                                                    ?: Text("لا يوجد موضع تالٍ في الاتجاه المختار.", color = MaterialTheme.colorScheme.error)
                                            }
                                            else -> {
                                                Text("لا يوجد موضع سابق معتمد لهذا الطالب قبل بداية المرحلة؛ سيستخدم النظام بداية الخطة إن كانت محددة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                TextButton(onClick = { startMode = "manual" }) { Text("تحديد البداية يدويًا") }
                                            }
                                        }
                                        if (previewSnapshots.size > 1) Text("لكل طالب نقطة استمرار مستقلة؛ هذه معاينة للطالب المختار فقط.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    if (goalMode == "quran_range" || startMode == "manual" || startMode == "range_start") item {
                        OutlinedButton(onClick = { showRange = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                            Icon(Icons.Default.AutoStories, null); Spacer(Modifier.width(6.dp));
                            Text(if (rangeStartSurah != null) "تعديل البداية / النطاق القرآني" else "اختيار البداية / النطاق القرآني")
                        }
                    }

                    if (type == "sard") item {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("اتجاه السرد", fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ProFilterChip(sardRoute == "reverse_surahs_forward_ayahs", { sardRoute = "reverse_surahs_forward_ayahs" }, "من الناس إلى البقرة")
                                    ProFilterChip(sardRoute == "forward_surahs_forward_ayahs", { sardRoute = "forward_surahs_forward_ayahs" }, "من البقرة إلى الناس")
                                }
                                Text(
                                    if (sardRoute == "reverse_surahs_forward_ayahs") "الافتراضي لمسار الحفظ العام: من الناس باتجاه البقرة." else "اتجاه مخصص: من البقرة باتجاه الناس.",
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
                                )
                                Text("داخل كل سورة تبقى الآيات تصاعدية 1 ← آخر آية؛ التغيير يكون في ترتيب السور فقط.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (type in setOf("hifz", "sard", "tathbit")) item {
                        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f))) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Preview, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("معاينة نطاق المرحلة قبل الحفظ", fontWeight = FontWeight.ExtraBold)
                                }
                                when {
                                    goalMode == "open" -> Text("سرد مفتوح: لا توجد نهاية محددة. سيبدأ من ${previewStart?.let(::quranLocationLabel) ?: "النقطة التي تحددها"}.")
                                    phasePreview != null -> {
                                        Text("المطلوب: ${quranLocationLabel(phasePreview.start)} ← ${quranLocationLabel(phasePreview.end)}", fontWeight = FontWeight.SemiBold)
                                        Text("الحجم المحسوب: ${phaseNumber(phasePreview.exactPages)} صفحة · الاتجاه: ${phaseRouteLabel(phaseRoute)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    startMode == "last_activity" && latestActivity == null -> Text("لا يوجد Anchor سابق معتمد؛ سيستخدم النظام بداية الخطة أو البداية اليدوية.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    else -> Text("حدد بداية صحيحة وهدفًا حتى يظهر النطاق المتوقع.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    item {
                        Text("التوزيع", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProFilterChip(distribution == "equal", { distribution = "equal" }, "متساوٍ")
                            ProFilterChip(distribution == "flexible", { distribution = "flexible" }, "مرن")
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("استمرار إضافي بعد إكمال الهدف", modifier = Modifier.weight(1f))
                            Switch(checked = allowSurplus, onCheckedChange = { allowSurplus = it })
                        }
                    }
                    error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                    Button(
                        onClick = {
                            try {
                                error = null
                                val s = LocalDate.parse(startDate)
                                val e = LocalDate.parse(endDate)
                                require(e >= s) { "الفترة غير صالحة" }
                                require(weekdays.isNotEmpty()) { "اختر أيام المرحلة" }
                                val numeric = target.toDoubleOrNull()
                                if (goalMode !in setOf("open", "quran_range")) require(numeric != null && numeric > 0) { "حدد الهدف" }
                                if (goalMode == "quran_range") require(rangeStartSurah != null && rangeStartAyah != null && rangeEndSurah != null && rangeEndAyah != null) { "حدد النطاق القرآني كاملًا" }
                                val pages = when (goalMode) {
                                    "juz" -> numeric?.times(20.0)
                                    "open" -> null
                                    "quran_range" -> phasePreview?.exactPages ?: numeric
                                    else -> numeric
                                }
                                val units = when (goalMode) {
                                    "juz" -> numeric
                                    "open" -> null
                                    "quran_range" -> pages
                                    else -> numeric
                                }
                                val draft = SmartPlanPhaseDraft(
                                    id = source?.id, planId = plan.id, phaseOrder = source?.phaseOrder ?: nextOrder, phaseType = type, title = title,
                                    startDate = startDate, endDate = endDate, scheduleWeekdays = weekdays.sorted(), goalMode = goalMode,
                                    goalUnit = when (goalMode) { "juz" -> "juz"; "quran_range" -> "range"; "open" -> "open"; else -> "pages" },
                                    targetUnits = units, targetPages = pages, distributionMode = distribution, startMode = startMode,
                                    rangeStartPage = rangeStartPage, rangeEndPage = rangeEndPage, rangeStartSurah = rangeStartSurah, rangeStartAyah = rangeStartAyah,
                                    rangeEndSurah = rangeEndSurah, rangeEndAyah = rangeEndAyah, allowSurplus = allowSurplus, status = source?.status ?: "active",
                                    settings = JSONObject(source?.settings?.toString() ?: "{}").apply {
                                        if (type == "sard") put("route_direction", sardRoute)
                                    },
                                    expectedRevision = source?.revision,
                                )
                                if (source == null) onSave(draft) else pendingDraft = draft
                            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { error = e.message ?: "تحقق من البيانات" }
                        },
                        modifier = Modifier.weight(1.6f).heightIn(min = 48.dp),
                    ) { Text(if (source == null) "اعتماد المرحلة" else "معاينة التعديل") }
                }
            }
        }
    }

    if (showStartDatePicker) {
        Rc11DatePickerDialog(startDate, { showStartDatePicker = false }) { startDate = it; showStartDatePicker = false }
    }
    if (showEndDatePicker) {
        Rc11DatePickerDialog(endDate, { showEndDatePicker = false }) { endDate = it; showEndDatePicker = false }
    }

    pendingDraft?.let { draft ->
        val stateCount = source?.let { src -> states.count { it.phaseId == src.id && it.status in setOf("active", "completed") } } ?: 0
        val sample = source?.let { src -> snapshots.firstOrNull { it.phase?.id == src.id } }
        val sampleCompleted = sample?.phaseProgress?.completedPages
        val newTarget = draft.targetPages ?: draft.targetUnits?.takeIf { draft.goalMode == "juz" }?.times(20.0)
        val previewStartDate = runCatching { LocalDate.parse(draft.startDate) }.getOrNull()
        val previewEndDate = runCatching { LocalDate.parse(draft.endDate) }.getOrNull()
        val today = LocalDate.now()
        val remainingDays = if (previewStartDate != null && previewEndDate != null) {
            val first = maxOf(today, previewStartDate)
            if (first > previewEndDate) 0 else generateSequence(first) { it.plusDays(1) }
                .takeWhile { it <= previewEndDate }
                .count { (it.dayOfWeek.value % 7) in draft.scheduleWeekdays }
        } else 0
        val sampleRemaining = if (sampleCompleted != null && newTarget != null) max(0.0, newTarget - sampleCompleted) else null
        HalaqatiAlertDialog(
            onDismissRequest = { pendingDraft = null },
            title = { Text("معاينة تأثير التعديل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("الفترة الجديدة: ${draft.startDate} ← ${draft.endDate}")
                    Text("الهدف الجديد: ${newTarget?.let { "${phaseNumber(it)} ص" } ?: "مفتوح"}")
                    Text("هناك $stateCount طالب لديهم حالة منفذة/نشطة في هذه المرحلة.")
                    if (sampleCompleted != null) Text("مثال: ${sample?.student?.fullName ?: "طالب"} أنجز ${phaseNumber(sampleCompleted)} ص.")
                    if (sampleRemaining != null) {
                        Text("المتبقي للمثال: ${phaseNumber(sampleRemaining)} ص · الأيام المجدولة المتبقية: $remainingDays")
                        if (remainingDays > 0) Text("المقترح بعد التعديل ≈ ${phaseNumber(sampleRemaining / remainingDays)} ص/يوم، ثم يعاد توزيعه حسب الإنجاز.")
                    }
                    Text("التسميعات المنفذة لا تُحذف؛ يعاد حساب المقرر القادم فقط.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(onClick = { pendingDraft = null; onSave(draft) }) { Text("اعتماد التعديل") } },
            dismissButton = { TextButton(onClick = { pendingDraft = null }) { Text("رجوع للتعديل") } },
        )
    }

    if (showRange) {
        QuranRangePickerDialog(
            initialStartPage = rangeStartPage,
            initialEndPage = rangeEndPage,
            initialStartSurah = rangeStartSurah,
            initialStartAyah = rangeStartAyah,
            initialEndSurah = rangeEndSurah,
            initialEndAyah = rangeEndAyah,
            track = if (type == "tathbit") "tathbit" else "general_hifz",
            activityType = when (type) { "sard" -> "sard"; "tathbit" -> "tathbit"; else -> "new_hifz" },
            direction = if (type == "sard") traversal else phaseTraversalForRoute(plan.settings.routeDirection ?: "reverse_surahs_forward_ayahs"),
            onDismiss = { showRange = false },
            onConfirm = { selected ->
                rangeStartPage = selected.startPage; rangeEndPage = selected.endPage
                rangeStartSurah = selected.start?.surah; rangeStartAyah = selected.start?.ayah
                rangeEndSurah = selected.end?.surah; rangeEndAyah = selected.end?.ayah
                if (goalMode == "quran_range") target = phaseNumber(selected.exactPages)
                showRange = false
            },
        )
    }
}


@Composable
fun Rc11DatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initialMillis = runCatching {
        LocalDate.parse(initialDate).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                onConfirm(date.toString())
            }) { Text("اختيار") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    ) {
        DatePicker(state = state, title = { Text("اختر التاريخ", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@Composable
private fun Rc11SearchChoiceField(
    label: String,
    selectedId: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    HalaqatiLazySelectField(
        label = label,
        selectedKey = selectedId,
        options = options,
        onSelected = onSelected,
        modifier = Modifier.fillMaxWidth(),
        searchable = true,
    )
}

private fun phaseWeekdayName(index: Int): String = when (index) {
    0 -> "الأحد"
    1 -> "الاثنين"
    2 -> "الثلاثاء"
    3 -> "الأربعاء"
    4 -> "الخميس"
    5 -> "الجمعة"
    6 -> "السبت"
    else -> "—"
}

private fun phaseStartModeAr(mode: String): String = when (mode) {
    "last_sard" -> "آخر سرد (قديم)"
    "last_activity" -> "آخر تسميع معتمد"
    "manual" -> "تحديد يدوي"
    "range_start" -> "بداية نطاق"
    else -> "بداية الخطة"
}

private fun phaseRouteLabel(route: String): String = when (route) {
    "reverse_surahs_forward_ayahs" -> "من الناس باتجاه البقرة"
    else -> "من البقرة باتجاه الناس"
}

private fun phaseTraversalForRoute(route: String): QuranTraversalDirection = when (route) {
    "reverse_surahs_forward_ayahs" -> QuranTraversalDirection.REVERSE_SURAH_ORDER
    else -> QuranTraversalDirection.FORWARD_QURAN
}

internal fun quranLocationLabel(location: QuranLocation?): String = location?.let {
    "${QuranMetadata.surahName(it.surah)} ${it.ayah}"
} ?: "—"

private fun recitationEndLabel(recitation: SmartPlanEngine.Recitation): String = when {
    recitation.endSurah != null && recitation.endAyah != null -> "${QuranMetadata.surahName(recitation.endSurah)} ${recitation.endAyah}"
    recitation.endPage != null -> "ص ${recitation.endPage}"
    else -> "—"
}

internal fun recitationRangeLabel(recitation: SmartPlanEngine.Recitation): String {
    val start = if (recitation.startSurah != null && recitation.startAyah != null) {
        "${QuranMetadata.surahName(recitation.startSurah)} ${recitation.startAyah}"
    } else recitation.startPage?.let { "ص $it" } ?: "—"
    return "$start ← ${recitationEndLabel(recitation)}"
}

private fun SmartPlanPhaseRow.toDraftWithPagesTarget(target: Double): SmartPlanPhaseDraft = SmartPlanPhaseDraft(
    id = id,
    planId = planId,
    phaseOrder = phaseOrder,
    phaseType = phaseType,
    title = title,
    startDate = startDate,
    endDate = endDate,
    scheduleWeekdays = scheduleWeekdays,
    goalMode = "pages",
    goalUnit = "pages",
    targetUnits = target,
    targetPages = target,
    distributionMode = distributionMode,
    startMode = startMode,
    rangeStartPage = rangeStartPage,
    rangeEndPage = rangeEndPage,
    rangeStartSurah = rangeStartSurah,
    rangeStartAyah = rangeStartAyah,
    rangeEndSurah = rangeEndSurah,
    rangeEndAyah = rangeEndAyah,
    allowSurplus = allowSurplus,
    status = status,
    settings = JSONObject(settings.toString()),
    expectedRevision = revision,
)


private fun templateModeAr(mode: String): String = when (mode) {
    "dates" -> "بالتاريخ"
    "weeks" -> "بالأسابيع"
    else -> "يدوي"
}

private fun phaseTitle(phase: SmartPlanPhaseRow): String = phase.title.ifBlank { when (phase.phaseType) { "sard" -> "مرحلة السرد"; "tathbit" -> "مرحلة التثبيت"; else -> "مرحلة الحفظ" } }

private fun phaseGoalText(phase: SmartPlanPhaseRow): String = when (phase.goalMode) {
    "open" -> "الهدف: سرد مفتوح"
    "juz" -> "الهدف: ${phaseNumber(phase.targetUnits ?: ((phase.targetPages ?: 0.0) / 20.0))} جزء"
    "quran_range" -> "الهدف: نطاق قرآني · ${phase.targetPages?.let { "${phaseNumber(it)} ص" } ?: "حسب النطاق"}"
    else -> "الهدف: ${phaseNumber(phase.targetPages ?: 0.0)} صفحة"
}

private fun phaseNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (kotlin.math.abs(rounded - rounded.toInt()) < .0001) rounded.toInt().toString() else rounded.toString()
}

private fun LocalDate.coerceAtLeast(other: LocalDate): LocalDate = if (this < other) other else this


/** RC15.2: the only plan surface a tathbit student has. Month → range → target → progress. */
@Composable
fun TathbitMonthlyPlanSheet(
    plan: PlanRow,
    snapshots: List<PlanSnapshot>,
    onDismiss: () -> Unit,
) {
    val students = snapshots
        .filter { com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(it.student.track) }
        .distinctBy { it.student.id }
        .sortedBy { it.student.fullName }
    val month = runCatching { YearMonth.from(LocalDate.parse(plan.startDate)) }.getOrNull()
    val today = LocalDate.now()
    val ended = runCatching { LocalDate.parse(plan.endDate) < today }.getOrDefault(false)
    val rangeLabel = when {
        plan.rangeStartSurah != null && plan.rangeEndSurah != null ->
            "${QuranMetadata.surahName(plan.rangeStartSurah)} ${plan.rangeStartAyah ?: 1} ← ${QuranMetadata.surahName(plan.rangeEndSurah)} ${plan.rangeEndAyah ?: ""}".trim()
        plan.rangeStartPage != null && plan.rangeEndPage != null -> "ص ${plan.rangeStartPage} ← ص ${plan.rangeEndPage}"
        else -> "غير محدد"
    }
    val days = plan.scheduleWeekdays.sorted().joinToString("، ") { phaseWeekdayName(it) }.ifBlank { "—" }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("خطة التثبيت الشهرية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "المسار دائم (تثبيت) والخطة شهرية. عند نهاية الشهر تُجمَّد نتيجتها للتقارير وتبدأ خطة الشهر التالي تلقائيًا.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("الشهر: ${month?.toString() ?: plan.startDate}", fontWeight = FontWeight.Bold)
                        Text("المدة: ${plan.startDate} ← ${plan.endDate}${if (ended) " · مكتملة ومجمّدة" else ""}", style = MaterialTheme.typography.bodyMedium)
                        Text("نطاق التثبيت: $rangeLabel", style = MaterialTheme.typography.bodyMedium)
                        Text("الهدف: ${phaseNumber(plan.target)} صفحة", style = MaterialTheme.typography.bodyMedium)
                        Text("أيام التسميع: $days", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (students.isEmpty()) item {
                Text("لا يوجد طالب في مسار التثبيت مرتبط بهذه الخطة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(students, key = { it.student.id }) { snap ->
                val progress = snap.progress
                val pct = progress.percentage.coerceIn(0.0, 100.0)
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OneLineName(snap.student.fullName, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(drawStopIndicator = {}, progress = { (pct / 100.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "المنجز ${phaseNumber(progress.completed)} ص · المتبقي ${phaseNumber(progress.remaining)} ص · ${phaseNumber(pct)}٪",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "آخر موضع تثبيت: ${snap.lastRecitation?.let { recitationEndLabel(it) } ?: "لا يوجد بعد"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
