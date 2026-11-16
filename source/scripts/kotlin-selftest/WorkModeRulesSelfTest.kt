import com.mohammedsamir.halaqati.plans.SessionQuickActivityRules
import com.mohammedsamir.halaqati.plans.WorkMode
import com.mohammedsamir.halaqati.plans.WorkModeRules

private fun check(ok: Boolean, msg: String) { if (!ok) { println("FAIL: $msg"); kotlin.system.exitProcess(1) } }

fun main() {
    check(WorkModeRules.modeOf(emptyList()) == WorkMode.REGULAR, "empty -> regular")
    check(WorkModeRules.modeOf(listOf("regular", null)) == WorkMode.REGULAR, "regular/legacy")
    check(WorkModeRules.modeOf(listOf("tilawah", "tajweed", "course")) == WorkMode.PROGRAM, "all programs")
    check(WorkModeRules.modeOf(listOf("regular", "tilawah")) == WorkMode.MIXED, "mixed")
    check(WorkModeRules.effective("tilawah", true, listOf("regular", "tilawah")) == WorkMode.PROGRAM, "selected program")
    check(WorkModeRules.effective("regular", true, listOf("regular", "tilawah")) == WorkMode.REGULAR, "selected regular")
    check(WorkModeRules.effective(null, false, listOf("regular", "tilawah")) == WorkMode.MIXED, "all halaqat mixed")
    check(WorkModeRules.resolveKind(WorkMode.MIXED, null) == WorkMode.REGULAR, "mixed default regular")
    check(WorkModeRules.resolveKind(WorkMode.MIXED, WorkMode.PROGRAM) == WorkMode.PROGRAM, "mixed choice program")
    check(WorkModeRules.resolveKind(WorkMode.PROGRAM, WorkMode.REGULAR) == WorkMode.PROGRAM, "fixed mode ignores choice")
    check(!WorkModeRules.showSmartPlans(WorkMode.PROGRAM) && WorkModeRules.showSmartPlans(WorkMode.REGULAR), "plans gating")

    // students
    check(WorkModeRules.studentBelongs(WorkMode.PROGRAM, "tilawah", emptyList()), "primary program student")
    check(WorkModeRules.studentBelongs(WorkMode.PROGRAM, "regular", listOf("tilawah")), "member of a program")
    check(!WorkModeRules.studentBelongs(WorkMode.PROGRAM, "regular", emptyList()), "regular-only student hidden in program mode")
    check(WorkModeRules.studentBelongs(WorkMode.REGULAR, "regular", listOf("tilawah")), "halaqa student who also joined a program stays in halaqa mode")
    check(!WorkModeRules.studentBelongs(WorkMode.REGULAR, "tilawah", emptyList()), "program-only student hidden in halaqa mode")

    // certificates
    val prog = WorkModeRules.certificateTypes(WorkMode.PROGRAM).map { it.first }
    check(prog == listOf("tajweed", "excellence", "rank", "ideal"), "program certificate types $prog")
    check(WorkModeRules.certificateTypes(WorkMode.PROGRAM).none { it.first in setOf("sard", "khatam", "monthly") }, "no sard/khatam in programs")
    check(WorkModeRules.certificateTypes(WorkMode.REGULAR).map { it.first } == listOf("sard", "khatam", "monthly", "ideal", "rank"), "regular types unchanged")

    // program ranking: score, then attendance, recitations, points
    val ranked = WorkModeRules.rankProgram(listOf(
        WorkModeRules.ProgramRankInput("a", listOf(90.0, 80.0), 5, 4, 10),
        WorkModeRules.ProgramRankInput("b", listOf(85.0), 9, 9, 99),
        WorkModeRules.ProgramRankInput("c", listOf(), 20, 20, 500),
        WorkModeRules.ProgramRankInput("d", listOf(85.0), 9, 9, 120),
    )).map { it.studentId }
    check(ranked == listOf("d", "b", "a", "c"), "exact rank order $ranked")
    check(WorkModeRules.programScore(listOf(150.0, -3.0)) == null, "out-of-range scores ignored")

    // quick buttons
    check(SessionQuickActivityRules.activities(false, true, emptySet()).map { it.first } == listOf("tathbit"), "tathbit only")
    check(SessionQuickActivityRules.activities(true, false, emptySet()).map { it.first } == listOf("tilawah", "test"), "program tilawah/test")
    check(SessionQuickActivityRules.activities(false, false, setOf("hifz")).map { it.first } == listOf("new_hifz", "review_near"), "no sard when plan lacks it")
    check(SessionQuickActivityRules.activities(false, false, setOf("hifz", "sard")).map { it.first } == listOf("new_hifz", "review_near", "sard"), "sard when planned")
    check(SessionQuickActivityRules.activities(false, false, emptySet()).map { it.first } == listOf("new_hifz", "review_near", "sard"), "no plan -> sard offered")
    check(SessionQuickActivityRules.activities(false, false, emptySet()).none { it.first == "tilawah" }, "no tilawah for hifz")
    println("WorkModeRulesSelfTest PASS")
}
