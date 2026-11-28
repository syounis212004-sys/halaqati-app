@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import com.mohammedsamir.halaqati.ui.components.nourHeroFill
import com.mohammedsamir.halaqati.ui.components.OneLineName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.DashPathEffect
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import com.mohammedsamir.halaqati.ui.components.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import com.mohammedsamir.halaqati.R
import com.mohammedsamir.halaqati.BuildConfig
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.ImportRules
import com.mohammedsamir.halaqati.data.LocalAccountScopeRules
import com.mohammedsamir.halaqati.data.SafetyScoring
import com.mohammedsamir.halaqati.data.StudentTrackRules
import com.mohammedsamir.halaqati.data.ThemeMode
import com.mohammedsamir.halaqati.data.UiDensity
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.Student
import com.mohammedsamir.halaqati.model.CertificateCoreDraft
import com.mohammedsamir.halaqati.model.CertificateRecord
import com.mohammedsamir.halaqati.model.CertificateRenderSnapshot
import com.mohammedsamir.halaqati.model.CertificateRevisionRow
import com.mohammedsamir.halaqati.model.CertificateSeriesRow
import com.mohammedsamir.halaqati.model.CertificateRules
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.documents.CertificateHtmlRenderer
import com.mohammedsamir.halaqati.documents.DocumentOrientation
import com.mohammedsamir.halaqati.documents.DocumentExportPhase
import com.mohammedsamir.halaqati.documents.DocumentExportManager
import com.mohammedsamir.halaqati.documents.HtmlDocumentPreview
import com.mohammedsamir.halaqati.documents.LiveDocumentPreview
import com.mohammedsamir.halaqati.documents.ReportDocumentType
import com.mohammedsamir.halaqati.documents.ReportHtmlRenderer
import com.mohammedsamir.halaqati.documents.VectorDocumentRenderer
import com.mohammedsamir.halaqati.quran.QuranCore
import com.mohammedsamir.halaqati.quran.QuranMetadata
import com.mohammedsamir.halaqati.quran.QuranRangeRequest
import com.mohammedsamir.halaqati.quran.QuranSelection
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.latticeOverlay
import com.mohammedsamir.halaqati.ui.components.SegmentedTabs
import com.mohammedsamir.halaqati.ui.components.GazaTime
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.MetricCard
import com.mohammedsamir.halaqati.ui.components.GoldenFeatureHero
import com.mohammedsamir.halaqati.ui.components.HalaqatiLazySelectField
import com.mohammedsamir.halaqati.ui.components.HalaqatiMonthPickerDialog
import com.mohammedsamir.halaqati.ui.components.HalaqatiDateRangePickerDialog
import com.mohammedsamir.halaqati.ui.components.HalaqatiDatePickerDialog
import com.mohammedsamir.halaqati.ui.components.HalaqatiDateField
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.WorkScopeBar
import com.mohammedsamir.halaqati.ui.components.WorkKindToggle
import com.mohammedsamir.halaqati.ui.components.rememberWorkKind
import com.mohammedsamir.halaqati.ui.components.ResponsiveChipGrid
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.components.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

internal const val REPORT_STUDIO_SCROLL_TAG = "ReportStudioScrollableSurface"
internal const val REPORT_STUDIO_LAST_OPTION_TAG = "ReportStudioLastOption"

@Composable
internal fun ReportStudioScrollableSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
        modifier = modifier.testTag(REPORT_STUDIO_SCROLL_TAG),
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

private data class ReportLoadBundle(
    val summary: JSONArray,
    val details: JSONArray,
    val notes: JSONArray,
    val students: List<Student>,
    val previousSummary: JSONArray,
    /** student id -> halaqa ids of the student's active extra memberships (programs / courses). */
    val memberHalaqas: Map<String, List<String>> = emptyMap(),
    val absences: JSONArray = JSONArray(),
)

