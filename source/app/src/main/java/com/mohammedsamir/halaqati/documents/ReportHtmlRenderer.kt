package com.mohammedsamir.halaqati.documents

import android.content.Context
import com.mohammedsamir.halaqati.BuildConfig
import com.mohammedsamir.halaqati.R
import com.mohammedsamir.halaqati.model.Student
import com.mohammedsamir.halaqati.quran.QuranMetadata
import com.mohammedsamir.halaqati.ui.screens.NativeReportStyle
import com.mohammedsamir.halaqati.ui.screens.ReportOrientation
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

object ReportDocumentType {
    const val HALAQA = "halaqa"
    const val LEADERS = "leaders"
    const val POINTS = "points"
    const val ATTENDANCE = "attendance"
    const val RECITATIONS = "recitations"
    const val STUDENTS = "students"
    const val WEEKLY = "weekly"

    val all = listOf(HALAQA, LEADERS, POINTS, ATTENDANCE, RECITATIONS, STUDENTS, WEEKLY)
    fun title(type: String): String = when (type) {
        LEADERS -> "لائحة الأوائل"
        POINTS -> "نقاط الطلاب"
        ATTENDANCE -> "حضور الحلقة"
        RECITATIONS -> "سجل التسميعات"
        STUDENTS -> "تقرير أولياء الأمور"
        WEEKLY -> "الملخص الأسبوعي"
        else -> "التقرير الشامل للحلقة"
    }
}

/** RC13 report renderer: v2.44 report semantics, one HTML source for preview + vector PDF. */
object ReportHtmlRenderer {
    internal fun build(
        context: Context,
        reportType: String,
        summaryRows: List<JSONObject>,
        detailRows: List<JSONObject>,
        notesRows: List<JSONObject>,
        students: List<Student>,
        style: NativeReportStyle,
        from: String,
        to: String,
        track: String,
        halaqaLabel: String,
        teacherName: String = "",
        centerLogoData: String? = null,
        halaqaLogoData: String? = null,
        builtInLogos: Boolean = false,
        absences: List<JSONObject> = emptyList(),
        fullRoster: Boolean = false,
    ): String {
        require(reportType in ReportDocumentType.all) { "نوع التقرير غير معروف" }
        val baseCss = context.assets.open("documents/report.css").bufferedReader().use { it.readText() }
        // RC42: each account shows ITS OWN centre / halaqa logo (uploaded in the certificate studio). The bundled logos belong to
        // the centre's administrator and are only a fallback for that account, never shown to other halaqas.
        val centerLogo = if (style.logoMode in setOf("both", "center"))
            DocumentImageEncoder.dataUrl(centerLogoData, 260, false) ?: if (builtInLogos) DocumentImageEncoder.resourceJpeg(context, R.drawable.center_logo, 260, 82) else null
        else null
        val halaqaLogo = if (style.logoMode in setOf("both", "halaqa"))
            DocumentImageEncoder.dataUrl(halaqaLogoData, 260, false) ?: if (builtInLogos) DocumentImageEncoder.resourceJpeg(context, R.drawable.halaqa_logo, 260, 82) else null
        else null
        val primary = longToHex(style.primaryColor)
        val text = longToHex(style.textColor)
        val orientation = if (style.orientation == ReportOrientation.PORTRAIT) "portrait" else "landscape"
        val screenWidth = if (style.orientation == ReportOrientation.PORTRAIT) 792 else 1120
        val tracks = if (track == "all") listOf("general_hifz", "tathbit") else listOf(track)
        val studentById = students.associateBy { it.id }
        val sections = buildString {
            for (t in tracks) {
                val sr = summaryRows.filter { it.optString("track_code") == t || track != "all" }
                val dr = detailRows.filter { rowTrack(it) == t || track != "all" }
                val nr = notesRows.filter { rowTrack(it) == t || track != "all" }
                if (track == "all" && sr.isEmpty() && dr.isEmpty() && nr.isEmpty()) continue
                when (reportType) {
                    ReportDocumentType.LEADERS -> append(leadersSection(sr, style, t, halaqaLabel, from, to, teacherName, centerLogo, halaqaLogo))
                    ReportDocumentType.POINTS -> append(pointsSection(sr, style, t, halaqaLabel, from, to, teacherName, centerLogo, halaqaLogo))
                    ReportDocumentType.ATTENDANCE -> append(attendanceSection(sr, style, t, halaqaLabel, from, to, teacherName, centerLogo, halaqaLogo))
                    ReportDocumentType.RECITATIONS -> append(recitationsSection(dr, absences.filter { rowTrack(it) == t || track != "all" }, rosterFor(students, t, fullRoster), style, t, halaqaLabel, from, to, teacherName, centerLogo, halaqaLogo))
                    ReportDocumentType.WEEKLY -> append(weeklySection(dr, style, t, halaqaLabel, from, to, teacherName, centerLogo, halaqaLogo))
                    ReportDocumentType.STUDENTS -> append(studentSections(sr, dr, nr, absences, studentById, style, t, halaqaLabel, from, to, teacherName, centerLogo, halaqaLogo))
                    else -> append(halaqaSection(sr, style, t, halaqaLabel, from, to, teacherName, centerLogo, halaqaLogo))
                }
            }
        }
        val customCss = """
          :root{--report-primary:$primary;--report-text:$text;--report-gold:#C89D4A;--report-scale:${String.format(Locale.US, "%.3f", style.fontScale.coerceIn(.75f,1.55f))}}
          .pro-table th{background:$primary!important;color:#fff!important}.pro-head-title h1,.pro-kpi b,.pro-meta b{color:$primary}
          @page{size:A4 $orientation;margin:0!important}
          @media screen{.pro-report-page{width:${screenWidth}px}}
          body{font-family:${reportFontStack(style.fontFamily)}!important}
          .pro-head-logo{width:62px;height:62px;border-radius:50%;overflow:hidden;display:flex;align-items:center;justify-content:center;background:#fff;border:1px solid #e3d6ad}.pro-head-logo img{width:100%;height:100%;object-fit:contain}
          .pro-head-title h1{font-size:calc(24px*var(--report-scale))!important;letter-spacing:0}.teacher{unicode-bidi:plaintext}
          .rm-table{table-layout:fixed;width:100%;border-collapse:separate;border-spacing:0}
          .rm-table th{font-size:calc(10px*var(--report-scale));line-height:1.3;padding:6px 3px;text-align:center}
          .rm-table td{font-size:calc(9.6px*var(--report-scale));vertical-align:top;line-height:1.45;text-align:right;padding:4px 5px;border-bottom:1px solid #ece4cc;border-inline-start:1px solid #f2ecd8}
          .rm-table tbody tr:nth-child(even) td{background:#fbf8ee}.rm-table tr{break-inside:avoid}
          .rm-name{width:19%;font-weight:800}.rm-total{width:8%;text-align:center!important;font-weight:800;color:var(--report-primary)}
          .rm-line{margin-bottom:2px}.nw{white-space:nowrap}.rm-arrow{color:#b08d3c;font-weight:800;padding:0 1px}
          .rm-k{display:inline-block;min-width:1.5em;text-align:center;border-radius:6px;font-weight:800;font-size:.84em;padding:0 3px;margin-inline-start:4px;color:#fff;line-height:1.5}
          .k-hifz{background:#1f7a52}.k-review{background:#2f6fb2}.k-sard{background:#b7892b}.k-tathbit{background:#6b4fa3}.k-tilawah{background:#2a8f9a}.k-tajweed{background:#536a9b}.k-test{background:#a34a4a}.k-other{background:#7a8580}
          .rm-abs{color:#8f9893;font-weight:800;text-align:center!important;font-size:1.15em}
          .rm-legend{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin:10px 0 2px;font-size:calc(9.5px*var(--report-scale));color:#59645f}
          .rm-block-title{margin:12px 0 5px;font-weight:800;color:var(--report-primary);font-size:calc(11px*var(--report-scale));break-after:avoid}
          .rm-wk{display:block;margin-bottom:1px}.rm-att{display:flex;flex-wrap:wrap;gap:8px 14px;margin:10px 0;padding:8px 10px;border:1px solid #e3d6ad;border-radius:10px;background:#fbf8ee;font-size:calc(10px*var(--report-scale))}
        """.trimIndent()
        return """
          <!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8">
          <meta name="viewport" content="width=$screenWidth,initial-scale=1,maximum-scale=1,user-scalable=no">
          <style>$baseCss\n$customCss</style></head><body><div class="pro-report">$sections</div>
          <script>${reportFitScript()}</script></body></html>
        """.trimIndent()
    }

