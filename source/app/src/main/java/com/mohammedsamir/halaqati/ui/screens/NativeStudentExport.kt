package com.mohammedsamir.halaqati.ui.screens

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.mohammedsamir.halaqati.R
import com.mohammedsamir.halaqati.model.Student
import java.io.BufferedOutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Native replacement for the v2.44 student directory Excel / Word / PDF exports. */
object NativeStudentExport {
    data class Row(
        val index: Int,
        val name: String,
        val halaqa: String,
        val category: String,
        val track: String,
        val status: String,
        val nationalId: String,
        val birth: String,
        val grade: String,
        val phone: String,
        val guardian: String,
        val guardianPhone: String,
        val guardianEmail: String,
    )

    val headers = listOf(
        "#", "الاسم", "الحلقة", "الفئة", "المسار", "الحالة", "الهوية",
        "تاريخ الميلاد", "المرحلة", "الهاتف", "ولي الأمر", "جوال ولي الأمر", "بريد ولي الأمر",
    )

    fun rows(students: List<Student>, halaqas: Map<String, String>, categories: Map<String, String>): List<Row> =
        students.sortedBy { it.fullName }.mapIndexed { i, s ->
            Row(
                index = i + 1,
                name = s.fullName,
                halaqa = s.halaqaId?.let { halaqas[it] }.orEmpty(),
                category = s.categoryId?.let { categories[it] }.orEmpty(),
                track = if (s.track == "tathbit") "التثبيت" else "الحفظ العام",
                status = when (s.status) {
                    "active" -> "نشط"
                    "withdrawn" -> "منسحب / مؤرشف"
                    "suspended" -> "موقوف"
                    else -> s.status
                },
                nationalId = s.nationalId.orEmpty(),
                birth = s.dateOfBirth.orEmpty(),
                grade = s.schoolGrade.orEmpty(),
                phone = s.phone.orEmpty(),
                guardian = s.guardianName.orEmpty(),
                guardianPhone = s.guardianPhone.orEmpty(),
                guardianEmail = s.guardianEmail.orEmpty(),
            )
        }

    fun exportXlsx(context: Context, uri: Uri, rows: List<Row>) {
        context.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                fun entry(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
                entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
                entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="الطلاب" sheetId="1" r:id="rId1"/></sheets></workbook>""")
                entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
                val all = buildList {
                    add(headers)
                    rows.forEach { add(rowValues(it)) }
                }
                val sheetRows = all.mapIndexed { r, cells ->
                    val cellXml = cells.mapIndexed { c, value ->
                        val ref = "${columnName(c + 1)}${r + 1}"
                        "<c r=\"$ref\" t=\"inlineStr\"><is><t>${xml(value)}</t></is></c>"
                    }.joinToString("")
                    "<row r=\"${r + 1}\">$cellXml</row>"
                }.joinToString("")
                entry("xl/worksheets/sheet1.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0" rightToLeft="1"/></sheetViews><cols><col min="1" max="1" width="6" customWidth="1"/><col min="2" max="13" width="22" customWidth="1"/></cols><sheetData>$sheetRows</sheetData></worksheet>""")
            }
        } ?: error("تعذر فتح ملف Excel للحفظ")
    }

    fun exportWord(context: Context, uri: Uri, rows: List<Row>) {
        val head = headers.joinToString("") { "<th>${html(it)}</th>" }
        val body = rows.joinToString("") { row -> rowValues(row).joinToString("", "<tr>", "</tr>") { "<td>${html(it)}</td>" } }
        val doc = """<!doctype html><html dir="rtl"><head><meta charset="UTF-8"><style>body{font-family:Cairo,Tahoma,Arial,sans-serif;direction:rtl}h1{text-align:center;color:#0D3B2F}table{width:100%;border-collapse:collapse;font-size:11pt}th,td{border:1px solid #bbb;padding:7px;text-align:right}th{background:#E9F3EE;color:#0D3B2F}</style></head><body><h1>دليل طلاب حلقتي</h1><table><thead><tr>$head</tr></thead><tbody>$body</tbody></table></body></html>"""
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer -> writer.write('\ufeff'.toString()); writer.write(doc) }
        } ?: error("تعذر فتح ملف Word للحفظ")
    }

    fun exportPdf(context: Context, uri: Uri, rows: List<Row>) {
        val document = PdfDocument()
        val pageWidth = 842 // A4 landscape points
        val pageHeight = 595
        val margin = 34
        val typeface = ResourcesCompat.getFont(context, R.font.cairo)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = 0xFF0D3B2F.toInt(); this.typeface = typeface }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF17211D.toInt(); this.typeface = typeface }
        val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = 0xFF52615A.toInt(); this.typeface = typeface }
        var pageNo = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create())
        var y = 26f

        fun layout(text: String, paint: TextPaint, x: Float, width: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_OPPOSITE): Int {
            val l = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(align).setIncludePad(false).build()
            page.canvas.save(); page.canvas.translate(x, y); l.draw(page.canvas); page.canvas.restore()
            return l.height
        }
        fun header() {
            y = 26f
            y += layout("دليل طلاب حلقتي", titlePaint, margin.toFloat(), pageWidth - margin * 2) + 8
            y += layout("الاسم · الحلقة · الفئة · المسار · الحالة · الهوية · بيانات التواصل", mutedPaint, margin.toFloat(), pageWidth - margin * 2) + 10
            page.canvas.drawLine(margin.toFloat(), y, (pageWidth - margin).toFloat(), y, Paint().apply { color = 0xFF2F8B69.toInt(); strokeWidth = 1.5f })
            y += 10
        }
        fun finishAndNew() {
            document.finishPage(page)
            pageNo++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create())
            header()
        }
        header()
        rows.forEach { row ->
            if (y > pageHeight - 68) finishAndNew()
            val first = "${row.index}. ${row.name}  |  ${row.halaqa}  |  ${row.category}  |  ${row.track}  |  ${row.status}"
            val second = listOf("هوية: ${row.nationalId}", "هاتف: ${row.phone}", "ولي الأمر: ${row.guardian}", "جوال ولي الأمر: ${row.guardianPhone}").filterNot { it.endsWith(": ") }.joinToString("   ·   ")
            y += layout(first, bodyPaint, margin.toFloat(), pageWidth - margin * 2) + 3
            if (second.isNotBlank()) y += layout(second, mutedPaint, margin.toFloat(), pageWidth - margin * 2) + 5
            page.canvas.drawLine(margin.toFloat(), y, (pageWidth - margin).toFloat(), y, Paint().apply { color = 0xFFE4EBE7.toInt(); strokeWidth = .7f })
            y += 7
        }
        document.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) } ?: error("تعذر فتح ملف PDF للحفظ")
        document.close()
    }

    private fun rowValues(r: Row) = listOf(
        r.index.toString(), r.name, r.halaqa, r.category, r.track, r.status, r.nationalId,
        r.birth, r.grade, r.phone, r.guardian, r.guardianPhone, r.guardianEmail,
    )

    private fun columnName(index: Int): String {
        var n = index
        val out = StringBuilder()
        while (n > 0) { n--; out.append(('A'.code + n % 26).toChar()); n /= 26 }
        return out.reverse().toString()
    }
    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun html(s: String) = xml(s).replace("'", "&#39;")
}
