package com.mohammedsamir.halaqati.plans

import com.mohammedsamir.halaqati.plans.SmartPlanCoreStateEngine.StatusCode
import com.mohammedsamir.halaqati.plans.SmartPlanEngine.Plan
import com.mohammedsamir.halaqati.plans.SmartPlanEngine.Recitation
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SmartPlanCoreStateTest {
    private val plan = Plan(
        id = "plan-core",
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

    private fun rec(
        id: String,
        type: String,
        date: String,
        endSurah: Int,
        endAyah: Int,
        startPage: Int,
        endPage: Int = startPage,
        evaluation: String = "excellent",
    ) = Recitation(
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

    @Test fun canonicalStateUsesHifzAnchorAndConfiguredWeeklyTarget() {
        val rows = listOf(
            rec("h1", "new_hifz", "2026-09-20", 114, 6, 580, 579),
            rec("h2", "new_hifz", "2026-09-21", 113, 5, 578),
            rec("sard-newer", "sard", "2026-09-22", 78, 40, 570),
        )
        val progress = SmartPlanEngine.calculateProgress(
            plan = plan,
            studentId = "student-1",
            recitations = rows,
            today = LocalDate.parse("2026-09-22"),
        )
        val state = SmartPlanCoreStateEngine.build(
            plan = plan,
            progress = progress,
            recitations = rows,
            today = LocalDate.parse("2026-09-22"),
            weeklyTargetOverride = 40.0,
        )

        assertEquals(40.0, state.weekly.target, 0.001)
        assertEquals(24.0, state.weekly.requiredByToday, 0.001)
        assertEquals(3.0, state.weekly.completed, 0.001)
        assertEquals(21.0, state.weekly.behindBy, 0.001)
        assertEquals("h2", state.lastMemorizationAnchor?.recitationId)
        assertEquals(113, state.lastMemorizationAnchor?.surah)
        assertEquals(5, state.lastMemorizationAnchor?.ayah)
        assertEquals(StatusCode.BEHIND, state.status)
    }

    @Test fun failedHifzAndSardCannotMoveMemorizationAnchor() {
        val rows = listOf(
            rec("good", "new_hifz", "2026-09-20", 114, 6, 580),
            rec("failed-newer", "new_hifz", "2026-09-21", 113, 5, 579, evaluation = "repeat"),
            rec("sard-newest", "sard", "2026-09-22", 112, 4, 578),
        )
        val progress = SmartPlanEngine.calculateProgress(plan, "student-1", rows, today = LocalDate.parse("2026-09-22"))
        val state = SmartPlanCoreStateEngine.build(plan, progress, rows, LocalDate.parse("2026-09-22"))
        assertEquals("good", state.lastMemorizationAnchor?.recitationId)
        assertEquals(114, state.lastMemorizationAnchor?.surah)
    }

    @Test fun completedPlanHasZeroRemainingAndCompleteStatus() {
        val rows = listOf(
            rec("all", "new_hifz", "2026-09-20", 113, 5, 580, 571),
        )
        val progress = SmartPlanEngine.calculateProgress(plan, "student-1", rows, today = LocalDate.parse("2026-09-20"))
        val state = SmartPlanCoreStateEngine.build(plan, progress, rows, LocalDate.parse("2026-09-20"))
        assertEquals(10.0, state.completedPages, 0.001)
        assertEquals(0.0, state.remainingPages, 0.001)
        assertEquals(StatusCode.COMPLETE_EARLY, state.status)
        assertTrue(state.completedEarly)
        assertEquals(0.0, state.surplusPages, 0.001)
        assertEquals(4, state.daysRemainingInPlan)
        assertNull(state.nextAssignment)
    }

    @Test fun completionBeyondTargetTracksSurplusWithoutNegativeRemaining() {
        val surplusPlan = plan.copy(targetPages = 5.0)
        val rows = listOf(rec("surplus", "new_hifz", "2026-09-20", 113, 5, 580, 574))
        val progress = SmartPlanEngine.calculateProgress(surplusPlan, "student-1", rows, today = LocalDate.parse("2026-09-20"))
        val state = SmartPlanCoreStateEngine.build(surplusPlan, progress, rows, LocalDate.parse("2026-09-20"))
        assertEquals(StatusCode.COMPLETE_EARLY, state.status)
        assertEquals(7.0, state.completedPages, 0.001)
        assertEquals(2.0, state.surplusPages, 0.001)
        assertEquals(0.0, state.remainingPages, 0.001)
        assertTrue(state.completedEarly)
    }

    @Test fun completionOnPlanEndIsCompleteNotEarly() {
        val rows = listOf(rec("all", "new_hifz", "2026-09-24", 113, 5, 580, 571))
        val progress = SmartPlanEngine.calculateProgress(plan, "student-1", rows, today = LocalDate.parse("2026-09-24"))
        val state = SmartPlanCoreStateEngine.build(plan, progress, rows, LocalDate.parse("2026-09-24"))
        assertEquals(StatusCode.COMPLETE, state.status)
        assertFalse(state.completedEarly)
        assertEquals(0, state.daysRemainingInPlan)
    }
}