    private fun header(
        typeTitle: String,
        sub: String,
        track: String,
        halaqaLabel: String,
        from: String,
        to: String,
        teacherName: String,
        centerLogo: String?,
        halaqaLogo: String?,
        style: NativeReportStyle,
    ): String {
        val left = logoHtml(halaqaLogo, "ح")
        val right = logoHtml(centerLogo, "۞")
        val customTitle = style.title.trim().takeIf { it.isNotBlank() && it != "تقرير حلقتي" } ?: typeTitle
        val teacher = if (style.includeTeacher && teacherName.isNotBlank()) "<div class=\"teacher\">إعداد: <bdi>${h(teacherName)}</bdi></div>" else ""
        return """
          <div class="pro-head">$left<div class="pro-head-title"><h1>${h(customTitle)}</h1>${headerSubtitle(customTitle, typeTitle, sub)}$teacher</div>$right</div>
          <div class="pro-meta"><span><b>الحلقة:</b> ${h(halaqaLabel)}</span><span><b>المسار:</b> ${h(trackLabel(track))}</span><span><b>الفترة:</b> ${h("$from — $to")}</span><span><b>تاريخ الإصدار:</b> ${LocalDate.now()}</span></div>
        """.trimIndent()
    }

    private fun footer(style: NativeReportStyle): String {
        val left = style.footer.ifBlank { "حلقتي · النظام الرسمي لإدارة ومتابعة حلقات القرآن الكريم" }
        return "<div class=\"pro-footer\"><span>${h(left)}</span><span>الإصدار ${h(BuildConfig.VERSION_NAME)}</span></div>"
    }

