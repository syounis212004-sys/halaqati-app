package com.mohammedsamir.halaqati.documents

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * RC43: the recitation log as a grid. One row per student, one column per session day; each cell lists what the student
 * recited that day (one line per kind: ح حفظ · م مراجعة · س سرد · ث تثبيت · ل تلاوة · ا اختبار) as a range only, and a
 * grey "غ" / "ع" marks a day the student was absent / excused. Days are split into blocks of [blocks] columns; every block
 * repeats the full student list. The total column follows the app-wide rule: sard is a span (highest end page minus
 * lowest start page + 1), everything else adds up pages.
 */
object RecitationMatrix {
    data class Entry(
        val studentId: String,
        val studentName: String,
        val date: String,
        val kind: String,
        val range: String,
        val pages: Double,
        val startPage: Int? = null,
        val endPage: Int? = null,
    )

    data class Absence(val studentId: String, val studentName: String, val date: String, val excused: Boolean)

    data class Row(
        val studentId: String,
        val name: String,
        val cells: Map<String, List<Pair<String, String>>>,
        val absent: Map<String, String>,
        val total: Double,
    )

    data class Matrix(val days: List<String>, val rows: List<Row>, val sessionsCount: Int, val absences: Int)

    data class WeekTotals(val hifz: Double = 0.0, val review: Double = 0.0, val sard: Double = 0.0, val tathbit: Double = 0.0) {
        val all: Double get() = hifz + review + sard + tathbit
    }
    data class WeekRow(val studentId: String, val name: String, val weeks: Map<String, WeekTotals>, val total: WeekTotals)
    data class Weekly(val weekStarts: List<String>, val rows: List<WeekRow>)

    fun kindOf(activityType: String): String = when (activityType) {
        "new_hifz", "hifz" -> "hifz"
        "review_near", "review_far", "review" -> "review"
        else -> activityType
    }

    fun letter(kind: String): String = when (kind) {
        "hifz" -> "ح"; "review" -> "م"; "sard" -> "س"; "tathbit" -> "ث"; "tilawah" -> "ل"; "tajweed" -> "ج"; "test" -> "ا"; else -> "•"
    }

    fun shortRange(range: String): String = range.replace("سورة ", "").trim()

    fun weekdayAr(date: String): String = runCatching {
        when (LocalDate.parse(date).dayOfWeek) {
            DayOfWeek.SATURDAY -> "السبت"; DayOfWeek.SUNDAY -> "الأحد"; DayOfWeek.MONDAY -> "الاثنين"
            DayOfWeek.TUESDAY -> "الثلاثاء"; DayOfWeek.WEDNESDAY -> "الأربعاء"; DayOfWeek.THURSDAY -> "الخميس"
            DayOfWeek.FRIDAY -> "الجمعة"
        }
    }.getOrDefault("")

    /** Sunday-based week start of a date (the app's week begins on Sunday). */
    fun weekStart(date: String): String = runCatching {
        val d = LocalDate.parse(date)
        d.minusDays((d.dayOfWeek.value % 7).toLong()).toString()
    }.getOrDefault(date)

    private fun sardPages(sard: List<Entry>): Double =
        if (sard.any { it.startPage != null && it.endPage != null }) {
            val starts = sard.mapNotNull { it.startPage ?: it.endPage }
            val ends = sard.mapNotNull { it.endPage ?: it.startPage }
            max(0, (ends.maxOrNull() ?: 0) - (starts.minOrNull() ?: 0) + 1).toDouble()
        } else sard.sumOf { it.pages }

    fun total(entries: List<Entry>): Double =
        entries.filter { it.kind != "sard" }.sumOf { it.pages } + sardPages(entries.filter { it.kind == "sard" })

    private fun totals(entries: List<Entry>) = WeekTotals(
        hifz = entries.filter { it.kind == "hifz" }.sumOf { it.pages },
        review = entries.filter { it.kind == "review" }.sumOf { it.pages },
        sard = sardPages(entries.filter { it.kind == "sard" }),
        tathbit = entries.filter { it.kind == "tathbit" }.sumOf { it.pages },
    )

    /**
     * @param roster students that must appear even when they recited nothing (id to name); pass empty to list only active students.
     */
    fun build(entries: List<Entry>, absences: List<Absence> = emptyList(), roster: List<Pair<String, String>> = emptyList()): Matrix {
        val days = (entries.map { it.date } + absences.map { it.date }).filter { it.isNotBlank() }.distinct().sorted()
        val names = LinkedHashMap<String, String>()
        roster.forEach { (id, name) -> names[id] = name }
        entries.forEach { names.putIfAbsent(it.studentId, it.studentName) }
        absences.forEach { names.putIfAbsent(it.studentId, it.studentName) }
        val byStudent = entries.groupBy { it.studentId }
        val absByStudent = absences.groupBy { it.studentId }
        val rows = names.map { (id, name) ->
            val list = byStudent[id].orEmpty()
            val cells = list.groupBy { it.date }.mapValues { (_, dayEntries) ->
                dayEntries.map { e -> e.kind to shortRange(e.range) }
            }
            val absent = absByStudent[id].orEmpty().filter { it.date !in cells }.associate { it.date to (if (it.excused) "ع" else "غ") }
            Row(id, name, cells, absent, total(list))
        }.sortedBy { it.name }
        return Matrix(days, rows, entries.map { it.date }.distinct().size, absences.size)
    }

    fun blocks(days: List<String>, daysPerPage: Int): List<List<String>> =
        if (days.isEmpty()) emptyList() else days.chunked(max(1, min(daysPerPage, days.size.coerceAtLeast(1))))

    fun weekly(entries: List<Entry>): Weekly {
        val weekStarts = entries.map { weekStart(it.date) }.distinct().sorted()
        val rows = entries.groupBy { it.studentId }.map { (id, list) ->
            val perWeek = list.groupBy { weekStart(it.date) }.mapValues { (_, w) -> totals(w) }
            WeekRow(id, list.first().studentName, perWeek, totals(list))
        }.sortedBy { it.name }
        return Weekly(weekStarts, rows)
    }
}
