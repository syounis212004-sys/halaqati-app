package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.documents.ReportDocumentType
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.ResponsiveChipGrid

internal enum class ReportOrientation { LANDSCAPE, PORTRAIT }

internal data class NativeReportStyle(
    val title: String = "تقرير حلقتي",
    val footer: String = "مركز إبراهيم خليل الرحمن · حلقة معاوية بن أبي سفيان",
    val orientation: ReportOrientation = ReportOrientation.LANDSCAPE,
    val fontFamily: String = "Cairo",
    val fontScale: Float = 1f,
    val primaryColor: Long = 0xFF0D3B2F,
    val textColor: Long = 0xFF1C302A,
    val logoMode: String = "both",
    val visibleColumns: Set<String> = defaultReportColumns(ReportDocumentType.HALAQA),
    val includePhotos: Boolean = false,
    val includeBirthDate: Boolean = true,
    val includeTeacher: Boolean = true,
)

internal fun defaultReportColumns(reportType: String): Set<String> = when (reportType) {
    ReportDocumentType.LEADERS -> setOf("rank", "student", "category", "pages", "attendance", "points")
    ReportDocumentType.POINTS -> setOf("rank", "student", "points", "quality", "attendance")
    ReportDocumentType.ATTENDANCE -> setOf("rank", "student", "present", "late", "absent", "excused", "sessions")
    ReportDocumentType.RECITATIONS -> setOf("date", "student", "activity", "range", "pages", "mistakes", "prompts", "evaluation", "points")
    ReportDocumentType.STUDENTS -> setOf("date", "activity", "range", "pages", "mistakes", "points")
    ReportDocumentType.WEEKLY -> emptySet()
    else -> setOf("rank", "student", "category", "hifz", "tathbit", "review", "sard", "attendance", "absence", "points")
}

private fun reportColumns(reportType: String): List<Pair<String, String>> = when (reportType) {
    ReportDocumentType.LEADERS -> listOf("rank" to "#", "student" to "الطالب", "category" to "الفئة", "pages" to "الصفحات", "attendance" to "الحضور", "points" to "النقاط")
    ReportDocumentType.POINTS -> listOf("rank" to "#", "student" to "الطالب", "points" to "النقاط", "quality" to "الجودة", "attendance" to "الحضور")
    ReportDocumentType.ATTENDANCE -> listOf("rank" to "#", "student" to "الطالب", "present" to "حاضر", "late" to "متأخر", "absent" to "غائب", "excused" to "بعذر", "sessions" to "التسجيلات")
    ReportDocumentType.RECITATIONS -> listOf("date" to "التاريخ", "student" to "الطالب", "activity" to "النوع", "range" to "النطاق", "pages" to "الصفحات", "mistakes" to "الأخطاء", "prompts" to "النقرات", "evaluation" to "التقييم", "points" to "النقاط")
    ReportDocumentType.STUDENTS -> listOf("date" to "التاريخ", "activity" to "النوع", "range" to "النطاق", "pages" to "الصفحات", "mistakes" to "الأخطاء", "points" to "النقاط")
    else -> listOf("rank" to "#", "student" to "الطالب", "category" to "الفئة", "hifz" to "الحفظ", "tathbit" to "التثبيت", "review" to "المراجعة", "sard" to "السرد", "attendance" to "الحضور", "absence" to "الغياب", "points" to "النقاط")
}