    private fun kpis(rows: List<JSONObject>, track: String = "all"): String = """
      <div class="pro-kpis">
        <div class="pro-kpi"><b>${rows.size}</b><span>طلاب</span></div>
        ${if (track == "tathbit") "<div class=\"pro-kpi\"><b>${fmt(rows.sumOf { it.optDouble("tathbit_pages",0.0) })}</b><span>تثبيت</span></div><div class=\"pro-kpi\"><b>${fmt(rows.sumOf { it.optDouble("points",0.0) })}</b><span>نقاط</span></div><div class=\"pro-kpi\"><b>${fmt(rows.sumOf { it.optDouble("tathbit_pages",0.0) } / rows.size.coerceAtLeast(1))}</b><span>متوسط التثبيت</span></div>"
          else "<div class=\"pro-kpi\"><b>${fmt(rows.sumOf { it.optDouble("hifz_pages",0.0) })}</b><span>حفظ</span></div><div class=\"pro-kpi\"><b>${fmt(rows.sumOf { it.optDouble("review_pages",0.0) })}</b><span>مراجعة</span></div><div class=\"pro-kpi\"><b>${fmt(rows.sumOf { it.optDouble("sard_pages",0.0) })}</b><span>سرد</span></div>"}
        <div class="pro-kpi"><b>${rows.sumOf { it.optInt("present",0)+it.optInt("late",0) }}</b><span>حضور</span></div>
        <div class="pro-kpi"><b>${rows.sumOf { it.optInt("absent",0)+it.optInt("excused",0) }}</b><span>غياب</span></div>
      </div>
    """.trimIndent()

    private data class Col(val key: String, val label: String, val cls: String = "", val value: (JSONObject, Int)->String)

    private fun relevant(cols: List<Col>, track: String): List<Col> = cols.filterNot { c ->
        when (track) {
            "tathbit" -> c.key in setOf("hifz", "review", "sard", "track")
            "general_hifz" -> c.key in setOf("tathbit", "track")
            else -> false
        }
    }

    private fun table(rows: List<JSONObject>, cols: List<Col>): String {
        if (cols.isEmpty()) return "<div class=\"report-empty-pro\">اختر عمودًا واحدًا على الأقل.</div>"
        val head = cols.joinToString("") { "<th>${h(it.label)}</th>" }
        val body = rows.mapIndexed { index, row ->
            "<tr>" + cols.joinToString("") { c -> "<td class=\"${c.cls}\">${h(c.value(row,index))}</td>" } + "</tr>"
        }.joinToString("")
        return "<div class=\"pro-table-wrap\"><table class=\"pro-table\"><thead><tr>$head</tr></thead><tbody>$body</tbody></table></div>"
    }

    private fun halaqaSection(rows: List<JSONObject>, style: NativeReportStyle, track: String, halaqa: String, from: String, to: String, teacher: String, centerLogo: String?, halaqaLogo: String?): String {
        val candidates = listOf(
            Col("rank","#","compact num") { _,i -> (i+1).toString() },
            Col("student","الطالب","name") { r,_ -> txt(r,"student_name","طالب") },
            Col("category","الفئة") { r,_ -> txt(r,"category_name") },
            Col("track","المسار") { _,_ -> trackLabel(track) },
            Col("hifz","الحفظ","num") { r,_ -> fmt(r.optDouble("hifz_pages",0.0)) },
            Col("tathbit","التثبيت","num") { r,_ -> fmt(r.optDouble("tathbit_pages",0.0)) },
            Col("review","المراجعة","num") { r,_ -> fmt(r.optDouble("review_pages",0.0)) },
            Col("sard","السرد","num") { r,_ -> fmt(r.optDouble("sard_pages",0.0)) },
            Col("points","النقاط","num") { r,_ -> fmt(r.optDouble("points",0.0)) },
            Col("attendance","الحضور","num") { r,_ -> (r.optInt("present",0)+r.optInt("late",0)).toString() },
            Col("absence","الغياب","num") { r,_ -> (r.optInt("absent",0)+r.optInt("excused",0)).toString() },
            Col("mistakes","الأخطاء","num") { r,_ -> r.optInt("mistakes",0).toString() },
        )
        val cols = relevant(candidates.filter { it.key in style.visibleColumns }, track)
        return section(
            header(ReportDocumentType.title(ReportDocumentType.HALAQA),"",track,halaqa,from,to,teacher,centerLogo,halaqaLogo,style) +
                kpis(rows, track) + table(rows, cols) +
                (if (rows.isEmpty()) empty("لا توجد بيانات في الفترة المحددة.") else "") + footer(style),
        )
    }

