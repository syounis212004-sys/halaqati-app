import com.mohammedsamir.halaqati.plans.SmartPlanEngine
import com.mohammedsamir.halaqati.plans.SmartPlanEngine.Event
import com.mohammedsamir.halaqati.plans.SmartPlanEngine.Plan
import com.mohammedsamir.halaqati.plans.SmartPlanEngine.Recitation
import com.mohammedsamir.halaqati.plans.SmartPlanEngine.Student
import java.time.LocalDate

private fun plan(overrides: (Plan) -> Plan = { it }): Plan = overrides(
    Plan(
        id = "plan-1",
        halaqaId = "halaqa-1",
        studentId = "student-1",
        targetPages = 10.0,
        startDate = LocalDate.parse("2026-08-30"),
        endDate = LocalDate.parse("2026-09-03"),
        scheduleWeekdays = setOf(0, 1, 2, 3, 4),
        milestoneMode = "none",
    )
)

private fun rec(
    start: Int,
    end: Int = start,
    evaluation: String? = "excellent",
    date: String = "2026-08-30",
    type: String = "new_hifz",
    id: String = "$start-$end-$date",
    mistakes: Int = 0,
    prompts: Int = 0,
    taggedPlanId: String? = null,
): Recitation = Recitation(
    id = id,
    studentId = "student-1",
    activityType = type,
    date = LocalDate.parse(date),
    startPage = start,
    endPage = end,
    pagesCount = (end - start + 1).toDouble(),
    evaluation = evaluation,
    mistakes = mistakes,
    prompts = prompts,
    taggedPlanId = taggedPlanId,
    createdAt = "${date}T12:00:00Z",
)

