package com.mohammedsamir.halaqati.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.mohammedsamir.halaqati.R
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Golden-master compatible report exports: Excel, Word, image and shareable text. */
object NativeReportExport {
    data class Table(val headers: List<String>, val rows: List<List<String>>)

    fun table(source: List<JSONObject>, details: Boolean, program: Boolean = false): Table {
        return if (details) {
            val headers = listOf("#", "الطالب", "التاريخ", "النشاط", "الصفحات", "التقييم", "السلامة", "الأخطاء", "النقرات")
            val rows = source.mapIndexed { index, row ->
                val st = row.optJSONObject("students")
                val session = row.optJSONObject("sessions")
                listOf(
                    (index + 1).toString(),
                    st?.optString("full_name").orEmpty().ifBlank { row.optString("student_name", "طالب") },
                    session?.optString("session_date").orEmpty().ifBlank { row.optString("session_date") },
                    activityLabel(row.optString("activity_type")),
                    clean(row.optDouble("pages_count", 0.0)),
                    evaluationLabel(row.optString("evaluation")),
                    clean(row.optDouble("safety_percentage", 0.0)) + "%",
                    row.optInt("mistakes", 0).toString(),
                    row.optInt("prompts", 0).toString(),
                )
            }
            Table(headers, rows)
        } else if (program) {
            val headers = listOf("#", "الطالب", "التلاوة (صفحات)", "التجويد (صفحات)", "الاختبار (صفحات)", "الحضور", "الغياب", "الغياب بعذر", "نقاط النشاط")
            val rows = source.mapIndexed { index, row ->
                listOf((index + 1).toString(), row.optString("student_name", "طالب"),
                    clean(row.optDouble("tilawah_pages",0.0)), clean(row.optDouble("tajweed_pages",0.0)),
                    clean(row.optDouble("test_pages",0.0)), (row.optInt("present",0) + row.optInt("late",0)).toString(),
                    row.optInt("absent",0).toString(),row.optInt("excused",0).toString(),clean(row.optDouble("recitation_points",0.0)))
            }
            Table(headers, rows)
        } else {
            val headers = listOf("#", "الطالب", "الفئة", "المسار", "الحفظ", "التثبيت", "المراجعة", "السرد", "الحضور", "الغياب", "النقاط")
            val rows = source.mapIndexed { index, row ->
                listOf(
                    (index + 1).toString(),
                    row.optString("student_name", "طالب"),
                    row.optString("category_name"),
                    if (row.optString("track_code") == "tathbit") "التثبيت" else "الحفظ العام",
                    clean(row.optDouble("hifz_pages", 0.0)),
                    clean(row.optDouble("tathbit_pages", 0.0)),
                    clean(row.optDouble("review_pages", 0.0)),
                    clean(row.optDouble("sard_pages", 0.0)),
                    (row.optInt("present", 0) + row.optInt("late", 0)).toString(),
                    row.optInt("absent", 0).toString(),
                    clean(row.optDouble("points", 0.0)),
                )
            }
            Table(headers, rows)
        }
    }

    private fun matrixEntries(source: List<JSONObject>): List<com.mohammedsamir.halaqati.documents.RecitationMatrix.Entry> = source.map { r ->
        val st = r.optJSONObject("students")
        com.mohammedsamir.halaqati.documents.RecitationMatrix.Entry(
            studentId = r.optString("student_id"),
            studentName = st?.optString("full_name").orEmpty().ifBlank { "طالب" },
            date = r.optJSONObject("sessions")?.optString("session_date").orEmpty(),
            kind = com.mohammedsamir.halaqati.documents.RecitationMatrix.kindOf(r.optString("activity_type")),
            range = com.mohammedsamir.halaqati.documents.ReportHtmlRenderer.rangeText(r),
            pages = r.optDouble("pages_count", 0.0),
            startPage = r.optInt("start_page").takeIf { it > 0 },
            endPage = r.optInt("end_page").takeIf { it > 0 },
        )
    }

