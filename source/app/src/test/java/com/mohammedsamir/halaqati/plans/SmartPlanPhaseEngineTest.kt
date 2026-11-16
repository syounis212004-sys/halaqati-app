package com.mohammedsamir.halaqati.plans

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SmartPlanPhaseEngineTest {
    private fun phase(
        target: Double? = 60.0,
        distribution: String = SmartPlanPhaseEngine.DISTRIBUTION_FLEXIBLE,
    ) = SmartPlanPhaseEngine.Phase(
        id = "phase-sard",
        planId = "plan",
        order = 2,
        type = SmartPlanPhaseEngine.TYPE_SARD,
        startDate = LocalDate.parse("2026-09-25"),
        endDate = LocalDate.parse("2026-09-30"),
        scheduleWeekdays = setOf(0,1,2,3,4,5,6),
        goalMode = SmartPlanPhaseEngine.GOAL_PAGES,
        targetPages = target,
        targetUnits = null,
        goalUnit = "pages",
        distributionMode = distribution,
        startMode = SmartPlanPhaseEngine.START_LAST_SARD,
    )

    @Test fun flexibleDistributionRebalancesAfterSurplusDay() {
        val p = SmartPlanPhaseEngine.progress(phase(), completedPages = 18.0, today = LocalDate.parse("2026-09-26"))
        assertEquals(42.0, p.remainingPages!!, .001)
        assertEquals(8.4, p.suggestedTodayPages!!, .001)
    }

    @Test fun phaseCanFinishEarlyAndKeepSurplusSeparate() {
        val p = SmartPlanPhaseEngine.progress(phase(), completedPages = 67.0, today = LocalDate.parse("2026-09-27"))
        assertTrue(p.completedEarly)
        assertTrue(p.goalComplete)
        assertEquals(7.0, p.surplusPages, .001)
        assertEquals(111.7, p.percentage!!, .1)
    }

    @Test fun openSardHasNoForcedDailyTarget() {
        val open = phase(target = null).copy(goalMode = SmartPlanPhaseEngine.GOAL_OPEN)
        val p = SmartPlanPhaseEngine.progress(open, completedPages = 22.0, today = LocalDate.parse("2026-09-27"))
        assertNull(p.targetPages)
        assertNull(p.remainingPages)
        assertNull(p.suggestedTodayPages)
        assertFalse(p.goalComplete)
    }

    @Test fun optionalCarryBecomesPartOfNextPhaseTarget() {
        val state = SmartPlanPhaseEngine.StudentState(
            phaseId = "phase-sard",
            studentId = "student",
            carriedShortfallPages = 10.0,
        )
        val p = SmartPlanPhaseEngine.progress(phase(), completedPages = 18.0, today = LocalDate.parse("2026-09-26"), state = state)
        assertEquals(70.0, p.targetPages!!, .001)
        assertEquals(52.0, p.remainingPages!!, .001)
        assertEquals(10.4, p.suggestedTodayPages!!, .001)
    }

    @Test fun manualStartDoesNotUseHistoricalSardAnchor() {
        val manual = phase().copy(
            startMode = SmartPlanPhaseEngine.START_MANUAL,
            rangeStartSurah = 78,
            rangeStartAyah = 1,
        )
        assertEquals(78, SmartPlanPhaseEngine.configuredStart(manual)?.surah)
        assertFalse(SmartPlanPhaseEngine.usesHistoricalSardAnchor(manual))
        assertTrue(SmartPlanPhaseEngine.usesHistoricalSardAnchor(phase()))
    }

    @Test fun sardAnchorNeverUsesHifz() {
        val rows = listOf(
            SmartPlanEngine.Recitation("h", "s", "new_hifz", LocalDate.parse("2026-09-26"), endPage=485, endSurah=42, endAyah=53, createdAt="2"),
            SmartPlanEngine.Recitation("sard", "s", "sard", LocalDate.parse("2026-09-25"), endPage=582, endSurah=78, endAyah=40, createdAt="1"),
        )
        val anchor = SmartPlanPhaseEngine.latestSardAnchor(rows, LocalDate.parse("2026-09-26"))
        assertEquals("sard", anchor?.recitationId)
        assertEquals(78, anchor?.surah)
        assertEquals(40, anchor?.ayah)
    }

    @Test fun shortfallIsSuggestedNotImplicitlyCarried() {
        val p = SmartPlanPhaseEngine.progress(phase(), completedPages = 44.0, today = LocalDate.parse("2026-09-30"))
        assertEquals(16.0, SmartPlanPhaseEngine.previousShortfall(p), .001)
    }

    @Test fun genericTestDoesNotMoveSardAnchor() {
        val rows = listOf(
            SmartPlanEngine.Recitation("sard", "s", "sard", LocalDate.parse("2026-09-25"), endPage=582, endSurah=78, endAyah=40, createdAt="1"),
            SmartPlanEngine.Recitation("test", "s", "test", LocalDate.parse("2026-09-26"), endPage=590, endSurah=85, endAyah=22, createdAt="9"),
        )
        assertEquals("sard", SmartPlanPhaseEngine.latestSardAnchor(rows, LocalDate.parse("2026-09-26"))?.recitationId)
    }

    @Test fun gapKeepsContextButDoesNotCreateActivePhase() {
        val hifz = phase().copy(id="h", order=1, type=SmartPlanPhaseEngine.TYPE_HIFZ, startDate=LocalDate.parse("2026-09-01"), endDate=LocalDate.parse("2026-09-20"))
        val sard = phase().copy(startDate=LocalDate.parse("2026-09-25"))
        val day = LocalDate.parse("2026-09-22")
        assertNull(SmartPlanPhaseEngine.effectiveActivePhase(listOf(hifz, sard), emptyList(), "student", day))
        assertEquals("h", SmartPlanPhaseEngine.contextPhase(listOf(hifz, sard), emptyList(), "student", day)?.id)
        assertEquals(SmartPlanPhaseEngine.Lifecycle.ENDED, SmartPlanPhaseEngine.lifecycle(hifz, null, day))
    }

    @Test fun overlapIsBlockingAndGapIsExplicit() {
        val hifz = phase().copy(id="h", order=1, type=SmartPlanPhaseEngine.TYPE_HIFZ, startDate=LocalDate.parse("2026-09-01"), endDate=LocalDate.parse("2026-09-20"))
        val sardGap = phase().copy(startDate=LocalDate.parse("2026-09-25"))
        assertTrue(SmartPlanPhaseEngine.validateTimeline(listOf(hifz, sardGap)).any { it.code == "gap" && !it.blocking })
        val sardOverlap = phase().copy(startDate=LocalDate.parse("2026-09-20"))
        assertTrue(SmartPlanPhaseEngine.validateTimeline(listOf(hifz, sardOverlap)).any { it.code == "overlap" && it.blocking })
    }

    @Test fun rc112SardDefaultsAreTwentyFivePagesFromNasToBaqarah() {
        assertEquals(25.0, SmartPlanPhaseEngine.DEFAULT_SARD_TARGET_PAGES, .001)
        assertEquals("reverse_surahs_forward_ayahs", SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH)
    }
}