    private fun leadersSection(rows0: List<JSONObject>, style: NativeReportStyle, track: String, halaqa: String, from: String, to: String, teacher: String, centerLogo: String?, halaqaLogo: String?): String {
        val rows = rows0.sortedWith(compareByDescending<JSONObject>{it.optDouble("points",0.0)}.thenByDescending{it.optDouble("total_pages",0.0)})
        val top = rows.take(3)
        val podium = if (top.isEmpty()) "" else {
            val order = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
            "<div class=\"podium\">" + order.mapIndexed { i,r ->
                val actual = when { i==0 && top.size>1 -> 2; i==1 -> 1; else -> 3 }
                val cls = when(actual){1->"first";2->"second";else->"third"}
                val name = txt(r,"student_name","طالب")
                "<div class=\"podium-person $cls\"><div class=\"podium-avatar\">${h(name.take(1))}</div><div class=\"podium-step\"><div class=\"podium-rank\">$actual</div><div class=\"podium-name\">${h(name)}</div><div class=\"podium-score\">${fmt(r.optDouble("points",0.0))} نقطة · ${fmt(r.optDouble("total_pages",0.0))} ص</div></div></div>"
            }.joinToString("") + "</div>"
        }
        val cols = listOf(
            Col("rank","الترتيب","compact num") { _,i -> (i+1).toString() },
            Col("student","الطالب","name") { r,_ -> txt(r,"student_name","طالب") },
            Col("points","النقاط","num") { r,_ -> fmt(r.optDouble("points",0.0)) },
            Col("pages","الصفحات","num") { r,_ -> fmt(r.optDouble("total_pages",0.0)) },
            Col("mistakes","الأخطاء","num") { r,_ -> r.optInt("mistakes",0).toString() },
            Col("attendance","الحضور","num") { r,_ -> (r.optInt("present",0)+r.optInt("late",0)).toString() },
        )
        val visible = cols.filter { it.key in style.visibleColumns }
        return section(
            header(ReportDocumentType.title(ReportDocumentType.LEADERS),"",track,halaqa,from,to,teacher,centerLogo,halaqaLogo,style) +
                podium + table(rows, visible) + (if (rows.isEmpty()) empty("لا توجد بيانات لهذا المسار.") else "") + footer(style),
        )
    }

    private fun pointsSection(rows: List<JSONObject>, style: NativeReportStyle, track: String, halaqa: String, from: String, to: String, teacher: String, centerLogo: String?, halaqaLogo: String?): String {
        val cols = listOf(
            Col("student","الطالب","name") { r,_ -> txt(r,"student_name","طالب") },
            Col("hifz","حفظ","num") { r,_ -> fmt(r.optDouble("hifz_pages",0.0)) },
            Col("tathbit","تثبيت","num") { r,_ -> fmt(r.optDouble("tathbit_pages",0.0)) },
            Col("review","مراجعة","num") { r,_ -> fmt(r.optDouble("review_pages",0.0)) },
            Col("sard","سرد","num") { r,_ -> fmt(r.optDouble("sard_pages",0.0)) },
            Col("mistakes","أخطاء","num") { r,_ -> r.optInt("mistakes",0).toString() },
            Col("prompts","نقرات","num") { r,_ -> r.optInt("prompts",0).toString() },
            Col("points","المجموع","num") { r,_ -> fmt(r.optDouble("points",0.0)) },
        )
        val visible = relevant(cols.filter { it.key in style.visibleColumns }, track)
        return section(
            header(ReportDocumentType.title(ReportDocumentType.POINTS),"",track,halaqa,from,to,teacher,centerLogo,halaqaLogo,style) +
                kpis(rows, track) + table(rows, visible) + (if(rows.isEmpty()) empty("لا توجد بيانات لهذا المسار.") else "") + footer(style),
        )
    }

    private fun attendanceSection(rows: List<JSONObject>, style: NativeReportStyle, track: String, halaqa: String, from: String, to: String, teacher: String, centerLogo: String?, halaqaLogo: String?): String {
        val cols = listOf(
            Col("student","الطالب","name") { r,_ -> txt(r,"student_name","طالب") },
            Col("present","حاضر","num") { r,_ -> r.optInt("present",0).toString() },
            Col("absent","غائب","num") { r,_ -> r.optInt("absent",0).toString() },
            Col("excused","بعذر","num") { r,_ -> r.optInt("excused",0).toString() },
            Col("late","متأخر","num") { r,_ -> r.optInt("late",0).toString() },
            Col("sessions","إجمالي التسجيلات","num") { r,_ -> r.optInt("sessions",0).toString() },
        )
        val visible = cols.filter { it.key in style.visibleColumns }
        return section(
            header(ReportDocumentType.title(ReportDocumentType.ATTENDANCE),"",track,halaqa,from,to,teacher,centerLogo,halaqaLogo,style) +
                kpis(rows, track) + table(rows, visible) + (if(rows.isEmpty()) empty("لا توجد بيانات حضور لهذا المسار.") else "") + footer(style),
        )
    }

    private fun rosterFor(students: List<Student>, track: String, fullRoster: Boolean): List<Pair<String, String>> =
        if (!fullRoster) emptyList() else students.filter { it.status == "active" && (track == "all" || it.track == track) }.map { it.id to it.fullName }

    private fun kindClass(kind: String) = when (kind) {
        "hifz", "review", "sard", "tathbit", "tilawah", "tajweed", "test" -> "k-$kind"
        else -> "k-other"
    }

    /** Range text with its numbers kept together ("الجاثية: 6 ← 22" never splits across lines except at the arrow). */
    private fun rangeHtml(range: String): String =
        range.split(" ← ").joinToString("<span class=\"rm-arrow\"> ← </span>") { "<span class=\"nw\">${h(it)}</span>" }