@Composable
internal fun ReportStudioControls(style: NativeReportStyle, reportType: String, onChange: (NativeReportStyle) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                Text("تخصيص استوديو التقارير", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("المعاينة والـPDF يستخدمان نفس قالب HTML/CSS المتجهي.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(style.title, { onChange(style.copy(title = it.take(120))) }, label = { Text("عنوان التقرير") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(style.footer, { onChange(style.copy(footer = it.take(180))) }, label = { Text("تذييل التقرير") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("اتجاه A4", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProFilterChip(style.orientation == ReportOrientation.LANDSCAPE, { onChange(style.copy(orientation = ReportOrientation.LANDSCAPE)) }, "أفقي A4", Modifier.weight(1f))
                ProFilterChip(style.orientation == ReportOrientation.PORTRAIT, { onChange(style.copy(orientation = ReportOrientation.PORTRAIT)) }, "رأسي A4", Modifier.weight(1f))
            }
            Text("الخط", style = MaterialTheme.typography.labelLarge)
            ResponsiveChipGrid(
                items = listOf("Cairo" to "Cairo", "sans-serif" to "Sans", "serif" to "Serif", "monospace" to "Mono"),
                minItemWidth = 104.dp,
                maxColumns = 2,
            ) { pair, itemModifier ->
                ProFilterChip(style.fontFamily == pair.first, { onChange(style.copy(fontFamily = pair.first)) }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
            }
            Text("حجم الخط ${(style.fontScale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(style.fontScale, { onChange(style.copy(fontScale = it)) }, valueRange = .75f..1.45f)
            Text("الهوية اللونية", style = MaterialTheme.typography.labelLarge)
            val palettes = listOf(
                Triple(0xFF0D3B2F, 0xFF1C302A, "الأخضر"),
                Triple(0xFF12345B, 0xFF1D2C40, "الأزرق"),
                Triple(0xFF5A3825, 0xFF35251D, "البني"),
                Triple(0xFF342B5F, 0xFF29233E, "البنفسجي"),
            )
            ResponsiveChipGrid(items = palettes, minItemWidth = 104.dp, maxColumns = 3) { item, itemModifier ->
                val (primary, textColor, label) = item
                ProFilterChip(style.primaryColor == primary, { onChange(style.copy(primaryColor = primary, textColor = textColor)) }, label, itemModifier.fillMaxWidth(), showCheckIcon = false)
            }
            Text("الشعارات", style = MaterialTheme.typography.labelLarge)
            ResponsiveChipGrid(items = listOf("both" to "الشعاران", "center" to "المركز", "halaqa" to "الحلقة", "none" to "بدون"), minItemWidth = 104.dp, maxColumns = 3) { (value, label), itemModifier ->
                ProFilterChip(style.logoMode == value, { onChange(style.copy(logoMode = value)) }, label, itemModifier.fillMaxWidth(), showCheckIcon = false)
            }
            if (reportType == ReportDocumentType.STUDENTS) {
                Text("محتوى تقرير الطالب", style = MaterialTheme.typography.labelLarge)
                ResponsiveChipGrid(
                    items = listOf("birth" to "تاريخ الميلاد", "teacher" to "اسم المحفظ", "photo" to "صورة الطالب"),
                    minItemWidth = 120.dp,
                    maxColumns = 2,
                ) { pair, itemModifier ->
                    val selected = when (pair.first) { "birth" -> style.includeBirthDate; "teacher" -> style.includeTeacher; else -> style.includePhotos }
                    ProFilterChip(selected, {
                        onChange(when (pair.first) {
                            "birth" -> style.copy(includeBirthDate = !style.includeBirthDate)
                            "teacher" -> style.copy(includeTeacher = !style.includeTeacher)
                            else -> style.copy(includePhotos = !style.includePhotos)
                        })
                    }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
            }
            Text("الأعمدة الظاهرة", style = MaterialTheme.typography.labelLarge)
            ResponsiveChipGrid(items = reportColumns(reportType), minItemWidth = 104.dp, maxColumns = 3) { pair, itemModifier ->
                val (key, label) = pair
                ProFilterChip(style.visibleColumns.contains(key), {
                    val next = if (style.visibleColumns.contains(key)) style.visibleColumns - key else style.visibleColumns + key
                    onChange(style.copy(visibleColumns = next))
                }, label, itemModifier.fillMaxWidth(), showCheckIcon = false)
            }
        }
    }
}