fun main() {
    // RC36: Sard is the continuous stretch from the first ayah to the last ayah reached; reciting the same ayahs again on
    // another day must not add pages. (Stub line provider: one line per ayah, 15 lines = 1 page.)
    run {
        SmartPlanEngine.spanLineKeyProvider = { from, to, _ ->
            if (from.surah != to.surah) null else (from.ayah..to.ayah).map { "${from.surah}:$it" }
        }
        try {
            val sard = plan { it.copy(planType = "sard", targetPages = 25.0) }
            fun r(id: String, date: String, endAyah: Int) = Recitation(
                id = id, studentId = "student-1", activityType = "sard", date = LocalDate.parse(date),
                startSurah = 50, startAyah = 1, endSurah = 50, endAyah = endAyah,
                startPage = 10, endPage = 11, pagesCount = endAyah / 15.0, evaluation = "excellent", mistakes = 0, prompts = 0,
                taggedPlanId = null, createdAt = "${date}T12:00:00Z",
            )
            val rows = listOf(r("a", "2026-08-30", 30), r("b", "2026-08-31", 30), r("c", "2026-09-01", 45))
            val p = SmartPlanEngine.calculateProgress(sard, "student-1", rows, today = LocalDate.parse("2026-09-02"))
            check(Math.abs(p.completed - 3.0) < 0.01) { "repeated sard must count once: expected 3.0 but was ${p.completed}" }
        } finally {
            SmartPlanEngine.spanLineKeyProvider = null
        }
    }
    // RC34: a Sard recited across surahs can store the same start/end page; progress must follow pages_count, not page sets.
    run {
        val sard = plan { it.copy(planType = "sard", targetPages = 25.0, rangeStartPage = 500, rangeEndPage = 400) }
        val r = Recitation(
            id = "sard-1", studentId = "student-1", activityType = "sard", date = LocalDate.parse("2026-08-31"),
            startPage = 467, endPage = 467, pagesCount = 18.53, evaluation = "excellent", mistakes = 0, prompts = 0,
            taggedPlanId = null, createdAt = "2026-08-31T12:00:00Z",
        )
        val p = SmartPlanEngine.calculateProgress(sard, "student-1", listOf(r), today = LocalDate.parse("2026-09-01"))
        check(Math.abs(p.completed - 18.53) < 0.01) { "sard completed should be 18.53 but was ${p.completed}" }
    }
    run {
        val p = SmartPlanEngine.calculateProgress(plan(), "student-1", today = LocalDate.parse("2026-09-01"))
        check(p.originalDaily == 2.0) { p }
        check(p.deficit == 4.0) { p }
        check(p.dailyCap == 3.0) { p }
        check(p.assignment.pages == 3.0) { p }
    }
    run {
        val p = SmartPlanEngine.calculateProgress(
            plan(),
            "student-1",
            listOf(rec(1, 2, taggedPlanId = "null", id = "legacy-json-null")),
            today = LocalDate.parse("2026-08-30"),
        )
        check(p.completed == 2.0) { "legacy smart_plan_id=\"null\" must not shadow valid activity/phase progress: $p" }
    }
    run {
        val reversePages = plan {
            it.copy(
                targetPages = 3.0,
                rangeStartPage = 500,
                rangeEndPage = 498,
                startDate = LocalDate.parse("2026-08-30"),
                endDate = LocalDate.parse("2026-08-30"),
                settings = it.settings.copy(routeDirection = "reverse_surahs_forward_ayahs"),
            )
        }
        val p = SmartPlanEngine.calculateProgress(reversePages, "student-1", today = LocalDate.parse("2026-08-30"))
        check(p.assignment.startPage == 500) { p }
        check(p.assignment.endPage == 498) { p }
    }
    run {
        val slow = plan { it.copy(targetPages = 2.0, rangeStartPage = 100, rangeEndPage = 101) }
        val recitations = mutableListOf<Recitation>()
        val assignments = mutableListOf<Double>()
        var nextPage = 100
        listOf("2026-08-30", "2026-08-31", "2026-09-01", "2026-09-02", "2026-09-03").forEach { day ->
            val due = SmartPlanEngine.calculateProgress(slow, "student-1", recitations, today = LocalDate.parse(day)).assignment.pages
            assignments += due
            if (due > 0.0) recitations += rec(nextPage++, date = day, id = "slow-$day")
        }
        check(assignments.count { it > 0.0 } == 2) { assignments }
        check(assignments.sum() == 2.0) { assignments }
    }
    run {
        val ahmad = plan {
            it.copy(
                id = "ahmad-plan",
                targetPages = 27.0,
                rangeStartPage = 1,
                rangeEndPage = 27,
                startDate = LocalDate.parse("2026-08-31"),
                endDate = LocalDate.parse("2026-09-30"),
            )
        }
        val rows = listOf(rec(1, 2, date = "2026-08-31", id = "ahmad-1"), rec(3, 4, date = "2026-08-31", id = "ahmad-2"))
        val p = SmartPlanEngine.calculateProgress(ahmad, "student-1", rows, today = LocalDate.parse("2026-08-31"))
        check(SmartPlanEngine.planDates(ahmad).size == 23)
        check(p.originalDaily == 1.17) { p }
        check(p.dailyCap == 2.0) { p }
        check(p.plannedToday == 1.0) { p }
        check(p.dailyTarget == 1.0) { p }
        check(p.assignment.pages == 0.0) { p }
        check(p.completedToday == 4.0) { p }
        check(p.completed == 4.0) { p }
        check(p.remaining == 23.0) { p }
        check(p.percentage == 14.8) { p }
        check(p.pace == "ahead") { p }
    }
    run {
        val scoped = plan { it.copy(targetPages = 4.0, rangeStartPage = 10, rangeEndPage = 13) }
        val p = SmartPlanEngine.calculateProgress(
            scoped,
            "student-1",
            listOf(rec(10, 11, id = "first"), rec(10, 11, id = "dupe"), rec(1, 5, id = "outside")),
            today = LocalDate.parse("2026-08-30"),
        )
        check(p.completed == 2.0) { p }
        check(p.completedPages == listOf(10, 11)) { p }
    }
    run {
        val p = SmartPlanEngine.calculateProgress(
            plan { it.copy(targetPages = 20.0) },
            "student-1",
            today = LocalDate.parse("2026-09-03"),
        )
        check(p.originalDaily == 4.0) { p }
        check(p.dailyCap == 6.0) { p }
        check(p.assignment.pages == 6.0) { p }
        check(!p.extensionNeeded) { p }
        check(p.suggestedEndDate == LocalDate.parse("2026-09-03")) { p }
    }
    run {
        val scoped = plan { it.copy(targetPages = 4.0, rangeStartPage = 221, rangeEndPage = 224) }
        val p = SmartPlanEngine.calculateProgress(
            scoped,
            "student-1",
            listOf(rec(221, 222), rec(223, 224, evaluation = "repeat", id = "failed")),
            today = LocalDate.parse("2026-08-31"),
        )
        check(p.completed == 2.0) { p }
        check(p.retryPages == listOf(223, 224)) { p }
        check(p.assignment.kind == "tathbit") { p }
        check(p.assignment.blockedByRetry) { p }
        check(p.assignment.retryRanges == listOf(SmartPlanEngine.PageRange(223, 224))) { p }
    }
    run {
        val scoped = plan { it.copy(targetPages = 2.0, rangeStartPage = 10, rangeEndPage = 11) }
        check(SmartPlanEngine.isPassing(rec(10, 11, evaluation = null, mistakes = 1), scoped))
        check(!SmartPlanEngine.isPassing(rec(10, 11, evaluation = null, mistakes = 5), scoped))
    }
    run {
        val scoped = plan { it.copy(targetPages = 4.0, rangeStartPage = 221, rangeEndPage = 224) }
        val p = SmartPlanEngine.calculateProgress(
            scoped,
            "student-1",
            listOf(
                rec(221, 222),
                rec(223, 224, evaluation = "repeat", id = "failed"),
                rec(223, 224, evaluation = "very_good", date = "2026-08-31", id = "pass"),
            ),
            today = LocalDate.parse("2026-08-31"),
        )
        check(p.completed == 4.0) { p }
        check(p.retryPages.isEmpty()) { p }
        check(!p.assignment.blockedByRetry) { p }
    }
    run {
        val scoped = plan {
            it.copy(
                targetPages = 30.0,
                rangeStartPage = 1,
                rangeEndPage = 30,
                milestoneMode = "juz",
                startDate = LocalDate.parse("2026-08-01"),
                endDate = LocalDate.parse("2026-09-30"),
            )
        }
        val rows = listOf(rec(1, 21))
        val p = SmartPlanEngine.calculateProgress(scoped, "student-1", rows, today = LocalDate.parse("2026-08-31"))
        check(p.assignment.kind == "hifz") { "Milestone must not replace daily hifz assignment: $p" }
        check(p.assignment.milestone == null) { "Smart-plan milestone exams are disabled: $p" }
        check(p.nextPage == 22) { p }
        val approved = SmartPlanEngine.calculateProgress(
            scoped,
            "student-1",
            rows,
            events = listOf(Event(type = "milestone_approved", milestoneKey = "juz:1")),
            today = LocalDate.parse("2026-08-31"),
        )
        check(approved.assignment.kind != "test") { approved }
        check(approved.nextPage == 22) { approved }
    }
    run {
        val student = Student(id = "student-1", halaqaId = "halaqa-1")
        val group = plan { it.copy(id = "group", scopeType = "halaqa", studentId = null, updatedAt = "2026-08-20T00:00:00Z") }
        val override = plan { it.copy(id = "override", parentPlanId = "group", updatedAt = "2026-08-19T00:00:00Z") }
        val ids = SmartPlanEngine.effectivePlans(listOf(group, override), student, LocalDate.parse("2026-08-31")).map { it.id }
        check(ids == listOf("override")) { ids }
    }
    run {
        val rows = (1..30).map { page -> rec(page, date = "2026-08-${page.toString().padStart(2, '0')}", id = "p$page") }
        val s = SmartPlanEngine.reviewSuggestions("student-1", rows, nearPages = 7, farPages = 10, today = LocalDate.parse("2026-08-31"))
        check(s.nearPages == (24..30).toList()) { s }
        check(s.farPages.size == 10) { s }
        check(s.farPages.none { it in s.nearPages }) { s }
    }
    run {
        val catchup = plan { it.copy(catchUpWeekday = 4, endDate = LocalDate.parse("2026-09-10")) }
        val p = SmartPlanEngine.calculateProgress(catchup, "student-1", today = LocalDate.parse("2026-09-03"))
        check(SmartPlanEngine.weekday(LocalDate.parse("2026-09-03")) == 4)
        check(p.assignment.pages > 0.0) { p }
        check(p.assignment.isCatchUp) { p }
        check(p.assignment.pages <= p.dailyCap) { p }
    }
    run {
        val p = SmartPlanEngine.calculateProgress(
            plan { it.copy(endDate = LocalDate.parse("2026-09-10")) },
            "student-1",
            today = LocalDate.parse("2026-09-02"),
        )
        check(p.consecutiveMissed >= 3) { p }
        check(p.health == "red") { p }
        check(p.paceLabel == "متأخر") { p }
    }
    println("SmartPlanEngine self-tests passed")
}
