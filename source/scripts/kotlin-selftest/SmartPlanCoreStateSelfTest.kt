import com.mohammedsamir.halaqati.plans.SmartPlanCoreStateEngine
import com.mohammedsamir.halaqati.plans.SmartPlanEngine
import java.time.LocalDate

private fun rec(
    id: String,
    type: String,
    date: String,
    endSurah: Int,
    endAyah: Int,
    startPage: Int,
    endPage: Int = startPage,
    evaluation: String = "excellent",
) = SmartPlanEngine.Recitation(
    id = id,
    studentId = "student-1",
    activityType = type,
    date = LocalDate.parse(date),
    startPage = startPage,
    endPage = endPage,
    endSurah = endSurah,
    endAyah = endAyah,
    pagesCount = kotlin.math.abs(endPage - startPage).toDouble() + 1.0,
    evaluation = evaluation,
    createdAt = "${date}T12:00:00Z",
)

fun main() {
    val plan = SmartPlanEngine.Plan(
        id = "rc91-plan",
        halaqaId = "halaqa-1",
        studentId = "student-1",
        planType = "hifz",
        targetPages = 10.0,
        startDate = LocalDate.parse("2026-09-20"),
        endDate = LocalDate.parse("2026-09-24"),
        scheduleWeekdays = setOf(0, 1, 2, 3, 4),
        rangeStartPage = 580,
        rangeEndPage = 571,
        milestoneMode = "none",
    )
    val rows = listOf(
        rec("h1", "new_hifz", "2026-09-20", 114, 6, 580, 579),
        rec("h2", "new_hifz", "2026-09-21", 113, 5, 578),
        rec("sard", "sard", "2026-09-22", 78, 40, 570),
    )
    val today = LocalDate.parse("2026-09-22")
    val progress = SmartPlanEngine.calculateProgress(plan, "student-1", rows, today = today)
    check(progress.weekly.weekStart == LocalDate.parse("2026-09-20")) { progress.weekly }
    check(progress.weekly.target == 10.0) { progress.weekly }
    check(progress.weekly.requiredByToday == 6.0) { progress.weekly }
    check(progress.weekly.completed == 3.0) { progress.weekly }

    val state = SmartPlanCoreStateEngine.build(plan, progress, rows, today, weeklyTargetOverride = 40.0)
    check(state.weekly.target == 40.0) { state.weekly }
    check(state.weekly.requiredByToday == 24.0) { state.weekly }
    check(state.weekly.completed == 3.0) { state.weekly }
    check(state.lastMemorizationAnchor?.recitationId == "h2") { state.lastMemorizationAnchor.toString() }
    check(state.status == SmartPlanCoreStateEngine.StatusCode.BEHIND) { state }

    val completeRows = listOf(rec("all", "new_hifz", "2026-09-20", 113, 5, 580, 571))
    val completeProgress = SmartPlanEngine.calculateProgress(plan, "student-1", completeRows, today = LocalDate.parse("2026-09-20"))
    val completeState = SmartPlanCoreStateEngine.build(plan, completeProgress, completeRows, LocalDate.parse("2026-09-20"))
    check(completeState.remainingPages == 0.0) { completeState }
    check(completeState.status == SmartPlanCoreStateEngine.StatusCode.COMPLETE_EARLY) { completeState }
    check(completeState.completedEarly) { completeState }
    check(completeState.daysRemainingInPlan == 4) { completeState }
    check(completeState.nextAssignment == null) { completeState }

    // RC9.3 regression: once the monthly target is exceeded, actual completed
    // pages must remain visible so surplus mode can report the extra work.
    val surplusPlan = plan.copy(targetPages = 5.0)
    val surplusRows = listOf(rec("surplus", "new_hifz", "2026-09-20", 113, 5, 580, 574))
    val surplusProgress = SmartPlanEngine.calculateProgress(
        surplusPlan, "student-1", surplusRows, today = LocalDate.parse("2026-09-20")
    )
    val surplusState = SmartPlanCoreStateEngine.build(
        surplusPlan, surplusProgress, surplusRows, LocalDate.parse("2026-09-20")
    )
    check(surplusState.completedPages == 7.0) { surplusState }
    check(surplusState.surplusPages == 2.0) { surplusState }
    check(surplusState.remainingPages == 0.0) { surplusState }

    println("SmartPlanCoreState RC9.1 self-tests passed")
}