    private fun matrixEntries(rows: List<JSONObject>): List<RecitationMatrix.Entry> = rows.map { r ->
        val st = r.optJSONObject("students")
        RecitationMatrix.Entry(
            studentId = r.optString("student_id").ifBlank { st?.optString("id").orEmpty() },
            studentName = st?.let { txt(it, "full_name", "طالب") } ?: "طالب",
            date = r.optJSONObject("sessions")?.optString("session_date").orEmpty(),
            kind = RecitationMatrix.kindOf(com.mohammedsamir.halaqati.plans.ReportScopeRules.reportActivity(r.optString("activity_type"), r.optJSONObject("sessions")?.optString("session_type"))),
            range = recitationRange(r),
            pages = r.optDouble("pages_count", 0.0),
            startPage = nullableInt(r, "start_page"),
            endPage = nullableInt(r, "end_page"),
        )
    }

    private fun matrixAbsences(rows: List<JSONObject>): List<RecitationMatrix.Absence> = rows.mapNotNull { r ->
        val date = r.optJSONObject("sessions")?.optString("session_date").orEmpty()
        if (date.isBlank()) return@mapNotNull null
        RecitationMatrix.Absence(
            studentId = r.optString("student_id"),
            studentName = r.optJSONObject("students")?.let { txt(it, "full_name", "طالب") } ?: "طالب",
            date = date,
            excused = r.optString("status") == "excused",
        )
    }

    private fun recitationsSection(rows0: List<JSONObject>, absenceRows: List<JSONObject>, roster: List<Pair<String, String>>, style: NativeReportStyle, track: String, halaqa: String, from: String, to: String, teacher: String, centerLogo: String?, halaqaLogo: String?): String {
        val entries = matrixEntries(rows0)
        val matrix = RecitationMatrix.build(entries, matrixAbsences(absenceRows), roster)
        val head = header(ReportDocumentType.title(ReportDocumentType.RECITATIONS), "", track, halaqa, from, to, teacher, centerLogo, halaqaLogo, style)
        if (matrix.days.isEmpty()) return section(head + empty("لا توجد تسميعات لهذا المسار.") + footer(style))
        val kpiStrip = """<div class="pro-kpis" style="grid-template-columns:repeat(5,1fr)">
          <div class="pro-kpi"><b>${matrix.rows.size}</b><span>طلاب</span></div>
          <div class="pro-kpi"><b>${matrix.days.size}</b><span>أيام</span></div>
          <div class="pro-kpi"><b>${entries.size}</b><span>تسميعات</span></div>
          <div class="pro-kpi"><b>${fmt(matrix.rows.sumOf { it.total })}</b><span>صفحات</span></div>
          <div class="pro-kpi"><b>${matrix.absences}</b><span>غياب</span></div></div>"""
        val perPage = if (style.orientation == ReportOrientation.PORTRAIT) 6 else 7
        val blocks = RecitationMatrix.blocks(matrix.days, perPage)
        val tables = blocks.mapIndexed { index, days ->
            val first = index == 0
            val th = "<th class=\"rm-name\">الطالب</th>" + (if (first) "<th class=\"rm-total\">الإجمالي (ص)</th>" else "") +
                days.joinToString("") { d -> "<th>${h(RecitationMatrix.weekdayAr(d))}<br>${h(d.drop(5))}</th>" }
            val body = matrix.rows.joinToString("") { row ->
                "<tr><td class=\"rm-name\">${h(row.name)}</td>" + (if (first) "<td class=\"rm-total\">${h(fmt(row.total))}</td>" else "") +
                    days.joinToString("") { d ->
                        val lines = row.cells[d].orEmpty()
                        val mark = row.absent[d]
                        when {
                            lines.isNotEmpty() -> "<td>" + lines.joinToString("") { (k, range) ->
                                "<div class=\"rm-line\"><span class=\"rm-k ${kindClass(k)}\">${h(RecitationMatrix.letter(k))}</span>${rangeHtml(range)}</div>"
                            } + "</td>"
                            mark != null -> "<td class=\"rm-abs\">${h(mark)}</td>"
                            else -> "<td></td>"
                        }
                    } + "</tr>"
            }
            val title = if (blocks.size > 1) "<div class=\"rm-block-title\">المجموعة ${index + 1} من ${blocks.size} · ${h(days.first())} — ${h(days.last())}</div>" else ""
            title + "<div class=\"pro-table-wrap\"><table class=\"pro-table rm-table\"><thead><tr>$th</tr></thead><tbody>$body</tbody></table></div>"
        }.joinToString("")
        val legend = "<div class=\"rm-legend\"><span><span class=\"rm-k k-hifz\">ح</span>حفظ</span><span><span class=\"rm-k k-review\">م</span>مراجعة</span><span><span class=\"rm-k k-sard\">س</span>سرد</span><span><span class=\"rm-k k-tathbit\">ث</span>تثبيت</span><span><span class=\"rm-k k-tilawah\">ل</span>تلاوة</span><span><span class=\"rm-k k-tajweed\">ج</span>تجويد</span><span><span class=\"rm-k k-test\">ا</span>اختبار</span><span><b>غ</b> غائب</span><span><b>ع</b> غائب بعذر</span><span>الخانة الفارغة: لم يُسمّع</span></div>"
        return section(head + kpiStrip + tables + legend + footer(style))
    }