    /** RC43: the recitation log as one wide grid (student x day) for Word / Excel, same content as the PDF (ranges, غ / ع marks, total). */
    fun matrixTable(source: List<JSONObject>, absenceRows: List<JSONObject> = emptyList()): Table {
        val absences = absenceRows.mapNotNull { r ->
            val date = r.optJSONObject("sessions")?.optString("session_date").orEmpty()
            if (date.isBlank()) null else com.mohammedsamir.halaqati.documents.RecitationMatrix.Absence(
                r.optString("student_id"), r.optJSONObject("students")?.optString("full_name").orEmpty().ifBlank { "طالب" }, date, r.optString("status") == "excused",
            )
        }
        val m = com.mohammedsamir.halaqati.documents.RecitationMatrix.build(matrixEntries(source), absences)
        val headers = listOf("الطالب", "الإجمالي (ص)") + m.days
        val rows = m.rows.map { row ->
            listOf(row.name, clean(row.total)) + m.days.map { d ->
                val lines = row.cells[d].orEmpty()
                if (lines.isNotEmpty()) lines.joinToString(" | ") { (k, range) -> com.mohammedsamir.halaqati.documents.RecitationMatrix.letter(k) + " " + range }
                else row.absent[d].orEmpty()
            }
        }
        return Table(headers, rows)
    }

    fun weeklyTable(source: List<JSONObject>): Table {
        val w = com.mohammedsamir.halaqati.documents.RecitationMatrix.weekly(matrixEntries(source))
        val headers = listOf("الطالب", "الإجمالي (ص)") + w.weekStarts.map { "أسبوع $it" }
        val rows = w.rows.map { row ->
            listOf(row.name, clean(row.total.all)) + w.weekStarts.map { ws ->
                val t = row.weeks[ws]
                if (t == null || t.all <= 0.0) "" else listOfNotNull(
                    if (t.hifz > 0) "ح ${clean(t.hifz)}" else null, if (t.review > 0) "م ${clean(t.review)}" else null,
                    if (t.sard > 0) "س ${clean(t.sard)}" else null, if (t.tathbit > 0) "ث ${clean(t.tathbit)}" else null,
                ).joinToString(" | ")
            }
        }
        return Table(headers, rows)
    }

    fun exportXlsx(context: Context, uri: Uri, table: Table, sheetName: String = "تقرير حلقتي") {
        context.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                fun entry(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
                entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
                entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${xml(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets></workbook>""")
                entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
                val all = listOf(table.headers) + table.rows
                val sheetRows = all.mapIndexed { r, cells ->
                    val cellXml = cells.mapIndexed { c, value ->
                        val ref = "${columnName(c + 1)}${r + 1}"
                        "<c r=\"$ref\" t=\"inlineStr\"><is><t>${xml(value)}</t></is></c>"
                    }.joinToString("")
                    "<row r=\"${r + 1}\">$cellXml</row>"
                }.joinToString("")
                entry("xl/worksheets/sheet1.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0" rightToLeft="1"/></sheetViews><cols><col min="1" max="1" width="6" customWidth="1"/><col min="2" max="20" width="18" customWidth="1"/></cols><sheetData>$sheetRows</sheetData></worksheet>""")
            }
        } ?: error("تعذر فتح ملف Excel للحفظ")
    }

