import com.mohammedsamir.halaqati.documents.RecitationMatrix as M

fun main() {
    val e = listOf(
        M.Entry("a", "أحمد", "2026-10-04", "hifz", "سورة الأحزاب 51 ← 73", 3.0),
        M.Entry("a", "أحمد", "2026-10-04", "review", "سورة النجم 1 ← 20", 2.0),
        M.Entry("a", "أحمد", "2026-10-05", "sard", "x", 10.0, 100, 110),
        M.Entry("a", "أحمد", "2026-10-06", "sard", "x", 10.0, 105, 115),
        M.Entry("b", "بلال", "2026-10-05", "hifz", "سورة ق 1 ← 18", 1.0),
    )
    val abs = listOf(M.Absence("b", "بلال", "2026-10-04", false), M.Absence("c", "جميل", "2026-10-07", true), M.Absence("a", "أحمد", "2026-10-04", false))
    val m = M.build(e, abs, listOf("d" to "داود"))
    check(m.days == listOf("2026-10-04", "2026-10-05", "2026-10-06", "2026-10-07")) { m.days }
    check(m.rows.map { it.name } == listOf("أحمد", "بلال", "جميل", "داود")) { m.rows.map { it.name } }
    val a = m.rows[0]
    check(a.cells["2026-10-04"]!!.size == 2)
    check(a.cells["2026-10-04"]!![0] == ("hifz" to "الأحزاب 51 ← 73")) { a.cells["2026-10-04"]!![0] }
    check(a.absent.isEmpty()) { "a day with a recitation is not shown as absent" }
    check(m.rows[1].absent["2026-10-04"] == "غ")
    check(m.rows[2].absent["2026-10-07"] == "ع")
    check(m.rows[3].total == 0.0 && m.rows[3].cells.isEmpty())
    // total: hifz 3 + review 2 + sard span 100..115 = 16
    check(a.total == 21.0) { a.total }
    check(M.blocks(m.days, 3) == listOf(m.days.take(3), m.days.drop(3)))
    check(M.blocks(emptyList(), 7).isEmpty())
    check(M.weekdayAr("2026-10-04") == "الأحد") { M.weekdayAr("2026-10-04") }
    check(M.weekStart("2026-10-07") == "2026-10-04") { M.weekStart("2026-10-07") }
    check(M.weekStart("2026-10-04") == "2026-10-04")
    check(M.weekStart("2026-10-10") == "2026-10-04") { M.weekStart("2026-10-10") }
    val w = M.weekly(e)
    check(w.weekStarts == listOf("2026-10-04"))
    val wa = w.rows.first { it.studentId == "a" }.total
    check(wa.hifz == 3.0 && wa.review == 2.0 && wa.sard == 16.0) { wa }
    println("RecitationMatrix self-tests passed")
}