    private fun weeklySection(rows0: List<JSONObject>, style: NativeReportStyle, track: String, halaqa: String, from: String, to: String, teacher: String, centerLogo: String?, halaqaLogo: String?): String {
        val weekly = RecitationMatrix.weekly(matrixEntries(rows0))
        val head = header(ReportDocumentType.title(ReportDocumentType.WEEKLY), "", track, halaqa, from, to, teacher, centerLogo, halaqaLogo, style)
        if (weekly.weekStarts.isEmpty()) return section(head + empty("لا توجد تسميعات لهذا المسار.") + footer(style))
        fun cell(t: RecitationMatrix.WeekTotals?): String {
            if (t == null || t.all <= 0.0) return "<td></td>"
            val lines = buildList {
                if (t.hifz > 0) add("hifz" to t.hifz)
                if (t.review > 0) add("review" to t.review)
                if (t.sard > 0) add("sard" to t.sard)
                if (t.tathbit > 0) add("tathbit" to t.tathbit)
            }
            return "<td>" + lines.joinToString("") { (k, v) -> "<span class=\"rm-wk\"><span class=\"rm-k ${kindClass(k)}\">${h(RecitationMatrix.letter(k))}</span>${h(fmt(v))} ص</span>" } + "</td>"
        }
        val perPage = if (style.orientation == ReportOrientation.PORTRAIT) 4 else 6
        val blocks = weekly.weekStarts.chunked(perPage)
        val tables = blocks.mapIndexed { index, weeks ->
            val first = index == 0
            val th = "<th class=\"rm-name\">الطالب</th>" + (if (first) "<th class=\"rm-total\">الإجمالي (ص)</th>" else "") +
                weeks.joinToString("") { w -> "<th>أسبوع<br>${h(w.drop(5))}</th>" }
            val body = weekly.rows.joinToString("") { row ->
                "<tr><td class=\"rm-name\">${h(row.name)}</td>" + (if (first) "<td class=\"rm-total\">${h(fmt(row.total.all))}</td>" else "") +
                    weeks.joinToString("") { w -> cell(row.weeks[w]) } + "</tr>"
            }
            val title = if (blocks.size > 1) "<div class=\"rm-block-title\">المجموعة ${index + 1} من ${blocks.size}</div>" else ""
            title + "<div class=\"pro-table-wrap\"><table class=\"pro-table rm-table\"><thead><tr>$th</tr></thead><tbody>$body</tbody></table></div>"
        }.joinToString("")
        val legend = "<div class=\"rm-legend\"><span><span class=\"rm-k k-hifz\">ح</span>حفظ</span><span><span class=\"rm-k k-review\">م</span>مراجعة</span><span><span class=\"rm-k k-sard\">س</span>سرد (نطاق الأسبوع)</span><span><span class=\"rm-k k-tathbit\">ث</span>تثبيت</span><span>الأسبوع يبدأ يوم الأحد</span></div>"
        return section(head + tables + legend + footer(style))
    }

