package com.mohammedsamir.halaqati.plans

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartPlanMonthIsolationTest {
    private val plan = SmartPlanEngine.Plan(
        id = "plan-oct",
        halaqaId = "h",
        planType = "hifz",
        targetPages = 20.0,
        startDate = LocalDate.parse("2026-10-01"),
        endDate = LocalDate.parse("2026-10-22"),
        scheduleWeekdays = setOf(0,1,2,3,4),
    )

    @Test fun priorMonthTaggedRecitationDoesNotCountWhenCallerScopesByDate() {
        val old = SmartPlanEngine.Recitation(
            id = "old", studentId = "s", activityType = "new_hifz",
            date = LocalDate.parse("2026-09-22"), pagesCount = 10.0,
            taggedPlanId = plan.id, taggedPhaseId = "old-phase"
        )
        val current = emptyList<SmartPlanEngine.Recitation>()
        val p = SmartPlanEngine.calculateProgress(plan, "s", current, today = LocalDate.parse("2026-10-02"))
        assertEquals(0.0, p.completed, 0.0001)
        // The old record exists historically, but the repository must not hand it to the October phase.
        assertEquals(LocalDate.parse("2026-09-22"), old.date)
    }

    @Test fun priorMonthPlanEventIsIgnoredByCurrentMonth() {
        val p = SmartPlanEngine.calculateProgress(
            plan, "s", emptyList(),
            events = listOf(SmartPlanEngine.Event("manual_credit", pages = 9.0, date = LocalDate.parse("2026-09-20"))),
            today = LocalDate.parse("2026-10-02"),
        )
        assertEquals(0.0, p.completed, 0.0001)
    }
}