@Composable
fun ReportsScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var reportFiltersExpanded by rememberSaveable { mutableStateOf(false) }
    var showStudio by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var summaryRows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var detailRows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var notesRows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var absenceRows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var reportStudents by remember { mutableStateOf<List<Student>>(emptyList()) }
    var reportView by remember { mutableStateOf(ReportDocumentType.HALAQA) }
    var mode by remember { mutableStateOf("month") }
    var track by remember { mutableStateOf("all") }
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var halaqaId by remember { mutableStateOf("all") }
    var sessionType by remember { mutableStateOf("all") }
    var from by remember { mutableStateOf(YearMonth.now().atDay(1).toString()) }
    var to by remember { mutableStateOf(YearMonth.now().atEndOfMonth().toString()) }
    var loading by remember { mutableStateOf(false) }
    var reportPdfBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var reportStyle by remember {
        mutableStateOf(
            NativeReportStyle(
                title = ReportDocumentType.title(ReportDocumentType.HALAQA),
                visibleColumns = defaultReportColumns(ReportDocumentType.HALAQA),
            ),
        )
    }
    var monthComparison by remember { mutableStateOf<MonthComparison?>(null) }
    var showDayPicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    var previewExpanded by remember { mutableStateOf(false) }
    var previewZoom by remember { mutableFloatStateOf(1f) }
    // RC27.10: reports follow the work mode. Programs: attendance, evaluation results, course report (no hifz/sard/tathbit).
    val reportKind = rememberWorkKind()
    val reportMode by com.mohammedsamir.halaqati.data.WorkModeHolder.state.collectAsState()
    val programReports = reportKind == com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM
    val hifzColumns = setOf("hifz", "tathbit", "review", "sard")
    fun reportTitleFor(type: String): String = if (programReports) when (type) {
        ReportDocumentType.ATTENDANCE -> "تقرير الحضور"
        ReportDocumentType.RECITATIONS -> "نتائج التقييم"
        else -> "تقرير الدورة"
    } else ReportDocumentType.title(type)
    fun reportColumnsFor(type: String): Set<String> = defaultReportColumns(type).let { cols -> if (programReports) cols.filterNot { it in hifzColumns }.toSet() else cols }
    LaunchedEffect(reportMode.selectedId) { halaqaId = reportMode.selectedId ?: "all" }
    LaunchedEffect(programReports) {
        if (programReports) {
            track = "all"
            if (reportView !in listOf(ReportDocumentType.ATTENDANCE, ReportDocumentType.RECITATIONS, ReportDocumentType.HALAQA)) reportView = ReportDocumentType.HALAQA
            if (sessionType !in listOf("all", "tajweed", "tilawah", "test", "lesson")) sessionType = "all"
        }
        reportStyle = reportStyle.copy(title = reportTitleFor(reportView), visibleColumns = reportColumnsFor(reportView))
    }

    var reportInitialized by remember { mutableStateOf(false) }
    val reportScopeIds = com.mohammedsamir.halaqati.plans.ReportScopeRules.ids(reportKind, halaqaId, reportMode.typeById)
    var reportRequest by remember { mutableIntStateOf(0) }
    fun halaqaLabel(): String = if (halaqaId == "all") (if (programReports) "جميع البرامج" else "جميع الحلقات") else {
        halaqas.firstOrNull { it.optString("id") == halaqaId }?.optString("name", "حلقة") ?: "حلقة"
    }

    fun applyMode(value: String) {
        when (value) {
            "day" -> showDayPicker = true
            "month" -> showMonthPicker = true
            "custom" -> showRangePicker = true
        }
    }

    suspend fun reload() {
        val request = ++reportRequest
        val selectedIds = reportScopeIds
        val selectedHalaqa = halaqaId
        val selectedTrack = if (programReports) "all" else track
        val selectedType = sessionType
        val selectedMode = mode
        val start = runCatching { LocalDate.parse(from) }.getOrNull()
        val end = runCatching { LocalDate.parse(to) }.getOrNull()
        // Clear before a new request: failed or slow scope switches cannot display a previous report.
        summaryRows = emptyList(); detailRows = emptyList(); notesRows = emptyList()
        absenceRows = emptyList(); reportStudents = emptyList(); monthComparison = null
        if (start == null || end == null || start > end) { loading = false; message = "تأكد من تاريخ البداية والنهاية"; return }
        if (selectedIds.isEmpty()) { loading = false; message = "اختر نطاقًا متاحًا للتقرير"; return }
        val cacheKey = NativeReportCache.key(profile.id, start.toString(), end.toString(), selectedTrack, selectedHalaqa, selectedType) +
            "|scope-v2|" + com.mohammedsamir.halaqati.plans.ReportScopeRules.cachePart(selectedIds)
        loading = true
        message = ""
        try {
            val bundle = withContext(Dispatchers.IO) {
                val currentSummary = AppGraph.repo.reportSummary(start.toString(), end.toString(), selectedTrack, selectedHalaqa, selectedType, scopedHalaqaIds = selectedIds)
                val details = AppGraph.repo.reportRecitations(start.toString(), end.toString(), selectedTrack, selectedHalaqa, selectedType, scopedHalaqaIds = selectedIds)
                val notes = AppGraph.repo.reportNotes(start.toString(), end.toString(), selectedTrack, selectedHalaqa, selectedType, scopedHalaqaIds = selectedIds)
                val allStudents = if (selectedHalaqa == "all") AppGraph.repo.students().filter { it.status == "active" }
                    else AppGraph.repo.studentsForHalaqa(selectedHalaqa)
                val memberRows = AppGraph.repo.studentHalaqaMemberships().let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } }
                val memberHalaqas = memberRows.filter { it.optString("status") == "active" }
                    .groupBy({ it.optString("student_id") }, { it.optString("halaqa_id") })
                val previousSummary = if (selectedMode == "month") {
                    val prev = YearMonth.from(start).minusMonths(1)
                    AppGraph.repo.reportSummary(prev.atDay(1).toString(), prev.atEndOfMonth().toString(), selectedTrack, selectedHalaqa, selectedType, scopedHalaqaIds = selectedIds)
                } else JSONArray()
                val absences = AppGraph.repo.reportAbsences(start.toString(), end.toString(), selectedTrack, selectedHalaqa, selectedType, scopedHalaqaIds = selectedIds)
                ReportLoadBundle(currentSummary, details, notes, allStudents, previousSummary, memberHalaqas, absences)
            }
            if (request != reportRequest) return
            fun scopedRows(rows: JSONArray) = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                .filter { it.optJSONObject("sessions")?.optString("halaqa_id") in selectedIds }
            summaryRows = (0 until bundle.summary.length()).mapNotNull { bundle.summary.optJSONObject(it) }
            detailRows = scopedRows(bundle.details)
            notesRows = scopedRows(bundle.notes)
            absenceRows = scopedRows(bundle.absences)
            reportStudents = bundle.students.filter { com.mohammedsamir.halaqati.plans.ReportScopeRules.contains(it.halaqaId, bundle.memberHalaqas[it.id].orEmpty(), selectedIds) }
            monthComparison = if (selectedMode == "month") buildMonthComparison(bundle.summary, bundle.previousSummary) else null
            NativeReportCache.save(context, cacheKey, summaryRows, detailRows)
            message = "تم تجهيز البيانات: ${summaryRows.size} طالب · ${detailRows.size} تسميع · ${notesRows.size} متابعة"
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (request != reportRequest) return
            val cached = NativeReportCache.load(context, cacheKey)
            if (cached != null) {
                summaryRows = cached.summary
                detailRows = cached.details.filter { it.optJSONObject("sessions")?.optString("halaqa_id") in selectedIds }
                message = "تعذر الوصول للخادم؛ نسخة محفوظة للملخص والتسميع فقط · ${cached.savedAt}"
            } else {
                message = "تعذر تجهيز التقرير: ${e.message ?: "خطأ"}"
            }
        } finally { if (request == reportRequest) loading = false }
    }

    // RC42: this account's own centre / halaqa logos; the bundled ones are only a fallback for the administrator.
    val ownLogos = remember(profile.id) { runCatching { NativeCertificate.loadAssets(context, profile.id) }.getOrDefault(NativeCertificateAssets()) }
    val reportHtml = remember(reportView, summaryRows, detailRows, notesRows, absenceRows, reportStudents, reportStyle, from, to, track, halaqaId, halaqas, ownLogos) {
        runCatching {
            ReportHtmlRenderer.build(
                context = context,
                reportType = reportView,
                summaryRows = summaryRows,
                detailRows = detailRows,
                notesRows = notesRows,
                students = reportStudents,
                style = reportStyle,
                from = from,
                to = to,
                track = track,
                halaqaLabel = halaqaLabel(),
                teacherName = if (reportStyle.includeTeacher) profile.fullName.orEmpty() else "",
                centerLogoData = ownLogos.centerLogo,
                halaqaLogoData = ownLogos.halaqaLogo,
                builtInLogos = com.mohammedsamir.halaqati.documents.LogoPolicy.builtIn,
                absences = absenceRows,
                fullRoster = halaqaId != "all",
            )
        }.getOrElse { "<html dir='rtl'><body><p>${it.message ?: "تعذر إنشاء المعاينة"}</p><script>window.__HALAQATI_READY__=true</script></body></html>" }
    }

    fun exportTable(): NativeReportExport.Table =
        when (reportView) {
            ReportDocumentType.RECITATIONS -> NativeReportExport.matrixTable(detailRows, absenceRows)
            ReportDocumentType.WEEKLY -> NativeReportExport.weeklyTable(detailRows)
            else -> NativeReportExport.table(summaryRows, details = false, program = programReports)
        }
    fun exportTitle(): String = exportTitleForReport(reportView, reportStyle)
    fun exportSubtitle(): String = if (programReports) "الفترة: $from — $to · ${halaqaLabel()}" else "الفترة: $from — $to · ${when (track) { "general_hifz" -> "الحفظ العام"; "tathbit" -> "التثبيت"; else -> "جميع المسارات" }} · ${halaqaLabel()}"

    val createReportXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) scope.launch {
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { NativeReportExport.exportXlsx(context, uri, exportTable(), exportTitle()) } }
                .onSuccess { message = "تم تصدير التقرير Excel" }
                .onFailure { message = "تعذر تصدير Excel: ${it.message ?: "خطأ"}" }
        }
    }
    val createReportWord = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/msword")) { uri ->
        if (uri != null) scope.launch {
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { NativeReportExport.exportWord(context, uri, exportTable(), exportTitle(), exportSubtitle()) } }
                .onSuccess { message = "تم تصدير التقرير Word" }
                .onFailure { message = "تعذر تصدير Word: ${it.message ?: "خطأ"}" }
        }
    }
    val createReportPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) scope.launch {
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { NativeReportExport.exportPng(context, uri, exportTable(), exportTitle(), exportSubtitle()) } }
                .onSuccess { message = "تم تصدير التقرير صورة PNG" }
                .onFailure { message = "تعذر تصدير الصورة: ${it.message ?: "خطأ"}" }
        }
    }

    LaunchedEffect(profile.id) {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                val data = AppGraph.repo.table("halaqas", "select=id,name,active,halaqa_type,program_specialization,affects_primary_plan,starts_on,ends_on&order=name.asc&limit=200")
                (0 until data.length()).mapNotNull { data.optJSONObject(it) }.filter { it.optBoolean("active", true) }
            }
        }.onSuccess { halaqas = it }
        reload()
        reportInitialized = true
    }
    // The kind or halaqa chosen in the scope bar changes the data set: prepare it again.
    LaunchedEffect(programReports, halaqaId, reportScopeIds) { if (reportInitialized) reload() }

    val totalPages = summaryRows.sumOf { it.optDouble("total_pages", 0.0) }
    val totalPresent = summaryRows.sumOf { it.optInt("present", 0) + it.optInt("late", 0) }
    val totalAbsent = summaryRows.sumOf { it.optInt("absent", 0) }
    val hasReportData = !loading && when (reportView) {
        ReportDocumentType.RECITATIONS, ReportDocumentType.WEEKLY -> detailRows.isNotEmpty()
        else -> summaryRows.isNotEmpty()
    }

    Column {
        AppTopBar(if (profile.role == UserRole.GUARDIAN) "تقارير أولياء الأمور" else "التقارير", onRefresh = { scope.launch { reload() }; onRefresh() }, pending = pending)
        ReportStudioScrollableSurface(
            modifier = Modifier.fillMaxSize(),
        ) {
            monthComparison?.let { cmp ->
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("هذا الشهر مقابل الشهر الماضي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            MonthComparisonRow("الحفظ / التثبيت", cmp.previousPages, cmp.currentPages, " ص", positiveUp = true)
                            HorizontalDivider()
                            MonthComparisonRow("الغياب", cmp.previousAbsent.toDouble(), cmp.currentAbsent.toDouble(), "", positiveUp = false)
                            HorizontalDivider()
                            MonthComparisonRow("السلامة", cmp.previousSafety, cmp.currentSafety, "%", positiveUp = true)
                        }
                    }
                }
            }
            item { WorkScopeBar(profile.role) }
            item {
                ReportTypeSelector(reportView, programReports) { selected ->
                    reportView = selected
                    reportStyle = reportStyle.copy(
                        title = reportTitleFor(selected),
                        visibleColumns = reportColumnsFor(selected),
                    )
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("الفترة ونطاق البيانات", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { reportFiltersExpanded = !reportFiltersExpanded }) {
                                Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp)); Text(if (reportFiltersExpanded) "إخفاء" else "تعديل")
                            }
                            IconButton(onClick = { showStudio = true }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Palette, contentDescription = "تخصيص شكل التقرير")
                            }
                        }
                        if (!reportFiltersExpanded) Text(exportSubtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (reportFiltersExpanded) {
                        ChipLine(items = listOf("day" to "يوم محدد", "month" to "شهر محدد", "custom" to "فترة")) { pair, itemModifier ->
                            ProFilterChip(selected = mode == pair.first, onClick = { applyMode(pair.first) }, label = pair.second, modifier = itemModifier.fillMaxWidth(), showCheckIcon = false)
                        }
                        OutlinedButton(
                            onClick = { applyMode(mode) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (mode) {
                                    "day" -> "اليوم المختار: $from"
                                    "month" -> "الشهر المختار: ${certificateMonthLabel(runCatching { YearMonth.from(LocalDate.parse(from)) }.getOrDefault(YearMonth.now()).toString())}"
                                    else -> "الفترة: $from ← $to"
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.EditCalendar, contentDescription = null)
                        }
                        if (!programReports) {
                            Text("المسار", style = MaterialTheme.typography.labelLarge)
                            ChipLine(items = listOf("all" to "جميع المسارات", "general_hifz" to "الحفظ العام", "tathbit" to "التثبيت")) { pair, itemModifier ->
                                ProFilterChip(selected = track == pair.first, onClick = { track = pair.first }, label = pair.second, modifier = itemModifier.fillMaxWidth(), showCheckIcon = false)
                            }
                        }
                        HalaqatiLazySelectField(
                            label = "الحلقة",
                            selectedKey = halaqaId,
                            options = listOf("all" to (if (programReports) "جميع البرامج" else "جميع الحلقات")) + halaqas
                                .filter { com.mohammedsamir.halaqati.plans.WorkModeRules.isProgramType(it.optString("halaqa_type", "regular")) == programReports }
                                .map { it.optString("id") to it.optString("name", "حلقة") },
                            onSelected = { halaqaId = it; com.mohammedsamir.halaqati.data.AppGraph.halaqaScope.select(com.mohammedsamir.halaqati.data.AppGraph.store.load()?.userId, it.takeIf { id -> id != "all" }) },
                            modifier = Modifier.fillMaxWidth(),
                            searchable = halaqas.size > 8,
                        )
                        Text("نوع اللقاء", style = MaterialTheme.typography.labelLarge)
                        ChipLine(items = if (programReports) listOf("all" to "كل الأنواع", "tilawah" to "تلاوة", "tajweed" to "تجويد", "test" to "اختبار", "lesson" to "درس") else listOf("all" to "كل الأنواع", "tasmee" to "تسميع", "sard" to "سرد", "review" to "مراجعة", "test" to "اختبار", "tilawah" to "تلاوة", "tajweed" to "تجويد", "lesson" to "درس", "other" to "أخرى")) { pair, itemModifier ->
                            ProFilterChip(selected = sessionType == pair.first, onClick = { sessionType = pair.first }, label = pair.second, modifier = itemModifier.fillMaxWidth(), showCheckIcon = false)
                        }
                        }
                        Button(onClick = { scope.launch { reload() } }, enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("تجهيز التقرير")
                        }
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (message.isNotBlank()) StateNotice(message, if (message.startsWith("تعذر")) "error" else if (message.startsWith("تم")) "success" else "info")
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("معاينة التقرير · A4", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("راجع تنسيق التقرير قبل الحفظ أو المشاركة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { previewExpanded = true }) {
                                Icon(Icons.Default.OpenInFull, contentDescription = null)
                                Spacer(Modifier.width(5.dp)); Text("تكبير")
                            }
                        }
                        if (hasReportData) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${(previewZoom * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily)
                                Slider(value = previewZoom, onValueChange = { previewZoom = it }, valueRange = .75f..1.2f, modifier = Modifier.weight(1f))
                            }
                            HtmlDocumentPreview(
                                html = reportHtml,
                                zoom = previewZoom,
                                modifier = Modifier.fillMaxWidth().height(if (reportStyle.orientation == ReportOrientation.PORTRAIT) 600.dp else 470.dp),
                            )
                        } else {
                            // RC95: a 600dp blank page looked broken before the report was prepared; say what to do instead.
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                            ) {
                                com.mohammedsamir.halaqati.ui.components.NourIconBadge(Icons.Default.Description, tone = "success", size = 56.dp)
                                Text(if (loading) "جاري تجهيز التقرير…" else "لا توجد معاينة بعد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(if (loading) "تظهر صفحة A4 هنا بعد اكتمال التحميل." else "اختر الفترة والحلقة ثم اضغط «تجهيز التقرير». إن بقيت فارغة فلا توجد بيانات في هذه الفترة.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().testTag(REPORT_STUDIO_LAST_OPTION_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.mohammedsamir.halaqati.ui.components.NourSectionTitle("التصدير والمشاركة")
                    if (!hasReportData) Text("تُفعَّل الأزرار بعد تجهيز تقرير فيه بيانات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            DocumentExportManager.launch {
                                if (reportPdfBusy) return@launch
                                reportPdfBusy = true
                                message = "جاري إنشاء PDF كامل ثم حفظه في Downloads/Halaqati/Reports…"
                                try {
                                    val orientation = if (reportStyle.orientation == ReportOrientation.PORTRAIT) DocumentOrientation.PORTRAIT else DocumentOrientation.LANDSCAPE
                                    val fileName = "${safeReportFileName(exportTitle())}_${from}_إلى_${to}.pdf"
                                    val result = VectorDocumentRenderer.saveHtmlPdfToDownloads(
                                        context = context,
                                        html = reportHtml,
                                        fileName = fileName,
                                        subdirectory = "Reports",
                                        documentName = exportTitle(),
                                        orientation = orientation,
                                        onProgress = { phase -> message = phase.arabicLabel },
                                    )
                                    val kb = kotlin.math.max(1L, result.bytes / 1024L)
                                    val modeLabel = if (result.engine.contains("fallback")) "وضع التوافق" else "PDF متجهي"
                                    message = "تم حفظ PDF فعليًا في Downloads/Halaqati/Reports · ${kb} KB · $modeLabel"
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    message = "تعذر إنشاء PDF: ${e.message ?: "خطأ"}"
                                } finally { reportPdfBusy = false }
                            }
                        },
                        enabled = hasReportData && !reportPdfBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    ) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text(if (reportPdfBusy) "جاري إنشاء PDF…" else "PDF A4") }
                    ResponsiveChipGrid(items = listOf("excel", "word", "image", "share"), minItemWidth = 130.dp, maxColumns = 2) { action, itemModifier ->
                        OutlinedButton(
                            onClick = {
                                when (action) {
                                    "excel" -> createReportXlsx.launch("${safeReportFileName(exportTitle())}_${from}_إلى_${to}.xlsx")
                                    "word" -> createReportWord.launch("${safeReportFileName(exportTitle())}_${from}_إلى_${to}.doc")
                                    "image" -> createReportPng.launch("${safeReportFileName(exportTitle())}_${from}_إلى_${to}.png")
                                    else -> NativeReportExport.shareText(context, exportTable(), exportTitle(), exportSubtitle())
                                }
                            },
                            enabled = hasReportData,
                            modifier = itemModifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(when(action){"excel"->Icons.Default.TableView;"word"->Icons.Default.Description;"image"->Icons.Default.Image;else->Icons.Default.Share}, null)
                            Spacer(Modifier.width(6.dp)); Text(when(action){"excel"->"Excel";"word"->"Word";"image"->"صورة";else->"مشاركة"})
                        }
                    }
                }
            }
            if (loading && summaryRows.isEmpty() && detailRows.isEmpty()) item { LoadingSkeleton(rows = 4) }
            if (!loading && !hasReportData) item { EmptyState(if (reportView == ReportDocumentType.RECITATIONS) "لا توجد تسميعات في الفترة المحددة" else "لا توجد بيانات للتقرير المحدد") }
            if (summaryRows.isNotEmpty() && reportView != ReportDocumentType.RECITATIONS) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { MetricCard("الطلاب", summaryRows.size.toString(), "ضمن الفلاتر") }
                        Box(Modifier.weight(1f)) { MetricCard("الصفحات", formatNumber(totalPages), "جميع الأنشطة") }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { MetricCard("الحضور", totalPresent.toString(), "حاضر + متأخر") }
                        Box(Modifier.weight(1f)) { MetricCard("الغياب", totalAbsent.toString(), "بدون المعذور") }
                    }
                }
                itemsIndexed(summaryRows.take(300), key = { _, item -> item.optString("student_id").ifBlank { item.optString("student_name") } }, contentType = { _, _ -> "report_summary" }) { index, item ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { OneLineName("${index + 1}. ${item.optString("student_name", "طالب")}", fontWeight = FontWeight.Bold) },
                            supportingContent = {
                                val hifzTarget = if (item.has("hifz_target_pages") && !item.isNull("hifz_target_pages")) " / ${formatNumber(item.optDouble("hifz_target_pages", 0.0))}" else ""
                                val sardTarget = if (item.has("sard_target_pages") && !item.isNull("sard_target_pages")) " / ${formatNumber(item.optDouble("sard_target_pages", 0.0))}" else ""
                                Text("${item.optString("category_name").ifBlank { "بدون فئة" }} · حفظ ${formatNumber(item.optDouble("hifz_pages", 0.0))}$hifzTarget · تثبيت ${formatNumber(item.optDouble("tathbit_pages", 0.0))} · سرد ${formatNumber(item.optDouble("sard_pages", 0.0))}$sardTarget · ${formatNumber(item.optDouble("points", 0.0))} نقطة · ${item.optString("quality_label", "—")}")
                            },
                        )
                    }
                }
            }
            if (reportView == ReportDocumentType.RECITATIONS) {
                items(detailRows.take(1000), key = { item -> item.optString("id").ifBlank { "${item.optString("student_id")}:${item.optString("created_at")}" } }, contentType = { "report_detail" }) { item ->
                    val student = item.optJSONObject("students")
                    val session = item.optJSONObject("sessions")
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { OneLineName(student?.optString("full_name") ?: "طالب") },
                            supportingContent = { Text("${session?.optString("session_date").orEmpty()} · ${activityAr(item.optString("activity_type"))} · ${formatNumber(item.optDouble("pages_count", 0.0))} صفحة · ${evaluationAr(item.optString("evaluation"))}") },
                        )
                    }
                }
            }
        }
    }

    if (showDayPicker) {
        HalaqatiDatePickerDialog(
            initial = runCatching { LocalDate.parse(from) }.getOrDefault(LocalDate.now()),
            title = "اختر اليوم",
            onDismiss = { showDayPicker = false },
            onConfirm = { selected -> from = selected.toString(); to = selected.toString(); mode = "day"; showDayPicker = false },
        )
    }
    if (showMonthPicker) {
        HalaqatiMonthPickerDialog(
            initial = runCatching { YearMonth.from(LocalDate.parse(from)) }.getOrDefault(YearMonth.now()),
            onDismiss = { showMonthPicker = false },
            onConfirm = { selected -> from = selected.atDay(1).toString(); to = selected.atEndOfMonth().toString(); mode = "month"; showMonthPicker = false },
        )
    }
    if (showRangePicker) {
        HalaqatiDateRangePickerDialog(
            initialStart = runCatching { LocalDate.parse(from) }.getOrDefault(LocalDate.now()),
            initialEnd = runCatching { LocalDate.parse(to) }.getOrDefault(LocalDate.now()),
            onDismiss = { showRangePicker = false },
            onConfirm = { startDate, endDate -> from = startDate.toString(); to = endDate.toString(); mode = "custom"; showRangePicker = false },
        )
    }
    if (showStudio) {
        ModalBottomSheet(onDismissRequest = { showStudio = false }) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ReportStudioControls(style = reportStyle, reportType = reportView, onChange = { reportStyle = it })
                Button(onClick = { showStudio = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("تم", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (previewExpanded) {
        Dialog(onDismissRequest = { previewExpanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("معاينة التقرير", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { previewExpanded = false }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${(previewZoom * 100).toInt()}%")
                        Slider(value = previewZoom, onValueChange = { previewZoom = it }, valueRange = .75f..1.2f, modifier = Modifier.weight(1f))
                    }
                    HtmlDocumentPreview(html = reportHtml, zoom = previewZoom, modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReportTypeSelector(selected: String, programMode: Boolean, onSelected: (String) -> Unit) {
    val options = if (programMode) listOf(
        Triple(ReportDocumentType.ATTENDANCE, "تقرير الحضور", Icons.Default.FactCheck),
        Triple(ReportDocumentType.RECITATIONS, "نتائج التقييم", Icons.Default.MenuBook),
        Triple(ReportDocumentType.HALAQA, "تقرير الدورة", Icons.Default.Assessment),
    ) else listOf(
        Triple(ReportDocumentType.HALAQA, "التقرير الشامل", Icons.Default.Assessment),
        Triple(ReportDocumentType.LEADERS, "الأوائل", Icons.Default.EmojiEvents),
        Triple(ReportDocumentType.POINTS, "النقاط", Icons.Default.Leaderboard),
        Triple(ReportDocumentType.ATTENDANCE, "الحضور", Icons.Default.FactCheck),
        Triple(ReportDocumentType.RECITATIONS, "التسميعات", Icons.Default.MenuBook),
        Triple(ReportDocumentType.WEEKLY, "أسبوعي", Icons.Default.DateRange),
        Triple(ReportDocumentType.STUDENTS, "أولياء الأمور", Icons.Default.Person),
    )
    // RC64: one horizontal row (scroll sideways) instead of a tall stack of chips, so the preview is reachable without a long scroll.
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label, icon) ->
            ProFilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = label,
                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                showCheckIcon = false,
            )
        }
    }
}

private fun exportTitleForReport(reportView: String, style: NativeReportStyle): String = style.title.ifBlank { ReportDocumentType.title(reportView) }
private fun safeReportFileName(value: String): String = value.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').take(80).ifBlank { "تقرير_حلقتي" }

@Composable
private fun ReportLivePreview(
    style: NativeReportStyle,
    detailsMode: Boolean,
    summaryRows: List<JSONObject>,
    detailRows: List<JSONObject>,
    from: String,
    to: String,
    track: String,
) {
    val primary = androidx.compose.ui.graphics.Color(style.primaryColor)
    val ink = androidx.compose.ui.graphics.Color(style.textColor)
    val ratio = if (style.orientation == ReportOrientation.LANDSCAPE) 1.414f else .707f
    val columnLabels = if (detailsMode) {
        listOf("rank" to "#", "student" to "الطالب", "date" to "التاريخ", "activity" to "النوع", "pages" to "الصفحات", "evaluation" to "التقييم", "safety" to "الأمان")
    } else {
        listOf("rank" to "#", "student" to "الطالب", "category" to "الفئة", "hifz" to "الحفظ", "tathbit" to "التثبيت", "review" to "المراجعة", "sard" to "السرد", "attendance" to "الحضور", "absence" to "الغياب", "points" to "النقاط")
    }.filter { it.first in style.visibleColumns }.take(6)
    val previewRows = if (detailsMode) detailRows.take(3) else summaryRows.take(3)
    val trackLabel = when (track) { "general_hifz" -> "الحفظ العام"; "tathbit" -> "التثبيت"; else -> "جميع المسارات" }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = primary)
                Spacer(Modifier.width(8.dp))
                Text("المعاينة المباشرة", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (style.orientation == ReportOrientation.LANDSCAPE) "A4 أفقي" else "A4 رأسي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(.62f, 1.5f)),
                color = androidx.compose.ui.graphics.Color.White,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(style.title.ifBlank { "تقرير حلقتي" }, color = primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("$trackLabel · $from — $to · خط ${style.fontFamily} ${(style.fontScale * 100).toInt()}%", color = ink, style = MaterialTheme.typography.labelSmall)
                    HorizontalDivider(color = primary.copy(alpha = .35f))
                    if (columnLabels.isEmpty()) {
                        Text("اختر عمودًا واحدًا على الأقل للمعاينة", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            columnLabels.forEach { (_, label) ->
                                Text(label, color = primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                        if (previewRows.isEmpty()) {
                            Text("بعد تجهيز التقرير ستظهر هنا عينة من البيانات قبل الحفظ.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        } else {
                            previewRows.forEachIndexed { index, row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    columnLabels.forEach { (key, _) ->
                                        val value = if (detailsMode) reportDetailPreviewValue(row, index, key) else reportSummaryPreviewValue(row, index, key)
                                        Text(value, color = ink, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .22f))
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(style.footer, color = ink.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

private fun reportSummaryPreviewValue(row: JSONObject, index: Int, key: String): String = when (key) {
    "rank" -> (index + 1).toString()
    "student" -> row.optString("student_name", "طالب")
    "category" -> row.optString("category_name").ifBlank { "—" }
    "hifz" -> formatNumber(row.optDouble("hifz_pages", 0.0))
    "tathbit" -> formatNumber(row.optDouble("tathbit_pages", 0.0))
    "review" -> formatNumber(row.optDouble("review_pages", 0.0))
    "sard" -> formatNumber(row.optDouble("sard_pages", 0.0))
    "attendance" -> (row.optInt("present", 0) + row.optInt("late", 0)).toString()
    "absence" -> row.optInt("absent", 0).toString()
    "points" -> formatNumber(row.optDouble("points", 0.0))
    else -> "—"
}

private fun reportDetailPreviewValue(row: JSONObject, index: Int, key: String): String = when (key) {
    "rank" -> (index + 1).toString()
    "student" -> row.optJSONObject("students")?.optString("full_name") ?: "طالب"
    "date" -> row.optJSONObject("sessions")?.optString("session_date").orEmpty()
    "activity" -> activityAr(row.optString("activity_type"))
    "pages" -> formatNumber(row.optDouble("pages_count", 0.0))
    "evaluation" -> evaluationAr(row.optString("evaluation"))
    "safety" -> "${row.optDouble("safety_percentage", 0.0).toInt()}%"
    else -> "—"
}

private fun activityAr(value: String) = when (value) {
    "new_hifz" -> "حفظ"
    "review_near" -> "مراجعة قريبة"
    "review_far" -> "مراجعة بعيدة"
    "sard" -> "سرد"
    "tilawah" -> "تلاوة"
    "test" -> "اختبار"
    else -> value
}

private fun evaluationAr(value: String) = when (value) {
    "excellent" -> "ممتاز"
    "very_good" -> "جيد جدًا"
    "good" -> "جيد"
    "repeat" -> "إعادة"
    "not_rated" -> "غير مقيم"
    else -> value
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')

data class NativeReportCacheEntry(
    val summary: List<JSONObject>,
    val details: List<JSONObject>,
    val savedAt: String,
)

object NativeReportCache {
    private const val PREFS = "halaqati_native_report_cache"

    fun key(userId: String, from: String, to: String, track: String, halaqaId: String, sessionType: String): String =
        listOf(userId, from, to, track, halaqaId, sessionType).joinToString("|")

    fun save(context: Context, key: String, summary: List<JSONObject>, details: List<JSONObject>) {
        val root = JSONObject()
            .put("version", 1)
            .put("saved_at", java.time.Instant.now().toString())
            .put("summary", JSONArray().apply { summary.forEach { put(it) } })
            .put("details", JSONArray().apply { details.forEach { put(it) } })
        val raw = root.toString()
        if (raw.length <= 3_800_000) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, raw).apply()
    }

    fun load(context: Context, key: String): NativeReportCacheEntry? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optInt("version") != 1) return null
        val summaryArray = root.optJSONArray("summary") ?: return null
        val detailArray = root.optJSONArray("details") ?: return null
        val summary = (0 until summaryArray.length()).mapNotNull { summaryArray.optJSONObject(it) }
        val details = (0 until detailArray.length()).mapNotNull { detailArray.optJSONObject(it) }
        return NativeReportCacheEntry(summary, details, root.optString("saved_at", "نسخة محلية"))
    }
}

data class NativeCertificateStyle(
    val template: String = "emerald",
    val fontFamily: String = "Cairo",
    val customFontName: String = "",
    val titleSize: Float = 44f,
    val studentSize: Float = 38f,
    val bodySize: Float = 20f,
    val logoSize: Float = 168f,
    val borderWidth: Float = 3f,
    val ornamentStrength: Float = .82f,
    val primaryHex: String = "",
    val accentHex: String = "",
    val secondaryHex: String = "",
    val paperHex: String = "",
    val inkHex: String = "",
    val showVerse: Boolean = true,
    val showLogos: Boolean = true,
    val showSignatures: Boolean = true,
    val showStamp: Boolean = true,
    val showDate: Boolean = true,
    val showNumber: Boolean = true,
    val numberPosition: String = "under_logo",
)

data class NativeCertificateDraft(
    val number: String,
    val studentId: String = "",
    val halaqaId: String? = null,
    val categoryId: String? = null,
    val categoryName: String = "غير مصنف",
    val studentName: String,
    val type: String,
    val title: String,
    val achievement: String,
    val date: String,
    val intro: String = "تتشرف حلقة معاوية بن أبي سفيان التابعة لـمركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم بمنح هذه الشهادة للطالب",
    val dua: String = "سائلين الله تعالى أن يثبت القرآن في صدره، وأن يجعله له نورًا وهدًى ورفعةً في الدنيا والآخرة.",
    val centerName: String = "مركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم",
    val halaqaName: String = "حلقة معاوية بن أبي سفيان",
    val teacherName: String = "د. محمد سمير أبو يونس",
    val supervisorName: String = "الشيخ محمد حلمي أبو عنزة",
    val fromJuz: Int = 1,
    val toJuz: Int = 1,
    val month: String = "",
    val rank: Int = 1,
    val track: String = "general_hifz",
    val idealScore: Double? = null,
    val style: NativeCertificateStyle = NativeCertificateStyle(),
    val certificateId: String = "",
    val revisionId: String = "",
    val revisionNo: Int = 1,
    val status: String = "draft",
    val legacyPartial: Boolean = false,
    val assetAvailability: String = "available",
)

data class NativeCertificateAssets(
    val centerLogo: String? = null,
    val halaqaLogo: String? = null,
    val teacherSignature: String? = null,
    val supervisorSignature: String? = null,
    val stamp: String? = null,
) {
    fun value(kind: String): String? = when (kind) {
        "center_logo" -> centerLogo
        "halaqa_logo" -> halaqaLogo
        "teacher_signature" -> teacherSignature
        "supervisor_signature" -> supervisorSignature
        "stamp" -> stamp
        else -> null
    }
}

@Composable
fun CertificatesScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val ownerUserId = profile.id
    val scope = rememberCoroutineScope()
    var allStudents by remember { mutableStateOf<List<com.mohammedsamir.halaqati.model.Student>>(emptyList()) }
    var categories by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var studentsLoading by remember { mutableStateOf(true) }
    var memberHalaqasByStudent by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    // RC27.10: certificate types and students follow the work mode (halaqa vs program).
    val certKind = rememberWorkKind()
    val certMode by com.mohammedsamir.halaqati.data.WorkModeHolder.state.collectAsState()
    val students = remember(allStudents, certKind, certMode.typeById, certMode.selectedId, memberHalaqasByStudent) {
        allStudents.filter { st ->
            com.mohammedsamir.halaqati.plans.WorkModeRules.studentBelongs(
                certKind, st.halaqaId?.let { certMode.typeById[it] }, memberHalaqasByStudent[st.id].orEmpty().map { certMode.typeById[it] },
            ) && (certMode.selectedId == null || st.halaqaId == certMode.selectedId || certMode.selectedId in memberHalaqasByStudent[st.id].orEmpty())
        }
    }
    var selectedStudentId by remember { mutableStateOf("") }
    var selectedStudentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bulkMode by remember { mutableStateOf(false) }
    var studentSearch by remember { mutableStateOf("") }
    var studentCategoryFilter by remember { mutableStateOf("all") }
    var rankCategoryId by remember { mutableStateOf("all") }
    var studentMenu by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("sard") }
    var number by remember(ownerUserId) { mutableStateOf("سيُخصص عند الإصدار") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var fromJuz by remember { mutableStateOf("28") }
    var toJuz by remember { mutableStateOf("30") }
    var month by remember { mutableStateOf(YearMonth.now().toString()) }
    var rank by remember { mutableStateOf("1") }
    var track by remember { mutableStateOf("general_hifz") }
    var monthlyReason by remember { mutableStateOf("") }
    var ethics by remember { mutableStateOf("100") }
    var discipline by remember { mutableStateOf("100") }
    var attendance by remember { mutableStateOf("100") }
    var memorization by remember { mutableStateOf("100") }
    var customAchievement by remember { mutableStateOf("") }
    var customEdited by remember { mutableStateOf(false) }
    var customTitle by remember { mutableStateOf("") }
    var introText by remember { mutableStateOf("تتشرف حلقة معاوية بن أبي سفيان التابعة لـمركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم بمنح هذه الشهادة للطالب") }
    var duaText by remember { mutableStateOf("سائلين الله تعالى أن يثبت القرآن في صدره، وأن يجعله له نورًا وهدًى ورفعةً في الدنيا والآخرة.") }
    var centerName by remember { mutableStateOf("مركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم") }
    var halaqaName by remember { mutableStateOf("حلقة معاوية بن أبي سفيان") }
    var teacherName by remember { mutableStateOf("د. محمد سمير أبو يونس") }
    var supervisorName by remember { mutableStateOf("الشيخ محمد حلمي أبو عنزة") }
    var certificateStyle by remember(ownerUserId) { mutableStateOf(NativeCertificate.loadStyle(context, ownerUserId)) }
    var previewZoom by remember { mutableStateOf(100f) }
    var certificateAssets by remember(ownerUserId) { mutableStateOf(NativeCertificate.loadAssets(context, ownerUserId)) }
    var assetPickerTarget by remember { mutableStateOf<String?>(null) }
    var designTab by remember { mutableStateOf("templates") }
    var archiveSearch by remember { mutableStateOf("") }
    var archive by remember(ownerUserId) { mutableStateOf(NativeCertificate.listArchive(context, ownerUserId)) }
    var serverArchive by remember(ownerUserId) { mutableStateOf<List<CertificateRecord>>(emptyList()) }
    var certificateSeries by remember(ownerUserId) { mutableStateOf<List<CertificateSeriesRow>>(emptyList()) }
    var editingCertificateId by remember { mutableStateOf("") }
    var editingRevisionNo by remember { mutableIntStateOf(0) }
    var showSeriesSettings by remember { mutableStateOf(false) }
    var showLegacyImport by remember { mutableStateOf(false) }
    var legacyNumberInput by remember { mutableStateOf("") }
    var voidReason by remember { mutableStateOf("إلغاء إداري") }
    // RC24.1 archive UX state
    var archiveFilter by remember { mutableStateOf("all") }
    var archiveSort by remember { mutableStateOf("newest") }
    var officialLimit by remember { mutableIntStateOf(12) }
    var expandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hiddenIds by remember(ownerUserId) { mutableStateOf(NativeCertificate.hiddenIds(context, ownerUserId)) }
    var showArchiveTools by remember { mutableStateOf(false) }
    var showLocalIndex by remember { mutableStateOf(false) }
    var manageTarget by remember { mutableStateOf<CertificateRecord?>(null) }
    var permanentDeleteTarget by remember { mutableStateOf<CertificateRecord?>(null) }
    var localDeleteTarget by remember { mutableStateOf<NativeCertificateDraft?>(null) }
    var layoutCheckBusy by remember { mutableStateOf(false) }
    var seriesCodeDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var certificatePdfBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showCertificateDatePicker by remember { mutableStateOf(false) }
    var showCertificateMonthPicker by remember { mutableStateOf(false) }

    LaunchedEffect(ownerUserId, certificateStyle) { NativeCertificate.saveStyle(context, ownerUserId, certificateStyle) }
    LaunchedEffect(certificateSeries) {
        if (certificateSeries.isNotEmpty()) seriesCodeDrafts = certificateSeries.associate { it.key to (seriesCodeDrafts[it.key] ?: it.code) }
    }

    LaunchedEffect(Unit) {
        studentsLoading = true
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                val studentRows = AppGraph.repo.students().filter { it.status == "active" }
                val categoryRows = AppGraph.repo.studentCategories()
                val members = AppGraph.repo.studentHalaqaMemberships().let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                        .filter { it.optString("status", "active") == "active" }
                        .groupBy { it.optString("student_id") }
                        .mapValues { (_, l) -> l.mapNotNull { it.optString("halaqa_id").takeIf(String::isNotBlank) }.toSet() }
                }
                Triple(studentRows, (0 until categoryRows.length()).mapNotNull { categoryRows.optJSONObject(it) }, members)
            }
        }.onSuccess { (list, categoryRows, members) ->
                allStudents = list
                memberHalaqasByStudent = members
                categories = categoryRows
            }
            .onFailure { message = "تعذر تحميل الطلاب: ${it.message ?: "خطأ"}" }
        // RC27.44: a failed refresh keeps what is already on screen and says so, instead of silently showing an empty archive.
        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.certificates(forceRefresh = true) } }
            .onSuccess { serverArchive = it }
            .onFailure { if (message.isBlank()) message = "تعذر تحديث أرشيف الشهادات الآن، يُعرض آخر ما هو محفوظ." }
        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.certificateSeries(forceRefresh = true) } }
            .onSuccess { certificateSeries = it }
        studentsLoading = false
    }

    LaunchedEffect(students) {
        if (selectedStudentId.isBlank() || students.none { it.id == selectedStudentId }) selectedStudentId = students.firstOrNull()?.id.orEmpty()
    }
    // RC46: a winner chosen on the awards page pre-fills the rank certificate.
    LaunchedEffect(students) {
        val req = AwardCertificateBus.pending
        if (req != null && students.any { it.id == req.studentId }) {
            AwardCertificateBus.pending = null
            type = "rank"; rank = req.rank.toString(); month = req.month; track = "general_hifz"
            rankCategoryId = req.categoryId ?: "all"
            selectedStudentId = req.studentId; bulkMode = false; customEdited = false
        }
    }
    LaunchedEffect(certKind, certMode.selectedId) {
        if (certKind == com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM) {
            val name = certMode.selectedId?.let { certMode.nameById[it] }?.takeIf { it.isNotBlank() } ?: com.mohammedsamir.halaqati.plans.ProgramKindRules.TILAWAH_TAJWEED_LABEL
            halaqaName = name
            introText = "يتشرف برنامج $name التابع لـمركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم بمنح هذه الشهادة للطالب"
        } else if (introText.startsWith("يتشرف برنامج")) {
            halaqaName = "حلقة معاوية بن أبي سفيان"
            introText = "تتشرف حلقة معاوية بن أبي سفيان التابعة لـمركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم بمنح هذه الشهادة للطالب"
        }
    }
    LaunchedEffect(certKind) {
        val allowed = com.mohammedsamir.halaqati.plans.WorkModeRules.certificateTypes(certKind).map { it.first }
        if (type !in allowed) { type = allowed.first(); customEdited = false; customAchievement = "" }
        if (certKind == com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM) track = "program" else if (track == "program") track = "general_hifz"
    }

    val categoryNames = remember(categories) { categories.associate { it.optString("id") to it.optString("name", "غير مصنف") } }
    fun categoryNameOf(student: com.mohammedsamir.halaqati.model.Student): String =
        student.categoryId?.let(categoryNames::get).orEmpty().ifBlank { "غير مصنف" }
    val studentName = students.firstOrNull { it.id == selectedStudentId }?.fullName.orEmpty()
    val idealScore = remember(ethics, discipline, attendance, memorization) {
        val e = ethics.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
        val d = discipline.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
        val a = attendance.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
        val m = memorization.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
        kotlin.math.round((e * .35 + d * .30 + a * .20 + m * .15) * 10.0) / 10.0
    }
    val rankCategoryName = if (rankCategoryId == "all") "جميع الفئات" else categoryNames[rankCategoryId].orEmpty().ifBlank { "غير مصنف" }
    val autoAchievement = certificateAchievement(type, fromJuz.toIntOrNull() ?: 1, toJuz.toIntOrNull() ?: 1, month, monthlyReason, rank.toIntOrNull() ?: 1, track, idealScore, rankCategoryName, halaqaName)
    val achievement = if (customEdited) customAchievement else autoAchievement
    val title = customTitle.ifBlank { certificateTitle(type) }

    fun resetAutoText() { customEdited = false; customAchievement = "" }
    fun draftFor(name: String, certNumber: String, studentId: String = selectedStudentId): NativeCertificateDraft {
        val st = students.firstOrNull { it.id == studentId }
        return NativeCertificateDraft(
            number = certNumber.trim(), studentId = studentId, halaqaId = st?.halaqaId,
            categoryId = st?.categoryId, categoryName = st?.let(::categoryNameOf) ?: "غير مصنف",
            studentName = name, type = type, title = title, achievement = achievement, date = date,
            intro = introText, dua = duaText, centerName = centerName, halaqaName = halaqaName,
            teacherName = teacherName, supervisorName = supervisorName,
            fromJuz = (fromJuz.toIntOrNull() ?: 1).coerceIn(1, 30),
            toJuz = (toJuz.toIntOrNull() ?: 1).coerceIn(1, 30), month = month,
            rank = (rank.toIntOrNull() ?: 1).coerceIn(1, 3), track = track,
            idealScore = idealScore.takeIf { type == "ideal" }, style = certificateStyle,
        )
    }

    val previewStudent = if (bulkMode) students.firstOrNull { it.id in selectedStudentIds } else students.firstOrNull { it.id == selectedStudentId }
    val previewStudentName = previewStudent?.fullName ?: studentName.ifBlank { "اسم الطالب" }
    val editingRecord = serverArchive.firstOrNull { it.id == editingCertificateId }
    val previewNumber = editingRecord?.number ?: number.ifBlank { "سيُخصص عند الإصدار" }
    val previewDraft = draftFor(previewStudentName, previewNumber, previewStudent?.id ?: selectedStudentId)
    val certificateHtml = remember(previewDraft, certificateAssets) {
        runCatching { CertificateHtmlRenderer.build(context, previewDraft, certificateAssets) }
            .getOrElse { "<html dir='rtl'><body><p>${it.message ?: "تعذر إنشاء المعاينة"}</p><script>window.__HALAQATI_READY__=true</script></body></html>" }
    }

    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val kind = assetPickerTarget
        assetPickerTarget = null
        if (uri != null && kind != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { NativeCertificate.importAsset(context, ownerUserId, kind, uri) }
                certificateAssets = NativeCertificate.loadAssets(context, ownerUserId)
                message = "تم حفظ صورة الشهادة للحساب الحالي فقط"
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                message = "تعذر حفظ الصورة: ${e.message ?: "خطأ"}"
            }
        }
    }


    suspend fun issueOfficialCertificate(draft: NativeCertificateDraft): NativeCertificateDraft = withTimeout(30_000L) {
        withContext(Dispatchers.IO) {
            require(AppGraph.repo.online()) { "اعتماد رقم الشهادة يحتاج اتصالًا قصيرًا بالخادم" }
            val coreType = when (draft.type) {
                "sard" -> "sard"
                "khatam" -> "hifz"
                else -> "achievement"
            }
            val seriesKey = certificateSeriesKey(draft.type)
            val snapshotDraft = draft.copy(number = "")
            val snapshot = NativeCertificate.renderSnapshot(snapshotDraft, seriesKey)
            val errors = CertificateRules.validateRequiredSnapshot(snapshot, requireOfficialNumber = false)
            require(errors.isEmpty()) { "بيانات الشهادة ناقصة: ${errors.joinToString("، ") { it.arabicLabel }}" }
            val saved = AppGraph.repo.saveCertificateDraft(
                CertificateCoreDraft(
                    studentId = draft.studentId,
                    halaqaId = draft.halaqaId,
                    certificateType = coreType,
                    title = draft.title,
                    issueDate = draft.date,
                    snapshot = NativeCertificate.lightweightServerSnapshot(draft),
                    seriesKey = seriesKey,
                ),
            )
            AppGraph.repo.syncNow()
            val issued = AppGraph.repo.issueCertificateRc24(saved.id, seriesKey, draft.date, snapshot)
            draft.copy(number = issued.number ?: error("لم يُرجع الخادم رقم الشهادة"), certificateId = issued.id, revisionNo = 1, status = issued.status)
        }
    }

    suspend fun reviseOfficialCertificate(draft: NativeCertificateDraft, original: CertificateRecord): NativeCertificateDraft = withTimeout(30_000L) {
        withContext(Dispatchers.IO) {
            val seriesKey = original.seriesKey ?: certificateSeriesKey(draft.type)
            require(CertificateRules.canReviseIdentity(original, draft.studentId, seriesKey, draft.date)) {
                "تغيير الطالب أو نوع السلسلة أو سنة الإصدار يتطلب شهادة جديدة برقم جديد"
            }
            val numbered = draft.copy(number = original.number.orEmpty(), certificateId = original.id, status = original.status)
            val revision = AppGraph.repo.createCertificateRevision(original.id, NativeCertificate.renderSnapshot(numbered, seriesKey), "نسخة مصححة من التطبيق")
            numbered.copy(revisionId = revision.id, revisionNo = revision.revisionNo)
        }
    }


    val exportArchiveIndex = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val count = withContext(Dispatchers.IO) { NativeCertificate.exportArchiveIndex(context, ownerUserId, uri) }
                message = "تم تصدير فهرس أرشيف الشهادات ($count سجل)"
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = "تعذر تصدير الأرشيف: ${e.message ?: "خطأ"}" }
        }
    }
    val importArchiveIndex = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val count = withContext(Dispatchers.IO) { NativeCertificate.importArchiveIndex(context, ownerUserId, uri) }
                archive = NativeCertificate.listArchive(context, ownerUserId)
                certificateStyle = NativeCertificate.loadStyle(context, ownerUserId)
                certificateAssets = NativeCertificate.loadAssets(context, ownerUserId)
                message = "تم استيراد/دمج $count سجل مع إعدادات الشهادة للحساب الحالي"
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = "تعذر استيراد الأرشيف: ${e.message ?: "خطأ"}" }
        }
    }

    val studentCandidates = remember(students, studentSearch, studentCategoryFilter, categoryNames) {
        val q = studentSearch.trim()
        students.asSequence()
            .filter { studentCategoryFilter == "all" || it.categoryId == studentCategoryFilter }
            .filter { q.isBlank() || it.fullName.contains(q, true) }
            .sortedWith(compareBy<com.mohammedsamir.halaqati.model.Student>({ it.categoryId?.let(categoryNames::get) ?: "zzzz" }, { it.fullName }))
            .toList()
    }
    val archiveFiltered = remember(archive, archiveSearch) {
        val q = archiveSearch.trim()
        if (q.isBlank()) archive else archive.filter {
            it.studentName.contains(q, true) || it.number.contains(q, true) || it.title.contains(q, true)
        }
    }

    // ───────── RC24.1 archive helpers ─────────
    val isAdminRole = profile.role == UserRole.ADMIN
    fun officialNameOf(cert: CertificateRecord): String = cert.snapshot.optString("student_name").ifBlank {
        students.firstOrNull { it.id == cert.studentId }?.fullName.orEmpty().ifBlank { "طالب" }
    }
    val officialAll = remember(serverArchive) { serverArchive.filter { !it.number.isNullOrBlank() } }
    val officialVisible = remember(officialAll, hiddenIds, archiveFilter, archiveSort, archiveSearch, students) {
        val q = archiveSearch.trim()
        val base = officialAll.asSequence()
            .filter { c ->
                when (archiveFilter) {
                    "hidden" -> c.id in hiddenIds
                    "issued" -> c.id !in hiddenIds && c.status == "issued"
                    "voided" -> c.id !in hiddenIds && c.status == "voided"
                    "legacy" -> c.id !in hiddenIds && c.legacyPartial
                    else -> c.id !in hiddenIds
                }
            }
            .filter { c -> q.isBlank() || c.number.orEmpty().contains(q, true) || officialNameOf(c).contains(q, true) }
        val comparator: Comparator<CertificateRecord> = when (archiveSort) {
            "oldest" -> compareBy<CertificateRecord>({ it.issueDate.orEmpty() }, { it.number.orEmpty() })
            "name" -> compareBy<CertificateRecord>({ officialNameOf(it) }, { it.number.orEmpty() })
            "number" -> compareBy<CertificateRecord> { it.number.orEmpty() }
            else -> compareByDescending<CertificateRecord> { it.issueDate.orEmpty() }.thenByDescending { it.number.orEmpty() }
        }
        base.sortedWith(comparator).toList()
    }

    fun editOfficial(cert: CertificateRecord) {
        scope.launch {
            try {
                val revisions = withContext(Dispatchers.IO) { AppGraph.repo.certificateRevisions(cert.id, forceRefresh = true) }
                val revision = revisions.firstOrNull { it.id == cert.currentRevisionId } ?: revisions.maxByOrNull { it.revisionNo }
                    ?: error("لا توجد نسخة قابلة للتحرير")
                val restored = NativeCertificate.draftFromRevision(cert, revision)
                type = restored.type; date = restored.date; fromJuz = restored.fromJuz.toString(); toJuz = restored.toJuz.toString()
                month = restored.month.ifBlank { YearMonth.now().toString() }; rank = restored.rank.toString(); track = restored.track
                certificateStyle = restored.style; customAchievement = restored.achievement; customEdited = true; customTitle = restored.title
                introText = restored.intro; duaText = restored.dua; centerName = restored.centerName; halaqaName = restored.halaqaName
                teacherName = restored.teacherName; supervisorName = restored.supervisorName; number = cert.number.orEmpty()
                selectedStudentId = cert.studentId; bulkMode = false; editingCertificateId = cert.id; editingRevisionNo = revision.revisionNo
                message = "تم تحميل ${cert.number} v${revision.revisionNo}. الحفظ سيُنشئ نسخة مصححة بنفس الرقم."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) { message = "تعذر فتح النسخة: ${e.message ?: "خطأ"}" }
        }
    }

    fun voidOfficial(cert: CertificateRecord) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { AppGraph.repo.voidCertificate(cert.id, voidReason) }
                serverArchive = withContext(Dispatchers.IO) { AppGraph.repo.certificates(forceRefresh = true) }
                message = "تم إلغاء ${cert.number} رسميًا مع حفظ الرقم والسجل"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) { message = "تعذر إلغاء الشهادة: ${e.message ?: "خطأ"}" }
        }
    }

    fun deleteOfficial(cert: CertificateRecord) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { AppGraph.repo.deleteCertificate(cert.id) }
                NativeCertificate.deleteLocalArchive(context, ownerUserId, cert.id, cert.number.orEmpty(), null)
                hiddenIds = NativeCertificate.setHidden(context, ownerUserId, cert.id, false)
                archive = NativeCertificate.listArchive(context, ownerUserId)
                if (editingCertificateId == cert.id) { editingCertificateId = ""; editingRevisionNo = 0; number = "سيُخصص عند الإصدار" }
                serverArchive = withContext(Dispatchers.IO) { AppGraph.repo.certificates(forceRefresh = true) }
                message = "تم حذف ${cert.number} نهائيًا من السجل"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) { message = "تعذر حذف الشهادة: ${e.message ?: "خطأ"}" }
        }
    }

    fun reuseLocal(item: NativeCertificateDraft) {
        type = item.type
        date = LocalDate.now().toString()
        fromJuz = item.fromJuz.toString()
        toJuz = item.toJuz.toString()
        month = item.month.ifBlank { YearMonth.now().toString() }
        rank = item.rank.toString()
        track = item.track
        certificateStyle = item.style
        customAchievement = item.achievement
        customEdited = true
        customTitle = item.title
        introText = item.intro
        duaText = item.dua
        centerName = item.centerName
        halaqaName = item.halaqaName
        teacherName = item.teacherName
        supervisorName = item.supervisorName
        number = "سيُخصص عند الإصدار"
        selectedStudentId = students.firstOrNull { it.fullName == item.studentName }?.id.orEmpty()
        bulkMode = false
        message = "تم تحميل بيانات الشهادة المؤرشفة كنسخة جديدة برقم جديد"
    }

    var certTab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        ExamCertHandoff.take()?.let { h ->
            selectedStudentId = h.studentId; type = "sard"; certTab = 0
            h.fromJuz?.let { fromJuz = it.toString() }; h.toJuz?.let { toJuz = it.toString() }
        }
    }
    Column {
        AppTopBar("الشهادات", onRefresh = {
            archive = NativeCertificate.listArchive(context, ownerUserId)
            scope.launch { serverArchive = withContext(Dispatchers.IO) { AppGraph.repo.certificates(forceRefresh = true) }; certificateSeries = withContext(Dispatchers.IO) { AppGraph.repo.certificateSeries(forceRefresh = true) } }
            onRefresh()
        }, pending = pending)
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SegmentedTabs(listOf("إنشاء شهادة", "الأرشيف (${officialAll.size})"), certTab, { certTab = it }, Modifier.padding(bottom = 4.dp)) }
            if (certTab == 0) {
            item { CertificateStudioHeader(version = BuildConfig.VERSION_NAME) }
            item { WorkScopeBar(profile.role, Modifier.padding(horizontal = 0.dp)) }
            item {
                Text("نوع الشهادة", style = MaterialTheme.typography.titleSmall)
                ResponsiveChipGrid(
                    items = com.mohammedsamir.halaqati.plans.WorkModeRules.certificateTypes(certKind).filterNot { bulkMode && it.first == "rank" },
                    minItemWidth = 128.dp,
                    maxColumns = 2,
                ) { pair, itemModifier ->
                    ProFilterChip(selected = type == pair.first, onClick = { type = pair.first; resetAutoText() }, label = pair.second, modifier = itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("الإصدار الجماعي", style = MaterialTheme.typography.titleSmall)
                            Text("الطلاب مرتّبون حسب الفئة؛ لكل طالب رقم رسمي وملف PDF مستقل محليًا", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = bulkMode, onCheckedChange = { enabled ->
                            bulkMode = enabled
                            if (enabled && type == "rank") type = com.mohammedsamir.halaqati.plans.WorkModeRules.certificateTypes(certKind).first { it.first != "rank" }.first
                            if (!enabled) selectedStudentIds = emptySet()
                        })
                    }
                }
            }
            if (studentsLoading) {
                item { LoadingSkeleton(rows = 2) }
            } else if (students.isEmpty()) {
                item { StateNotice("لا يوجد طلاب نشطون لإصدار شهادة", "warning") }
            } else if (bulkMode) {
                item {
                    OutlinedTextField(
                        value = studentSearch,
                        onValueChange = { studentSearch = it },
                        label = { Text("بحث باسم الطالب") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    HalaqatiLazySelectField(
                        label = "الفئة",
                        selectedKey = studentCategoryFilter,
                        options = listOf("all" to "جميع الفئات") + categories.map { it.optString("id") to it.optString("name", "فئة") },
                        onSelected = { studentCategoryFilter = it },
                        modifier = Modifier.fillMaxWidth(),
                        searchable = categories.size > 8,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { selectedStudentIds = selectedStudentIds + studentCandidates.map { it.id } },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("تحديد الكل") }
                        OutlinedButton(
                            onClick = { selectedStudentIds = emptySet() },
                            enabled = selectedStudentIds.isNotEmpty(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("إلغاء التحديد") }
                    }
                    Text("تم تحديد ${selectedStudentIds.size} طالب", style = MaterialTheme.typography.bodyMedium, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                }
                items(studentCandidates, key = { "cert-bulk:${it.id}" }) { student ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = student.id in selectedStudentIds,
                                onCheckedChange = { checked -> selectedStudentIds = if (checked) selectedStudentIds + student.id else selectedStudentIds - student.id },
                                modifier = Modifier.size(48.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                OneLineName(student.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(categoryNameOf(student), style = MaterialTheme.typography.bodySmall, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                            }
                            Text(if (student.track == "tathbit") "التثبيت" else "الحفظ العام", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    HalaqatiLazySelectField(
                        label = "الفئة",
                        selectedKey = studentCategoryFilter,
                        options = listOf("all" to "جميع الفئات") + categories.map { it.optString("id") to it.optString("name", "فئة") },
                        onSelected = { id ->
                            studentCategoryFilter = id
                            if (id != "all") students.firstOrNull { it.categoryId == id }?.let { selectedStudentId = it.id }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        searchable = categories.size > 8,
                    )
                    Spacer(Modifier.height(8.dp))
                    HalaqatiLazySelectField(
                        label = "الطالب · مرتب حسب الفئة ثم الاسم",
                        selectedKey = selectedStudentId,
                        options = studentCandidates.map { student -> student.id to "${categoryNameOf(student)} · ${student.fullName}" },
                        onSelected = { selectedStudentId = it },
                        modifier = Modifier.fillMaxWidth(),
                        searchable = true,
                    )
                }
            }
            if (!bulkMode) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = number, onValueChange = { number = normalizeArabicDigits(it).take(40) }, label = { Text("رقم الشهادة") }, modifier = Modifier.weight(1f), singleLine = true)
                    HalaqatiDateField(
                        label = "تاريخ الشهادة",
                        value = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()),
                        onClick = { showCertificateDatePicker = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else item {
                HalaqatiDateField(
                    label = "تاريخ الشهادات",
                    value = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()),
                    onClick = { showCertificateDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (type == "sard") item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fromJuz, onValueChange = { fromJuz = normalizeArabicDigits(it).filter(Char::isDigit).take(2); resetAutoText() }, label = { Text("من الجزء") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = toJuz, onValueChange = { toJuz = normalizeArabicDigits(it).filter(Char::isDigit).take(2); resetAutoText() }, label = { Text("إلى الجزء") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }
            if (type == "monthly" || type == "excellence") item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCertificateMonthPicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("الشهر: ${certificateMonthLabel(month)}", modifier = Modifier.weight(1f)); Icon(Icons.Default.EditCalendar, contentDescription = null)
                    }
                    OutlinedTextField(value = monthlyReason, onValueChange = { monthlyReason = it; resetAutoText() }, label = { Text("سبب التميز") }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (type == "ideal") item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("معايير الطالب المثالي", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = ethics, onValueChange = { ethics = normalizeArabicDigits(it).filter(Char::isDigit).take(3); resetAutoText() }, label = { Text("الأخلاق") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = discipline, onValueChange = { discipline = normalizeArabicDigits(it).filter(Char::isDigit).take(3); resetAutoText() }, label = { Text("الالتزام") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = attendance, onValueChange = { attendance = normalizeArabicDigits(it).filter(Char::isDigit).take(3); resetAutoText() }, label = { Text("الحضور") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = memorization, onValueChange = { memorization = normalizeArabicDigits(it).filter(Char::isDigit).take(3); resetAutoText() }, label = { Text(if (certKind == com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM) "التلاوة والتجويد" else "الحفظ") }, modifier = Modifier.weight(1f))
                    }
                    Text("الدرجة التقديرية: ${formatNumber(idealScore)}/100", fontWeight = FontWeight.Bold, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                }
            }
            if (type == "rank") item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = rank, onValueChange = { rank = normalizeArabicDigits(it).filter(Char::isDigit).take(1); resetAutoText() }, label = { Text("المركز 1–3") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (certKind != com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProFilterChip(selected = track == "general_hifz", onClick = { track = "general_hifz"; resetAutoText() }, label = "الحفظ العام")
                        ProFilterChip(selected = track == "tathbit", onClick = { track = "tathbit"; resetAutoText() }, label = "التثبيت")
                    }
                    HalaqatiLazySelectField(
                        label = "الفئة",
                        selectedKey = rankCategoryId,
                        options = listOf("all" to "جميع الفئات") + categories.map { it.optString("id") to it.optString("name", "فئة") },
                        onSelected = { rankCategoryId = it; resetAutoText() },
                        modifier = Modifier.fillMaxWidth(),
                        searchable = categories.size > 8,
                    )
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(46.dp),
                            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) } }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("استوديو تصميم الشهادة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                Text("كل تخصيصات v2.44 عادت هنا بشكل مباشر، والملفات والصور تبقى محلية على الجهاز.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ResponsiveChipGrid(items = listOf("templates" to "القوالب", "typography" to "الخط والألوان", "elements" to "العناصر", "assets" to "الشعارات والتوقيعات"), minItemWidth = 135.dp, maxColumns = 2) { pair, itemModifier ->
                            ProFilterChip(selected = designTab == pair.first, onClick = { designTab = pair.first }, label = pair.second, modifier = itemModifier.fillMaxWidth(), showCheckIcon = false)
                        }
                        when (designTab) {
                            "templates" -> {
                                Text("اختر قالبًا بصريًا", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("النصوص لا تتغير عند تبديل القالب؛ الذي يتغير هو الإطار والزخرفة ولوحة الألوان فقط.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ResponsiveChipGrid(
                                    items = listOf(
                                        "emerald" to "الرسمي الفيروزي", "royal" to "الملكي الأخضر", "gold" to "الذهبي الفاخر",
                                        "blue" to "الأزرق الرسمي", "minimal" to "الكلاسيكي الهادئ", "burgundy" to "العنابي",
                                    ),
                                    minItemWidth = 148.dp,
                                    maxColumns = 2,
                                    spacing = 10.dp,
                                ) { pair, itemModifier ->
                                    val (value, label) = pair
                                    CertificateTemplateCard(
                                        template = value,
                                        label = label,
                                        selected = certificateStyle.template == value,
                                        modifier = itemModifier.fillMaxWidth(),
                                        onClick = {
                                            val strength = when (value) { "royal" -> .9f; "gold" -> .86f; "blue" -> .76f; "minimal" -> .45f; "burgundy" -> .78f; else -> .8f }
                                            certificateStyle = certificateStyle.copy(
                                                template = value, ornamentStrength = strength,
                                                primaryHex = "", accentHex = "", secondaryHex = "", paperHex = "", inkHex = "",
                                            )
                                        },
                                    )
                                }
                                CertificateStyleSlider("سماكة الإطار", certificateStyle.borderWidth, 1f..6f) { certificateStyle = certificateStyle.copy(borderWidth = it) }
                                CertificateStyleSlider("قوة الزخارف", certificateStyle.ornamentStrength, 0f..1f) { certificateStyle = certificateStyle.copy(ornamentStrength = it) }
                            }
                            "typography" -> {
                                Text("الخط", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                ResponsiveChipGrid(
                                    items = listOf("Cairo" to "Cairo", "sans-serif" to "Sans", "serif" to "Serif", "monospace" to "Mono", "device" to "خط الجهاز"),
                                    minItemWidth = 104.dp,
                                    maxColumns = 3,
                                ) { (value, label), itemModifier ->
                                    ProFilterChip(
                                        selected = certificateStyle.fontFamily == value,
                                        onClick = { certificateStyle = certificateStyle.copy(fontFamily = value) },
                                        label = label,
                                        modifier = itemModifier.fillMaxWidth(),
                                        showCheckIcon = false,
                                    )
                                }
                                if (certificateStyle.fontFamily == "device") {
                                    OutlinedTextField(
                                        value = certificateStyle.customFontName,
                                        onValueChange = { certificateStyle = certificateStyle.copy(customFontName = it.take(80)) },
                                        label = { Text("اسم الخط المثبت على الجهاز") },
                                        placeholder = { Text("مثال: Noto Naskh Arabic") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                    )
                                }
                                CertificateStyleSlider("حجم العنوان", certificateStyle.titleSize, 30f..64f) { certificateStyle = certificateStyle.copy(titleSize = it) }
                                CertificateStyleSlider("حجم اسم الطالب", certificateStyle.studentSize, 28f..58f) { certificateStyle = certificateStyle.copy(studentSize = it) }
                                CertificateStyleSlider("حجم نص الإنجاز", certificateStyle.bodySize, 15f..28f) { certificateStyle = certificateStyle.copy(bodySize = it) }
                                CertificateStyleSlider("حجم الشعارات", certificateStyle.logoSize, 100f..230f) { certificateStyle = certificateStyle.copy(logoSize = it) }
                                HorizontalDivider()
                                Text("ألوان الشهادة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("اضغط اللون الجاهز أو اكتب HEX. ترك الحقل فارغًا يعيد لون القالب.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                CertificateColorField("الرئيسي", certificateStyle.primaryHex, Modifier.fillMaxWidth()) { certificateStyle = certificateStyle.copy(primaryHex = it) }
                                CertificateColorField("الذهبي", certificateStyle.accentHex, Modifier.fillMaxWidth()) { certificateStyle = certificateStyle.copy(accentHex = it) }
                                CertificateColorField("الثانوي", certificateStyle.secondaryHex, Modifier.fillMaxWidth()) { certificateStyle = certificateStyle.copy(secondaryHex = it) }
                                CertificateColorField("الخلفية", certificateStyle.paperHex, Modifier.fillMaxWidth()) { certificateStyle = certificateStyle.copy(paperHex = it) }
                                CertificateColorField("النص", certificateStyle.inkHex, Modifier.fillMaxWidth()) { certificateStyle = certificateStyle.copy(inkHex = it) }
                            }
                            "elements" -> {
                                Text("عناصر الشهادة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                CertificateToggle("الآية في التذييل", certificateStyle.showVerse) { certificateStyle = certificateStyle.copy(showVerse = it) }
                                CertificateToggle("الشعارات", certificateStyle.showLogos) { certificateStyle = certificateStyle.copy(showLogos = it) }
                                CertificateToggle("التوقيعات", certificateStyle.showSignatures) { certificateStyle = certificateStyle.copy(showSignatures = it) }
                                CertificateToggle("الختم", certificateStyle.showStamp) { certificateStyle = certificateStyle.copy(showStamp = it) }
                                CertificateToggle("التاريخ الميلادي والهجري", certificateStyle.showDate) { certificateStyle = certificateStyle.copy(showDate = it) }
                                CertificateToggle("رقم الشهادة", certificateStyle.showNumber) { certificateStyle = certificateStyle.copy(showNumber = it) }
                                Text("موضع رقم الشهادة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                ResponsiveChipGrid(items = listOf("under_logo" to "أسفل شعار المركز", "top" to "أعلى الوسط", "footer_start" to "أسفل يمين", "footer_end" to "أسفل يسار"), minItemWidth = 140.dp, maxColumns = 2) { pair, itemModifier ->
                                    ProFilterChip(
                                        selected = certificateStyle.numberPosition == pair.first,
                                        onClick = { certificateStyle = certificateStyle.copy(numberPosition = pair.first) },
                                        label = pair.second,
                                        modifier = itemModifier.fillMaxWidth(),
                                        showCheckIcon = false,
                                    )
                                }
                            }
                            else -> {
                                Text("الصور محلية فقط", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("لا يتم رفع الشعار أو التوقيع أو الختم أو ملف PDF إلى Supabase.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                listOf(
                                    "center_logo" to "شعار المركز" to Icons.Default.AccountBalance,
                                    "halaqa_logo" to "شعار الحلقة" to Icons.Default.AutoStories,
                                    "teacher_signature" to "توقيع المحفظ" to Icons.Default.Draw,
                                    "supervisor_signature" to "توقيع المشرف" to Icons.Default.Draw,
                                    "stamp" to "الختم الرسمي" to Icons.Default.Verified,
                                ).forEach { triple ->
                                    val kind = triple.first.first
                                    val label = triple.first.second
                                    val icon = triple.second
                                    CertificateAssetRow(
                                        label = label,
                                        hasCustom = certificateAssets.value(kind) != null,
                                        icon = icon,
                                        onPick = { assetPickerTarget = kind; assetPicker.launch("image/*") },
                                        onRemove = {
                                            NativeCertificate.removeAsset(context, ownerUserId, kind)
                                            certificateAssets = NativeCertificate.loadAssets(context, ownerUserId)
                                            message = "تمت إزالة $label المخصص"
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { certificateStyle = NativeCertificateStyle() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Icon(Icons.Default.RestartAlt, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("استعادة تصميم v2.44 الافتراضي") }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EditNote, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("نصوص وهوية الشهادة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                Text("كل حقل قابل للتعديل مع استعادة الافتراضي مثل v2.44.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        CertificateEditableTextField(
                            label = "عنوان الشهادة",
                            value = customTitle.ifBlank { certificateTitle(type) },
                            customized = customTitle.isNotBlank(),
                            onValueChange = { customTitle = it },
                            onReset = { customTitle = "" },
                        )
                        CertificateEditableTextField(
                            label = "المقدمة",
                            value = introText,
                            customized = introText != "تتشرف حلقة معاوية بن أبي سفيان التابعة لـمركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم بمنح هذه الشهادة للطالب",
                            onValueChange = { introText = it },
                            onReset = { introText = "تتشرف حلقة معاوية بن أبي سفيان التابعة لـمركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم بمنح هذه الشهادة للطالب" },
                            minLines = 2,
                        )
                        CertificateEditableTextField(
                            label = "نص الإنجاز",
                            value = achievement,
                            customized = customEdited,
                            onValueChange = { customAchievement = it; customEdited = true },
                            onReset = { resetAutoText() },
                            minLines = 3,
                        )
                        CertificateEditableTextField(
                            label = "الدعاء / الخاتمة",
                            value = duaText,
                            customized = duaText != "سائلين الله تعالى أن يثبت القرآن في صدره، وأن يجعله له نورًا وهدًى ورفعةً في الدنيا والآخرة.",
                            onValueChange = { duaText = it },
                            onReset = { duaText = "سائلين الله تعالى أن يثبت القرآن في صدره، وأن يجعله له نورًا وهدًى ورفعةً في الدنيا والآخرة." },
                            minLines = 2,
                        )
                        HorizontalDivider()
                        OutlinedTextField(centerName, { centerName = it }, label = { Text("اسم المركز") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(halaqaName, { halaqaName = it }, label = { Text("اسم الحلقة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(teacherName, { teacherName = it }, label = { Text("المحفظ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(supervisorName, { supervisorName = it }, label = { Text("المشرف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("المعاينة A4", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Slider(
                            value = previewZoom,
                            onValueChange = { previewZoom = it },
                            valueRange = 70f..120f,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${previewZoom.toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        HtmlDocumentPreview(
                            html = certificateHtml,
                            zoom = previewZoom / 100f,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1122f / 794f),
                        )
                    }
                    OutlinedButton(
                        enabled = !layoutCheckBusy,
                        onClick = {
                            scope.launch {
                                layoutCheckBusy = true
                                try {
                                    val gate = withContext(Dispatchers.Main.immediate) {
                                        LiveDocumentPreview.current()?.let { LiveDocumentPreview.evaluateCertificateGate(it) }
                                    }
                                    message = when {
                                        gate == null -> "المعاينة غير جاهزة بعد؛ انتظر لحظة ثم أعد المحاولة"
                                        gate.valid -> "تم ضبط التنسيق: كل العناصر داخل حدود A4 بلا تداخل"
                                        else -> "تعذر حفظ الشهادة: ${gate.arabicError()}"
                                    }
                                } finally { layoutCheckBusy = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (layoutCheckBusy) "جاري الضبط…" else "إصلاح التنسيق وفحص A4")
                    }
                }
            }
            item {
                if (bulkMode) {
                    Button(
                        onClick = {
                            val chosen = students.filter { it.id in selectedStudentIds }
                                .sortedWith(compareBy({ it.categoryId?.let(categoryNames::get) ?: "zzzz" }, { it.fullName }))
                            DocumentExportManager.launch {
                                if (!AppGraph.repo.online()) { message = "الإصدار الجماعي الرسمي يحتاج اتصالًا قصيرًا لأرقام الشهادات"; return@launch }
                                message = "جاري إصدار ${chosen.size} شهادة مستقلة…"
                                val issued = mutableListOf<NativeCertificateDraft>()
                                var failed = 0
                                for (student in chosen) {
                                    try {
                                        val base = draftFor(student.fullName, "", student.id)
                                        val official = issueOfficialCertificate(base)
                                        NativeCertificate.exportToDownloads(context, ownerUserId, official)
                                        issued += official
                                    } catch (_: Exception) { failed++ }
                                }
                                NativeCertificate.archive(context, ownerUserId, issued)
                                archive = NativeCertificate.listArchive(context, ownerUserId)
                                serverArchive = withContext(Dispatchers.IO) { AppGraph.repo.certificates(forceRefresh = true) }
                                number = "سيُخصص عند الإصدار"
                                selectedStudentIds = emptySet()
                                message = if (failed == 0) "تم إصدار ${issued.size} شهادة PDF مستقلة وحفظها في Downloads/Halaqati/Certificates" else "تم إصدار ${issued.size} شهادة وتعذر $failed"
                            }
                        },
                        enabled = selectedStudentIds.isNotEmpty() && runCatching { LocalDate.parse(date) }.isSuccess,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Icon(Icons.Default.Groups, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("إصدار ${selectedStudentIds.size} شهادة PDF") }
                } else {
                    Button(
                        onClick = {
                            val draft = draftFor(studentName, number)
                            DocumentExportManager.launch {
                                if (certificatePdfBusy) return@launch
                                certificatePdfBusy = true
                                try {
                                    val gate = withContext(Dispatchers.Main.immediate) {
                                        LiveDocumentPreview.current()?.let { LiveDocumentPreview.evaluateCertificateGate(it) }
                                    } ?: error("المعاينة A4 غير جاهزة؛ انتظر لحظة ثم أعد المحاولة")
                                    require(gate.valid) { gate.arabicError() }
                                    val original = serverArchive.firstOrNull { it.id == editingCertificateId }
                                    message = if (original == null) "جاري اعتماد رقم الشهادة وإنشاء PDF كامل…" else "جاري إنشاء النسخة المصححة v${editingRevisionNo + 1}…"
                                    val official = if (original == null) issueOfficialCertificate(draft) else reviseOfficialCertificate(draft, original)
                                    NativeCertificate.exportToDownloads(context, ownerUserId, official) { phase ->
                                        message = phase.arabicLabel
                                    }
                                    NativeCertificate.archive(context, ownerUserId, listOf(official))
                                    archive = NativeCertificate.listArchive(context, ownerUserId)
                                    serverArchive = withContext(Dispatchers.IO) { AppGraph.repo.certificates(forceRefresh = true) }
                                    number = "سيُخصص عند الإصدار"
                                    editingCertificateId = ""
                                    editingRevisionNo = 0
                                    com.mohammedsamir.halaqati.ui.components.HalaqatiMotionEvents.milestone()
                                    message = "تم حفظ ${if (official.revisionNo > 1) "النسخة v${official.revisionNo}" else "الشهادة"} بالرقم الرسمي ${official.number}"
                                } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                                    message = "تعذر حفظ الشهادة: ${e.message ?: "خطأ"}"
                                } finally {
                                    certificatePdfBusy = false
                                }
                            }
                        },
                        enabled = studentName.isNotBlank() && achievement.isNotBlank() && title.isNotBlank() && runCatching { LocalDate.parse(date) }.isSuccess && !certificatePdfBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Icon(Icons.Default.WorkspacePremium, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (certificatePdfBusy) "جاري إنشاء الشهادة…" else if (editingCertificateId.isNotBlank()) "حفظ نسخة مصححة بنفس الرقم" else "إنشاء شهادة PDF") }
                }
            }
            if (message.isNotBlank()) item {
                StateNotice(message, if (message.startsWith("تعذر")) "error" else "success")
            }
            }
            if (certTab == 1) {
            item {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الأرشيف الرسمي للشهادات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${officialAll.count { it.status == "issued" }} سارية · ${officialAll.count { it.status == "voided" }} ملغاة · ${officialAll.count { it.id in hiddenIds }} مخفية",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    var sortMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(when (archiveSort) { "oldest" -> "الأقدم"; "name" -> "الاسم"; "number" -> "الرقم"; else -> "الأحدث" })
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("newest" to "الأحدث", "oldest" to "الأقدم", "name" to "حسب الاسم", "number" to "حسب الرقم").forEach { (key, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { archiveSort = key; sortMenu = false })
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = archiveSearch,
                    onValueChange = { archiveSearch = it },
                    placeholder = { Text("بحث بالطالب أو رقم الشهادة") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "all" to "الكل (${officialAll.count { it.id !in hiddenIds }})",
                        "issued" to "سارية (${officialAll.count { it.id !in hiddenIds && it.status == "issued" }})",
                        "voided" to "ملغاة (${officialAll.count { it.id !in hiddenIds && it.status == "voided" }})",
                        "legacy" to "قديمة (${officialAll.count { it.id !in hiddenIds && it.legacyPartial }})",
                        "hidden" to "مخفية (${officialAll.count { it.id in hiddenIds }})",
                    ).forEach { (key, label) ->
                        ProFilterChip(selected = archiveFilter == key, onClick = { archiveFilter = key; officialLimit = 12 }, label = label, showCheckIcon = false)
                    }
                }
            }
            if (officialVisible.isEmpty()) {
                item { Text("لا توجد نتائج في هذا العرض", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(officialVisible.take(officialLimit), key = { "cert-official:${it.id}" }) { cert ->
                    val expanded = cert.id in expandedIds
                    val isHidden = cert.id in hiddenIds
                    CertificateArchiveRow(
                        name = officialNameOf(cert),
                        meta = certTypeAr(cert.seriesKey ?: cert.certificateType) + " · " + cert.issueDate.orEmpty() + "\n" + cert.number.orEmpty() + " · v${cert.revisionCount.coerceAtLeast(1)}",
                        statusText = when { isHidden -> "مخفية"; cert.status == "voided" -> "ملغاة"; cert.status == "issued" -> "سارية"; else -> "مسودة" },
                        statusKind = when { isHidden -> "info"; cert.status == "voided" -> "error"; cert.status == "issued" -> "success"; else -> "info" },
                        flagText = if (cert.legacyPartial) "قديمة" else null,
                        expanded = expanded,
                        onToggle = { expandedIds = if (expanded) expandedIds - cert.id else expandedIds + cert.id },
                        onManage = { manageTarget = cert },
                        onDelete = null,
                    ) {
                        val snap = cert.snapshot
                        fun snapText(vararg keys: String): String = keys.firstNotNullOfOrNull { k -> snap.optString(k).takeIf { it.isNotBlank() && it != "null" } }.orEmpty()
                        val detailLines = listOf(
                            "السبب" to snapText("reason", "occasion", "note", "notes"),
                            "الجزء/النطاق" to (cert.juzCount?.takeIf { it > 0 }?.let { "${if (it == Math.floor(it)) it.toLong().toString() else it.toString()} جزء" }.orEmpty() +
                                (cert.pagesCount?.takeIf { it > 0 }?.let { " · ${if (it == Math.floor(it)) it.toLong().toString() else it.toString()} صفحة" }.orEmpty())).trim(' ', '·'),
                            "من طلبها" to snapText("requested_by_name", "requested_by", "teacher_name"),
                            "من اعتمدها" to snapText("approved_by_name", "approved_by", "issued_by_name", "issuer_name"),
                            "تاريخ الإصدار" to cert.issueDate.orEmpty(),
                            "أُنشئت" to (if (cert.createdAt.isBlank()) "" else GazaTime.dateTime(cert.createdAt)),
                            "اعتُمدت" to (cert.issuedAt?.takeIf { it.isNotBlank() }?.let { GazaTime.dateTime(it) }.orEmpty()),
                            "آخر تعديل" to (if (cert.updatedAt.isBlank()) "" else GazaTime.dateTime(cert.updatedAt)),
                            "التعديلات" to "${cert.revisionCount.coerceAtLeast(1)} إصدار",
                        ).filter { it.second.isNotBlank() }
                        detailLines.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("$k:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (cert.status == "voided" && !cert.voidReason.isNullOrBlank()) {
                            Text("سبب الإلغاء: ${cert.voidReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (cert.legacyPartial) {
                            Text(
                                "شهادة قديمة: بيانات التصميم الأصلية غير مكتملة؛ لن نخترع نصًا أو أصلًا بصريًا مفقودًا.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = cert.status == "issued" && !cert.legacyPartial,
                                onClick = { editOfficial(cert) },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            ) { Text("إنشاء نسخة مصححة", style = MaterialTheme.typography.labelLarge) }
                            OutlinedButton(
                                onClick = { manageTarget = cert },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            ) { Text("المزيد…", style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
                if (officialVisible.size > officialLimit) {
                    item {
                        OutlinedButton(onClick = { officialLimit += 12 }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                            Text("عرض المزيد (${officialVisible.size - officialLimit})")
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showArchiveTools = !showArchiveTools }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("أدوات الأرشيف")
                    Spacer(Modifier.weight(1f))
                    Icon(if (showArchiveTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }
            if (showArchiveTools) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { exportArchiveIndex.launch("Halaqati_Certificates_Archive_${LocalDate.now()}.json") },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("تصدير JSON") }
                        OutlinedButton(
                            onClick = { importArchiveIndex.launch(arrayOf("application/json", "text/json", "text/plain")) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("استيراد JSON") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showLegacyImport = !showLegacyImport }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("إضافة سجل أرشيف قديم") }
                        if (profile.role in setOf(UserRole.ADMIN, UserRole.SUPERVISOR)) {
                            OutlinedButton(onClick = { showSeriesSettings = !showSeriesSettings }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("رموز الشهادات") }
                        }
                    }
                    Text(
                        "Supabase يحتفظ بالرقم الرسمي والنص/إعدادات كل إصدار فقط. ملفات PDF والصور والتواقيع تبقى على الجهاز.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                if (showLegacyImport) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("إضافة سجل تاريخي", fontWeight = FontWeight.Bold)
                            OutlinedTextField(legacyNumberInput, { legacyNumberInput = it.take(80) }, label = { Text("الرقم التاريخي الرسمي") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Text("يُستخدم الطالب والنوع والتاريخ والنصوص الظاهرة حاليًا، ولا يُستهلك رقم جديد من العداد.", style = MaterialTheme.typography.bodySmall)
                            Button(
                                enabled = selectedStudentId.isNotBlank() && legacyNumberInput.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        try {
                                            val base = draftFor(studentName, legacyNumberInput.trim())
                                            val seriesKey = certificateSeriesKey(type)
                                            withContext(Dispatchers.IO) {
                                                AppGraph.repo.importLegacyCertificate(base.studentId, base.halaqaId, NativeCertificate.renderSnapshot(base, seriesKey).certificateType, seriesKey, base.date, base.number, NativeCertificate.renderSnapshot(base, seriesKey))
                                            }
                                            serverArchive = withContext(Dispatchers.IO) { AppGraph.repo.certificates(forceRefresh = true) }
                                            legacyNumberInput = ""; showLegacyImport = false; message = "تمت إضافة السجل التاريخي دون استهلاك رقم جديد"
                                        } catch (e: Exception) { message = "تعذر إضافة السجل التاريخي: ${e.message ?: "خطأ"}" }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("حفظ السجل التاريخي") }
                        }
                    }
                }
                if (showSeriesSettings && profile.role in setOf(UserRole.ADMIN, UserRole.SUPERVISOR)) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("رموز أرقام الشهادات الجديدة", fontWeight = FontWeight.Bold)
                            Text("تغيير الرمز يؤثر على الأرقام المستقبلية فقط؛ الأرقام القديمة لا تتغير.", style = MaterialTheme.typography.bodySmall)
                            certificateSeries.forEach { row ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = seriesCodeDrafts[row.key] ?: row.code,
                                        onValueChange = { raw -> seriesCodeDrafts = seriesCodeDrafts + (row.key to CertificateRules.normalizeSeriesCode(raw)) },
                                        label = { Text(row.arabicLabel) }, singleLine = true, modifier = Modifier.weight(1f),
                                    )
                                    Button(onClick = {
                                        scope.launch {
                                            try {
                                                val normalized = CertificateRules.normalizeSeriesCode(seriesCodeDrafts[row.key] ?: row.code)
                                                require(normalized.isNotBlank()) { "الرمز يجب أن يكون حرفًا/رقمًا إنجليزيًا" }
                                                withContext(Dispatchers.IO) {
                                                    AppGraph.repo.patch("certificate_series", "series_key=eq.${row.key}", JSONObject().put("code", normalized), dedupe = "certificate-series:${row.key}")
                                                    AppGraph.repo.syncNow()
                                                    certificateSeries = AppGraph.repo.certificateSeries(forceRefresh = true)
                                                }
                                                message = "تم تحديث رمز ${row.arabicLabel} للأرقام المستقبلية فقط"
                                            } catch (e: Exception) { message = "تعذر تعديل الرمز: ${e.message ?: "خطأ"}" }
                                        }
                                    }) { Text("حفظ") }
                                }
                            }
                        }
                    }
                }
                }
            }
            item {
                Surface(
                    onClick = { showLocalIndex = !showLocalIndex },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("نسخ هذا الجهاز (${archive.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(if (showLocalIndex) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                }
            }
            if (showLocalIndex) {
                if (archiveFiltered.isEmpty()) {
                    item { EmptyState("لا توجد شهادات مؤرشفة على هذا الجهاز") }
                } else {
                    items(archiveFiltered.take(50), key = { "cert-archive:${it.certificateId}:${it.revisionNo}:${it.number}:${it.studentName}" }) { item ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.studentName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(
                                        "${item.number} · ${item.title} · ${item.date} · v${item.revisionNo}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    if (item.assetAvailability != "available") {
                                        Text("الأصل البصري غير متوفر هنا", style = MaterialTheme.typography.labelSmall, color = com.mohammedsamir.halaqati.ui.theme.textAccent("tertiary"))
                                    }
                                }
                                IconButton(onClick = { reuseLocal(item) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ContentCopy, contentDescription = "إعادة إنشاء كنسخة جديدة") }
                                IconButton(onClick = { localDeleteTarget = item }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.DeleteOutline, contentDescription = "حذف من هذا الجهاز", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    manageTarget?.let { cert ->
        val isHidden = cert.id in hiddenIds
        HalaqatiAlertDialog(
            onDismissRequest = { manageTarget = null },
            title = { Text(officialNameOf(cert), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${cert.number.orEmpty()} · ${if (cert.status == "voided") "ملغاة" else if (cert.status == "issued") "سارية" else "مسودة"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CertificateActionButton("إنشاء نسخة مصححة بنفس الرقم", enabled = cert.status == "issued" && !cert.legacyPartial) { manageTarget = null; editOfficial(cert) }
                    CertificateActionButton("إلغاء رسمي (يبقى الرقم محفوظًا)", enabled = cert.status == "issued") { manageTarget = null; voidOfficial(cert) }
                    CertificateActionButton(if (isHidden) "إظهار في القائمة" else "إخفاء من القائمة") {
                        hiddenIds = NativeCertificate.setHidden(context, ownerUserId, cert.id, !isHidden)
                        manageTarget = null
                        message = if (isHidden) "تم إظهار ${cert.number} في القائمة" else "تم إخفاء ${cert.number} من القائمة؛ السجل الرسمي كما هو"
                    }
                    if (isAdminRole) CertificateActionButton("حذف نهائي من السجل…", destructive = true) { permanentDeleteTarget = cert; manageTarget = null }
                }
            },
            confirmButton = { TextButton(onClick = { manageTarget = null }) { Text("إغلاق") } },
        )
    }
    permanentDeleteTarget?.let { cert ->
        HalaqatiAlertDialog(
            onDismissRequest = { permanentDeleteTarget = null },
            title = { Text("حذف نهائي؟") },
            text = {
                Text(
                    "سيُحذف سجل ${cert.number} (${officialNameOf(cert)}) بكل نسخه من Supabase ولا يمكن التراجع. الرقم لن يُعاد استخدامه، وملفات PDF المحفوظة على الأجهزة لا تُحذف.\n\nإن أردت إيقاف الشهادة فقط فاستخدم «إلغاء رسمي»، أو «إخفاء» لإزالتها من القائمة.",
                )
            },
            confirmButton = { TextButton(onClick = { permanentDeleteTarget = null; deleteOfficial(cert) }) { Text("حذف نهائي", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { permanentDeleteTarget = null }) { Text("تراجع") } },
        )
    }
    localDeleteTarget?.let { item ->
        HalaqatiAlertDialog(
            onDismissRequest = { localDeleteTarget = null },
            title = { Text("حذف من هذا الجهاز؟") },
            text = { Text("سيُزال ${item.studentName} (${item.number} · v${item.revisionNo}) من فهرس هذا الجهاز فقط. السجل الرسمي في Supabase وملف PDF المحفوظ في Downloads لا يتأثران.") },
            confirmButton = {
                TextButton(onClick = {
                    NativeCertificate.deleteLocalArchive(context, ownerUserId, item.certificateId, item.number, item.revisionNo)
                    archive = NativeCertificate.listArchive(context, ownerUserId)
                    localDeleteTarget = null
                    message = "تم حذف النسخة من فهرس هذا الجهاز"
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { localDeleteTarget = null }) { Text("تراجع") } },
        )
    }

    if (showCertificateDatePicker) {
        HalaqatiDatePickerDialog(
            initial = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()),
            title = "اختر تاريخ الشهادة",
            onDismiss = { showCertificateDatePicker = false },
            onConfirm = { selected -> date = selected.toString(); showCertificateDatePicker = false },
        )
    }
    if (showCertificateMonthPicker) {
        HalaqatiMonthPickerDialog(
            initial = runCatching { YearMonth.parse(month) }.getOrDefault(YearMonth.now()),
            onDismiss = { showCertificateMonthPicker = false },
            onConfirm = { selected -> month = selected.toString(); resetAutoText(); showCertificateMonthPicker = false },
        )
    }
}

@Composable
private fun CertificateStudioHeader(version: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        com.mohammedsamir.halaqati.ui.components.NourIconBadge(Icons.Default.WorkspacePremium)
        Column(Modifier.weight(1f)) {
            Text("مركز الشهادات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text("تحرير · معاينة A4 · إصدار وأرشفة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(version, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/** RC24.1: one archive record in ~64dp; details open on tap instead of being always visible. */
@Composable
private fun CertificateArchiveRow(
    name: String,
    meta: String,
    statusText: String,
    statusKind: String,
    flagText: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onManage: () -> Unit,
    onDelete: (() -> Unit)?,
    details: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = if (expanded) 10.dp else 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                StatusPill(statusText, statusKind)
                if (flagText != null) StatusPill(flagText, "warning")
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.DeleteOutline, contentDescription = "حذف نهائي", tint = MaterialTheme.colorScheme.error) }
                }
                IconButton(onClick = onManage, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "إجراءات") }
            }
            if (expanded) {
                Column(Modifier.padding(end = 10.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = details)
            }
        }
    }
}

@Composable
private fun CertificateActionButton(label: String, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = if (destructive) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonColors(),
    ) { Text(label) }
}

@Composable
private fun CertificateTemplateCard(template: String, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = certificatePalette(template)
    val bg = androidx.compose.ui.graphics.Color(p.background)
    val primary = androidx.compose.ui.graphics.Color(p.primary)
    val accent = androidx.compose.ui.graphics.Color(p.accent)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 112.dp),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.fillMaxWidth().height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = bg,
                    border = BorderStroke(2.dp, primary),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.padding(5.dp)) {
                        Surface(
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            border = BorderStroke(1.dp, accent),
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.width(42.dp).height(4.dp).padding(0.dp))
                                Text("شهادة", color = primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(4.dp))
                                HorizontalDivider(thickness = 1.dp, color = accent)
                                Spacer(Modifier.height(4.dp))
                                Text("الطالب", color = androidx.compose.ui.graphics.Color(p.secondary), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (selected) {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(3.dp).size(14.dp))
                    }
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun CertificateColorField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val fallback = when (label) {
        "الذهبي" -> "#C5A350"
        "الثانوي" -> "#8A4D22"
        "الخلفية" -> "#FBF7EB"
        "النص" -> "#18211E"
        else -> "#0B5F5B"
    }
    val preview = runCatching { androidx.compose.ui.graphics.Color(Color.parseColor(value.ifBlank { fallback })) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { raw -> onChange(raw.filter { it.isLetterOrDigit() || it == '#' }.take(7)) },
            label = { Text(label) },
            placeholder = { Text(fallback) },
            leadingIcon = {
                Surface(shape = RoundedCornerShape(7.dp), color = preview, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.size(27.dp)) { }
            },
            trailingIcon = {
                if (value.isNotBlank()) IconButton(onClick = { onChange("") }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Restore, contentDescription = "لون القالب") }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("#0B5F5B", "#174936", "#164F63", "#6F2632", "#C5A350", "#8A4D22").forEach { hex ->
                val c = androidx.compose.ui.graphics.Color(Color.parseColor(hex))
                Surface(
                    onClick = { onChange(hex) },
                    color = c,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(if (value.equals(hex, true)) 2.dp else 1.dp, if (value.equals(hex, true)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(28.dp),
                ) { }
            }
        }
    }
}

@Composable
private fun CertificateEditableTextField(
    label: String,
    value: String,
    customized: Boolean,
    onValueChange: (String) -> Unit,
    onReset: () -> Unit,
    minLines: Int = 1,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset, enabled = customized, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) {
                Icon(Icons.Default.Restore, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
                Text("الافتراضي", style = MaterialTheme.typography.labelSmall)
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            maxLines = if (minLines > 1) 5 else 2,
        )
    }
}

@Composable
private fun CertificateStyleSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(kotlin.math.round(value * 10f).div(10f).toString(), style = MaterialTheme.typography.labelLarge, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun CertificateToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun CertificateAssetRow(
    label: String,
    hasCustom: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .34f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = if (hasCustom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (hasCustom) "مخصص ومحفوظ محليًا" else "يُستخدم الافتراضي عند توفره",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onPick) { Text(if (hasCustom) "استبدال" else "اختيار") }
            if (hasCustom) IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.DeleteOutline, contentDescription = "إزالة $label") }
        }
    }
}

private fun safeCertificateFileName(value: String): String = value.replace(Regex("[\\/:*?\"<>|]"), "_").take(70)

private fun certificateSeriesKey(type: String): String = when (type) {
    "sard" -> "sard"
    "khatam" -> "khatm"
    "monthly" -> "monthly"
    "ideal" -> "ideal"
    "rank" -> "rank"
    else -> "general"
}

private fun certificateTitle(type: String) = when (type) {
    "sard" -> "إتمام سرد أجزاء من القرآن الكريم"
    "khatam" -> "ختم القرآن الكريم"
    "monthly" -> "شهادة التميز الشهري"
    "ideal" -> "شهادة الطالب المثالي"
    "rank" -> "شهادة تفوق وتميز"
    "tajweed" -> "شهادة تجويد"
    "excellence" -> "شهادة تميز"
    else -> "شهادة تقدير"
}

private val certificateJuzNames = listOf(
    "الأول","الثاني","الثالث","الرابع","الخامس","السادس","السابع","الثامن","التاسع","العاشر",
    "الحادي عشر","الثاني عشر","الثالث عشر","الرابع عشر","الخامس عشر","السادس عشر","السابع عشر","الثامن عشر","التاسع عشر","العشرون",
    "الحادي والعشرون","الثاني والعشرون","الثالث والعشرون","الرابع والعشرون","الخامس والعشرون","السادس والعشرون","السابع والعشرون","الثامن والعشرون","التاسع والعشرون","الثلاثون",
)

private fun certificateMonthLabel(value: String): String {
    val months = listOf("يناير","فبراير","مارس","أبريل","مايو","يونيو","يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر")
    val parts = value.split('-')
    val y = parts.getOrNull(0)?.toIntOrNull() ?: return value
    val m = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return value
    return "${months[m - 1]} $y"
}

private fun certificateCountWords(count: Int): String = when (count) {
    1 -> "جزء واحد"; 2 -> "جزأين"; 3 -> "ثلاثة أجزاء"; 4 -> "أربعة أجزاء"; 5 -> "خمسة أجزاء"
    6 -> "ستة أجزاء"; 7 -> "سبعة أجزاء"; 8 -> "ثمانية أجزاء"; 9 -> "تسعة أجزاء"; 10 -> "عشرة أجزاء"
    else -> "$count أجزاء"
}

private fun certificateAchievement(type: String, fromJuz: Int, toJuz: Int, month: String, reason: String, rank: Int, track: String, idealScore: Double, categoryName: String = "جميع الفئات", programName: String = ""): String = when (type) {
    "tajweed" -> "تقديرًا لإتقانه أحكام التلاوة والتجويد وحسن أدائه في ${programName.ifBlank { "برنامج التلاوة والتجويد" }}، سائلين الله أن ينفع به وبعلمه."
    "excellence" -> "تقديرًا لتميزه في ${programName.ifBlank { "برنامج التلاوة والتجويد" }} خلال شهر ${certificateMonthLabel(month)}${reason.trim().takeIf { it.isNotBlank() }?.let { "، وذلك لـ$it" } ?: ""}."
    "sard" -> {
        val a = minOf(fromJuz.coerceIn(1, 30), toJuz.coerceIn(1, 30)); val b = maxOf(fromJuz.coerceIn(1, 30), toJuz.coerceIn(1, 30))
        val count = b - a + 1
        if (count == 1) "تقديرًا لإتمامه بحمد الله سرد الجزء ${certificateJuzNames[a - 1]} من كتاب الله تعالى"
        else "تقديرًا لإتمامه بحمد الله سرد ${certificateCountWords(count)} متتابعة من الجزء ${certificateJuzNames[a - 1]} إلى الجزء ${certificateJuzNames[b - 1]} من كتاب الله تعالى"
    }
    "khatam" -> "تقديرًا لإتمامه بحمد الله ختم القرآن الكريم كاملًا، سائلين الله أن يجعله من أهل القرآن وخاصته."
    "monthly" -> "تقديرًا لتميزه خلال شهر ${certificateMonthLabel(month)}${reason.trim().takeIf { it.isNotBlank() }?.let { "، وذلك لـ$it" } ?: ""}."
    "ideal" -> "تقديرًا لاختياره الطالب المثالي، بما جمعه من حسن الأخلاق والالتزام والمواظبة والجدية في حفظ كتاب الله. الدرجة التقديرية: ${formatNumber(idealScore)}/100."
    "rank" -> if (track == "program") "تقديرًا لحصوله على المركز ${listOf("الأول","الثاني","الثالث")[(rank.coerceIn(1, 3)) - 1]} في ${programName.ifBlank { "برنامج التلاوة والتجويد" }}، تقديرًا لتفوقه في التلاوة والتجويد." else "تقديرًا لحصوله على المركز ${listOf("الأول","الثاني","الثالث")[(rank.coerceIn(1, 3)) - 1]} في ${if (track == "tathbit") "مسار التثبيت" else "مسار الحفظ العام"} · فئة $categoryName."
    else -> "تقديرًا لتميزه في حفظ كتاب الله تعالى."
}

private data class CertificatePalette(val background: Int, val primary: Int, val accent: Int, val secondary: Int, val text: Int)

private fun certificatePalette(template: String): CertificatePalette = when (template) {
    "royal" -> CertificatePalette(Color.rgb(255, 250, 240), Color.rgb(23, 73, 54), Color.rgb(197, 163, 80), Color.rgb(111, 69, 35), Color.rgb(23, 35, 30))
    "gold" -> CertificatePalette(Color.rgb(255, 250, 242), Color.rgb(83, 66, 29), Color.rgb(200, 157, 61), Color.rgb(123, 74, 34), Color.rgb(36, 31, 23))
    "blue" -> CertificatePalette(Color.rgb(247, 246, 239), Color.rgb(22, 79, 99), Color.rgb(185, 150, 84), Color.rgb(107, 73, 40), Color.rgb(23, 32, 39))
    "minimal" -> CertificatePalette(Color.rgb(255, 253, 248), Color.rgb(38, 59, 54), Color.rgb(168, 138, 86), Color.rgb(102, 72, 47), Color.rgb(31, 39, 36))
    "burgundy" -> CertificatePalette(Color.rgb(255, 249, 242), Color.rgb(111, 38, 50), Color.rgb(196, 158, 76), Color.rgb(122, 76, 48), Color.rgb(49, 31, 32))
    else -> CertificatePalette(Color.rgb(251, 247, 235), Color.rgb(11, 95, 91), Color.rgb(185, 149, 74), Color.rgb(138, 77, 34), Color.rgb(24, 33, 30))
}

private fun parseCertificateColor(value: String, fallback: Int): Int = runCatching {
    val raw = value.trim()
    if (raw.isBlank()) fallback else Color.parseColor(if (raw.startsWith("#")) raw else "#$raw")
}.getOrDefault(fallback)

private fun certificatePalette(style: NativeCertificateStyle): CertificatePalette {
    val base = certificatePalette(style.template)
    return CertificatePalette(
        background = parseCertificateColor(style.paperHex, base.background),
        primary = parseCertificateColor(style.primaryHex, base.primary),
        accent = parseCertificateColor(style.accentHex, base.accent),
        secondary = parseCertificateColor(style.secondaryHex, base.secondary),
        text = parseCertificateColor(style.inkHex, base.text),
    )
}

object NativeCertificate {
    private const val PREFS = "halaqati_native_certificates"
    private const val ARCHIVE_KEY = "archive_v4"
    private const val HIDDEN_KEY = "hidden_v1"
    private const val LEGACY_ARCHIVE_KEY = "archive_v3"
    private const val STYLE_KEY = "style_v2"
    private const val ASSETS_KEY = "assets_v1"
    private const val MAX_SOURCE_ASSET_BYTES = 5_000_000
    private const val MAX_STORED_ASSET_BYTES = 800_000
    private const val MAX_ARCHIVE_IMPORT_BYTES = 8_000_000
    private val assetKinds = setOf("center_logo", "halaqa_logo", "teacher_signature", "supervisor_signature", "stamp")

    private fun scoped(ownerUserId: String, suffix: String): String = LocalAccountScopeRules.ownerKey(ownerUserId, suffix)

    fun loadStyle(context: Context, ownerUserId: String): NativeCertificateStyle {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(scoped(ownerUserId, STYLE_KEY), null) ?: return NativeCertificateStyle()
        return runCatching { styleFromJson(JSONObject(raw)) }.getOrElse { NativeCertificateStyle() }
    }

    fun saveStyle(context: Context, ownerUserId: String, style: NativeCertificateStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(scoped(ownerUserId, STYLE_KEY), styleToJson(style).toString()).apply()
    }

    fun peekNumber(context: Context, ownerUserId: String): String = peekNumbers(context, ownerUserId, 1).first()

    fun peekNumbers(context: Context, ownerUserId: String, count: Int): List<String> {
        if (count <= 0) return emptyList()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val year = LocalDate.now().year
        val yearKey = scoped(ownerUserId, "year")
        val seqKey = scoped(ownerUserId, "seq")
        val savedYear = prefs.getInt(yearKey, year)
        val current = if (savedYear == year) prefs.getInt(seqKey, 0) else 0
        return (1..count).map { offset -> "HAL-CERT-$year-${(current + offset).toString().padStart(5, '0')}" }
    }

    fun commitNumber(context: Context, ownerUserId: String, number: String) {
        val match = Regex("HAL-CERT-(\\d{4})-(\\d+)").matchEntire(number.trim()) ?: return
        val year = match.groupValues[1].toIntOrNull() ?: return
        val seq = match.groupValues[2].toIntOrNull() ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val yearKey = scoped(ownerUserId, "year")
        val seqKey = scoped(ownerUserId, "seq")
        val currentYear = prefs.getInt(yearKey, year)
        val currentSeq = if (currentYear == year) prefs.getInt(seqKey, 0) else 0
        if (seq > currentSeq) prefs.edit().putInt(yearKey, year).putInt(seqKey, seq).apply()
    }

    fun archive(context: Context, ownerUserId: String, drafts: List<NativeCertificateDraft>) {
        if (drafts.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        migrateArchiveV3IfNeeded(context, ownerUserId)
        val archiveKey = scoped(ownerUserId, ARCHIVE_KEY)
        val current = runCatching { JSONArray(prefs.getString(archiveKey, "[]") ?: "[]") }.getOrElse { JSONArray() }
        val byRevision = LinkedHashMap<String, JSONObject>()
        fun keyOf(obj: JSONObject): String {
            val cid = obj.optString("certificate_id")
            val revision = obj.optInt("revision_no", 1)
            val number = obj.optString("number")
            return if (cid.isNotBlank()) "$cid:$revision" else "legacy:$number:$revision"
        }
        drafts.asReversed().forEach { draft ->
            val obj = toJson(draft)
            byRevision[keyOf(obj)] = obj
        }
        for (i in 0 until current.length()) current.optJSONObject(i)?.let { obj -> byRevision.putIfAbsent(keyOf(obj), obj) }
        val out = JSONArray()
        byRevision.values.take(500).forEach { out.put(it) }
        prefs.edit().putString(archiveKey, out.toString()).apply()
    }

    fun listArchive(context: Context, ownerUserId: String): List<NativeCertificateDraft> {
        migrateArchiveV3IfNeeded(context, ownerUserId)
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(scoped(ownerUserId, ARCHIVE_KEY), "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { index -> arr.optJSONObject(index)?.let(::fromJson) }
    }

    /** RC24.1: certificates the user hid from the list on this device (reversible; nothing is deleted). */
    fun hiddenIds(context: Context, ownerUserId: String): Set<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(scoped(ownerUserId, HIDDEN_KEY), "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toSet()
    }

    fun setHidden(context: Context, ownerUserId: String, certificateId: String, hidden: Boolean): Set<String> {
        val next = if (hidden) hiddenIds(context, ownerUserId) + certificateId else hiddenIds(context, ownerUserId) - certificateId
        val out = JSONArray()
        next.take(2000).forEach { out.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(scoped(ownerUserId, HIDDEN_KEY), out.toString()).apply()
        return next
    }

    /**
     * RC24.1: removes entries from this device's certificate index only. Saved PDF files and the
     * official Supabase record are not touched. [revisionNo] = null removes every revision.
     */
    fun deleteLocalArchive(context: Context, ownerUserId: String, certificateId: String, number: String, revisionNo: Int?): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        migrateArchiveV3IfNeeded(context, ownerUserId)
        val archiveKey = scoped(ownerUserId, ARCHIVE_KEY)
        val current = runCatching { JSONArray(prefs.getString(archiveKey, "[]") ?: "[]") }.getOrElse { JSONArray() }
        val out = JSONArray()
        var removed = 0
        for (i in 0 until current.length()) {
            val obj = current.optJSONObject(i) ?: continue
            val sameRecord = if (certificateId.isNotBlank()) obj.optString("certificate_id") == certificateId
            else obj.optString("certificate_id").isBlank() && obj.optString("number") == number
            val sameRevision = revisionNo == null || obj.optInt("revision_no", 1) == revisionNo
            if (sameRecord && sameRevision) removed++ else out.put(obj)
        }
        if (removed > 0) prefs.edit().putString(archiveKey, out.toString()).apply()
        return removed
    }

    private fun migrateArchiveV3IfNeeded(context: Context, ownerUserId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v4 = scoped(ownerUserId, ARCHIVE_KEY)
        if (prefs.contains(v4)) return
        val legacy = prefs.getString(scoped(ownerUserId, LEGACY_ARCHIVE_KEY), null) ?: return
        val arr = runCatching { JSONArray(legacy) }.getOrElse { JSONArray() }
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.apply {
            if (!has("revision_no")) put("revision_no", 1)
            if (!has("asset_availability")) put("asset_availability", "unknown_legacy")
            if (!has("legacy_partial")) put("legacy_partial", false)
        }
        prefs.edit().putString(v4, arr.toString()).apply()
    }

    private fun toJson(draft: NativeCertificateDraft) = JSONObject()
        .put("number", draft.number)
        .put("student_id", draft.studentId)
        .put("halaqa_id", draft.halaqaId ?: JSONObject.NULL)
        .put("category_id", draft.categoryId ?: JSONObject.NULL)
        .put("category_name", draft.categoryName)
        .put("student_name", draft.studentName)
        .put("type", draft.type)
        .put("title", draft.title)
        .put("achievement", draft.achievement)
        .put("date", draft.date)
        .put("intro", draft.intro)
        .put("dua", draft.dua)
        .put("center_name", draft.centerName)
        .put("halaqa_name", draft.halaqaName)
        .put("teacher_name", draft.teacherName)
        .put("supervisor_name", draft.supervisorName)
        .put("from_juz", draft.fromJuz)
        .put("to_juz", draft.toJuz)
        .put("month", draft.month)
        .put("rank", draft.rank)
        .put("track", draft.track)
        .put("ideal_score", draft.idealScore ?: JSONObject.NULL)
        .put("certificate_id", draft.certificateId)
        .put("revision_id", draft.revisionId)
        .put("revision_no", draft.revisionNo)
        .put("status", draft.status)
        .put("legacy_partial", draft.legacyPartial)
        .put("asset_availability", draft.assetAvailability)
        .put("style", styleToJson(draft.style))

    private fun fromJson(obj: JSONObject): NativeCertificateDraft? {
        val number = obj.optString("number")
        val student = obj.optString("student_name")
        if (number.isBlank() || student.isBlank()) return null
        return NativeCertificateDraft(
            number = number,
            studentId = obj.optString("student_id"),
            halaqaId = obj.optString("halaqa_id").takeUnless { it.isBlank() || it == "null" },
            categoryId = obj.optString("category_id").takeUnless { it.isBlank() || it == "null" },
            categoryName = obj.optString("category_name", "غير مصنف"),
            studentName = student,
            type = obj.optString("type", "sard"),
            title = obj.optString("title", "شهادة تقدير"),
            achievement = obj.optString("achievement"),
            date = obj.optString("date"),
            intro = obj.optString("intro", "تتشرف حلقة معاوية بن أبي سفيان التابعة لـمركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم بمنح هذه الشهادة للطالب"),
            dua = obj.optString("dua", "سائلين الله تعالى أن يثبت القرآن في صدره، وأن يجعله له نورًا وهدًى ورفعةً في الدنيا والآخرة."),
            centerName = obj.optString("center_name", "مركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم"),
            halaqaName = obj.optString("halaqa_name", "حلقة معاوية بن أبي سفيان"),
            teacherName = obj.optString("teacher_name", "د. محمد سمير أبو يونس"),
            supervisorName = obj.optString("supervisor_name", "الشيخ محمد حلمي أبو عنزة"),
            fromJuz = obj.optInt("from_juz", 1),
            toJuz = obj.optInt("to_juz", 1),
            month = obj.optString("month"),
            rank = obj.optInt("rank", 1),
            track = obj.optString("track", "general_hifz"),
            idealScore = if (obj.isNull("ideal_score")) null else obj.optDouble("ideal_score"),
            style = obj.optJSONObject("style")?.let(::styleFromJson) ?: NativeCertificateStyle(),
            certificateId = obj.optString("certificate_id"),
            revisionId = obj.optString("revision_id"),
            revisionNo = obj.optInt("revision_no", 1),
            status = obj.optString("status", "issued"),
            legacyPartial = obj.optBoolean("legacy_partial", false),
            assetAvailability = obj.optString("asset_availability", "available"),
        )
    }

    /** RC24: tiny server archive index. Full text/config is stored in certificate_revisions; no binary assets are uploaded. */
    fun lightweightServerSnapshot(draft: NativeCertificateDraft): JSONObject = JSONObject()
        .put("schema", "halaqati_certificate_archive_lite_v2")
        .put("kind", draft.type)
        .put("student_name", draft.studentName)
        .put("category_name", draft.categoryName)
        .put("storage_policy", "metadata_only_no_pdf_no_assets")

    fun renderSnapshot(draft: NativeCertificateDraft, seriesKey: String): CertificateRenderSnapshot = CertificateRenderSnapshot(
        studentId = draft.studentId,
        studentName = draft.studentName,
        centerName = draft.centerName,
        halaqaName = draft.halaqaName,
        certificateType = when (draft.type) { "khatam" -> "hifz"; "sard" -> "sard"; else -> "achievement" },
        seriesKey = seriesKey,
        title = draft.title,
        intro = draft.intro,
        achievement = draft.achievement,
        dua = draft.dua,
        issueDate = draft.date,
        officialNumber = draft.number.takeUnless { it.startsWith("سيُ") }.orEmpty(),
        teacherName = draft.teacherName,
        supervisorName = draft.supervisorName,
        style = styleToJson(draft.style),
        assetRefs = JSONObject().put("policy", "local_only"),
        source = JSONObject().put("category_name", draft.categoryName).put("from_juz", draft.fromJuz).put("to_juz", draft.toJuz).put("month", draft.month).put("rank", draft.rank).put("track", draft.track),
        rendererVersion = "rc24",
    )

    fun draftFromRevision(record: CertificateRecord, revision: CertificateRevisionRow): NativeCertificateDraft {
        val snap = revision.renderSnapshot
        val src = snap.source
        return NativeCertificateDraft(
            number = record.number.orEmpty(), studentId = record.studentId, halaqaId = record.halaqaId,
            categoryName = src.optString("category_name", "غير مصنف"), studentName = snap.studentName,
            type = when (snap.certificateType) { "sard" -> "sard"; "hifz" -> "khatam"; else -> snap.seriesKey.takeIf { it in setOf("monthly","ideal","rank") } ?: "monthly" },
            title = snap.title, achievement = snap.achievement, date = snap.issueDate, intro = snap.intro, dua = snap.dua,
            centerName = snap.centerName, halaqaName = snap.halaqaName, teacherName = snap.teacherName, supervisorName = snap.supervisorName,
            fromJuz = src.optInt("from_juz", 1), toJuz = src.optInt("to_juz", 1), month = src.optString("month"), rank = src.optInt("rank", 1), track = src.optString("track", "general_hifz"),
            style = styleFromJson(snap.style), certificateId = record.id, revisionId = revision.id, revisionNo = revision.revisionNo, status = record.status, legacyPartial = record.legacyPartial,
        )
    }

    private fun styleToJson(style: NativeCertificateStyle) = JSONObject()
        .put("template", style.template)
        .put("font_family", style.fontFamily)
        .put("custom_font_name", style.customFontName)
        .put("title_size", style.titleSize.toDouble())
        .put("student_size", style.studentSize.toDouble())
        .put("body_size", style.bodySize.toDouble())
        .put("logo_size", style.logoSize.toDouble())
        .put("border_width", style.borderWidth.toDouble())
        .put("ornament_strength", style.ornamentStrength.toDouble())
        .put("primary_hex", style.primaryHex)
        .put("accent_hex", style.accentHex)
        .put("secondary_hex", style.secondaryHex)
        .put("paper_hex", style.paperHex)
        .put("ink_hex", style.inkHex)
        .put("show_verse", style.showVerse)
        .put("show_logos", style.showLogos)
        .put("show_signatures", style.showSignatures)
        .put("show_stamp", style.showStamp)
        .put("show_date", style.showDate)
        .put("show_number", style.showNumber)
        .put("number_position", style.numberPosition)

    private fun styleFromJson(obj: JSONObject) = NativeCertificateStyle(
        template = when (val raw = obj.optString("template", "emerald")) {
            "classic" -> "minimal"
            in setOf("emerald", "royal", "gold", "blue", "minimal", "burgundy") -> raw
            else -> "emerald"
        },
        fontFamily = when (obj.optString("font_family", "Cairo")) {
            "Tahoma", "Arial", "sans", "sans-serif" -> "sans-serif"
            "Serif", "serif" -> "serif"
            "Mono", "mono", "monospace" -> "monospace"
            "device" -> "device"
            else -> "Cairo"
        },
        customFontName = obj.optString("custom_font_name", "").take(80),
        titleSize = obj.optDouble("title_size", 44.0).toFloat().coerceIn(30f, 64f),
        studentSize = obj.optDouble("student_size", 38.0).toFloat().coerceIn(28f, 58f),
        bodySize = obj.optDouble("body_size", 20.0).toFloat().coerceIn(15f, 28f),
        logoSize = obj.optDouble("logo_size", 168.0).toFloat().coerceIn(100f, 230f),
        borderWidth = obj.optDouble("border_width", 3.0).toFloat().coerceIn(1f, 14f),
        ornamentStrength = obj.optDouble("ornament_strength", .82).toFloat().coerceIn(0f, 1f),
        primaryHex = obj.optString("primary_hex"),
        accentHex = obj.optString("accent_hex"),
        secondaryHex = obj.optString("secondary_hex"),
        paperHex = obj.optString("paper_hex"),
        inkHex = obj.optString("ink_hex"),
        showVerse = obj.optBoolean("show_verse", true),
        showLogos = obj.optBoolean("show_logos", true),
        showSignatures = obj.optBoolean("show_signatures", true),
        showStamp = obj.optBoolean("show_stamp", true),
        showDate = obj.optBoolean("show_date", true),
        showNumber = obj.optBoolean("show_number", true),
        numberPosition = obj.optString("number_position", "under_logo").takeIf { it in setOf("under_logo", "top", "footer_start", "footer_end", "header") } ?: "under_logo",
    )

    fun loadAssets(context: Context, ownerUserId: String): NativeCertificateAssets {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(scoped(ownerUserId, ASSETS_KEY), null)
            ?: return NativeCertificateAssets()
        return runCatching { assetsFromJson(JSONObject(raw)) }.getOrElse { NativeCertificateAssets() }
    }

    fun importAsset(context: Context, ownerUserId: String, kind: String, uri: Uri) {
        require(kind in assetKinds) { "نوع أصل الشهادة غير معروف" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_SOURCE_ASSET_BYTES) }
            ?: error("تعذر قراءة الصورة")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 1..12000 && options.outHeight in 1..12000) { "أبعاد الصورة غير صالحة" }
        require(options.outWidth.toLong() * options.outHeight.toLong() <= 60_000_000L) { "الصورة كبيرة جدًا" }
        var sample = 1
        while (maxOf(options.outWidth / sample, options.outHeight / sample) > 1800) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("صيغة الصورة غير مدعومة")
        val normalized = normalizeAssetBitmap(bitmap)
        if (normalized !== bitmap) bitmap.recycle()
        val png = compressAssetPng(normalized)
        normalized.recycle()
        val dataUrl = "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP)
        val current = loadAssets(context, ownerUserId)
        val updated = when (kind) {
            "center_logo" -> current.copy(centerLogo = dataUrl)
            "halaqa_logo" -> current.copy(halaqaLogo = dataUrl)
            "teacher_signature" -> current.copy(teacherSignature = dataUrl)
            "supervisor_signature" -> current.copy(supervisorSignature = dataUrl)
            "stamp" -> current.copy(stamp = dataUrl)
            else -> current
        }
        saveAssets(context, ownerUserId, updated)
    }

    fun removeAsset(context: Context, ownerUserId: String, kind: String) {
        require(kind in assetKinds) { "نوع أصل الشهادة غير معروف" }
        val current = loadAssets(context, ownerUserId)
        val updated = when (kind) {
            "center_logo" -> current.copy(centerLogo = null)
            "halaqa_logo" -> current.copy(halaqaLogo = null)
            "teacher_signature" -> current.copy(teacherSignature = null)
            "supervisor_signature" -> current.copy(supervisorSignature = null)
            "stamp" -> current.copy(stamp = null)
            else -> current
        }
        saveAssets(context, ownerUserId, updated)
    }

    private fun saveAssets(context: Context, ownerUserId: String, assets: NativeCertificateAssets) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(scoped(ownerUserId, ASSETS_KEY), assetsToJson(assets).toString())
            .apply()
    }

    private fun assetsToJson(assets: NativeCertificateAssets) = JSONObject().apply {
        assets.centerLogo?.let { put("center_logo", it) }
        assets.halaqaLogo?.let { put("halaqa_logo", it) }
        assets.teacherSignature?.let { put("teacher_signature", it) }
        assets.supervisorSignature?.let { put("supervisor_signature", it) }
        assets.stamp?.let { put("stamp", it) }
    }

    private fun assetsFromJson(obj: JSONObject): NativeCertificateAssets = NativeCertificateAssets(
        centerLogo = sanitizeStoredAsset(obj.optString("center_logo").takeIf(String::isNotBlank)),
        halaqaLogo = sanitizeStoredAsset(obj.optString("halaqa_logo").takeIf(String::isNotBlank)),
        teacherSignature = sanitizeStoredAsset(obj.optString("teacher_signature").takeIf(String::isNotBlank)),
        supervisorSignature = sanitizeStoredAsset(obj.optString("supervisor_signature").takeIf(String::isNotBlank)),
        stamp = sanitizeStoredAsset(obj.optString("stamp").takeIf(String::isNotBlank)),
    )

    private fun sanitizeStoredAsset(value: String?): String? {
        if (value == null || !value.startsWith("data:image/png;base64,")) return null
        val encoded = value.substringAfter(',')
        val decoded = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return null
        if (decoded.isEmpty() || decoded.size > MAX_STORED_ASSET_BYTES) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(decoded, 0, decoded.size, options)
        if (options.outWidth !in 1..1200 || options.outHeight !in 1..1200) return null
        return "data:image/png;base64," + Base64.encodeToString(decoded, Base64.NO_WRAP)
    }

    private fun normalizeAssetBitmap(source: Bitmap): Bitmap {
        val maxDim = maxOf(source.width, source.height)
        if (maxDim <= 900) return source
        val scale = 900f / maxDim.toFloat()
        return Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun compressAssetPng(source: Bitmap): ByteArray {
        var working = source
        repeat(4) { attempt ->
            val out = ByteArrayOutputStream()
            require(working.compress(Bitmap.CompressFormat.PNG, 100, out)) { "تعذر ضغط الصورة" }
            val bytes = out.toByteArray()
            if (bytes.size <= MAX_STORED_ASSET_BYTES) {
                if (working !== source) working.recycle()
                return bytes
            }
            if (attempt == 3) error("حجم الصورة بعد المعالجة ما زال كبيرًا")
            val next = Bitmap.createScaledBitmap(working, (working.width * .75f).toInt().coerceAtLeast(1), (working.height * .75f).toInt().coerceAtLeast(1), true)
            if (working !== source) working.recycle()
            working = next
        }
        error("تعذر معالجة الصورة")
    }

    private fun readLimited(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "الملف أكبر من الحد المسموح" }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun bitmapFromDataUrl(value: String?): Bitmap? {
        val safe = sanitizeStoredAsset(value) ?: return null
        val bytes = runCatching { Base64.decode(safe.substringAfter(','), Base64.DEFAULT) }.getOrNull() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun exportArchiveIndex(context: Context, ownerUserId: String, uri: Uri): Int {
        val items = listArchive(context, ownerUserId)
        val archiveJson = JSONArray().apply { items.forEach { put(toJson(it)) } }
        val assets = assetsToJson(loadAssets(context, ownerUserId))
        val root = JSONObject()
            .put("schema", "halaqati_certificate_archive")
            .put("version", 4)
            .put("exported_at", java.time.Instant.now().toString())
            .put("archive", archiveJson)
            .put("style", styleToJson(loadStyle(context, ownerUserId)))
            .put("assets", assets)
        context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
            ?: error("تعذر فتح ملف الأرشيف")
        return items.size
    }

    fun importArchiveIndex(context: Context, ownerUserId: String, uri: Uri): Int {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_ARCHIVE_IMPORT_BYTES) }
            ?: error("تعذر قراءة ملف الأرشيف")
        val text = bytes.toString(Charsets.UTF_8).trim()
        require(text.isNotEmpty()) { "ملف الأرشيف فارغ" }
        val (arr, importedStyle, importedAssets) = if (text.startsWith("[")) {
            Triple(JSONArray(text), null, null)
        } else {
            val root = JSONObject(text)
            require(root.optString("schema") == "halaqati_certificate_archive") { "صيغة أرشيف الشهادات غير معروفة" }
            require(root.optInt("version", 0) in 1..4) { "إصدار أرشيف الشهادات غير مدعوم" }
            Triple(
                root.optJSONArray("archive") ?: JSONArray(),
                root.optJSONObject("style")?.let(::styleFromJson),
                root.optJSONObject("assets")?.let(::assetsFromJson),
            )
        }
        val imported = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::fromJson) }
        require(imported.isNotEmpty() || arr.length() == 0) { "ملف الأرشيف لا يحتوي سجلات شهادات صالحة" }
        archive(context, ownerUserId, imported)
        importedStyle?.let { saveStyle(context, ownerUserId, it) }
        importedAssets?.let { saveAssets(context, ownerUserId, it) }
        return imported.size
    }

    suspend fun export(context: Context, uri: Uri, ownerUserId: String, draft: NativeCertificateDraft) {
        validate(draft)
        val html = CertificateHtmlRenderer.build(context, draft, loadAssets(context, ownerUserId))
        VectorDocumentRenderer.saveHtmlPdf(
            context = context,
            uri = uri,
            html = html,
            documentName = "شهادة ${draft.studentName}",
            orientation = DocumentOrientation.LANDSCAPE,
        )
    }

    /** RC13: each student receives an independent local vector PDF; Supabase stores metadata only. */
    suspend fun exportToDownloads(
        context: Context,
        ownerUserId: String,
        draft: NativeCertificateDraft,
        onProgress: ((DocumentExportPhase) -> Unit)? = null,
    ): String {
        validate(draft)
        val fileName = "شهادة_${safeCertificateFileName(draft.studentName)}_${safeCertificateFileName(draft.number)}.pdf"
        val html = CertificateHtmlRenderer.build(context, draft, loadAssets(context, ownerUserId))
        val result = VectorDocumentRenderer.saveHtmlPdfToDownloads(
            context = context,
            html = html,
            fileName = fileName,
            subdirectory = "Certificates",
            documentName = "شهادة ${draft.studentName}",
            orientation = DocumentOrientation.LANDSCAPE,
            onProgress = onProgress,
        )
        return result.uri ?: result.directory.orEmpty()
    }

    private fun validate(draft: NativeCertificateDraft) {
        val seriesKey = when (draft.type) { "sard" -> "sard"; "khatam" -> "khatm"; "monthly" -> "monthly"; "ideal" -> "ideal"; "rank" -> "rank"; else -> "general" }
        val errors = CertificateRules.validateRequiredSnapshot(renderSnapshot(draft, seriesKey), requireOfficialNumber = true)
        require(errors.isEmpty()) { "بيانات الشهادة ناقصة: ${errors.joinToString("، ") { it.arabicLabel }}" }
    }

    private fun drawOctagram(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = Path()
        repeat(16) { i ->
            val angle = Math.toRadians(-90.0 + i * 22.5)
            val r = if (i % 2 == 0) radius else radius * .43f
            val x = cx + kotlin.math.cos(angle).toFloat() * r
            val y = cy + kotlin.math.sin(angle).toFloat() * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun mixColor(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun c(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return Color.rgb(c(Color.red(a), Color.red(b)), c(Color.green(a), Color.green(b)), c(Color.blue(a), Color.blue(b)))
    }

    private fun resolveCertificateTypeface(context: Context, style: NativeCertificateStyle, bold: Boolean): Typeface {
        val base = when (style.fontFamily) {
            "Cairo" -> ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            "sans-serif" -> Typeface.SANS_SERIF
            "device" -> Typeface.create(style.customFontName.ifBlank { "sans-serif" }, Typeface.NORMAL)
            else -> Typeface.SANS_SERIF
        }
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun drawTextBlock(
        context: Context,
        canvas: Canvas,
        style: NativeCertificateStyle,
        value: String,
        left: Float,
        top: Float,
        width: Int,
        size: Float,
        color: Int,
        bold: Boolean = false,
        maxLines: Int = 2,
        align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
    ): Int {
        if (value.isBlank()) return 0
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = resolveCertificateTypeface(context, style, bold)
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .setMaxLines(maxLines)
            .build()
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    private fun drawCircleImage(canvas: Canvas, bitmap: Bitmap, cx: Float, cy: Float, size: Float, borderColor: Int) {
        val radius = size / 2f
        val path = Path().apply { addCircle(cx, cy, radius - 2f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, android.graphics.RectF(cx - radius, cy - radius, cx + radius, cy + radius), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            strokeWidth = 2.2f
            color = borderColor
        })
    }

    /**
     * RC13: visual parity with the v2.44 certificate language.
     * The full-page lattice is deliberately extremely faint; visual weight is kept
     * in the nested frame, corner geometry and side rosettes instead of wallpaper.
     */
    private fun drawCertificateOrnaments(canvas: Canvas, style: NativeCertificateStyle, palette: CertificatePalette) {
        val factor = when (style.template) {
            "royal" -> 1.08f
            "gold" -> 1.04f
            "blue" -> .92f
            "minimal" -> .48f
            "burgundy" -> .96f
            else -> .9f
        }
        val strength = (style.ornamentStrength * factor).coerceIn(0f, 1.1f)
        if (strength <= .01f) return

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            strokeWidth = .75f
            color = palette.primary
            alpha = (18 * strength).toInt().coerceIn(1, 20)
        }
        val bgAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            strokeWidth = .65f
            color = palette.accent
            alpha = (14 * strength).toInt().coerceIn(1, 18)
        }
        if (style.template != "minimal") {
            var y = 90f
            while (y <= 705f) {
                var x = 100f
                while (x <= 1025f) {
                    drawOctagram(canvas, x, y, 12f, bg)
                    canvas.drawLine(x - 19f, y, x + 19f, y, bgAccent)
                    canvas.drawLine(x, y - 19f, x, y + 19f, bgAccent)
                    x += 116f
                }
                y += 116f
            }
        }

        val primary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            strokeWidth = 1.8f
            color = palette.primary
            alpha = (228 * strength).toInt().coerceIn(28, 240)
        }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            strokeWidth = 1.65f
            color = palette.accent
            alpha = (240 * strength).toInt().coerceIn(32, 248)
        }
        val faint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            strokeWidth = 1f
            color = palette.secondary
            alpha = (118 * strength).toInt().coerceIn(16, 130)
        }

        fun corner(cx: Float, cy: Float, sx: Float, sy: Float) {
            drawOctagram(canvas, cx, cy, 39f, accent)
            drawOctagram(canvas, cx, cy, 26f, primary)
            drawOctagram(canvas, cx, cy, 12f, accent)
            canvas.drawCircle(cx, cy, 5f, primary)
            canvas.drawLine(cx, cy, cx + sx * 78f, cy, faint)
            canvas.drawLine(cx, cy, cx, cy + sy * 78f, faint)
            canvas.drawLine(cx + sx * 18f, cy + sy * 18f, cx + sx * 64f, cy + sy * 64f, faint)
        }
        corner(77f, 77f, 1f, 1f)
        corner(1046f, 77f, -1f, 1f)
        corner(77f, 717f, 1f, -1f)
        corner(1046f, 717f, -1f, -1f)

        listOf(52f to 397f, 1071f to 397f).forEach { (cx, cy) ->
            drawOctagram(canvas, cx, cy, 23f, primary)
            drawOctagram(canvas, cx, cy, 12f, accent)
        }
    }

    private fun certificateHijriDate(date: String): String = runCatching {
        val h = HijrahDate.from(LocalDate.parse(date))
        val months = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
        val day = h.get(ChronoField.DAY_OF_MONTH)
        val month = h.get(ChronoField.MONTH_OF_YEAR).coerceIn(1, 12)
        val year = h.get(ChronoField.YEAR_OF_ERA)
        "$day ${months[month - 1]} $year هـ"
    }.getOrDefault("—")

    private fun certificateGregorianDate(date: String): String = runCatching {
        val d = LocalDate.parse(date)
        val months = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
        "${d.dayOfMonth} ${months[d.monthValue - 1]} ${d.year}م"
    }.getOrDefault(date)

    private fun drawCertificateCanvas(context: Context, canvas: Canvas, draft: NativeCertificateDraft, assets: NativeCertificateAssets) {
        val style = draft.style
        val palette = certificatePalette(style)
        canvas.drawColor(palette.background)

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                561f, 302f, 470f,
                mixColor(palette.background, palette.accent, .075f),
                palette.background,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, 1123f, 794f, this)
        }

        val frameMain = Paint(Paint.ANTI_ALIAS_FLAG).apply { setStyle(Paint.Style.STROKE); strokeWidth = style.borderWidth.coerceIn(1f, 6f); color = palette.primary }
        val frameGold = Paint(Paint.ANTI_ALIAS_FLAG).apply { setStyle(Paint.Style.STROKE); strokeWidth = 1.9f; color = palette.accent }
        val frameFine = Paint(Paint.ANTI_ALIAS_FLAG).apply { setStyle(Paint.Style.STROKE); strokeWidth = 1f; color = palette.primary; alpha = 180 }
        canvas.drawRect(16f, 16f, 1107f, 778f, frameMain)
        canvas.drawRect(22f, 22f, 1101f, 772f, frameGold)
        canvas.drawRect(30f, 30f, 1093f, 764f, frameMain)
        canvas.drawRect(36f, 36f, 1087f, 758f, frameFine)
        drawCertificateOrnaments(canvas, style, palette)

        val logoSize = style.logoSize.coerceIn(100f, 230f)
        val logoTop = 42f
        val logoY = logoTop + (logoSize / 2f)
        if (style.showLogos) runCatching {
            val customLeft = bitmapFromDataUrl(assets.centerLogo)
            val customRight = bitmapFromDataUrl(assets.halaqaLogo)
            val left = customLeft ?: BitmapFactory.decodeResource(context.resources, R.drawable.center_logo)
            val right = customRight ?: BitmapFactory.decodeResource(context.resources, R.drawable.halaqa_logo)
            drawCircleImage(canvas, left, 142f, logoY, logoSize, palette.accent)
            drawCircleImage(canvas, right, 981f, logoY, logoSize, palette.accent)
            left.recycle(); right.recycle()
        }

        // Organization block: this was lost in the first native renderer.
        drawTextBlock(context, canvas, style, draft.centerName, 295f, 45f, 533, 29f, palette.primary, bold = true, maxLines = 2)
        drawTextBlock(context, canvas, style, draft.halaqaName, 315f, 112f, 493, 23f, palette.secondary, bold = true, maxLines = 1)

        // Refined separator from v2.44.
        val sep = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent; strokeWidth = 1.2f; alpha = 220 }
        canvas.drawLine(388f, 157f, 533f, 157f, sep)
        canvas.drawLine(590f, 157f, 735f, 157f, sep)
        val diamond = Path().apply {
            moveTo(561.5f, 145f); lineTo(573f, 157f); lineTo(561.5f, 169f); lineTo(550f, 157f); close()
        }
        canvas.drawPath(diamond, Paint(Paint.ANTI_ALIAS_FLAG).apply { setStyle(Paint.Style.STROKE); strokeWidth = 2f; color = palette.accent })
        val inner = Path().apply {
            moveTo(561.5f, 150.5f); lineTo(568f, 157f); lineTo(561.5f, 163.5f); lineTo(555f, 157f); close()
        }
        canvas.drawPath(inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { setStyle(Paint.Style.STROKE); strokeWidth = 1.4f; color = palette.primary })

        // Certificate number belongs under the center logo by default, not in the content column.
        if (style.showNumber && style.numberPosition == "under_logo") {
            val numberTop = (logoTop + logoSize + 7f).coerceAtMost(288f)
            canvas.drawLine(64f, numberTop, 220f, numberTop, frameGold)
            canvas.drawLine(64f, numberTop + 35f, 220f, numberTop + 35f, Paint(frameGold).apply { alpha = 130 })
            drawTextBlock(context, canvas, style, "رقم الشهادة:", 70f, numberTop + 6f, 144, 11.5f, palette.text, bold = true, maxLines = 1)
            canvas.drawText(draft.number, 142f, numberTop + 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.text; textSize = 11.5f; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            })
        }
        if (style.showNumber && style.numberPosition == "top") {
            val rr = android.graphics.RectF(425f, 24f, 698f, 57f)
            canvas.drawRoundRect(rr, 17f, 17f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mixColor(palette.background, Color.WHITE, .45f) })
            canvas.drawRoundRect(rr, 17f, 17f, Paint(Paint.ANTI_ALIAS_FLAG).apply { setStyle(Paint.Style.STROKE); strokeWidth = 1f; color = palette.accent })
            drawTextBlock(context, canvas, style, "رقم الشهادة: ${draft.number}", 435f, 33f, 253, 12f, palette.text, true, 1)
        }

        // Kicker pill.
        val kicker = android.graphics.RectF(437f, 177f, 686f, 215f)
        canvas.drawRoundRect(kicker, 21f, 21f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mixColor(palette.background, Color.WHITE, .52f) })
        canvas.drawRoundRect(kicker, 21f, 21f, Paint(Paint.ANTI_ALIAS_FLAG).apply { setStyle(Paint.Style.STROKE); strokeWidth = 1.5f; color = palette.accent })
        drawTextBlock(context, canvas, style, "شهادة تقدير وإنجاز", 453f, 186f, 217, 16.5f, palette.secondary, bold = true, maxLines = 1)

        drawTextBlock(context, canvas, style, draft.title, 135f, 222f, 853, style.titleSize.coerceIn(30f, 54f), palette.primary, bold = true, maxLines = 2)
        drawTextBlock(context, canvas, style, draft.intro, 125f, 298f, 873, style.bodySize.coerceIn(15f, 25f), palette.text, maxLines = 2)

        // Student name framed exactly as a focal element, not a loose text line.
        val nameBox = android.graphics.RectF(181f, 363f, 942f, 448f)
        canvas.drawRoundRect(nameBox, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mixColor(palette.background, Color.WHITE, .58f) })
        canvas.drawRoundRect(nameBox, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { setStyle(Paint.Style.STROKE); strokeWidth = 2f; color = palette.accent })
        drawTextBlock(context, canvas, style, draft.studentName, 205f, 383f, 713, style.studentSize.coerceIn(28f, 52f), palette.secondary, bold = true, maxLines = 1)
        drawTextBlock(context, canvas, style, "✦", 152f, 390f, 58, 21f, palette.primary, bold = true, maxLines = 1)
        drawTextBlock(context, canvas, style, "✦", 913f, 390f, 58, 21f, palette.primary, bold = true, maxLines = 1)

        drawTextBlock(context, canvas, style, draft.achievement, 120f, 472f, 883, (style.bodySize + 1f).coerceIn(16f, 28f), palette.text, bold = true, maxLines = 3)
        drawTextBlock(context, canvas, style, draft.dua, 135f, 536f, 853, (style.bodySize - 4f).coerceIn(13f, 22f), mixColor(palette.text, palette.background, .2f), maxLines = 2)

        // Separate Hijri and Gregorian date cards, mirroring v2.44 geometry.
        if (style.showDate) {
            fun dateCard(left: Float, textValue: String, textColor: Int) {
                val rect = android.graphics.RectF(left, 590f, left + 195f, 628f)
                canvas.drawRoundRect(rect, 11f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = mixColor(palette.background, Color.WHITE, .58f) })
                canvas.drawRoundRect(rect, 11f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { setStyle(Paint.Style.STROKE); strokeWidth = 1f; this.color = palette.accent; alpha = 205 })
                drawTextBlock(context, canvas, style, textValue, left + 10f, 601f, 175, 12.5f, textColor, bold = true, maxLines = 1)
            }
            dateCard(152f, certificateGregorianDate(draft.date), palette.secondary)
            dateCard(776f, certificateHijriDate(draft.date), palette.primary)
        }

        // Three-column signature area: teacher | stamp | supervisor.
        if (style.showSignatures) {
            fun signColumn(cx: Float, role: String, name: String, signature: Bitmap?) {
                val lineY = 690f
                canvas.drawLine(cx - 128f, lineY, cx + 128f, lineY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent; strokeWidth = 1f })
                signature?.let { bmp ->
                    canvas.drawBitmap(bmp, null, android.graphics.RectF(cx - 76f, 646f, cx + 76f, 688f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    bmp.recycle()
                }
                drawTextBlock(context, canvas, style, role, cx - 125f, 696f, 250, 11.5f, mixColor(palette.text, palette.background, .28f), maxLines = 1)
                drawTextBlock(context, canvas, style, name, cx - 145f, 714f, 290, 15.5f, palette.primary, bold = true, maxLines = 1)
            }
            signColumn(278f, "المحفظ", draft.teacherName, bitmapFromDataUrl(assets.teacherSignature))
            signColumn(845f, "المشرف", draft.supervisorName, bitmapFromDataUrl(assets.supervisorSignature))
        }

        if (style.showStamp) {
            val stampBitmap = bitmapFromDataUrl(assets.stamp)
            if (stampBitmap != null) {
                drawCircleImage(canvas, stampBitmap, 561.5f, 683f, 91f, palette.accent)
                stampBitmap.recycle()
            } else {
                canvas.drawCircle(561.5f, 683f, 45.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    setStyle(Paint.Style.STROKE); strokeWidth = 2f; color = palette.accent; pathEffect = DashPathEffect(floatArrayOf(7f, 6f), 0f)
                })
                drawTextBlock(context, canvas, style, "الختم الرسمي", 515f, 675f, 93, 11.5f, palette.secondary, bold = true, maxLines = 1)
            }
        }

        // Footer verse is a separate branded badge, not part of the heading.
        if (style.showVerse) {
            val verse = android.graphics.RectF(420f, 744f, 703f, 779f)
            canvas.drawRoundRect(verse, 19f, 19f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.primary })
            canvas.drawRoundRect(verse, 19f, 19f, Paint(Paint.ANTI_ALIAS_FLAG).apply { setStyle(Paint.Style.STROKE); strokeWidth = 1.7f; color = palette.accent })
            drawTextBlock(context, canvas, style, "﴿ وَقُلْ رَبِّ زِدْنِي عِلْمًا ﴾", 435f, 752f, 253, 16f, Color.WHITE, bold = true, maxLines = 1)
        }
        if (style.showNumber && style.numberPosition == "footer_start") {
            drawTextBlock(context, canvas, style, "رقم الشهادة: ${draft.number}", 64f, 752f, 320, 11.5f, palette.text, bold = true, maxLines = 1)
        }
        if (style.showNumber && style.numberPosition == "footer_end") {
            drawTextBlock(context, canvas, style, "رقم الشهادة: ${draft.number}", 739f, 752f, 320, 11.5f, palette.text, bold = true, maxLines = 1)
        }
    }
}

private data class MonthComparison(
    val currentPages: Double,
    val previousPages: Double,
    val currentAbsent: Int,
    val previousAbsent: Int,
    val currentSafety: Double,
    val previousSafety: Double,
)

private fun buildMonthComparison(current: JSONArray, previous: JSONArray): MonthComparison {
    fun metrics(arr: JSONArray): Triple<Double, Int, Double> {
        var pages = 0.0
        var absent = 0
        var mistakes = 0
        var prompts = 0
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            pages += row.optDouble("total_pages", 0.0)
            absent += row.optInt("absent", 0) + row.optInt("excused", 0)
            mistakes += row.optInt("mistakes", 0)
            prompts += row.optInt("prompts", 0)
        }
        return Triple(pages, absent, SafetyScoring.calculate(pages, mistakes, prompts))
    }
    val c = metrics(current); val p = metrics(previous)
    return MonthComparison(c.first, p.first, c.second, p.second, c.third, p.third)
}

@Composable
private fun <T> ChipLine(items: List<T>, content: @Composable (T, Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { content(it, Modifier) }
    }
}

@Composable
private fun MonthComparisonRow(label: String, previous: Double, current: Double, suffix: String, positiveUp: Boolean) {
    val delta = current - previous
    val favorable = if (positiveUp) delta >= 0 else delta <= 0
    val tone = if (favorable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val top = maxOf(previous, current, 1.0)
    fun one(v: Double) = String.format(java.util.Locale.US, "%.1f", v).removeSuffix(".0")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1.2f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(one(previous) + suffix, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(one(current) + suffix, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(
                (if (delta >= 0) "+" else "") + one(delta) + suffix + if (delta >= 0) " ↑" else " ↓",
                Modifier.weight(1f), color = tone, fontWeight = FontWeight.Bold, maxLines = 1,
                style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth((previous / top).toFloat().coerceIn(0f, 1f)).height(3.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outline))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth((current / top).toFloat().coerceIn(0f, 1f)).height(3.dp).clip(RoundedCornerShape(50)).background(tone))
        }
    }
}

@Composable
fun QuranCalculatorScreen() {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    val page1 = first.toIntOrNull()
    val page2 = second.toIntOrNull()
    val range = if (page1 != null && page2 != null && page1 in 1..604 && page2 in 1..604) {
        runCatching {
            QuranCore.engine().calculate(
                QuranRangeRequest(
                    track = "tathbit",
                    activityType = "review_near",
                    selection = QuranSelection.Pages(page1, page2),
                ),
            )
        }.getOrNull()
    } else null

    Column {
        AppTopBar("المصحف والاختيار الدقيق")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "مصحف المدينة — حفص · 604 صفحة · 30 جزءًا · 240 ربع حزب",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = { picker = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("اختيار سورة / آية / صفحة / جزء / ربع")
            }
            HorizontalDivider()
            Text("الحاسبة السريعة بالصفحات", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it.filter(Char::isDigit).take(3) },
                    label = { Text("الصفحة الأولى") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = second,
                    onValueChange = { second = it.filter(Char::isDigit).take(3) },
                    label = { Text("الصفحة الثانية") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            MetricCard("عدد الصفحات", range?.exactPages?.let(::formatNumber) ?: "—", "يحافظ على اتجاه البداية والنهاية")
            range?.let { selected ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(selected.displayLabel, style = MaterialTheme.typography.titleSmall)
                        Text("المقدار ${formatNumber(selected.exactPages)} صفحة · ${selected.ayahCount} آية")
                        Text("${selected.surahCount} سورة · محسوب بدقة QCF4", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(
                "RC8 يحافظ على اتجاه الإدخال؛ لا يتم قلب البداية والنهاية تلقائيًا.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (picker) {
        QuranRangePickerDialog(
            initialStartPage = page1,
            initialEndPage = page2,
            track = "tathbit",
            activityType = "review_near",
            onDismiss = { picker = false },
            onConfirm = { selected ->
                first = selected.startPage?.toString().orEmpty()
                second = selected.endPage?.toString().orEmpty()
                picker = false
            },
        )
    }
}

@Composable
fun RankingsScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var periodMode by remember { mutableStateOf("month") }
    var programTrack by remember { mutableStateOf("general_hifz") }
    var rankingTrack by remember { mutableStateOf("hifz") }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var showDayPicker by remember { mutableStateOf(false) }
    var rankFiltersOpen by rememberSaveable { mutableStateOf(false) }
    // RC83: students' photos from the copy on the device, shown next to each ranking row
    var photoById by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    LaunchedEffect(Unit) { photoById = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.students().associate { it.id to it.photoPath } }.getOrDefault(emptyMap()) } }
    // Nour: the bulk student list no longer carries photos, so the ranked students' photos are fetched on demand (small batches).
    LaunchedEffect(rows) {
        val ids = rows.mapNotNull { r -> (r.optJSONObject("students")?.optString("id")?.takeIf { it.isNotBlank() } ?: r.optString("student_id")).takeIf { it.isNotBlank() } }.distinct()
        val missing = ids.filter { photoById[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            val got = withContext(Dispatchers.IO) {
                com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                    missing.chunked(15).flatMap { c -> AppGraph.repo.table("students", "select=id,photo_path&id=in.(${c.joinToString(",")})&limit=1000").sRows() }
                }.getOrDefault(emptyList())
            }
            val add = got.mapNotNull { r -> r.supStr("photo_path").takeIf { it.startsWith("data:image/") }?.let { r.optString("id") to it } }.toMap()
            if (add.isNotEmpty()) photoById = photoById + add
        }
    }

    val effectiveTrack = if (programTrack == "tathbit") "review" else rankingTrack
    // RC27.10: programs have their own ranking (tilawah / tajweed score, attendance, recitations, points).
    val rankKind = rememberWorkKind()
    val rankState by com.mohammedsamir.halaqati.data.WorkModeHolder.state.collectAsState()
    val programRanking = profile.role != UserRole.GUARDIAN && rankKind == com.mohammedsamir.halaqati.plans.WorkMode.PROGRAM

    suspend fun reload() {
        loading = true
        try {
            val today = LocalDate.now()
            val from = if (periodMode == "day") selectedDay else selectedMonth.atDay(1)
            val to = if (periodMode == "day") selectedDay else if (selectedMonth == YearMonth.from(today)) today else selectedMonth.atEndOfMonth()
            rows = withContext(Dispatchers.IO) {
                if (profile.role == UserRole.GUARDIAN) {
                    val children = AppGraph.repo.guardianChildren().filter { it.status == "active" }
                    buildList {
                        children.forEach { child ->
                            val dashboard = AppGraph.repo.guardianDashboard(child.id, from.toString(), to.toString())
                            val ranks = dashboard.optJSONObject("ranks") ?: JSONObject()
                            listOf("hifz", "review", "sard").forEach { track ->
                                val metric = ranks.optJSONObject(track) ?: JSONObject()
                                add(
                                    JSONObject()
                                        .put("student_id", child.id)
                                        .put("student_name", child.fullName)
                                        .put("track", track)
                                        .put("rank_no", metric.optInt("halaqa_rank", 0))
                                        .put("total_students", metric.optInt("halaqa_total", 0))
                                        .put("category_rank", metric.optInt("category_rank", 0))
                                        .put("category_total", metric.optInt("category_total", 0))
                                        .put("points", metric.optDouble("points", 0.0))
                                        .put("pages", metric.optDouble("pages", 0.0)),
                                )
                            }
                        }
                    }
                } else if (programRanking) {
                    val data = AppGraph.repo.programRankingRange(from.toString(), to.toString(), rankState.programIds)
                    (0 until data.length()).mapNotNull { data.optJSONObject(it) }
                } else {
                    val data = AppGraph.repo.rankingRange(from.toString(), to.toString(), programTrack, effectiveTrack)
                    (0 until data.length()).mapNotNull { data.optJSONObject(it) }
                }
            }
            message = null
        } catch (_: Exception) {
            message = "تعذر تحديث البيانات من الخادم. ستبقى آخر بيانات محلية متاحة ظاهرة."
        } finally {
            loading = false
        }
    }

    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { if (syncEpoch > 0L) reload() }
    LaunchedEffect(periodMode, programTrack, rankingTrack, selectedDay, selectedMonth, programRanking, rankState.selectedId, rankState.activeIds) { reload() }

    Column {
        AppTopBar(
            "الترتيب والإحصائيات",
            onRefresh = {
                scope.launch { reload() }
                onRefresh()
            },
            pending = pending,
        )
        if (profile.role != UserRole.GUARDIAN) WorkScopeBar(profile.role)
        ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // RC84: the filters fold into one summary line (track · activity · period); tap to open them.
                val filterSummary = listOf(
                    if (programRanking) "البرامج" else if (programTrack == "tathbit") "التثبيت" else "الحفظ العام",
                    if (programRanking) null else when (effectiveTrack) { "hifz" -> "الحفظ"; "review" -> "المراجعة"; else -> "السرد" },
                    if (periodMode == "day") selectedDay.toString() else rankingMonthLabel(selectedMonth),
                ).filterNotNull().joinToString(" · ")
                Row(
                    Modifier.fillMaxWidth().clickable { rankFiltersOpen = !rankFiltersOpen }.heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("الفلترة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(filterSummary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Icon(if (rankFiltersOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (rankFiltersOpen) "إخفاء الفلاتر" else "إظهار الفلاتر")
                }
                androidx.compose.animation.AnimatedVisibility(visible = rankFiltersOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (programRanking) {
                    Text(
                        "ترتيب البرنامج: متوسط درجة التلاوة والتجويد، ثم الحضور، ثم عدد التسميعات، ثم النقاط",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (profile.role != UserRole.GUARDIAN) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RankingFilterSelect(
                            label = "المسار",
                            value = programTrack,
                            options = listOf("general_hifz" to "الحفظ العام", "tathbit" to "التثبيت"),
                            onSelected = {
                                programTrack = it
                                if (it == "tathbit") rankingTrack = "review"
                            },
                            modifier = Modifier.weight(1f),
                        )
                        RankingFilterSelect(
                            label = "النشاط",
                            value = effectiveTrack,
                            options = if (programTrack == "tathbit") listOf("review" to "التثبيت") else listOf(
                                "hifz" to "الحفظ",
                                "review" to "المراجعة",
                                "sard" to "السرد",
                            ),
                            onSelected = { rankingTrack = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RankingFilterSelect(
                        label = "الفترة",
                        value = periodMode,
                        options = listOf("day" to "يوم محدد", "month" to "شهر محدد"),
                        onSelected = { periodMode = it },
                        modifier = Modifier.weight(1f),
                    )
                    if (periodMode == "day") {
                        OutlinedButton(
                            onClick = { showDayPicker = true },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(selectedDay.toString(), maxLines = 1)
                        }
                    } else {
                        val monthOptions = remember { (0..35).map { YearMonth.now().minusMonths(it.toLong()) }.map { it.toString() to rankingMonthLabel(it) } }
                        RankingFilterSelect(
                            label = "الشهر",
                            value = selectedMonth.toString(),
                            options = monthOptions,
                            onSelected = { raw -> runCatching { YearMonth.parse(raw) }.getOrNull()?.let { selectedMonth = it } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                }
                }
            }
        }

        if (!AppGraph.repo.online()) {
            StateNotice("وضع دون اتصال: يتم عرض البيانات المحلية المحفوظة ويمكن تغيير الفلاتر دون الحاجة للاتصال.", "warning", Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { StateNotice(it, "warning", Modifier.padding(12.dp)) }
        if (!loading && rows.isEmpty()) EmptyState(if (programRanking) "لا طلاب أو تقييمات تلاوة في هذا البرنامج خلال الفترة المختارة" else if (AppGraph.repo.online()) "لا توجد بيانات لهذا المسار والفترة" else "لا توجد بيانات محلية محفوظة لهذه الفترة بعد")
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (profile.role == UserRole.GUARDIAN) {
                items(rows, key = { "${it.optString("student_id")}:${it.optString("track")}" }) { item ->
                    val pages = item.optDouble("pages", 0.0)
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OneLineName(item.optString("student_name", "طالب"), fontWeight = FontWeight.Bold)
                            Text(activityTrackAr(item.optString("track")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${formatNumber(item.optDouble("points", 0.0))} نقطة · ${formatNumber(pages)} ص")
                        }
                    }
                }
            } else if (programRanking) {
                itemsIndexed(rows) { index, item ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                      Column {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
                                Text("${index + 1}", modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
                            }
                            com.mohammedsamir.halaqati.ui.components.StudentAvatar(photoById[item.optString("student_id")], item.optString("student_name", "طالب"), 44.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                OneLineName(item.optString("student_name", "طالب"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "حضور ${item.optInt("present", 0)} · تسميعات ${item.optInt("recitations", 0)} · نقاط ${item.optInt("points", 0)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (item.isNull("score")) "—" else formatNumber(item.optDouble("score", 0.0)), style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold)
                                Text("درجة التلاوة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                      }
                    }
                }
            } else {
                itemsIndexed(rows) { index, item ->
                    val student = item.optJSONObject("students")
                    ElevatedCard(Modifier.fillMaxWidth()) {
                      Column {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(color = if (index < 3) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold,
                                        color = if (index < 3) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            com.mohammedsamir.halaqati.ui.components.StudentAvatar(photoById[item.optString("student_id").ifBlank { student?.optString("id").orEmpty() }], student?.optString("full_name") ?: item.optString("student_name", "طالب"), 44.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                OneLineName(student?.optString("full_name") ?: item.optString("student_name", "طالب"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatNumber(item.optDouble("pages", 0.0))} ص · ${item.optInt("mistakes", 0)} أخطاء · ${item.optInt("prompts", 0)} نقرات",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                            Text(formatNumber(item.optDouble("points", 0.0)), style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                      }
                    }
                }
            }
        }
    }

    if (showDayPicker) {
        val initialMillis = selectedDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDayPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        selectedDay = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDayPicker = false
                }) { Text("اعتماد") }
            },
            dismissButton = { TextButton(onClick = { showDayPicker = false }) { Text("إلغاء") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun RankingFilterSelect(
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

private fun rankingMonthLabel(month: YearMonth): String {
    val names = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    return "${names[month.monthValue - 1]} ${month.year}"
}

private fun activityTrackAr(value: String) = when (value) {
    "hifz" -> "الحفظ"
    "review" -> "المراجعة"
    "sard" -> "السرد"
    else -> value
}

@Composable
fun ImportExportScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var fileName by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var importedCount by remember { mutableIntStateOf(0) }
    var failedCount by remember { mutableIntStateOf(0) }
    var skippedCount by remember { mutableIntStateOf(0) }
    var importKind by remember { mutableStateOf("students") }
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedHalaqaId by remember { mutableStateOf("") }
    var manualText by remember { mutableStateOf("") }
    var showManualPaste by remember { mutableStateOf(false) }
    var mappingEpoch by remember { mutableIntStateOf(0) }
    var mappingEditorOpen by remember { mutableStateOf(false) }

    val canImport = profile.role in setOf(UserRole.ADMIN, UserRole.SUPERVISOR, UserRole.TEACHER)
    val advanced = profile.role in setOf(UserRole.ADMIN, UserRole.SUPERVISOR)

    LaunchedEffect(profile.id) {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                val data = AppGraph.repo.table("halaqas", "select=id,name,active&active=eq.true&order=name.asc&limit=100")
                (0 until data.length()).mapNotNull { data.optJSONObject(it) }
            }
        }.onSuccess {
            halaqas = it
            if (selectedHalaqaId.isBlank()) selectedHalaqaId = it.firstOrNull()?.optString("id").orEmpty()
        }.onFailure { status = "تعذر تحميل الحلقات: ${it.message ?: "خطأ"}" }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    fileName = SmartImport.displayName(context, uri)
                    preview = withContext(Dispatchers.IO) { SmartImport.read(context, uri, fileName) }
                    mappingEpoch++
                    importedCount = 0; failedCount = 0; skippedCount = 0
                    status = if (preview.isEmpty()) "لم يتم العثور على صفوف قابلة للقراءة" else "تمت القراءة: ${preview.size} صف. راجع الأعمدة والبيانات قبل الاعتماد."
                } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                    preview = emptyList()
                    status = "تعذر القراءة الآمنة: ${e.message ?: "خطأ"}"
                }
            }
        }
    }
    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { Backup.export(context, uri) }
                    status = "تم إنشاء النسخة الاحتياطية"
                } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                    status = e.message ?: "تعذر إنشاء النسخة الاحتياطية"
                }
            }
        }
    }

    suspend fun studentExportRows(): List<NativeStudentExport.Row> = withContext(Dispatchers.IO) {
        val students = AppGraph.repo.students(forceRefresh = true)
        val h = AppGraph.repo.table("halaqas", "select=id,name&limit=500")
        val c = AppGraph.repo.table("student_categories", "select=id,name&limit=1000")
        val halaqaNames = (0 until h.length()).mapNotNull { h.optJSONObject(it) }.associate { it.optString("id") to it.optString("name") }
        val categoryNames = (0 until c.length()).mapNotNull { c.optJSONObject(it) }.associate { it.optString("id") to it.optString("name") }
        NativeStudentExport.rows(students, halaqaNames, categoryNames)
    }
    val exportXlsx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val data = studentExportRows()
                require(data.isNotEmpty()) { "لا يوجد طلاب للتصدير" }
                withContext(Dispatchers.IO) { NativeStudentExport.exportXlsx(context, uri, data) }
            }.onSuccess { status = "تم تصدير الطلاب Excel" }
                .onFailure { status = "تعذر تصدير Excel: ${it.message ?: "خطأ"}" }
        }
    }
    val exportWord = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/msword"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val data = studentExportRows()
                require(data.isNotEmpty()) { "لا يوجد طلاب للتصدير" }
                withContext(Dispatchers.IO) { NativeStudentExport.exportWord(context, uri, data) }
            }.onSuccess { status = "تم تصدير الطلاب Word" }
                .onFailure { status = "تعذر تصدير Word: ${it.message ?: "خطأ"}" }
        }
    }
    val exportPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val data = studentExportRows()
                require(data.isNotEmpty()) { "لا يوجد طلاب للتصدير" }
                withContext(Dispatchers.IO) { NativeStudentExport.exportPdf(context, uri, data) }
            }.onSuccess { status = "تم تصدير الطلاب PDF" }
                .onFailure { status = "تعذر تصدير PDF: ${it.message ?: "خطأ"}" }
        }
    }

    val headers = preview.firstOrNull().orEmpty()
    val rows = if (preview.size > 1) preview.drop(1) else emptyList()
    var studentMapping by remember(mappingEpoch, headers) { mutableStateOf(ImportMapping.detect(headers)) }
    var recitationMapping by remember(mappingEpoch, headers) { mutableStateOf(RecitationImportMapping.detect(headers)) }
    var monthlyPlanMapping by remember(mappingEpoch, headers) { mutableStateOf(MonthlyPlanImportMapping.detect(headers)) }
    val preflight = remember(rows, importKind, studentMapping, recitationMapping, monthlyPlanMapping) {
        importPreflight(rows, importKind, studentMapping, recitationMapping, monthlyPlanMapping)
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("الاستيراد والتصدير", onRefresh = onRefresh, pending = pending)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("الاستيراد والتصدير", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("تصدير بيانات الطلاب الأساسية واستيراد البيانات مع مرحلة مراجعة قبل الحفظ.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f))
                }
            }
            Text("تصدير بيانات الطلاب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ResponsiveChipGrid(items = listOf("excel", "word", "pdf"), minItemWidth = 135.dp, maxColumns = 2) { action, itemModifier ->
                val click = when (action) {
                    "excel" -> ({ exportXlsx.launch("طلاب_حلقتي_${LocalDate.now()}.xlsx") })
                    "word" -> ({ exportWord.launch("طلاب_حلقتي_${LocalDate.now()}.doc") })
                    else -> ({ exportPdf.launch("طلاب_حلقتي_${LocalDate.now()}.pdf") })
                }
                OutlinedButton(onClick = click, modifier = itemModifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(when (action) { "excel" -> Icons.Default.TableView; "word" -> Icons.Default.Description; else -> Icons.Default.PictureAsPdf }, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(when (action) { "excel" -> "Excel"; "word" -> "Word"; else -> "PDF" })
                }
            }
            if (canImport) {
                Text("نوع الاستيراد", style = MaterialTheme.typography.labelLarge)
                val importKinds = if (advanced) listOf("students" to "الطلاب", "recitations" to "تسميعات سابقة", "plans" to "الأهداف الشهرية") else listOf("students" to "الطلاب")
                ResponsiveChipGrid(items = importKinds, minItemWidth = 125.dp, maxColumns = 3) { pair, itemModifier ->
                    ProFilterChip(selected = importKind == pair.first, onClick = { importKind = pair.first }, label = pair.second, modifier = itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
                Text("الحلقة المستهدفة", style = MaterialTheme.typography.labelLarge)
                if (halaqas.isEmpty()) {
                    Text("لا توجد حلقة مفعلة متاحة لهذا الحساب", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                } else {
                    HalaqatiLazySelectField(
                        label = "الحلقة المستهدفة",
                        selectedKey = selectedHalaqaId,
                        options = halaqas.map { it.optString("id") to it.optString("name", "حلقة") },
                        onSelected = { selectedHalaqaId = it },
                        modifier = Modifier.fillMaxWidth(),
                        searchable = halaqas.size > 8,
                    )
                }
            }
            val dataActions = buildList { if (canImport) { add("file"); add("paste") }; add("backup") }
            ResponsiveChipGrid(items = dataActions, minItemWidth = 135.dp, maxColumns = 2) { action, itemModifier ->
                OutlinedButton(
                    onClick = {
                        when (action) {
                            "file" -> open.launch(arrayOf("text/csv", "text/plain", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/pdf", "image/*"))
                            "paste" -> showManualPaste = !showManualPaste
                            else -> create.launch("Halaqati_Backup_${LocalDate.now()}.json")
                        }
                    },
                    modifier = itemModifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(when (action) { "file" -> Icons.Default.UploadFile; "paste" -> Icons.Default.ContentPaste; else -> Icons.Default.Download }, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(when (action) { "file" -> "اختيار ملف"; "paste" -> "لصق بيانات"; else -> "نسخة احتياطية" })
                }
            }

            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(
                    "لا يتم حفظ أي صف أثناء المعاينة. CSV/TXT وXLSX وDOCX تُحلل محليًا. PDF والصور لا تُعتمد عبر OCR تلقائي؛ استخرج النص بأداة موثوقة ثم الصقه هنا وراجعه قبل الحفظ.",
                    Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (showManualPaste && canImport) {
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    label = { Text("نص جدولي تمت مراجعته") },
                    supportingText = { Text("يدعم Tab أو فاصلة أو فاصلة منقوطة أو |. أول سطر يجب أن يحتوي أسماء الأعمدة.") },
                )
                Button(
                    onClick = {
                        preview = SmartImport.readText(manualText)
                        mappingEpoch++
                        fileName = "manual-reviewed-${LocalDate.now()}.txt"
                        importedCount = 0; failedCount = 0; skippedCount = 0
                        status = if (preview.size > 1) "تم تحليل النص يدويًا. راجع المعاينة ثم اعتمد." else "النص لا يحتوي جدولًا صالحًا"
                    },
                    enabled = manualText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("تحليل النص للمعاينة") }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
        }

        if (preview.isNotEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TableView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(fileName.ifBlank { "معاينة الملف" }, fontWeight = FontWeight.Bold)
                            Text("${rows.size} صف بيانات · ${headers.size} عمود", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (headers.isNotEmpty()) {
                        Text("الأعمدة المكتشفة", style = MaterialTheme.typography.labelLarge)
                        Text(headers.joinToString(" | "), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = { mappingEditorOpen = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("تعديل ربط الأعمدة")
                        }
                        Text("فحص ما قبل الاعتماد", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImportResultBadge("صالح", preflight.valid, Modifier.weight(1f))
                            ImportResultBadge("سيتجاوز", preflight.skipped, Modifier.weight(1f))
                            ImportResultBadge("يحتاج تصحيح", preflight.invalid, Modifier.weight(1f))
                        }
                        if (preflight.invalid > 0) {
                            StateNotice("يوجد ${preflight.invalid} صف غير صالح بنيويًا. لن يتم تحويل القيم المجهولة أو السالبة إلى بيانات صحيحة تلقائيًا.", kind = "warning")
                        }
                    }

                    when (importKind) {
                        "students" -> {
                            val summary = buildList {
                                add("الاسم: ${studentMapping.fullName?.let { headers.getOrNull(it) } ?: "غير مكتشف"}")
                                studentMapping.track?.let { add("المسار: ${headers.getOrNull(it)}") }
                                studentMapping.category?.let { add("الفئة: ${headers.getOrNull(it)}") }
                                studentMapping.guardianPhone?.let { add("جوال ولي الأمر: ${headers.getOrNull(it)}") }
                            }
                            Text(summary.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = if (studentMapping.fullName == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            ImportCommitButton(
                                importing = importing,
                                enabled = rows.isNotEmpty() && studentMapping.fullName != null && selectedHalaqaId.isNotBlank(),
                                label = "اعتماد الطلاب بعد المراجعة",
                            ) {
                                importing = true
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            importStudentsReviewed(rows, studentMapping, fileName.ifBlank { "import" }, selectedHalaqaId)
                                        }
                                        importedCount = result.imported; failedCount = result.failed; skippedCount = result.skipped
                                        status = importResultText(result)
                                        onRefresh()
                                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { status = e.message ?: "تعذر اعتماد الاستيراد" }
                                    finally { importing = false }
                                }
                            }
                        }
                        "recitations" -> {
                            val valid = recitationMapping.student != null && recitationMapping.date != null && recitationMapping.type != null && recitationMapping.pages != null
                            Text(
                                "الطالب: ${recitationMapping.student?.let { headers.getOrNull(it) } ?: "غير مكتشف"} · التاريخ: ${recitationMapping.date?.let { headers.getOrNull(it) } ?: "غير مكتشف"} · النوع: ${recitationMapping.type?.let { headers.getOrNull(it) } ?: "غير مكتشف"} · الصفحات: ${recitationMapping.pages?.let { headers.getOrNull(it) } ?: "غير مكتشف"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                            Text("اعتماد التسميعات القديمة يتطلب اتصالًا بالإنترنت حتى يتم ربط كل صف بجلسة صحيحة ومنع التكرار. التقييم والنقاط تُعاد حسابها بمحرك V7 نفسه.", style = MaterialTheme.typography.bodyMedium)
                            ImportCommitButton(importing, valid && rows.isNotEmpty() && selectedHalaqaId.isNotBlank(), "اعتماد التسميعات بعد المراجعة") {
                                importing = true
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            importRecitationsReviewed(rows, recitationMapping, fileName.ifBlank { "import" }, selectedHalaqaId)
                                        }
                                        importedCount = result.imported; failedCount = result.failed; skippedCount = result.skipped
                                        status = importResultText(result)
                                        onRefresh()
                                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { status = e.message ?: "تعذر اعتماد التسميعات" }
                                    finally { importing = false }
                                }
                            }
                        }
                        "plans" -> {
                            val valid = monthlyPlanMapping.student != null && monthlyPlanMapping.month != null &&
                                listOf(monthlyPlanMapping.hifz, monthlyPlanMapping.review, monthlyPlanMapping.sard).any { it != null }
                            Text(
                                "الطالب: ${monthlyPlanMapping.student?.let { headers.getOrNull(it) } ?: "غير مكتشف"} · الشهر: ${monthlyPlanMapping.month?.let { headers.getOrNull(it) } ?: "غير مكتشف"} · أهداف الحفظ/المراجعة/السرد تُحدّث في student_goals ولا تُنشئ Smart Plan جديدة.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                            ImportCommitButton(importing, valid && rows.isNotEmpty() && selectedHalaqaId.isNotBlank(), "اعتماد الأهداف الشهرية بعد المراجعة") {
                                importing = true
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            importMonthlyPlansReviewed(rows, monthlyPlanMapping, fileName.ifBlank { "import" }, selectedHalaqaId)
                                        }
                                        importedCount = result.imported; failedCount = result.failed; skippedCount = result.skipped
                                        status = importResultText(result)
                                        onRefresh()
                                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { status = e.message ?: "تعذر اعتماد الأهداف الشهرية" }
                                    finally { importing = false }
                                }
                            }
                        }
                    }
                    if (importedCount + failedCount + skippedCount > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImportResultBadge("مضاف/محدّث", importedCount, Modifier.weight(1f))
                            ImportResultBadge("متجاوز", skippedCount, Modifier.weight(1f))
                            ImportResultBadge("فشل", failedCount, Modifier.weight(1f))
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.take(40).forEachIndexed { index, row ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("صف ${index + 2}", style = MaterialTheme.typography.labelMedium, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                                val disposition = importRowDisposition(row, importKind, studentMapping, recitationMapping, monthlyPlanMapping)
                                StatusPill(
                                    text = when (disposition) {
                                        ImportRules.RowDisposition.VALID -> "صالح"
                                        ImportRules.RowDisposition.SKIP -> "سيتجاوز"
                                        ImportRules.RowDisposition.INVALID -> "يحتاج تصحيح"
                                    },
                                    kind = when (disposition) {
                                        ImportRules.RowDisposition.VALID -> "success"
                                        ImportRules.RowDisposition.SKIP -> "info"
                                        ImportRules.RowDisposition.INVALID -> "warning"
                                    },
                                )
                            }
                            Text(row.joinToString(" | "), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (rows.size > 40) Text("يتم عرض أول 40 صف فقط في المعاينة. الاعتماد يعالج جميع الصفوف.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            }
        }
    }

    if (mappingEditorOpen && headers.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { mappingEditorOpen = false }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("ربط الأعمدة يدويًا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("اختر العمود الصحيح لكل حقل. الحقول المعلّمة بـ * مطلوبة للاعتماد.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (importKind) {
                    "students" -> StudentImportMappingEditor(headers, studentMapping) { studentMapping = it }
                    "recitations" -> RecitationImportMappingEditor(headers, recitationMapping) { recitationMapping = it }
                    "plans" -> MonthlyPlanImportMappingEditor(headers, monthlyPlanMapping) { monthlyPlanMapping = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            when (importKind) {
                                "students" -> studentMapping = ImportMapping.detect(headers)
                                "recitations" -> recitationMapping = RecitationImportMapping.detect(headers)
                                "plans" -> monthlyPlanMapping = MonthlyPlanImportMapping.detect(headers)
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("اكتشاف تلقائي") }
                    Button(
                        onClick = { mappingEditorOpen = false },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("تم") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ImportCommitButton(importing: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !importing && enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        if (importing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.CheckCircle, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(if (importing) "جاري الاعتماد…" else label)
    }
}

@Composable
private fun ImportResultBadge(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ImportColumnField(
    label: String,
    headers: List<String>,
    index: Int?,
    required: Boolean = false,
    onChange: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (required) "$label *" else label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(index?.let { headers.getOrNull(it) } ?: "غير محدد", Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("غير محدد") },
                    onClick = { onChange(null); expanded = false },
                )
                headers.forEachIndexed { i, header ->
                    DropdownMenuItem(
                        text = { Text(header.ifBlank { "عمود ${i + 1}" }) },
                        onClick = { onChange(i); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentImportMappingEditor(headers: List<String>, m: ImportMapping, onChange: (ImportMapping) -> Unit) {
    ImportColumnField("اسم الطالب", headers, m.fullName, true) { onChange(m.copy(fullName = it)) }
    ImportColumnField("المسار", headers, m.track) { onChange(m.copy(track = it)) }
    ImportColumnField("الفئة", headers, m.category) { onChange(m.copy(category = it)) }
    ImportColumnField("الجنس", headers, m.gender) { onChange(m.copy(gender = it)) }
    ImportColumnField("رقم الهوية", headers, m.nationalId) { onChange(m.copy(nationalId = it)) }
    ImportColumnField("تاريخ الميلاد", headers, m.dateOfBirth) { onChange(m.copy(dateOfBirth = it)) }
    ImportColumnField("المرحلة الدراسية", headers, m.schoolGrade) { onChange(m.copy(schoolGrade = it)) }
    ImportColumnField("بريد ولي الأمر", headers, m.guardianEmail) { onChange(m.copy(guardianEmail = it)) }
    ImportColumnField("جوال ولي الأمر", headers, m.guardianPhone) { onChange(m.copy(guardianPhone = it)) }
    ImportColumnField("العنوان", headers, m.address) { onChange(m.copy(address = it)) }
}

@Composable
private fun RecitationImportMappingEditor(headers: List<String>, m: RecitationImportMapping, onChange: (RecitationImportMapping) -> Unit) {
    ImportColumnField("الطالب", headers, m.student, true) { onChange(m.copy(student = it)) }
    ImportColumnField("التاريخ", headers, m.date, true) { onChange(m.copy(date = it)) }
    ImportColumnField("نوع التسميع", headers, m.type, true) { onChange(m.copy(type = it)) }
    ImportColumnField("عدد الصفحات", headers, m.pages, true) { onChange(m.copy(pages = it)) }
    ImportColumnField("الأخطاء", headers, m.mistakes) { onChange(m.copy(mistakes = it)) }
    ImportColumnField("التلقين", headers, m.prompts) { onChange(m.copy(prompts = it)) }
    ImportColumnField("صفحة البداية", headers, m.startPage) { onChange(m.copy(startPage = it)) }
    ImportColumnField("صفحة النهاية", headers, m.endPage) { onChange(m.copy(endPage = it)) }
    ImportColumnField("الملاحظة", headers, m.note) { onChange(m.copy(note = it)) }
}

@Composable
private fun MonthlyPlanImportMappingEditor(headers: List<String>, m: MonthlyPlanImportMapping, onChange: (MonthlyPlanImportMapping) -> Unit) {
    ImportColumnField("الطالب", headers, m.student, true) { onChange(m.copy(student = it)) }
    ImportColumnField("الشهر", headers, m.month, true) { onChange(m.copy(month = it)) }
    ImportColumnField("هدف الحفظ", headers, m.hifz) { onChange(m.copy(hifz = it)) }
    ImportColumnField("هدف المراجعة", headers, m.review) { onChange(m.copy(review = it)) }
    ImportColumnField("هدف السرد", headers, m.sard) { onChange(m.copy(sard = it)) }
    ImportColumnField("أيام الحضور المتوقعة", headers, m.expectedDays) { onChange(m.copy(expectedDays = it)) }
    ImportColumnField("الملاحظة", headers, m.note) { onChange(m.copy(note = it)) }
}

data class ImportPreflight(val valid: Int, val skipped: Int, val invalid: Int)

private fun importPreflight(
    rows: List<List<String>>,
    kind: String,
    studentMapping: ImportMapping,
    recitationMapping: RecitationImportMapping,
    monthlyPlanMapping: MonthlyPlanImportMapping,
): ImportPreflight {
    var valid = 0; var skipped = 0; var invalid = 0
    rows.forEach { row ->
        when (importRowDisposition(row, kind, studentMapping, recitationMapping, monthlyPlanMapping)) {
            ImportRules.RowDisposition.VALID -> valid++
            ImportRules.RowDisposition.SKIP -> skipped++
            ImportRules.RowDisposition.INVALID -> invalid++
        }
    }
    return ImportPreflight(valid, skipped, invalid)
}

private fun importRowDisposition(
    row: List<String>,
    kind: String,
    studentMapping: ImportMapping,
    recitationMapping: RecitationImportMapping,
    monthlyPlanMapping: MonthlyPlanImportMapping,
): ImportRules.RowDisposition {
    return when (kind) {
    "students" -> {
        val nameIndex = studentMapping.fullName ?: return ImportRules.RowDisposition.INVALID
        ImportRules.studentDisposition(
            name = row.getOrNull(nameIndex).orEmpty(),
            track = cell(row, studentMapping.track),
            dateOfBirth = cell(row, studentMapping.dateOfBirth),
        )
    }
    "recitations" -> {
        val studentIndex = recitationMapping.student ?: return ImportRules.RowDisposition.INVALID
        val dateIndex = recitationMapping.date ?: return ImportRules.RowDisposition.INVALID
        val typeIndex = recitationMapping.type ?: return ImportRules.RowDisposition.INVALID
        val pagesIndex = recitationMapping.pages ?: return ImportRules.RowDisposition.INVALID
        ImportRules.recitationDisposition(
            student = row.getOrNull(studentIndex).orEmpty(),
            dateValue = row.getOrNull(dateIndex).orEmpty(),
            activityValue = row.getOrNull(typeIndex).orEmpty(),
            pagesValue = row.getOrNull(pagesIndex).orEmpty(),
            mistakesValue = cell(row, recitationMapping.mistakes),
            promptsValue = cell(row, recitationMapping.prompts),
            startPageValue = cell(row, recitationMapping.startPage),
            endPageValue = cell(row, recitationMapping.endPage),
        )
    }
    "plans" -> {
        val studentIndex = monthlyPlanMapping.student ?: return ImportRules.RowDisposition.INVALID
        val monthIndex = monthlyPlanMapping.month ?: return ImportRules.RowDisposition.INVALID
        if (listOf(monthlyPlanMapping.hifz, monthlyPlanMapping.review, monthlyPlanMapping.sard).none { it != null }) {
            return ImportRules.RowDisposition.INVALID
        }
        ImportRules.monthlyGoalDisposition(
            student = row.getOrNull(studentIndex).orEmpty(),
            monthValue = row.getOrNull(monthIndex).orEmpty(),
            hifzValue = cell(row, monthlyPlanMapping.hifz),
            reviewValue = cell(row, monthlyPlanMapping.review),
            sardValue = cell(row, monthlyPlanMapping.sard),
            expectedDaysValue = cell(row, monthlyPlanMapping.expectedDays),
        )
    }
        else -> ImportRules.RowDisposition.INVALID
    }
}

data class ImportMapping(
    val fullName: Int?, val track: Int?, val category: Int?, val gender: Int?, val nationalId: Int?,
    val dateOfBirth: Int?, val schoolGrade: Int?, val guardianEmail: Int?, val guardianPhone: Int?, val address: Int?,
) {
    companion object {
        fun detect(headers: List<String>): ImportMapping {
            val n = headers.map(::normalizeImportHeader)
            fun find(vararg names: String): Int? = findImportColumn(n, *names)
            return ImportMapping(
                fullName = find("اسم الطالب", "الاسم", "full name", "full_name", "name"),
                track = find("المسار", "track", "track_code"), category = find("الفئة", "category"),
                gender = find("الجنس", "gender"), nationalId = find("رقم الهوية", "الهوية", "national id", "national_id"),
                dateOfBirth = find("تاريخ الميلاد", "date of birth", "date_of_birth", "birth"),
                schoolGrade = find("المرحلة الدراسية", "الصف", "school grade", "school_grade", "grade"),
                guardianEmail = find("بريد ولي الأمر", "guardian email", "guardian_email"),
                guardianPhone = find("جوال ولي الأمر", "هاتف ولي الأمر", "guardian phone", "guardian_phone"),
                address = find("العنوان", "address"),
            )
        }
    }
}

data class RecitationImportMapping(
    val student: Int?, val date: Int?, val type: Int?, val pages: Int?, val mistakes: Int?, val prompts: Int?,
    val note: Int?, val startPage: Int?, val endPage: Int?,
) {
    companion object {
        fun detect(headers: List<String>): RecitationImportMapping {
            val n = headers.map(::normalizeImportHeader)
            fun find(vararg names: String): Int? = findImportColumn(n, *names)
            return RecitationImportMapping(
                student = find("اسم الطالب", "الطالب", "student", "student name", "name"),
                date = find("التاريخ", "تاريخ التسميع", "date", "session date"),
                type = find("النوع", "نوع التسميع", "activity", "activity type", "type"),
                pages = find("الصفحات", "عدد الصفحات", "pages", "pages count", "pages_count"),
                mistakes = find("الأخطاء", "عدد الأخطاء", "mistakes"), prompts = find("التلقين", "التلميحات", "prompts"),
                note = find("الملاحظة", "ملاحظة", "note", "teacher note"), startPage = find("صفحة البداية", "start page", "start_page"),
                endPage = find("صفحة النهاية", "end page", "end_page"),
            )
        }
    }
}

data class MonthlyPlanImportMapping(
    val student: Int?, val month: Int?, val hifz: Int?, val review: Int?, val sard: Int?, val expectedDays: Int?, val note: Int?,
) {
    companion object {
        fun detect(headers: List<String>): MonthlyPlanImportMapping {
            val n = headers.map(::normalizeImportHeader)
            fun find(vararg names: String): Int? = findImportColumn(n, *names)
            return MonthlyPlanImportMapping(
                student = find("اسم الطالب", "الطالب", "student", "student name", "name"),
                month = find("الشهر", "month", "month start", "period start", "period_start"),
                hifz = find("هدف الحفظ", "حفظ", "target hifz", "target_hifz_pages"),
                review = find("هدف المراجعة", "مراجعة", "target review", "target_review_pages"),
                sard = find("هدف السرد", "سرد", "target sard", "target_sard_pages"),
                expectedDays = find("أيام الحضور", "الايام المتوقعة", "expected days", "expected_session_days"),
                note = find("الملاحظة", "ملاحظة", "note"),
            )
        }
    }
}

data class ImportCommitResult(val imported: Int, val failed: Int, val skipped: Int)

private fun importResultText(result: ImportCommitResult) =
    "تم الاعتماد: ${result.imported} مضاف/محدّث · ${result.skipped} مكرر/متجاوز · ${result.failed} فشل"

private fun normalizeImportHeader(value: String): String = value
    .trim().lowercase()
    .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
    .replace(Regex("[^a-z0-9\u0600-\u06ff]+"), " ")
    .replace(Regex("\\s+"), " ").trim()

private fun findImportColumn(normalizedHeaders: List<String>, vararg names: String): Int? =
    normalizedHeaders.indexOfFirst { h ->
        names.any { wanted ->
            val w = normalizeImportHeader(wanted)
            h == w || h.contains(w)
        }
    }.takeIf { it >= 0 }

private fun cell(row: List<String>, index: Int?): String = index?.let { row.getOrNull(it) }.orEmpty().trim()
private fun normalizedStudentName(value: String) = normalizeImportHeader(value)

private fun trackFromImport(value: String): String? = ImportRules.studentTrack(value)

private fun genderFromImport(value: String): String? = when {
    value.contains("ذكر") || value.equals("male", true) -> "male"
    value.contains("أنث") || value.contains("انث") || value.equals("female", true) -> "female"
    else -> null
}

private fun normalizeArabicDigits(value: String): String = ImportRules.normalizeDigits(value)
private fun importDouble(value: String): Double? = ImportRules.decimal(value)
private fun importInt(value: String): Int? = ImportRules.integer(value)
private fun importDate(value: String): LocalDate? = ImportRules.date(value)
private fun importMonth(value: String): YearMonth? = ImportRules.month(value)
private fun activityTypeFromImport(value: String): String? = ImportRules.activityType(value)

private fun importStudentsReviewed(rows: List<List<String>>, mapping: ImportMapping, fileName: String, halaqaId: String): ImportCommitResult {
    val nameIndex = mapping.fullName ?: error("تعذر اكتشاف عمود اسم الطالب")
    require(halaqaId.isNotBlank()) { "اختر الحلقة المستهدفة" }
    val existing = AppGraph.repo.students().filter { it.halaqaId == halaqaId }.map { normalizedStudentName(it.fullName) }.toMutableSet()
    val categories = AppGraph.repo.table("student_categories", "select=id,name,halaqa_id,active&halaqa_id=eq.$halaqaId&limit=500").let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.toMutableList()
    val uid = AppGraph.repo.store.load()?.userId
    var imported = 0; var failed = 0; var skipped = 0

    fun categoryId(name: String): String? {
        if (name.isBlank()) return null
        val normalized = normalizeImportHeader(name)
        categories.firstOrNull { normalizeImportHeader(it.optString("name")) == normalized }?.let { return it.optString("id") }
        val id = java.util.UUID.nameUUIDFromBytes("import-category:$halaqaId:$normalized".toByteArray()).toString()
        val obj = JSONObject().put("id", id).put("halaqa_id", halaqaId).put("name", name.trim()).put("active", true)
        uid?.let { obj.put("created_by", it) }
        AppGraph.repo.write("student_categories", obj, onConflict = "id", dedupe = "import-category:$halaqaId:$normalized")
        categories.add(JSONObject(obj.toString()))
        return id
    }

    rows.forEach { row ->
        try {
            val fullName = row.getOrNull(nameIndex).orEmpty().trim()
            if (fullName.length < 2) { skipped++; return@forEach }
            val normalized = normalizedStudentName(fullName)
            if (normalized in existing) { skipped++; return@forEach }
            val parsedTrack = trackFromImport(cell(row, mapping.track)) ?: run { skipped++; return@forEach }
            val obj = JSONObject().put("full_name", fullName).put("halaqa_id", halaqaId).put("status", "active").put("track_code", parsedTrack)
            categoryId(cell(row, mapping.category))?.let { obj.put("category_id", it) }
            genderFromImport(cell(row, mapping.gender))?.let { obj.put("gender", it) }
            cell(row, mapping.nationalId).takeIf { it.isNotBlank() }?.let { obj.put("national_id", it) }
            importDate(cell(row, mapping.dateOfBirth))?.let { obj.put("date_of_birth", it.toString()) }
            cell(row, mapping.schoolGrade).takeIf { it.isNotBlank() }?.let { obj.put("school_grade", it) }
            cell(row, mapping.guardianEmail).takeIf { it.isNotBlank() }?.let { obj.put("guardian_email", it) }
            cell(row, mapping.guardianPhone).takeIf { it.isNotBlank() }?.let { obj.put("guardian_phone", it) }
            cell(row, mapping.address).takeIf { it.isNotBlank() }?.let { obj.put("address", it) }
            AppGraph.repo.write("students", obj, dedupe = "import-student:$halaqaId:$normalized")
            existing.add(normalized); imported++
        } catch (_: Exception) { failed++ }
    }
    recordImportJob(halaqaId, fileName, "students", rows.size, imported, failed, skipped)
    return ImportCommitResult(imported, failed, skipped)
}

private fun importRecitationsReviewed(rows: List<List<String>>, mapping: RecitationImportMapping, fileName: String, halaqaId: String): ImportCommitResult {
    require(AppGraph.repo.online()) { "يلزم اتصال إنترنت لاعتماد التسميعات السابقة بأمان" }
    val studentIndex = mapping.student ?: error("تعذر اكتشاف عمود الطالب")
    val dateIndex = mapping.date ?: error("تعذر اكتشاف عمود التاريخ")
    val typeIndex = mapping.type ?: error("تعذر اكتشاف عمود نوع التسميع")
    val pagesIndex = mapping.pages ?: error("تعذر اكتشاف عمود عدد الصفحات")
    val students = AppGraph.repo.students().filter { it.halaqaId == halaqaId }
        .associateBy { normalizedStudentName(it.fullName) }
    var imported = 0; var failed = 0; var skipped = 0

    rows.forEachIndexed { rowIndex, row ->
        try {
            val student = students[normalizedStudentName(row.getOrNull(studentIndex).orEmpty())]
            if (student == null) { skipped++; return@forEachIndexed }
            val date = importDate(row.getOrNull(dateIndex).orEmpty()) ?: run { skipped++; return@forEachIndexed }
            val activity = activityTypeFromImport(row.getOrNull(typeIndex).orEmpty()) ?: run { skipped++; return@forEachIndexed }
            val pages = importDouble(row.getOrNull(pagesIndex).orEmpty()) ?: run { skipped++; return@forEachIndexed }
            val mistakesRaw = cell(row, mapping.mistakes)
            val promptsRaw = cell(row, mapping.prompts)
            val startRaw = cell(row, mapping.startPage)
            val endRaw = cell(row, mapping.endPage)
            val mistakes = if (mistakesRaw.isBlank()) 0 else importInt(mistakesRaw) ?: run { skipped++; return@forEachIndexed }
            val prompts = if (promptsRaw.isBlank()) 0 else importInt(promptsRaw) ?: run { skipped++; return@forEachIndexed }
            val startPage = if (startRaw.isBlank()) null else importInt(startRaw) ?: run { skipped++; return@forEachIndexed }
            val endPage = if (endRaw.isBlank()) null else importInt(endRaw) ?: run { skipped++; return@forEachIndexed }
            if (!ImportRules.validRecitation(activity, pages, mistakes, prompts, startPage, endPage)) { skipped++; return@forEachIndexed }
            val assignment = when (activity) { "sard" -> "sard"; "review_near", "review_far" -> "review"; "test" -> "test"; else -> "hifz" }
            val session = AppGraph.repo.ensureTodaySession(halaqaId, date.toString(), assignment)
            val clientId = java.util.UUID.nameUUIDFromBytes(
                "import-recitation:$halaqaId:${normalizeImportHeader(fileName)}:$rowIndex:${student.id}:$date:$activity:$pages:$mistakes:$prompts".toByteArray(),
            ).toString()
            val exists = AppGraph.repo.table("recitation_entries", "select=id&id=eq.$clientId&limit=1").length() > 0
            if (exists) { skipped++; return@forEachIndexed }
            AppGraph.repo.saveRecitation(
                com.mohammedsamir.halaqati.model.RecitationDraft(
                    studentId = student.id, sessionId = session.id, activityType = activity,
                    startPage = startPage, endPage = endPage, pages = pages, mistakes = mistakes, prompts = prompts,
                    evaluation = "not_rated", notes = cell(row, mapping.note), inputMethod = "import", clientId = clientId,
                ),
            )
            imported++
        } catch (_: Exception) { failed++ }
    }
    recordImportJob(halaqaId, fileName, "recitations", rows.size, imported, failed, skipped)
    return ImportCommitResult(imported, failed, skipped)
}

private fun importMonthlyPlansReviewed(rows: List<List<String>>, mapping: MonthlyPlanImportMapping, fileName: String, halaqaId: String): ImportCommitResult {
    require(AppGraph.repo.online()) { "يلزم اتصال إنترنت لاعتماد الأهداف الشهرية بأمان ومنع التكرار" }
    val studentIndex = mapping.student ?: error("تعذر اكتشاف عمود الطالب")
    val monthIndex = mapping.month ?: error("تعذر اكتشاف عمود الشهر")
    val students = AppGraph.repo.students().filter { it.halaqaId == halaqaId }.associateBy { normalizedStudentName(it.fullName) }
    val uid = AppGraph.repo.store.load()?.userId
    var imported = 0; var failed = 0; var skipped = 0

    rows.forEach { row ->
        try {
            val student = students[normalizedStudentName(row.getOrNull(studentIndex).orEmpty())]
            if (student == null) { skipped++; return@forEach }
            val month = importMonth(row.getOrNull(monthIndex).orEmpty()) ?: run { skipped++; return@forEach }
            fun parsedGoal(index: Int?): Double? {
                val raw = cell(row, index)
                return if (raw.isBlank()) 0.0 else importDouble(raw)
            }
            val hifz = parsedGoal(mapping.hifz) ?: run { skipped++; return@forEach }
            val review = parsedGoal(mapping.review) ?: run { skipped++; return@forEach }
            val sard = parsedGoal(mapping.sard) ?: run { skipped++; return@forEach }
            val expectedRaw = cell(row, mapping.expectedDays)
            val expectedDays = if (expectedRaw.isBlank()) 20 else importInt(expectedRaw) ?: run { skipped++; return@forEach }
            if (!ImportRules.validMonthlyGoals(hifz, review, sard, expectedDays)) { skipped++; return@forEach }
            if (hifz == 0.0 && review == 0.0 && sard == 0.0) { skipped++; return@forEach }
            val periodStart = month.atDay(1)
            val periodEnd = month.atEndOfMonth()
            val obj = JSONObject()
                .put("student_id", student.id).put("period_start", periodStart.toString()).put("period_end", periodEnd.toString())
                .put("target_hifz_pages", hifz).put("target_review_pages", review).put("target_sard_pages", sard)
                .put("target_daily_hifz_pages", hifz / expectedDays).put("target_daily_review_pages", review / expectedDays)
                .put("target_daily_sard_pages", sard / expectedDays).put("expected_session_days", expectedDays)
            cell(row, mapping.note).takeIf { it.isNotBlank() }?.let { obj.put("note", it) }
            uid?.let { obj.put("created_by", it) }
            val existing = AppGraph.repo.table("student_goals", "select=id&student_id=eq.${student.id}&period_start=eq.$periodStart&limit=1").optJSONObject(0)
            if (existing != null) {
                AppGraph.repo.patch("student_goals", "id=eq.${existing.optString("id")}", obj, dedupe = "import-goal:${student.id}:$periodStart")
            } else {
                obj.put("id", java.util.UUID.nameUUIDFromBytes("goal:${student.id}:$periodStart".toByteArray()).toString())
                AppGraph.repo.write("student_goals", obj, onConflict = "id", dedupe = "import-goal:${student.id}:$periodStart")
            }
            imported++
        } catch (_: Exception) { failed++ }
    }
    recordImportJob(halaqaId, fileName, "plans", rows.size, imported, failed, skipped)
    return ImportCommitResult(imported, failed, skipped)
}

private fun recordImportJob(halaqaId: String, fileName: String, kind: String, detected: Int, imported: Int, failed: Int, skipped: Int) {
    val uid = AppGraph.repo.store.load()?.userId
    runCatching {
        AppGraph.repo.write(
            "data_import_jobs",
            JSONObject().put("halaqa_id", halaqaId).put("file_name", fileName).put("file_type", "native_review")
                .put("import_kind", kind).put("rows_detected", detected).put("rows_imported", imported).put("rows_failed", failed)
                .put("status", when { failed == 0 -> "completed"; imported > 0 -> "partial"; else -> "failed" })
                .put("notes", "Native reviewed import; skipped=$skipped").apply { uid?.let { put("created_by", it) } },
            dedupe = "import-job:$kind:${normalizeImportHeader(fileName)}:${System.currentTimeMillis()}",
        )
    }
}

object SmartImport {
    fun displayName(context: Context, uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "ملف مستورد" }
    }

    fun read(context: Context, uri: Uri, hintedName: String = displayName(context, uri)): List<List<String>> {
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val name = hintedName.lowercase()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("تعذر فتح الملف")
        val zipEntries = if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) runCatching { entries(bytes).keys }.getOrDefault(emptySet()) else emptySet()
        return when {
            name.endsWith(".xlsx") || mime.contains("spreadsheet") || zipEntries.any { it.startsWith("xl/") } -> readXlsx(bytes)
            name.endsWith(".docx") || mime.contains("wordprocessingml") || zipEntries.contains("word/document.xml") -> readDocx(bytes)
            name.endsWith(".csv") || name.endsWith(".txt") || mime.contains("csv") || mime.startsWith("text/") || looksLikeDelimited(bytes) -> readText(bytes.toString(Charsets.UTF_8))
            name.endsWith(".pdf") || mime.contains("pdf") || mime.startsWith("image/") -> throw IllegalArgumentException("PDF/الصورة تحتاج OCR موثوق ثم مراجعة يدوية؛ لم يتم إدخال أي بيانات")
            else -> throw IllegalArgumentException("صيغة الملف غير مدعومة أو لم يتعرف النظام عليها. استخدم CSV/TXT أو XLSX أو DOCX")
        }.trimTable()
    }

    fun readText(text: String): List<List<String>> = parseDelimited(text).trimTable()

    private fun List<List<String>>.trimTable(): List<List<String>> =
        map { row -> row.map(String::trim) }.filter { row -> row.any { it.isNotBlank() } }

    private fun looksLikeDelimited(bytes: ByteArray): Boolean {
        val prefix = bytes.take(2048).toByteArray().toString(Charsets.UTF_8)
        return listOf('\t', ',', ';', '|').any { prefix.contains(it) }
    }

    private fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().filter { it.isNotBlank() }.take(5).toList()
        return listOf('\t', ',', ';', '|').maxByOrNull { d -> sample.sumOf { line -> line.count { it == d } } } ?: ','
    }

    private fun parseDelimited(text: String): List<List<String>> {
        if (text.isBlank()) return emptyList()
        val delimiter = detectDelimiter(text)
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun finishCell() { row.add(cell.toString()); cell.clear() }
        fun finishRow() { finishCell(); rows.add(row); row = mutableListOf() }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> finishCell()
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    finishRow()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }

    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val output = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) { output[entry.name] = zip.readBytes(); entry = zip.nextEntry }
        }
        return output
    }

    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        val e = entries(bytes)
        val shared = e["xl/sharedStrings.xml"]?.let { b ->
            val d = xml(b); (0 until d.getElementsByTagName("si").length).map { d.getElementsByTagName("si").item(it).textContent }
        } ?: emptyList()
        val sheet = e["xl/worksheets/sheet1.xml"] ?: return emptyList()
        val d = xml(sheet)
        val rowNodes = d.getElementsByTagName("row")
        val output = mutableListOf<List<String>>()
        for (r in 0 until rowNodes.length) {
            val node = rowNodes.item(r)
            val cells = node.childNodes
            val byColumn = mutableMapOf<Int, String>()
            var sequential = 0
            for (i in 0 until cells.length) {
                val c = cells.item(i)
                if (c.nodeName != "c") continue
                val element = c as? org.w3c.dom.Element ?: continue
                val ref = element.getAttribute("r")
                val col = xlsxColumnIndex(ref).takeIf { it >= 0 } ?: sequential
                sequential = col + 1
                val type = element.getAttribute("t")
                val valueNode = element.getElementsByTagName("v").item(0)
                val raw = valueNode?.textContent ?: element.textContent
                val value = when (type) {
                    "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1) ?: raw
                    "inlineStr" -> element.getElementsByTagName("t").item(0)?.textContent ?: raw
                    else -> raw
                }
                byColumn[col] = value
            }
            val max = byColumn.keys.maxOrNull() ?: -1
            output += if (max < 0) emptyList() else (0..max).map { byColumn[it].orEmpty() }
        }
        return output
    }

    private fun xlsxColumnIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isBlank()) return -1
        var value = 0
        letters.forEach { value = value * 26 + (it.code - 'A'.code + 1) }
        return value - 1
    }

    private fun readDocx(bytes: ByteArray): List<List<String>> {
        val document = entries(bytes)["word/document.xml"] ?: return emptyList()
        val d = xml(document)
        val tableRows = d.getElementsByTagName("w:tr")
        if (tableRows.length > 0) {
            return (0 until tableRows.length).map { r ->
                val row = tableRows.item(r) as? org.w3c.dom.Element
                val cells = row?.getElementsByTagName("w:tc")
                if (cells == null) emptyList() else (0 until cells.length).map { c -> cells.item(c).textContent.trim() }
            }
        }
        val paragraphs = d.getElementsByTagName("w:p")
        val text = (0 until paragraphs.length).map { paragraphs.item(it).textContent }.filter { it.isNotBlank() }.joinToString("\n")
        return parseDelimited(text)
    }
}

object Backup {
    private val tables = listOf(
        "profiles",
        "halaqas",
        "halaqa_teachers",
        "halaqa_supervisors",
        "student_categories",
        "students",
        "student_guardians",
        "sessions",
        "attendance_records",
        "recitation_entries",
        "student_daily_notes",
        "student_goals",
        "monthly_comments",
        "announcements",
        "forum_posts",
        "forum_comments",
        "student_points",
        "app_settings",
        "smart_plans",
        "smart_plan_events",
        "notification_preferences",
    )

    fun export(context: Context, uri: Uri) {
        val root = JSONObject()
            .put("format", "halaqati-backup")
            .put("version", 2)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("exported_at", java.time.Instant.now().toString())
        val tableData = JSONObject()
        tables.forEach { name ->
            try {
                tableData.put(name, AppGraph.repo.table(name, "select=*&limit=10000"))
            } catch (_: Exception) {
                // Keep backup export resilient across role-restricted tables.
            }
        }
        root.put("tables", tableData)
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: error("تعذر فتح ملف النسخة الاحتياطية")
    }
}

@Composable
private fun SettingsGroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column(Modifier.padding(vertical = 4.dp), content = content) }
}

/** Compact settings row: icon · title (+ value) · optional trailing control, chevron when it opens something. */
@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String?,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
        if (onClick != null && trailing == null) Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The two appearance choices used most, always visible at the top: dark mode and the app colour. The full appearance settings stay in the folded row below. */
@Composable
internal fun QuickAppearanceRow(prefs: com.mohammedsamir.halaqati.data.UiPreferences) {
    val presets = listOf(
        Triple(com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.DEFAULT_PRIMARY, com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.DEFAULT_ACCENT, "أخضر حلقاتي"),
        Triple(0xFF3B4929.toInt(), 0xFF5B7A3A.toInt(), "زيتوني الشعار"),
        Triple(0xFF12345B.toInt(), 0xFF2D6CDF.toInt(), "الأزرق"),
        Triple(0xFF5A3825.toInt(), 0xFFAD744F.toInt(), "البني"),
        Triple(0xFF342B5F.toInt(), 0xFF765BC4.toInt(), "البنفسجي"),
    )
    SettingsGroup {
        SettingsRow(Icons.Default.DarkMode, "الوضع الداكن", null, trailing = {
            Switch(
                checked = prefs.themeMode == ThemeMode.DARK,
                onCheckedChange = { on -> AppGraph.uiPreferences.update { it.copy(themeMode = if (on) ThemeMode.DARK else ThemeMode.LIGHT) } },
            )
        })
        SettingsRow(Icons.Default.Palette, "لون التطبيق", null, trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                presets.forEach { (primary, accent, label) ->
                    val selected = prefs.primaryArgb == primary && prefs.accentArgb == accent
                    Box(
                        Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(primary))
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
                            .clickable(onClickLabel = label) { AppGraph.uiPreferences.update { it.copy(primaryArgb = primary, accentArgb = accent) } },
                    )
                }
            }
        })
    }
}

/** One tappable row that opens or folds its content (folded by default), so the settings page stays short. */
@Composable
internal fun SettingsCollapsible(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(false) }
    SettingsGroup {
        SettingsRow(icon, title, subtitle, onClick = { open = !open }, trailing = {
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "طيّ $title" else "فتح $title", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        })
    }
    if (open) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun SettingsChipsRow(label: String, chips: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = chips)
    }
}

@Composable
fun SettingsScreen(profile: Profile, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiPreferences by AppGraph.uiPreferences.state.collectAsState()
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var testingPush by remember { mutableStateOf(false) }
    var pushMessage by remember { mutableStateOf<String?>(null) }
    var pendingWrites by remember { mutableIntStateOf(0) }
    var blockedWrites by remember { mutableIntStateOf(0) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordMessage by remember { mutableStateOf<String?>(null) }
    var showNotificationPreferences by remember { mutableStateOf(false) }
    var notificationPreferences by remember { mutableStateOf<JSONObject?>(null) }
    var preferencesMessage by remember { mutableStateOf<String?>(null) }
    var savingPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val counts = withContext(Dispatchers.IO) {
            AppGraph.repo.pendingQueueSize() to AppGraph.repo.blockedQueueSize()
        }
        pendingWrites = counts.first
        blockedWrites = counts.second
        notificationPreferences = withContext(Dispatchers.IO) {
            runCatching { AppGraph.repo.table("notification_preferences", "select=*&user_id=eq.${profile.id}&limit=1").optJSONObject(0) }.getOrNull()
        } ?: JSONObject().put("user_id", profile.id).put("push_enabled", true).put("attendance", true).put("recitation", true).put("behavior", true).put("announcements", true).put("achievements", true)
    }

    if (showPasswordDialog) {
        HalaqatiAlertDialog(
            onDismissRequest = { if (!savingPassword) showPasswordDialog = false },
            title = { Text("تغيير كلمة المرور") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("كلمة المرور الجديدة") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("تأكيد كلمة المرور") },
                        singleLine = true,
                    )
                    Text(if (confirmPassword.isNotEmpty() && confirmPassword != newPassword) "كلمتا المرور غير متطابقتين" else "6 أحرف على الأقل", style = MaterialTheme.typography.bodySmall, color = if (confirmPassword.isNotEmpty() && confirmPassword != newPassword) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        savingPassword = true
                        passwordMessage = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.updatePassword(newPassword) } }
                            savingPassword = false
                            result.onSuccess {
                                passwordMessage = "تم تغيير كلمة المرور"
                                newPassword = ""
                                confirmPassword = ""
                                showPasswordDialog = false
                            }.onFailure { passwordMessage = it.message ?: "تعذر تغيير كلمة المرور" }
                        }
                    },
                    enabled = newPassword.length >= 6 && newPassword == confirmPassword && !savingPassword,
                ) { Text(if (savingPassword) "جارٍ الحفظ…" else "حفظ") }
            },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }, enabled = !savingPassword) { Text("إلغاء") } },
        )
    }

    if (showLogoutConfirm) {
        HalaqatiAlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("هناك بيانات لم تُرفع بعد") },
            text = {
                Text(
                    "يوجد $pendingWrites عمليات بانتظار المزامنة و$blockedWrites عمليات تحتاج مراجعة. " +
                        "ستبقى محفوظة ومربوطة بهذا الحساب فقط، ولن تُرسل بحساب آخر. الأفضل المزامنة قبل الخروج إذا كان الاتصال متاحًا.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { showLogoutConfirm = false; onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("الخروج مع الاحتفاظ محليًا") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false; AppGraph.repo.scheduleSync() }) { Text("البقاء ومحاولة المزامنة") }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("الإعدادات")
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    shape = MaterialTheme.shapes.extraLarge,
                    border = androidx.compose.foundation.BorderStroke(0.dp, androidx.compose.ui.graphics.Color.Transparent),
                ) {
                    Row(
                        Modifier.fillMaxWidth().nourHeroFill(androidx.compose.material3.MaterialTheme.colorScheme.primary).padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // RC95: the account's own photo (or initial) instead of a generic person icon.
                        com.mohammedsamir.halaqati.ui.components.StudentAvatar(profile.avatarPath.takeIf { it.isNotBlank() && it != "null" }, profile.fullName.ifBlank { "؟" }, com.mohammedsamir.halaqati.ui.components.NourAvatarSize.Large)
                        Column(Modifier.weight(1f)) {
                            Text(profile.fullName, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${com.mohammedsamir.halaqati.ui.components.roleLabelArabic(profile.role)} · ${profile.email}", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .92f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            val isAdminRole = profile.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN

            item {
                SettingsCollapsible(Icons.Default.SystemUpdate, "تحديث التطبيق", "الإصدار ${BuildConfig.VERSION_NAME}") {
                    AppUpdateSettingsCard()
                }
            }
            item { QuickAppearanceRow(uiPreferences) }
            item {
                SettingsCollapsible(Icons.Default.Person, "الحساب", "الصورة والاسم وكلمة المرور") {
                    MyPhotoSettingsCard(profile)
                    AccountNameSettingsCard(profile)
                    SettingsGroup {
                        SettingsRow(Icons.Default.LockReset, "كلمة المرور", "تحديث كلمة مرور الحساب", onClick = { showPasswordDialog = true })
                        passwordMessage?.let { StateNotice(it, if (it.startsWith("تم")) "success" else "warning", Modifier.padding(12.dp)) }
                    }
                }
            }

            if (profile.role in setOf(UserRole.GUARDIAN, UserRole.TEACHER, UserRole.SUPERVISOR, UserRole.ADMIN)) item {
                SettingsCollapsible(Icons.Default.NotificationsActive, "تذكيرات المساهمة", "تذكير أسبوعي يمكن إطفاؤه") { ReminderPanel(profile.role.name) }
            }
            item { SettingsCollapsible(Icons.Default.Palette, "المظهر", "الوضع واللون والكثافة") {
                SettingsGroup {
                    SettingsRow(Icons.Default.DarkMode, "الوضع الداكن", "اتبع النظام أو اختر الوضع", trailing = {
                        Switch(
                            checked = uiPreferences.themeMode == ThemeMode.DARK,
                            onCheckedChange = { enabled -> AppGraph.uiPreferences.update { it.copy(themeMode = if (enabled) ThemeMode.DARK else ThemeMode.LIGHT) } },
                        )
                    })
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(ThemeMode.SYSTEM to "النظام", ThemeMode.LIGHT to "فاتح", ThemeMode.DARK to "داكن").forEach { (mode, label) ->
                            ProFilterChip(
                                selected = uiPreferences.themeMode == mode,
                                onClick = { AppGraph.uiPreferences.update { it.copy(themeMode = mode) } },
                                label = label, modifier = Modifier.weight(1f), showCheckIcon = false,
                            )
                        }
                    }
                    val presets = listOf(
                        Triple(com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.DEFAULT_PRIMARY, com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.DEFAULT_ACCENT, "أخضر حلقاتي"),
                        Triple(0xFF3B4929.toInt(), 0xFF5B7A3A.toInt(), "زيتوني الشعار"),
                        Triple(0xFF12345B.toInt(), 0xFF2D6CDF.toInt(), "الأزرق"),
                        Triple(0xFF5A3825.toInt(), 0xFFAD744F.toInt(), "البني"),
                        Triple(0xFF342B5F.toInt(), 0xFF765BC4.toInt(), "البنفسجي"),
                    )
                    SettingsRow(Icons.Default.Palette, "لون التطبيق", null, trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            presets.forEach { (primary, accent, label) ->
                                val selected = uiPreferences.primaryArgb == primary && uiPreferences.accentArgb == accent
                                Box(
                                    Modifier.size(28.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(androidx.compose.ui.graphics.Color(primary))
                                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
                                        .clickable(onClickLabel = label) { AppGraph.uiPreferences.update { it.copy(primaryArgb = primary, accentArgb = accent) } },
                                )
                            }
                        }
                    })
                    SettingsChipsRow("كثافة الواجهة") {
                        listOf(UiDensity.COMFORTABLE to "مريح", UiDensity.BALANCED to "متوازن", UiDensity.COMPACT to "مضغوط").forEach { (density, label) ->
                            ProFilterChip(selected = uiPreferences.density == density, onClick = { AppGraph.uiPreferences.update { it.copy(density = density) } }, label = label, modifier = Modifier.weight(1f), showCheckIcon = false)
                        }
                    }
                    SettingsChipsRow("مظهر الرئيسية") {
                        listOf(com.mohammedsamir.halaqati.data.HomeStyle.MODERN to "بطاقات حديثة", com.mohammedsamir.halaqati.data.HomeStyle.COMPACT to "لوحة مختصرة").forEach { (style, label) ->
                            ProFilterChip(selected = uiPreferences.homeStyle == style, onClick = { AppGraph.uiPreferences.update { it.copy(homeStyle = style) } }, label = label, modifier = Modifier.weight(1f), showCheckIcon = false)
                        }
                    }
                    SettingsRow(Icons.Default.Face, "إظهار صور الطلاب", "صورة مصغّرة أمام الاسم وفي الملف الشخصي", trailing = {
                        Switch(checked = uiPreferences.showStudentPhotos, onCheckedChange = { on -> AppGraph.uiPreferences.update { it.copy(showStudentPhotos = on) } })
                    })
                    if (isAdminRole) {
                        var donorPhotos by remember { mutableStateOf(true) }
                        var donorPhotosBusy by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { donorPhotos = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.donorPhotosSetting() }.getOrDefault(true) } }
                        SettingsRow(Icons.Default.Favorite, "صور المكرَّمين للمانحين", "يرى المانحون صور الطلاب المكرَّمين فقط", trailing = {
                            Switch(
                                checked = donorPhotos, enabled = !donorPhotosBusy,
                                onCheckedChange = { on ->
                                    donorPhotosBusy = true
                                    scope.launch {
                                        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.setDonorPhotosSetting(on) } }
                                            .onSuccess { donorPhotos = on }
                                        donorPhotosBusy = false
                                    }
                                },
                            )
                        })
                    }
                    SettingsChipsRow("شكل الصور") {
                        listOf(
                            com.mohammedsamir.halaqati.data.AvatarShape.RING to "حلقة",
                            com.mohammedsamir.halaqati.data.AvatarShape.CIRCLE to "دائرة",
                            com.mohammedsamir.halaqati.data.AvatarShape.ROUNDED to "مربع مدوّر",
                        ).forEach { (shape, label) ->
                            ProFilterChip(selected = uiPreferences.avatarShape == shape, onClick = { AppGraph.uiPreferences.update { it.copy(avatarShape = shape) } }, label = label, modifier = Modifier.weight(1f), showCheckIcon = false)
                        }
                    }
                    SettingsChipsRow("الحركة") {
                        listOf(com.mohammedsamir.halaqati.data.MotionLevel.NORMAL to "عادية", com.mohammedsamir.halaqati.data.MotionLevel.REDUCED to "مخففة").forEach { (motion, label) ->
                            ProFilterChip(selected = uiPreferences.motion == motion, onClick = { AppGraph.uiPreferences.update { it.copy(motion = motion) } }, label = label, modifier = Modifier.weight(1f), showCheckIcon = false)
                        }
                    }
                    SettingsRow(Icons.Default.RestartAlt, "إعادة ضبط المظهر", null, onClick = { AppGraph.uiPreferences.reset() })
                }
            } }

            item { SettingsGroupTitle("الإشعارات والمزامنة") }
            item {
                SettingsGroup {
                    SettingsRow(
                        Icons.Default.Sync, "المزامنة",
                        if (pendingWrites + blockedWrites == 0) "البيانات المحلية متزامنة لهذا الحساب" else "معلّق: $pendingWrites · يحتاج مراجعة: $blockedWrites · معزول حسب الحساب",
                    )
                    SettingsRow(
                        if (notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        "إشعارات Android",
                        if (notificationsEnabled) "مسموح بها على هذا الجهاز" else "موقوفة من إعدادات Android",
                        onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                        trailing = { StatusPill(if (notificationsEnabled) "مفعلة" else "موقوفة", if (notificationsEnabled) "success" else "warning") },
                    )
                    SettingsRow(Icons.Default.Tune, "تفضيلات إشعارات الحساب", null, onClick = { showNotificationPreferences = true })
                    preferencesMessage?.let { StateNotice(it, kind = if (it.startsWith("تم")) "success" else "warning", modifier = Modifier.padding(12.dp)) }
                    pushMessage?.let { StateNotice(it, kind = if (it.startsWith("تم")) "success" else "warning", modifier = Modifier.padding(12.dp)) }
                    if (isAdminRole) SettingsRow(
                        Icons.Default.NotificationsActive, if (testingPush) "جارٍ الاختبار…" else "اختبار Push", null,
                        onClick = if (!testingPush && AppGraph.repo.online()) ({
                            testingPush = true; pushMessage = null
                            scope.launch {
                                val message = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val result = AppGraph.repo.testPush()
                                        when {
                                            result.optInt("push_sent", 0) > 0 -> "تم إرسال إشعار اختباري إلى هذا الحساب."
                                            result.optBoolean("push_configured", false) -> "تم إنشاء الاختبار لكن لا يوجد جهاز Push نشط لهذا الحساب."
                                            else -> "مركز الإشعارات يعمل، لكن Firebase Push غير مهيأ على الخادم."
                                        }
                                    }.getOrElse { "تعذر إرسال الاختبار: ${it.message ?: "خطأ"}" }
                                }
                                pushMessage = message; testingPush = false
                                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                            }
                        }) else null,
                    )
                }
            }

            // Every role can send a problem report to the system admin; only the admin sees the admin tools below.
            item { SupportSettingsCard() }
            if (isAdminRole) {
                item { SettingsGroupTitle("الإدارة والتشخيص") }
                item { SettingsCollapsible(Icons.Default.Info, "معلومات الدعم", "بيانات الحساب والجهاز لمسؤول النظام") { AppSupportInfoCard(profile) } }
                item { SettingsCollapsible(Icons.Default.Backup, "النسخ الاحتياطي", null) { BackupSettingsCard() } }
                item { SettingsCollapsible(Icons.Default.Tune, "إعدادات الاحتساب", null) { V6ScoringSettingsCard() } }
                item { SettingsCollapsible(Icons.Default.Speed, "القياسات", "أزمنة التحميل الأخيرة") {
                    // RC27.10: timings of the heavy reads, so a slow screen can be pinned down by number instead of guessed.
                    var timings by remember { mutableStateOf(com.mohammedsamir.halaqati.data.PerfLog.snapshot()) }
                    SettingsGroup {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("أزمنة التحميل الأخيرة (م.ث). أي رقم فوق 1500 يعني أن العملية بطيئة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (timings.isEmpty()) {
                                Text("لا توجد قياسات بعد. افتح الخطط أو الجلسات ثم عد إلى هنا.", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                timings.take(12).forEach { e ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(
                                            "${e.millis} م.ث", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                                            color = if (e.millis > 1500) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                        SettingsRow(Icons.Default.Refresh, "تحديث القياسات", null, onClick = { timings = com.mohammedsamir.halaqati.data.PerfLog.snapshot() })
                    }
                } }
            }

            item { Text("الإصدار ${BuildConfig.VERSION_NAME} · Native Kotlin / Jetpack Compose", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
            item {
                Button(
                    onClick = { if (pendingWrites + blockedWrites > 0) showLogoutConfirm = true else onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("تسجيل الخروج", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showNotificationPreferences) {
        NotificationPreferencesDialog(
            current = notificationPreferences,
            close = { showNotificationPreferences = false },
            save = { updated ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            AppGraph.repo.write(
                                "notification_preferences",
                                updated.put("user_id", profile.id).put("updated_at", java.time.Instant.now().toString()),
                                onConflict = "user_id",
                                dedupe = "notification-preferences:${profile.id}",
                            )
                        }
                    }
                    result.onSuccess {
                        notificationPreferences = updated
                        preferencesMessage = "تم حفظ تفضيلات الإشعارات"
                        showNotificationPreferences = false
                    }.onFailure { preferencesMessage = it.message ?: "تعذر حفظ تفضيلات الإشعارات" }
                }
            },
        )
    }
}



/** Nour: the archive shows the Arabic name of the certificate type instead of the raw series key (ideal / sard ...). */
private fun certTypeAr(key: String?): String = when (key) {
    "sard" -> "إتمام سرد"; "khatam" -> "ختم القرآن"; "hifz" -> "إتمام حفظ"; "monthly" -> "التميز الشهري"
    "ideal" -> "الطالب المثالي"; "rank" -> "المراكز الأولى"; "achievement" -> "إنجاز"; else -> key.orEmpty()
}