    private fun studentSections(
        rows: List<JSONObject>, details: List<JSONObject>, notes: List<JSONObject>, absenceRows: List<JSONObject>, students: Map<String,Student>, style: NativeReportStyle,
        track: String, halaqa: String, from: String, to: String, teacher: String, centerLogo: String?, halaqaLogo: String?,
    ): String {
        if (rows.isEmpty()) return section(header(ReportDocumentType.title(ReportDocumentType.STUDENTS),"",track,halaqa,from,to,teacher,centerLogo,halaqaLogo,style) + empty("لا توجد بيانات لهذا المسار.") + footer(style))
        return rows.mapIndexed { index, r ->
            val id=r.optString("student_id"); val st=students[id]; val name=txt(r,"student_name","طالب")
            val photoData = if (style.includePhotos) DocumentImageEncoder.dataUrl(st?.photoPath, 320, transparent = false) else null
            val photo = if (style.includePhotos) {
                if (photoData != null) "<div class=\"student-report-photo\"><img src=\"${hAttr(photoData)}\" alt=\"\"></div>"
                else "<div class=\"student-report-photo\">${h(name.take(1))}</div>"
            } else ""
            val birth = if(style.includeBirthDate) "<div>الميلاد<b>${h(st?.dateOfBirth ?: "—")}</b></div>" else ""
            val cardClass = if (style.includePhotos) "student-report-card" else "student-report-card no-photo"
            val info="""<div class="$cardClass">$photo<div><h2 class="student-report-name">${h(name)}</h2><div class="student-info-grid"><div>المسار<b>${h(trackLabel(track))}</b></div><div>الفئة<b>${h(txt(r,"category_name"))}</b></div>$birth${if (track == "tathbit") "<div>التثبيت<b>${fmt(r.optDouble("tathbit_pages",0.0))} ص</b></div><div>النقاط<b>${fmt(r.optDouble("points",0.0))}</b></div><div>الحضور<b>${r.optInt("present",0)+r.optInt("late",0)}</b></div>" else "<div>الحفظ<b>${fmt(r.optDouble("hifz_pages",0.0))} ص</b></div><div>المراجعة<b>${fmt(r.optDouble("review_pages",0.0))} ص</b></div><div>السرد<b>${fmt(r.optDouble("sard_pages",0.0))} ص</b></div>"}</div></div></div>"""
            val recs=details.filter{it.optString("student_id")==id}
            val recCols=listOf(
                Col("date","التاريخ","compact"){x,_->x.optJSONObject("sessions")?.optString("session_date").orEmpty().ifBlank{"—"}},
                Col("activity","النوع"){x,_->activityAr(x.optString("activity_type"))},
                Col("range","النطاق","text-cell"){x,_->recitationRange(x)},
                Col("pages","صفحات","num"){x,_->fmt(x.optDouble("pages_count",0.0))},
                Col("mistakes","أخطاء","num"){x,_->x.optInt("mistakes",0).toString()},
                Col("points","نقاط","num"){x,_->fmt(x.optDouble("points",0.0))},
            )
            val ns=notes.filter{it.optString("student_id")==id}
            val notesHtml=if(ns.isEmpty())"" else "<div class=\"pro-section-title\">تعليقات ومتابعة</div>"+table(ns,listOf(
                Col("date","التاريخ","compact"){x,_->x.optJSONObject("sessions")?.optString("session_date").orEmpty().ifBlank{"—"}},
                Col("behavior","السلوك"){x,_->behaviorAr(x.optString("behavior"))},
                Col("homework","الواجب","text-cell"){x,_->txt(x,"homework")},
                Col("teacher_comment","تعليق المحفّظ","text-cell"){x,_->txt(x,"teacher_comment")},
            ))
            val visibleRecCols = recCols.filter { it.key in style.visibleColumns }
            val present = r.optInt("present",0); val late = r.optInt("late",0); val absent = r.optInt("absent",0); val excused = r.optInt("excused",0)
            val attended = present + late
            val totalSessions = attended + absent + excused
            val commitment = if (totalSessions > 0) "${kotlin.math.round(attended * 100.0 / totalSessions).toInt()}٪" else "—"
            val absentDays = matrixAbsences(absenceRows.filter { it.optString("student_id") == id }).sortedBy { it.date }
                .joinToString("، ") { it.date.drop(5) + if (it.excused) " (بعذر)" else "" }
            val strip = "<div class=\"rm-att\"><span>الالتزام بالحضور: <b>${h(commitment)}</b></span><span>حاضر <b>$present</b></span><span>متأخر <b>$late</b></span><span>غائب <b>$absent</b></span><span>بعذر <b>$excused</b></span>" +
                (if (absentDays.isNotBlank()) "<span>أيام الغياب: <b>${h(absentDays)}</b></span>" else "") + "</div>"
            section(header("تقرير الطالب ${index+1}/${rows.size}",name,track,halaqa,from,to,teacher,centerLogo,halaqaLogo,style)+info+strip+"<div class=\"pro-section-title\">التسميعات</div>"+table(recs,visibleRecCols)+notesHtml+footer(style))
        }.joinToString("")
    }

    private fun section(content: String) = "<section class=\"pro-report-page\">$content</section>"
    private fun empty(text:String)="<div class=\"report-empty-pro\">${h(text)}</div>"
    private fun logoHtml(src:String?,fallback:String)= if(src!=null) "<div class=\"pro-head-logo\"><img src=\"${hAttr(src)}\" alt=\"\"></div>" else "<div class=\"pro-head-logo\">${h(fallback)}</div>"

    private fun rowTrack(row: JSONObject): String = row.optJSONObject("students")?.optString("track_code").orEmpty()
    private fun trackLabel(v:String)=when(v){"general_hifz"->"الحفظ العام";"tathbit"->"التثبيت";else->"جميع المسارات"}
    private fun activityAr(v:String)=mapOf("new_hifz" to "حفظ","review_near" to "مراجعة قريبة","review_far" to "مراجعة بعيدة","sard" to "سرد","tilawah" to "تلاوة","test" to "اختبار","tathbit" to "تثبيت")[v]?:v.ifBlank{"—"}
    private fun evaluationAr(v:String)=mapOf("excellent" to "ممتاز","very_good" to "جيد جدًا","good" to "جيد","repeat" to "إعادة","not_rated" to "غير مقيم")[v]?:v.ifBlank{"—"}
    private fun behaviorAr(v:String)=mapOf("excellent" to "ممتاز","good" to "جيد","normal" to "طبيعي","needs_attention" to "يحتاج متابعة","warning" to "تنبيه")[v]?:v.ifBlank{"—"}

    fun rangeText(r: JSONObject): String = recitationRange(r)