    fun exportWord(context: Context, uri: Uri, table: Table, title: String, subtitle: String) {
        val head = table.headers.joinToString("") { "<th>${html(it)}</th>" }
        val body = table.rows.joinToString("") { row -> row.joinToString("", "<tr>", "</tr>") { "<td>${html(it)}</td>" } }
        val doc = """<!doctype html><html dir="rtl"><head><meta charset="UTF-8"><style>body{font-family:Cairo,Tahoma,Arial,sans-serif;direction:rtl;color:#18231f;padding:24px}h1{text-align:center;color:#0d3b2f;margin-bottom:4px}p{text-align:center;color:#65716b}table{width:100%;border-collapse:collapse;font-size:9.5pt}th{background:#0d3b2f;color:#fff;padding:7px;border:1px solid #d7e0dc}td{padding:6px;border:1px solid #d7e0dc;text-align:center}tr:nth-child(even){background:#f3f8f5}</style></head><body><h1>${html(title)}</h1><p>${html(subtitle)}</p><table><thead><tr>$head</tr></thead><tbody>$body</tbody></table></body></html>"""
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { it.write('\ufeff'.toString()); it.write(doc) }
        } ?: error("تعذر فتح ملف Word للحفظ")
    }

    fun exportPng(context: Context, uri: Uri, table: Table, title: String, subtitle: String) {
        val width = 1600
        val rowHeight = 58
        val height = (250 + table.rows.size.coerceAtMost(120) * rowHeight).coerceAtLeast(500)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val typeface = ResourcesCompat.getFont(context, R.font.cairo)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 48f; color = 0xFF0D3B2F.toInt(); this.typeface = typeface }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 25f; color = 0xFF65716B.toInt(); this.typeface = typeface }
        val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 25f; color = 0xFF18231F.toInt(); this.typeface = typeface }
        var y = 48f
        fun drawRtl(text: String, paint: TextPaint, top: Float, w: Int = width - 120): Int {
            val l = StaticLayout.Builder.obtain(text, 0, text.length, paint, w).setAlignment(Layout.Alignment.ALIGN_OPPOSITE).setIncludePad(false).build()
            canvas.save(); canvas.translate(60f, top); l.draw(canvas); canvas.restore(); return l.height
        }
        y += drawRtl(title, titlePaint, y) + 10
        y += drawRtl(subtitle, subPaint, y) + 22
        val headerLine = table.headers.joinToString("  |  ")
        y += drawRtl(headerLine, rowPaint, y) + 10
        canvas.drawLine(60f, y, width - 60f, y, Paint().apply { color = 0xFF2F8B69.toInt(); strokeWidth = 3f }); y += 12
        table.rows.take(120).forEach { row ->
            val line = row.joinToString("  |  ")
            y += drawRtl(line, rowPaint, y) + 8
            canvas.drawLine(60f, y, width - 60f, y, Paint().apply { color = 0xFFE4EBE7.toInt(); strokeWidth = 1f }); y += 8
        }
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 96, it) } ?: error("تعذر فتح ملف الصورة للحفظ")
        bitmap.recycle()
    }

    fun shareText(context: Context, table: Table, title: String, subtitle: String) {
        val body = buildString {
            appendLine(title)
            appendLine(subtitle)
            table.rows.take(20).forEach { row -> appendLine(row.joinToString(" · ")) }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير حلقتي"))
    }

    private fun activityLabel(v: String) = when (v) {
        "new_hifz" -> "حفظ"
        "review_near" -> "مراجعة قريبة"
        "review_far" -> "مراجعة بعيدة"
        "sard" -> "سرد"
        "tathbit" -> "تثبيت"
        "tilawah" -> "تلاوة"
        "tajweed" -> "تجويد"
        "test" -> "اختبار"
        else -> v.ifBlank { "—" }
    }
    private fun evaluationLabel(v: String) = when (v) {
        "excellent" -> "ممتاز"
        "very_good" -> "جيد جدًا"
        "good" -> "جيد"
        "repeat" -> "إعادة"
        else -> v.ifBlank { "—" }
    }
    private fun clean(v: Double) = if (kotlin.math.abs(v - v.toInt()) < .0001) v.toInt().toString() else "%.2f".format(java.util.Locale.US, v)
    private fun columnName(index: Int): String { var n=index; val out=StringBuilder(); while(n>0){ n--; out.append(('A'.code+n%26).toChar()); n/=26 }; return out.reverse().toString() }
    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun html(s: String) = xml(s).replace("'", "&#39;")
}
