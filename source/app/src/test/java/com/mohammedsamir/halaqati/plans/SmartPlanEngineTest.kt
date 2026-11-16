package com.mohammedsamir.halaqati.plans

import com.mohammedsamir.halaqati.plans.SmartPlanEngine.Event
import com.mohammedsamir.halaqati.plans.SmartPlanEngine.Plan
import com.mohammedsamir.halaqati.plans.SmartPlanEngine.Recitation
import com.mohammedsamir.halaqati.plans.SmartPlanEngine.Student
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SmartPlanEngineTest {
    private fun plan(overrides: (Plan) -> Plan = { it }) = overrides(
        Plan(
            id = "plan-1",
            halaqaId = "halaqa-1",
            studentId = "student-1",
            targetPages = 10.0,
            startDate = LocalDate.parse("2026-08-30"),
            endDate = LocalDate.parse("2026-09-03"),
            scheduleWeekdays = setOf(0, 1, 2, 3, 4),
            milestoneMode = "none",
        ),
    )

    private fun rec(
        start: Int,
        end: Int = start,
        evaluation: String? = "excellent",
        date: String = "2026-08-30",
        id: String = "$start-$end-$date",
        mistakes: Int = 0,
    ) = Recitation(
        id = id,
        studentId = "student-1",
        activityType = "new_hifz",
        date = LocalDate.parse(date),
        startPage = start,
        endPage = end,
        pagesCount = (end - start + 1).toDouble(),
        evaluation = evaluation,
        mistakes = mistakes,
        createdAt = "${date}T12:00:00Z",
    )

    @Test fun deficitSpreadRespectsCap() {
        val p = SmartPlanEngine.calculateProgress(plan(), "student-1", today = LocalDate.parse("2026-09-01"))
        assertEquals(2.0, p.originalDaily, 0.001)
        assertEquals(4.0, p.deficit, 0.001)
        assertEquals(3.0, p.dailyCap, 0.001)
        assertEquals(3.0, p.assignment.pages, 0.001)
    }

    @Test fun wholePageDistributionForSlowPlan() {
        val slow = plan { it.copy(targetPages = 2.0, rangeStartPage = 100, rangeEndPage = 101) }
        val rows = mutableListOf<Recitation>()
        val assignments = mutableListOf<Double>()
        var nextPage = 100
        listOf("2026-08-30", "2026-08-31", "2026-09-01", "2026-09-02", "2026-09-03").forEach { day ->
            val due = SmartPlanEngine.calculateProgress(slow, "student-1", rows, today = LocalDate.parse(day)).assignment.pages
            assignments += due
            if (due > 0) rows += rec(nextPage++, date = day, id = day)
        }
        assertEquals(2, assignments.count { it > 0 })
        assertEquals(2.0, assignments.sum(), 0.001)
    }

    @Test fun exactPagesDontDoubleCount() {
        val scoped = plan { it.copy(targetPages = 4.0, rangeStartPage = 10, rangeEndPage = 13) }
        val p = SmartPlanEngine.calculateProgress(
            scoped, "student-1",
            listOf(rec(10, 11, id = "first"), rec(10, 11, id = "dupe"), rec(1, 5, id = "outside")),
            today = LocalDate.parse("2026-08-30"),
        )
        assertEquals(2.0, p.completed, 0.001)
        assertEquals(listOf(10, 11), p.completedPages)
    }

    @Test fun failedPagesBecomeMandatoryTathbitAndLaterClear() {
        val scoped = plan { it.copy(targetPages = 4.0, rangeStartPage = 221, rangeEndPage = 224) }
        val failed = listOf(rec(221, 222), rec(223, 224, "repeat", id = "failed"))
        val p = SmartPlanEngine.calculateProgress(scoped, "student-1", failed, today = LocalDate.parse("2026-08-31"))
        assertEquals("tathbit", p.assignment.kind)
        assertTrue(p.assignment.blockedByRetry)
        assertEquals(listOf(SmartPlanEngine.PageRange(223, 224)), p.assignment.retryRanges)

        val passed = SmartPlanEngine.calculateProgress(
            scoped, "student-1", failed + rec(223, 224, "very_good", "2026-08-31", "retry-pass"),
            today = LocalDate.parse("2026-08-31"),
        )
        assertEquals(4.0, passed.completed, 0.001)
        assertTrue(passed.retryPages.isEmpty())
        assertFalse(passed.assignment.blockedByRetry)
    }

    @Test fun legacyEvaluationFallbackMatchesWeb() {
        val scoped = plan { it.copy(targetPages = 2.0, rangeStartPage = 10, rangeEndPage = 11) }
        assertTrue(SmartPlanEngine.isPassing(rec(10, 11, null, mistakes = 1), scoped))
        assertFalse(SmartPlanEngine.isPassing(rec(10, 11, null, mistakes = 5), scoped))
    }

    @Test fun juzMilestoneDoesNotBlockOrCreatePlanTest() {
        val scoped = plan {
            it.copy(
                targetPages = 30.0, rangeStartPage = 1, rangeEndPage = 30, milestoneMode = "juz",
                startDate = LocalDate.parse("2026-08-01"), endDate = LocalDate.parse("2026-09-30"),
            )
        }
        val rows = listOf(rec(1, 21))
        val pending = SmartPlanEngine.calculateProgress(scoped, "student-1", rows, today = LocalDate.parse("2026-08-31"))
        assertEquals("hifz", pending.assignment.kind)
        assertEquals(null, pending.assignment.milestone)
        assertEquals(22, pending.nextPage)
        val approved = SmartPlanEngine.calculateProgress(
            scoped, "student-1", rows, listOf(Event("milestone_approved", milestoneKey = "juz:1")), LocalDate.parse("2026-08-31"),
        )
        assertNotEquals("test", approved.assignment.kind)
        assertEquals(22, approved.nextPage)
    }

    @Test fun studentOverrideWins() {
        val student = Student("student-1", "halaqa-1")
        val group = plan { it.copy(id = "group", scopeType = "halaqa", studentId = null, updatedAt = "2026-08-20T00:00:00Z") }
        val override = plan { it.copy(id = "override", parentPlanId = "group", updatedAt = "2026-08-19T00:00:00Z") }
        assertEquals(listOf("override"), SmartPlanEngine.effectivePlans(listOf(group, override), student, LocalDate.parse("2026-08-31")).map { it.id })
    }

    @Test fun reviewNearAndFarNeverOverlap() {
        val rows = (1..30).map { page -> rec(page, date = "2026-08-${page.toString().padStart(2, '0')}", id = "p$page") }
        val s = SmartPlanEngine.reviewSuggestions("student-1", rows, 7, 10, LocalDate.parse("2026-08-31"))
        assertEquals((24..30).toList(), s.nearPages)
        assertEquals(10, s.farPages.size)
        assertTrue(s.farPages.none { it in s.nearPages })
    }
    @Test fun hizbMilestoneDoesNotCreatePlanTest() {
        val scoped = plan {
            it.copy(
                targetPages = 20.0,
                rangeStartPage = 1,
                rangeEndPage = 20,
                milestoneMode = "hizb",
                startDate = LocalDate.parse("2026-08-01"),
                endDate = LocalDate.parse("2026-09-30"),
            )
        }
        // Hizb 1 is pages 1..11 in the vendored Hafs metadata.
        val rows = listOf(rec(1, 11))
        val pending = SmartPlanEngine.calculateProgress(scoped, "student-1", rows, today = LocalDate.parse("2026-08-31"))
        assertEquals("hifz", pending.assignment.kind)
        assertEquals(null, pending.assignment.milestone)
        assertEquals(12, pending.nextPage)
        val approved = SmartPlanEngine.calculateProgress(
            scoped,
            "student-1",
            rows,
            listOf(Event("milestone_approved", milestoneKey = "hizb:1")),
            LocalDate.parse("2026-08-31"),
        )
        assertNotEquals("test", approved.assignment.kind)
    }

    @Test fun weeklyProgressUsesCanonicalSundayToSaturdayWindow() {
        val weeklyPlan = plan {
            it.copy(
                targetPages = 10.0,
                startDate = LocalDate.parse("2026-09-20"),
                endDate = LocalDate.parse("2026-09-24"),
                rangeStartPage = 100,
                rangeEndPage = 109,
            )
        }
        val rows = listOf(
            rec(100, 101, date = "2026-09-20", id = "sun"),
            rec(102, 102, date = "2026-09-21", id = "mon"),
        )
        val p = SmartPlanEngine.calculateProgress(
            weeklyPlan, "student-1", rows, today = LocalDate.parse("2026-09-22"),
        )
        assertEquals(LocalDate.parse("2026-09-20"), p.weekly.weekStart)
        assertEquals(LocalDate.parse("2026-09-26"), p.weekly.weekEnd)
        assertEquals(5, p.weekly.scheduledDays)
        assertEquals(3, p.weekly.elapsedScheduledDays)
        assertEquals(2, p.weekly.remainingScheduledDays)
        assertEquals(10.0, p.weekly.target, 0.001)
        assertEquals(6.0, p.weekly.requiredByToday, 0.001)
        assertEquals(3.0, p.weekly.completed, 0.001)
        assertEquals(3.0, p.weekly.behindBy, 0.001)
    }

    @Test
    fun shortLineRangeDoesNotShrinkTheQuotaOfALongTarget() {
        // RC56: a 30-page target whose generated range holds only 51 lines (clamped to a nearby terminal) used to be paced over
        // those 51 lines, so a student 7 pages behind was shown as 7 pages ahead.
        val keys = (417..420).flatMap { page -> (1..15).map { "$page:$it" } }.take(51)
        val longPlan = plan {
            it.copy(
                targetPages = 30.0,
                startDate = LocalDate.parse("2026-11-01"),
                endDate = LocalDate.parse("2026-11-22"),
                settings = it.settings.copy(memorizationLineKeys = keys),
            )
        }
        val p = SmartPlanEngine.calculateProgress(longPlan, "student-1", emptyList(), emptyList(), LocalDate.parse("2026-11-11"))
        assertEquals(16.87, p.plannedByToday, 0.05)
    }

    @Test
    fun planEndDateIsNeverExtendedAutomatically() {
        // RC57: even a student far behind a plan must not get a later end date.
        val p = SmartPlanEngine.calculateProgress(
            plan { it.copy(targetPages = 500.0, autoExtend = true) }, "student-1", emptyList(), emptyList(), LocalDate.parse("2026-09-02"),
        )
        assertFalse(p.extensionNeeded)
        assertEquals(LocalDate.parse("2026-09-03"), p.suggestedEndDate)
    }
}