    /** Human range = exact stored Surah/Ayah. Pages are only a fallback when no ayah data exists. */
    private fun recitationRange(r:JSONObject):String {
        val ss=nullableInt(r,"start_surah"); val sa=nullableInt(r,"start_ayah"); val es=nullableInt(r,"end_surah"); val ea=nullableInt(r,"end_ayah")
        if(ss!=null&&sa!=null){
            val endS=es?:ss; val endA=ea?:sa
            return when {
                endS==ss && endA==sa -> "سورة ${surahName(ss)}: $sa"
                endS==ss -> "سورة ${surahName(ss)}: $sa ← $endA"
                else -> "${surahName(ss)} $sa ← ${surahName(endS)} $endA"
            }
        }
        val surahs=r.optJSONArray("selected_surahs") ?: r.optJSONObject("selection_data")?.optJSONArray("selected_surahs")
        if(surahs!=null&&surahs.length()>0)return (0 until surahs.length()).joinToString("، "){surahName(surahs.optInt(it))}
        val juz=r.optJSONObject("selection_data")?.optJSONArray("selected_juz")
        if(juz!=null&&juz.length()>0)return "الأجزاء "+(0 until juz.length()).joinToString("، "){juz.optInt(it).toString()}
        val sp=nullableInt(r,"start_page"); val ep=nullableInt(r,"end_page")
        if(sp!=null||ep!=null){ val x=sp?:ep!!; val y=ep?:x; return if(x==y)"صفحة $x" else "صفحات $x–$y" }
        return "—"
    }
    private fun headerSubtitle(title:String, typeTitle:String, sub:String):String {
        val parts = listOfNotNull(typeTitle.takeIf { it.isNotBlank() && it != title }, sub.takeIf { it.isNotBlank() && it != title })
        return if (parts.isEmpty()) "" else "<p>${h(parts.joinToString(" · "))}</p>"
    }
    /** Null-safe text: JSON null / "null" / blank → fallback; collapses broken whitespace in names. */
    private fun txt(o:JSONObject, key:String, fallback:String="—"):String {
        if (!o.has(key) || o.isNull(key)) return fallback
        val v = o.optString(key).replace('\u00A0',' ').replace(Regex("\\s+")," ").trim()
        return if (v.isBlank() || v.equals("null", true)) fallback else v
    }
    private fun surahName(n:Int):String= runCatching { QuranMetadata.surahName(n) }.getOrDefault("سورة $n")
    private fun nullableInt(o:JSONObject,key:String):Int?=if(!o.has(key)||o.isNull(key))null else o.optInt(key).takeIf{it>0}
    private fun fmt(v:Double):String=if(kotlin.math.abs(v-kotlin.math.round(v))<.0001)kotlin.math.round(v).toInt().toString() else String.format(Locale.US,"%.2f",v).trimEnd('0').trimEnd('.')
    private fun longToHex(v:Long)=String.format(Locale.US,"#%06X",v and 0xFFFFFF)
    private fun h(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
    private fun hAttr(v:String)=h(v)
    private fun reportFontStack(value: String): String = when (value.lowercase(Locale.US)) {
        "cairo" -> "'Cairo',sans-serif"
        "sans", "sans-serif", "arial", "tahoma" -> "sans-serif"
        "serif" -> "serif"
        "mono", "monospace" -> "monospace"
        else -> "'Cairo',sans-serif"
    }
    private fun cssString(v:String)=v.replace("\\","\\\\").replace("'","\\'").replace("\n"," ").replace("\r"," ")

    private fun reportFitScript():String="""
      (function(){
        window.__HALAQATI_READY__=false;window.__HALAQATI_LAYOUT_OK__=true;
        function kind(label){if(/^(#|م|الترتيب|صفحات|أخطاء|نقرات|نقاط|حاضر|غائب|بعذر|متأخر|الحفظ|التثبيت|المراجعة|السرد|الحضور|الغياب|المجموع)$/i.test(label))return'compact';if(/الطالب/.test(label))return'name';if(/النطاق|تعليق|الواجب|ملاحظة/.test(label))return'flex';return'auto'}
        function fitTable(t){var hs=[...(t.tHead?.rows?.[0]?.cells||[])];if(!hs.length)return;var ks=hs.map(x=>kind((x.textContent||'').trim()));var ws=ks.map((k,i)=>{var max=(hs[i].textContent||'').length;[...t.tBodies].forEach(tb=>[...tb.rows].slice(0,80).forEach(r=>{var c=r.cells[i];if(c)max=Math.max(max,(c.textContent||'').trim().length)}));return k==='name'?Math.max(18,Math.min(28,18+max*.25)):k==='flex'?Math.max(14,Math.min(24,11+max*.24)):k==='compact'?Math.max(5,Math.min(8,4+max*.22)):Math.max(7,Math.min(14,6+max*.2))});var sum=ws.reduce((a,b)=>a+b,0)||1;var cg=document.createElement('colgroup');ws.forEach(w=>{var c=document.createElement('col');c.style.width=(w*100/sum).toFixed(2)+'%';cg.appendChild(c)});var old=t.querySelector('colgroup[data-auto]');if(old)old.remove();cg.dataset.auto='1';t.insertBefore(cg,t.firstChild);[...t.querySelectorAll('.name')].forEach(c=>{var s=parseFloat(getComputedStyle(c).fontSize)||9.5,g=0;while(c.scrollWidth>c.clientWidth+1&&s>7.1&&g++<14){s-=.25;c.style.fontSize=s+'px'}})}
        function run(){document.querySelectorAll('.pro-table:not(.rm-table)').forEach(fitTable);document.querySelectorAll('.pro-report-page').forEach(p=>{if(p.scrollWidth>p.clientWidth+3)window.__HALAQATI_LAYOUT_OK__=false});document.documentElement.dataset.layoutOk=window.__HALAQATI_LAYOUT_OK__?'1':'0';window.__HALAQATI_READY__=true;}
        var done=()=>requestAnimationFrame(()=>requestAnimationFrame(run));if(document.fonts&&document.fonts.ready)document.fonts.ready.then(done).catch(done);else done();
      })();
    """.trimIndent()
}
