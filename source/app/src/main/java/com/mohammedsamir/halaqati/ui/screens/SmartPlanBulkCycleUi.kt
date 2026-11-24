@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import com.mohammedsamir.halaqati.ui.components.OneLineName
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.BulkPlanCycleRequest
import com.mohammedsamir.halaqati.data.PlanSnapshot
import com.mohammedsamir.halaqati.data.StudentTrackRules
import com.mohammedsamir.halaqati.model.PlanRow
import com.mohammedsamir.halaqati.model.SmartPlanPhaseRow
import com.mohammedsamir.halaqati.model.SmartPlanPhaseStateRow
import com.mohammedsamir.halaqati.model.Student
import com.mohammedsamir.halaqati.plans.SmartPlanPhaseEngine
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.ResponsiveChipGrid
import com.mohammedsamir.halaqati.ui.components.StateNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

private data class BulkCycleRow(
    val plan: PlanRow,
    val student: Student,
    val snapshot: PlanSnapshot?,
    val hifzPhase: SmartPlanPhaseRow?,
    val sardPhase: SmartPlanPhaseRow?,
    val hifzState: SmartPlanPhaseStateRow?,
    val sardState: SmartPlanPhaseStateRow?,
) {
    val hifzTarget: Double
        get() = hifzState?.frozenTargetPages
            ?: hifzState?.targetPagesOverride
            ?: hifzPhase?.targetPages
            ?: plan.target

    val hifzCompleted: Double
        get() = hifzState?.frozenCompletedPages
            ?: snapshot?.takeIf { it.phase?.phaseType != "sard" }?.phaseProgress?.completedPages
            ?: snapshot?.takeIf { it.phase?.phaseType != "sard" }?.progress?.completed
            ?: 0.0
}

private data class BulkCycleCustomization(
    val hifzStart: String,
    val hifzEnd: String,
    val sardStart: String,
    val sardEnd: String,
    val sardTarget: String,
    val direction: String,
    val sardWeekdays: Set<Int>,
)

/**
 * RC11.3: one halaqa-level controller for the Hifz -> Sard monthly cycle.
 * The teacher works once on the whole roster, while individual dates/targets remain overridable.
 */
