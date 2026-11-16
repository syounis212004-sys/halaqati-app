import com.mohammedsamir.halaqati.plans.PlanRangeRebaseRules
import com.mohammedsamir.halaqati.plans.PlanRangeRebaseRules.Status
import com.mohammedsamir.halaqati.plans.SmartPlanEngine
import com.mohammedsamir.halaqati.quran.QuranLocation
import java.time.LocalDate

private fun rec(date: String, ss: Int, sa: Int, es: Int, ea: Int, eval: String = "excellent", type: String = "new_hifz") =
    SmartPlanEngine.Recitation(
        id = "$date-$es-$ea", studentId = "s", activityType = type, date = LocalDate.parse(date),
        startSurah = ss, startAyah = sa, endSurah = es, endAyah = ea, evaluation = eval, createdAt = date,
    )

private fun check(ok: Boolean, msg: String) { if (!ok) { println("FAIL: $msg"); kotlin.system.exitProcess(1) } }

fun main() {
    val rev = "reverse_surahs_forward_ayahs"
    val fwd = "forward_surahs_forward_ayahs"
    val history = listOf(
        rec("2026-09-21", 24, 59, 24, 64),
        rec("2026-09-22", 23, 1, 23, 27),
        rec("2026-10-04", 23, 28, 23, 74),
        rec("2026-10-11", 22, 56, 22, 72),
        rec("2026-10-12", 22, 73, 22, 78, eval = "repeat"),
    )
    val start = LocalDate.parse("2026-10-01")
    // stale end: student at 22:72 beyond the carried range end 22:27 (reverse route)
    val stale = PlanRangeRebaseRules.diagnose(history, start, rev, QuranLocation(26, 184), QuranLocation(22, 27))
    check(stale.status == Status.STALE_END, "stale end detected")
    check(stale.expectedStart == QuranLocation(23, 28), "expected start is right after the last pre-month anchor, got ${stale.expectedStart}")
    check(stale.latestEnd == QuranLocation(22, 72), "failed (repeat) recitation must not move the anchor")
    // healthy: range end ahead of the student
    val ok = PlanRangeRebaseRules.diagnose(history, start, rev, QuranLocation(23, 28), QuranLocation(17, 50))
    check(ok.status == Status.OK, "healthy plan stays OK, got ${ok.status}")
    // start mismatch only
    val mismatch = PlanRangeRebaseRules.diagnose(history, start, rev, QuranLocation(26, 184), QuranLocation(17, 50))
    check(mismatch.status == Status.START_MISMATCH, "start mismatch flagged")
    // no end configured: never stale
    val open = PlanRangeRebaseRules.diagnose(history, start, rev, null, null)
    check(open.status == Status.OK, "open ended plan OK")
    // forward route
    val f = listOf(rec("2026-09-30", 2, 1, 2, 50), rec("2026-10-05", 2, 51, 2, 120))
    val fs = PlanRangeRebaseRules.diagnose(f, start, fwd, QuranLocation(2, 1), QuranLocation(2, 100))
    check(fs.status == Status.STALE_END && fs.expectedStart == QuranLocation(2, 51), "forward route stale end, got $fs")
    // no hifz at all
    val none = PlanRangeRebaseRules.diagnose(emptyList(), start, rev, QuranLocation(26, 184), QuranLocation(22, 27))
    check(none.status == Status.OK, "no recitations -> nothing to rebase")
    // start-of-surah wrap: anchor at last ayah moves to next surah along route
    val wrap = listOf(rec("2026-09-30", 23, 1, 23, 118))
    check(PlanRangeRebaseRules.expectedStart(wrap, start, rev) == QuranLocation(22, 1), "reverse wrap goes to previous surah")
    // month first recitation when nothing before the month
    val newbie = listOf(rec("2026-10-03", 30, 1, 30, 20))
    check(PlanRangeRebaseRules.expectedStart(newbie, start, rev) == QuranLocation(30, 1), "falls back to first recitation start")
    check(PlanRangeRebaseRules.isUndersized(1, 45.0), "one line for 45 pages is undersized")
    check(PlanRangeRebaseRules.isUndersized(300, 45.0), "300 lines (20 pages) for 45 pages is undersized")
    check(!PlanRangeRebaseRules.isUndersized(675, 45.0), "range equal to target is fine")
    check(!PlanRangeRebaseRules.isUndersized(1200, 45.0), "range larger than target is never touched")
    check(!PlanRangeRebaseRules.isUndersized(340, 45.0), "range at half the target is left alone")
    check(!PlanRangeRebaseRules.isUndersized(null, 45.0), "unknown size is left alone")
    check(!PlanRangeRebaseRules.isUndersized(1, 0.0), "no target, nothing to compare")
    check(PlanRangeRebaseRules.emptyHifzDutyMessage(null, null, rev).contains("لا يوجد تسميع"), "message: no anchor")
    check(PlanRangeRebaseRules.emptyHifzDutyMessage(QuranLocation(22, 72), QuranLocation(22, 27), rev).contains("هدف الخطة"), "message: passing the range end is not an error")
    check(PlanRangeRebaseRules.emptyHifzDutyMessage(QuranLocation(22, 72), QuranLocation(17, 1), rev).contains("هدف الخطة"), "message: generic")
    check(PlanRangeRebaseRules.normalizePages(376, 332) == (332 to 376), "descending pages are stored ascending")
    check(PlanRangeRebaseRules.normalizePages(332, 376) == (332 to 376), "ascending pages stay")
    check(PlanRangeRebaseRules.normalizePages(null, 376) == (null to 376), "null side untouched")
        // RC27.3 regression: student finished the whole route (reverse hifz ends at 2:286)
    val term = QuranLocation(2, 286)
    val done = listOf(rec("2026-09-15", 2, 257, 2, 274), rec("2026-09-16", 2, 275, 2, 286))
    val complete = PlanRangeRebaseRules.diagnose(done, LocalDate.parse("2026-10-01"), rev, QuranLocation(2, 103), QuranLocation(2, 286), term)
    check(complete.status == Status.ROUTE_COMPLETE, "route complete detected, got ${complete.status}")
    check(PlanRangeRebaseRules.emptyHifzDutyMessage(QuranLocation(2, 286), QuranLocation(2, 286), rev, term).contains("أتمّ الطالب مسار الحفظ"), "complete message")
    // Student ahead inside the month: anchor 71:13 before the month, now at 55:9, plan end 66:10 -> still stale until the end is extended.
    val ahead = listOf(rec("2026-09-20", 71, 4, 71, 13), rec("2026-10-10", 55, 1, 55, 9))
    val d2 = PlanRangeRebaseRules.diagnose(ahead, LocalDate.parse("2026-10-01"), rev, QuranLocation(71, 14), QuranLocation(66, 10), term)
    check(d2.status == Status.STALE_END && d2.expectedStart == QuranLocation(71, 14), "ahead is stale: ${d2.status} ${d2.expectedStart}")
    // after the fix the end lies beyond 55:9, so the very next diagnosis must be OK (idempotent)
    val d3 = PlanRangeRebaseRules.diagnose(ahead, LocalDate.parse("2026-10-01"), rev, QuranLocation(71, 14), QuranLocation(50, 20), term)
    check(d3.status == Status.OK, "rebased range beyond progress is OK, got ${d3.status}")
    check(PlanRangeRebaseRules.rebasedLineCount(150, 600) == 750 && PlanRangeRebaseRules.rebasedLineCount(0, 0) == 1, "line count covers progress")
println("PlanRangeRebaseRules self-tests passed")
}
