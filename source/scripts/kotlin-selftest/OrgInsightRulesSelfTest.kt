import com.mohammedsamir.halaqati.plans.OrgInsightRules
import com.mohammedsamir.halaqati.plans.OrgInsightRules.Attendance

private fun check(ok: Boolean, msg: String) { if (!ok) { println("FAIL: $msg"); kotlin.system.exitProcess(1) } }

fun main() {
    val rows = listOf(
        Attendance("a", "2026-10-01", "present"),
        Attendance("a", "2026-10-03", "absent"),
        Attendance("a", "2026-10-05", "excused"),
        Attendance("a", "2026-10-07", "absent"),
        Attendance("a", "2026-10-09", "absent"),
        Attendance("b", "2026-10-07", "absent"),
        Attendance("b", "2026-10-09", "absent"),
        Attendance("c", "2026-10-03", "absent"), Attendance("c", "2026-10-05", "absent"), Attendance("c", "2026-10-07", "present"), Attendance("c", "2026-10-09", "absent"),
        Attendance("d", "2026-10-03", "absent"), Attendance("d", "2026-10-05", "absent"), Attendance("d", "2026-10-07", "absent", sessionCancelled = true), Attendance("d", "2026-10-09", "absent"),
    )
    val s = OrgInsightRules.absenceStreaks(rows)
    check(s.map { it.studentId } == listOf("a", "d"), "streak students ${s.map { it.studentId }}")
    check(s[0].count == 3 && s[0].lastAbsentDate == "2026-10-09", "a streak 3 (excused skipped, present stops)")
    check(s[1].count == 3 || s[1].count == 2, "d cancelled ignored")
    check(OrgInsightRules.absenceStreaks(rows, 2).map { it.studentId }.toSet() == setOf("a", "b", "d"), "min 2")
    check(OrgInsightRules.guardianMessage("أحمد", 3, "حلقة النور").contains("3 جلسات") && OrgInsightRules.guardianMessage("أحمد", 3).contains("أحمد"), "message")

    val stats = OrgInsightRules.compare(
        listOf("h1", "h2"), mapOf("h1" to 10, "h2" to 5),
        listOf(OrgInsightRules.HalaqaSession("h1", "2026-10-01", false), OrgInsightRules.HalaqaSession("h1", "2026-10-02", true), OrgInsightRules.HalaqaSession("h2", "2026-10-01", false)),
        listOf(OrgInsightRules.HalaqaAttendance("h1", "present"), OrgInsightRules.HalaqaAttendance("h1", "absent"), OrgInsightRules.HalaqaAttendance("h1", "excused"), OrgInsightRules.HalaqaAttendance("h2", "present")),
        listOf(
            OrgInsightRules.HalaqaRecitation("h1", "new_hifz", 10.0, "excellent"),
            OrgInsightRules.HalaqaRecitation("h1", "review_near", 5.0, "repeat"),
            OrgInsightRules.HalaqaRecitation("h2", "new_hifz", 20.0, "good"),
        ),
    )
    val h1 = stats.first { it.halaqaId == "h1" }
    check(h1.sessions == 1 && h1.attendancePercent == 50 && h1.hifzPages == 10.0 && h1.reviewPages == 5.0, "h1 basics $h1")
    check(h1.qualityPercent == 68 && Math.abs(h1.pagesPerStudent - 1.5) < 1e-9, "h1 quality/ppp ${h1.qualityPercent}")
    // RC29: quality is average safety, never "share that is not weak": recitations that are all merely "good" must not read 100%.
    check(OrgInsightRules.qualityPercent(listOf(
        OrgInsightRules.HalaqaRecitation("h", "new_hifz", 1.0, "good", 70.0),
        OrgInsightRules.HalaqaRecitation("h", "new_hifz", 1.0, "good", 80.0),
    )) == 75, "stored safety is averaged")
    check(OrgInsightRules.qualityPercent(listOf(OrgInsightRules.HalaqaRecitation("h", "new_hifz", 1.0, "good"))) == 75, "evaluation fallback is not 100")
    check(OrgInsightRules.qualityPercent(listOf(OrgInsightRules.HalaqaRecitation("h", "new_hifz", 1.0, "excellent", 96.5))) == 97, "rounded")
    check(OrgInsightRules.qualityPercent(listOf(
        OrgInsightRules.HalaqaRecitation("h", "new_hifz", 1.0, "good", 80.0),
        OrgInsightRules.HalaqaRecitation("h", "sard", 0.0, "not_rated", 0.0),
    )) == 80, "placeholder rows do not pull quality down")
    check(OrgInsightRules.qualityPercent(emptyList()) == null, "no recitations -> null")
    check(stats.first().halaqaId == "h2", "sorted by pages per student")
    check(OrgInsightRules.compare(listOf("x"), emptyMap(), emptyList(), emptyList(), emptyList()).single().attendancePercent == null, "no data -> null")
    println("OrgInsightRulesSelfTest PASS")
}