@Composable
fun SmartPlanBulkCycleManagerSheet(
    plans: List<PlanRow>,
    students: List<Student>,
    snapshots: List<PlanSnapshot>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phases by remember { mutableStateOf<List<SmartPlanPhaseRow>>(emptyList()) }
    var states by remember { mutableStateOf<List<SmartPlanPhaseStateRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var applying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var schemaIssue by remember { mutableStateOf<String?>(null) }

    val today = remember { LocalDate.now() }
    var commonHifzStart by remember { mutableStateOf(today.withDayOfMonth(1).toString()) }
    var commonHifzEnd by remember { mutableStateOf(today.toString()) }
    var commonStartWasSeeded by remember { mutableStateOf(false) }
    var commonSardStart by remember { mutableStateOf(today.plusDays(1).toString()) }
    var commonSardEnd by remember {
        val start = today.plusDays(1)
        mutableStateOf(maxOf(start, YearMonth.from(start).atEndOfMonth()).toString())
    }
    var commonTarget by remember { mutableStateOf("25") }
    var commonDirection by remember { mutableStateOf(SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH) }
    var sardWeekdays by remember { mutableStateOf(setOf(0, 1, 2, 3, 4, 5)) }
    // the days the students really have when the sheet opened: untouched common days never overwrite an individual choice
    var initialSardWeekdays by remember { mutableStateOf(setOf(0, 1, 2, 3, 4, 5)) }
    var weekdaysWereSeeded by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedStudent by remember { mutableStateOf<String?>(null) }
    var customizations by remember { mutableStateOf<Map<String, BulkCycleCustomization>>(emptyMap()) }
    var showConfirm by remember { mutableStateOf(false) }
    var datePickerTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var studentQuery by remember { mutableStateOf("") }

    suspend fun load(force: Boolean = false) {
        loading = true
        try {
            phases = withContext(Dispatchers.IO) { AppGraph.repo.planPhases(forceRefresh = force) }
            states = withContext(Dispatchers.IO) { AppGraph.repo.planPhaseStates(forceRefresh = force) }
            schemaIssue = withContext(Dispatchers.IO) { AppGraph.repo.smartPlanPhaseSchemaIssue() }
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر تحميل إدارة الدورة الجماعية"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    val studentById = remember(students) { students.associateBy { it.id } }
    val snapshotByPlan = remember(snapshots) { snapshots.associateBy { it.plan.id } }
    val phasesByPlan = remember(phases) { phases.filter { it.status != "archived" }.groupBy { it.planId } }
    val statesByKey = remember(states) { states.associateBy { it.phaseId to it.studentId } }

    val rows = remember(plans, students, snapshots, phases, states) {
        plans.asSequence()
            .filter { it.status == "active" && it.scopeType == "student" && !it.studentId.isNullOrBlank() }
            .mapNotNull { plan ->
                val student = studentById[plan.studentId] ?: return@mapNotNull null
                if (student.status != "active" || StudentTrackRules.canonicalStored(student.track) != StudentTrackRules.GENERAL_HIFZ) return@mapNotNull null
                val pp = phasesByPlan[plan.id].orEmpty()
                val hifz = pp.firstOrNull { it.phaseType == "hifz" }
                val sard = pp.firstOrNull { it.phaseType == "sard" }
                BulkCycleRow(
                    plan = plan,
                    student = student,
                    snapshot = snapshotByPlan[plan.id],
                    hifzPhase = hifz,
                    sardPhase = sard,
                    hifzState = hifz?.let { statesByKey[it.id to student.id] },
                    sardState = sard?.let { statesByKey[it.id to student.id] },
                )
            }
            .sortedBy { it.student.fullName }
            .toList()
    }

    LaunchedEffect(rows.map { it.student.id }) {
        if (selectedIds.isEmpty() && rows.isNotEmpty()) selectedIds = rows.mapTo(linkedSetOf()) { it.student.id }
        if (!commonStartWasSeeded && rows.isNotEmpty()) {
            commonHifzStart = rows.groupingBy { it.hifzPhase?.startDate ?: it.plan.startDate }
                .eachCount().maxByOrNull { it.value }?.key ?: commonHifzStart
            commonStartWasSeeded = true
        }
        if (!weekdaysWereSeeded && rows.isNotEmpty()) {
            com.mohammedsamir.halaqati.plans.PhaseDaysRules.commonDays(rows.map { it.sardPhase?.scheduleWeekdays })?.let {
                sardWeekdays = it.toSet(); initialSardWeekdays = it.toSet()
            }
            weekdaysWereSeeded = true
        }
    }

    fun effectiveCustomization(row: BulkCycleRow): BulkCycleCustomization = customizations[row.student.id]
        ?: BulkCycleCustomization(
            commonHifzStart, commonHifzEnd, commonSardStart, commonSardEnd, commonTarget, commonDirection,
            com.mohammedsamir.halaqati.plans.PhaseDaysRules.resolve(emptyList(), sardWeekdays, initialSardWeekdays, row.sardPhase?.scheduleWeekdays).toSet(),
        )

    fun setCustomization(row: BulkCycleRow, transform: (BulkCycleCustomization) -> BulkCycleCustomization) {
        customizations = customizations + (row.student.id to transform(effectiveCustomization(row)))
    }

    val visibleRows = rows.filter { row ->
        studentQuery.isBlank() || row.student.fullName.contains(studentQuery.trim(), ignoreCase = true)
    }
    val selectedRows = rows.filter { it.student.id in selectedIds }
    val noPhases = selectedRows.count { it.hifzPhase == null && it.sardPhase == null }
    val alreadyInSard = selectedRows.count { it.sardState?.status in setOf("active", "completed") }
    val totalCompleted = selectedRows.sumOf { it.hifzCompleted }
    val totalTarget = selectedRows.sumOf { it.hifzTarget.coerceAtLeast(0.0) }
    val canMutate = schemaIssue == null && !applying

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("إدارة جماعية للحفظ والسرد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("أغلق مرحلة الحفظ وابدأ/جدول السرد لجميع الطلاب مرة واحدة، مع تخصيص أي طالب عند الحاجة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            schemaIssue?.let { issue ->
                item { StateNotice(issue, kind = "error", modifier = Modifier.fillMaxWidth()) }
            }
            message?.let { text ->
                item { StateNotice(text, kind = if (text.startsWith("تم")) "success" else "warning", modifier = Modifier.fillMaxWidth()) }
            }

            item {
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الإعداد الموحد", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BulkDateButton("بداية الحفظ", commonHifzStart, Modifier.weight(1f)) { datePickerTarget = "common_hifz_start" to commonHifzStart }
                            BulkDateButton("نهاية الحفظ", commonHifzEnd, Modifier.weight(1f)) { datePickerTarget = "common_hifz_end" to commonHifzEnd }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BulkDateButton("بداية السرد", commonSardStart, Modifier.weight(1f)) { datePickerTarget = "common_sard_start" to commonSardStart }
                            BulkDateButton("نهاية السرد", commonSardEnd, Modifier.weight(1f)) { datePickerTarget = "common_sard_end" to commonSardEnd }
                        }
                        OutlinedTextField(
                            value = commonTarget,
                            onValueChange = { commonTarget = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("هدف السرد الموحد بالصفحات") },
                            supportingText = { Text("الافتراضي 25 صفحة. هدف الحفظ يبقى مختلفًا لكل طالب.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("اتجاه السرد", style = MaterialTheme.typography.labelLarge)
                        ResponsiveChipGrid(
                            items = listOf(
                                SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH to "من الناس إلى البقرة",
                                SmartPlanPhaseEngine.ROUTE_FROM_BAQARAH_TO_NAS to "من البقرة إلى الناس",
                            ), minItemWidth = 140.dp, maxColumns = 2,
                        ) { (route, label), itemModifier ->
                            ProFilterChip(commonDirection == route, { commonDirection = route }, label, itemModifier.fillMaxWidth(), showCheckIcon = false)
                        }
                        Text("أيام السرد", style = MaterialTheme.typography.labelLarge)
                        ResponsiveChipGrid(items = listOf(0 to "الأحد", 1 to "الاثنين", 2 to "الثلاثاء", 3 to "الأربعاء", 4 to "الخميس", 5 to "الجمعة", 6 to "السبت"), minItemWidth = 104.dp, maxColumns = 3) { (day, label), itemModifier ->
                            ProFilterChip(
                                selected = day in sardWeekdays,
                                onClick = { sardWeekdays = if (day in sardWeekdays) sardWeekdays - day else sardWeekdays + day },
                                label = label,
                                modifier = itemModifier.fillMaxWidth(),
                                showCheckIcon = false,
                            )
                        }
                        if (6 in sardWeekdays) Text("تنبيه: السبت إجازة ثابتة في الحلقة؛ اختره فقط إذا أردت مرحلة استثنائية.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = studentQuery,
                    onValueChange = { studentQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("بحث باسم الطالب") },
                    singleLine = true,
                )
            }

            item {
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("المعاينة العامة", fontWeight = FontWeight.Bold)
                        Text("المحدد: ${selectedRows.size} من ${rows.size} طالب")
                        Text("الحفظ الحالي: ${bulkN(totalCompleted)} / ${bulkN(totalTarget)} صفحة")
                        if (noPhases > 0) Text("$noPhases خطة بدون مراحل: سيتم إنشاء مرحلتي الحفظ والسرد تلقائيًا.", color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                        if (alreadyInSard > 0) Text("$alreadyInSard طالب لديهم سرد نشط/مكتمل؛ لن تُفقد حالتهم، وأي إعادة تشغيل ستكون idempotent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { selectedIds = rows.mapTo(linkedSetOf()) { it.student.id } }) { Text("تحديد الكل") }
                            TextButton(onClick = { selectedIds = emptySet() }) { Text("إلغاء الكل") }
                        }
                    }
                }
            }

            items(visibleRows, key = { it.student.id }) { row ->
                val checked = row.student.id in selectedIds
                val custom = customizations[row.student.id]
                val effective = effectiveCustomization(row)
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = checked, onCheckedChange = { yes -> selectedIds = if (yes) selectedIds + row.student.id else selectedIds - row.student.id })
                            Column(Modifier.weight(1f)) {
                                OneLineName(row.student.fullName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (row.hifzPhase == null && row.sardPhase == null) {
                                    Text("لا توجد مراحل بعد · سيتم إنشاؤها عند الاعتماد", style = MaterialTheme.typography.labelSmall, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                                }
                            }
                            TextButton(enabled = checked, onClick = { expandedStudent = if (expandedStudent == row.student.id) null else row.student.id }) {
                                Icon(Icons.Default.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(if (custom == null) "تخصيص" else "مخصص")
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f),
                            ) {
                                Column(Modifier.padding(9.dp)) {
                                    Text("مرحلة الحفظ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("${bulkN(row.hifzCompleted)} / ${bulkN(row.hifzTarget)} ص", style = MaterialTheme.typography.bodySmall)
                                    Text(when (row.hifzState?.status) { "completed" -> "مكتملة"; "active" -> "نشطة"; else -> "حسب الخطة الحالية" }, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .45f),
                            ) {
                                Column(Modifier.padding(9.dp)) {
                                    Text("مرحلة السرد", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("${bulkN(row.sardState?.targetPagesOverride ?: row.sardPhase?.targetPages ?: commonTarget.toDoubleOrNull() ?: 25.0)} ص", style = MaterialTheme.typography.bodySmall)
                                    Text(when (row.sardState?.status) { "completed" -> "مكتملة"; "active" -> "نشطة"; "not_started" -> "مجدولة"; else -> "لم تبدأ" }, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (checked && expandedStudent == row.student.id) {
                            HorizontalDivider()
                            Text("تخصيص هذا الطالب فقط", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BulkDateButton("بداية الحفظ", effective.hifzStart, Modifier.weight(1f)) { datePickerTarget = "student_hifz_start:${row.student.id}" to effective.hifzStart }
                                BulkDateButton("نهاية الحفظ", effective.hifzEnd, Modifier.weight(1f)) { datePickerTarget = "student_hifz_end:${row.student.id}" to effective.hifzEnd }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BulkDateButton("بداية السرد", effective.sardStart, Modifier.weight(1f)) { datePickerTarget = "student_sard_start:${row.student.id}" to effective.sardStart }
                                BulkDateButton("نهاية السرد", effective.sardEnd, Modifier.weight(1f)) { datePickerTarget = "student_sard_end:${row.student.id}" to effective.sardEnd }
                            }
                            OutlinedTextField(
                                value = effective.sardTarget,
                                onValueChange = { v -> setCustomization(row) { it.copy(sardTarget = v.filter { ch -> ch.isDigit() || ch == '.' }) } },
                                label = { Text("هدف سرد خاص") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            ResponsiveChipGrid(
                                items = listOf(
                                    SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH to "الناس ← البقرة",
                                    SmartPlanPhaseEngine.ROUTE_FROM_BAQARAH_TO_NAS to "البقرة ← الناس",
                                ), minItemWidth = 130.dp, maxColumns = 2,
                            ) { (route, label), itemModifier ->
                                ProFilterChip(effective.direction == route, { setCustomization(row) { it.copy(direction = route) } }, label, itemModifier.fillMaxWidth(), showCheckIcon = false)
                            }
                            Text("أيام السرد لهذا الطالب", style = MaterialTheme.typography.labelMedium)
                            ResponsiveChipGrid(items = listOf(0 to "الأحد", 1 to "الاثنين", 2 to "الثلاثاء", 3 to "الأربعاء", 4 to "الخميس", 5 to "الجمعة", 6 to "السبت"), minItemWidth = 104.dp, maxColumns = 3) { (day, label), itemModifier ->
                                ProFilterChip(
                                    selected = day in effective.sardWeekdays,
                                    onClick = { setCustomization(row) { c -> c.copy(sardWeekdays = if (day in c.sardWeekdays) c.sardWeekdays - day else c.sardWeekdays + day) } },
                                    label = label,
                                    modifier = itemModifier.fillMaxWidth(),
                                    showCheckIcon = false,
                                )
                            }
                            if (custom != null) TextButton(onClick = { customizations = customizations - row.student.id }) { Text("إزالة التخصيص والعودة للإعداد الموحد") }
                        }
                    }
                }
            }

            item {
                val commonDatesValid = runCatching {
                    LocalDate.parse(commonHifzEnd) >= LocalDate.parse(commonHifzStart) &&
                        LocalDate.parse(commonSardStart) > LocalDate.parse(commonHifzEnd) &&
                        LocalDate.parse(commonSardEnd) >= LocalDate.parse(commonSardStart)
                }.getOrDefault(false)
                Button(
                    onClick = { showConfirm = true },
                    enabled = canMutate && selectedRows.isNotEmpty() && commonTarget.toDoubleOrNull()?.let { it > 0.0 } == true && commonDatesValid && sardWeekdays.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Default.RecordVoiceOver, null); Spacer(Modifier.width(7.dp)); Text("معاينة وإعتماد الانتقال الجماعي")
                }
            }
        }
    }

    datePickerTarget?.let { (key, current) ->
        Rc11DatePickerDialog(
            initialDate = current,
            onDismiss = { datePickerTarget = null },
            onConfirm = { chosen ->
                when {
                    key == "common_hifz_start" -> commonHifzStart = chosen
                    key == "common_hifz_end" -> commonHifzEnd = chosen
                    key == "common_sard_start" -> commonSardStart = chosen
                    key == "common_sard_end" -> commonSardEnd = chosen
                    key.startsWith("student_hifz_start:") -> rows.firstOrNull { it.student.id == key.substringAfter(':') }?.let { row -> setCustomization(row) { it.copy(hifzStart = chosen) } }
                    key.startsWith("student_hifz_end:") -> rows.firstOrNull { it.student.id == key.substringAfter(':') }?.let { row -> setCustomization(row) { it.copy(hifzEnd = chosen) } }
                    key.startsWith("student_sard_start:") -> rows.firstOrNull { it.student.id == key.substringAfter(':') }?.let { row -> setCustomization(row) { it.copy(sardStart = chosen) } }
                    key.startsWith("student_sard_end:") -> rows.firstOrNull { it.student.id == key.substringAfter(':') }?.let { row -> setCustomization(row) { it.copy(sardEnd = chosen) } }
                }
                datePickerTarget = null
            },
        )
    }

    if (showConfirm) {
        val requests = selectedRows.mapNotNull { row ->
            val c = effectiveCustomization(row)
            val hStart = runCatching { LocalDate.parse(c.hifzStart) }.getOrNull() ?: return@mapNotNull null
            val hEnd = runCatching { LocalDate.parse(c.hifzEnd) }.getOrNull() ?: return@mapNotNull null
            val sStart = runCatching { LocalDate.parse(c.sardStart) }.getOrNull() ?: return@mapNotNull null
            val sEnd = runCatching { LocalDate.parse(c.sardEnd) }.getOrNull() ?: return@mapNotNull null
            val target = c.sardTarget.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@mapNotNull null
            if (hEnd < hStart || sStart <= hEnd || sEnd < sStart || c.sardWeekdays.isEmpty()) return@mapNotNull null
            BulkPlanCycleRequest(
                planId = row.plan.id,
                studentId = row.student.id,
                hifzCompletedPages = row.hifzCompleted,
                hifzTargetPages = row.hifzTarget,
                hifzStartDate = hStart.toString(),
                hifzEndDate = hEnd.toString(),
                sardStartDate = sStart.toString(),
                sardEndDate = sEnd.toString(),
                sardTargetPages = target,
                sardRouteDirection = c.direction,
                sardWeekdays = c.sardWeekdays.sorted(),
            )
        }
        val invalidCount = selectedRows.size - requests.size
        HalaqatiAlertDialog(
            onDismissRequest = { if (!applying) showConfirm = false },
            title = { Text("اعتماد دورة الحفظ ← السرد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("سيتم تحديث ${requests.size} طالب في عملية واحدة.", fontWeight = FontWeight.Bold)
                    Text("لكل طالب سيُجمّد إنجاز الحفظ وهدفه الحالي، ثم تُنشأ/تُحدّث مرحلة السرد حسب التواريخ والهدف والاتجاه المحدد.")
                    Text("الاتجاه الموحد الافتراضي: ${if (commonDirection == SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH) "من الناس إلى البقرة" else "من البقرة إلى الناس"}.")
                    if (customizations.isNotEmpty()) Text("هناك ${customizations.keys.count { it in selectedIds }} تخصيصات فردية.")
                    if (invalidCount > 0) Text("$invalidCount طالب لديهم تواريخ/هدف غير صالح ولن يتم الإرسال حتى تصحيحهم.", color = MaterialTheme.colorScheme.error)
                    Text("لن يتغير هدف الحفظ الفردي لأي طالب.", color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"), fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(
                    enabled = !applying && requests.isNotEmpty() && invalidCount == 0,
                    onClick = {
                        applying = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { AppGraph.repo.bulkConfigurePlanCycles(requests) }
                                message = "تم اعتماد الدورة الجماعية لـ${requests.size} طالب."
                                com.mohammedsamir.halaqati.ui.components.HalaqatiMotionEvents.saved()
                                showConfirm = false
                                load(force = true)
                                onChanged()
                            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                                message = com.mohammedsamir.halaqati.plans.PlanErrorRules.friendly(e.message).takeIf { e.message != null } ?: "تعذر تنفيذ الانتقال الجماعي"
                            } finally {
                                applying = false
                            }
                        }
                    },
                ) { Text(if (applying) "جاري الاعتماد…" else "اعتماد وتنفيذ") }
            },
            dismissButton = { TextButton(enabled = !applying, onClick = { showConfirm = false }) { Text("رجوع") } },
        )
    }
}

@Composable
private fun BulkDateButton(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 50.dp)) {
        Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun bulkN(v: Double): String = if (abs(v - v.toInt()) < .005) v.toInt().toString() else "%.1f".format(java.util.Locale.US, v)
