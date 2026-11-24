package com.mohammedsamir.halaqati.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhaseDaysRulesTest {
    @Test fun commonDaysIsTheMostCommonRealSet() {
        assertEquals(listOf(5), PhaseDaysRules.commonDays(listOf(listOf(5), listOf(5), listOf(5), listOf(0, 1, 2, 3, 4, 5))))
        assertEquals(listOf(0, 5), PhaseDaysRules.commonDays(listOf(listOf(5, 0), listOf(0, 5), listOf(1))))
        assertNull(PhaseDaysRules.commonDays(listOf(null, emptyList())))
    }

    @Test fun studentsKeepTheirOwnDaysUnlessTheCommonDaysChanged() {
        assertEquals(listOf(0, 1, 2), PhaseDaysRules.resolve(emptyList(), listOf(5), listOf(5), listOf(0, 1, 2)))
        assertEquals(listOf(0, 1, 2, 3, 4), PhaseDaysRules.resolve(emptyList(), listOf(0, 1, 2, 3, 4), listOf(5), listOf(5)))
        assertEquals(listOf(6), PhaseDaysRules.resolve(listOf(6), listOf(0, 1, 2, 3, 4), listOf(5), listOf(5)))
        assertEquals(listOf(5), PhaseDaysRules.resolve(emptyList(), listOf(5), listOf(5), null))
        assertEquals(listOf(0, 5), PhaseDaysRules.resolve(emptyList(), listOf(5, 0, 5, 9, -1), emptyList(), null))
    }

    @Test fun sardPaceMeasuresTheSardPhaseItself() {
        val first = PhaseDaysRules.sardPace(25.0, 0.0, 8, 8)
        assertEquals(PhaseDaysRules.SardPace(0.0, 0.0, 0.0, "green"), first)
        assertEquals(PhaseDaysRules.SardPace(12.5, 0.0, 0.0, "green"), PhaseDaysRules.sardPace(25.0, 12.5, 8, 4))
        assertEquals("red", PhaseDaysRules.sardPace(25.0, 0.0, 8, 4).health)
        assertEquals("yellow", PhaseDaysRules.sardPace(25.0, 10.0, 8, 4).health)
        val fast = PhaseDaysRules.sardPace(25.0, 15.0, 8, 4)
        assertEquals("blue", fast.health); assertEquals(2.5, fast.ahead, 0.0)
        assertEquals(PhaseDaysRules.SardPace(0.0, 0.0, 0.0, "green"), PhaseDaysRules.sardPace(null, 3.0, 0, 0))
    }

    @Test fun fridayOnlyInAWeekLongPeriodPutsTheWholeTargetOnOneDay() {
        val today = java.time.LocalDate.of(2026, 11, 24)   // a Tuesday
        val end = java.time.LocalDate.of(2026, 11, 30)
        val friday = PhaseDaysRules.check(today, java.time.LocalDate.of(2026, 11, 23), end, PhaseDaysRules.FRIDAY_ONLY, 25.0)
        assertEquals(1, friday.daysLeft); assertEquals("warn", friday.level); assertEquals(25.0, friday.perDay!!, 0.0)
        val all = PhaseDaysRules.check(today, java.time.LocalDate.of(2026, 11, 23), end, PhaseDaysRules.SUN_TO_FRI, 25.0)
        assertEquals(6, all.daysLeft); assertEquals("ok", all.level); assertEquals(25.0 / 6, all.perDay!!, 1e-9)
    }

    @Test fun noDaysOrNoScheduledDayLeftIsAnError() {
        val today = java.time.LocalDate.of(2026, 11, 24)
        assertEquals("error", PhaseDaysRules.check(today, today, today.plusDays(5), emptyList(), 25.0).level)
        // Saturday only, period ends Friday: nothing is left
        assertEquals("error", PhaseDaysRules.check(today, today, java.time.LocalDate.of(2026, 11, 27), listOf(6), 25.0).level)
        assertEquals(0, PhaseDaysRules.scheduledDays(today.plusDays(1), today, PhaseDaysRules.SUN_TO_FRI))
    }

    @Test fun heavyDailyShareWarnsAndPastStartDatesAreNotCounted() {
        val today = java.time.LocalDate.of(2026, 11, 24)
        val heavy = PhaseDaysRules.check(today, today, today.plusDays(2), PhaseDaysRules.SUN_TO_FRI, 40.0)
        assertEquals("warn", heavy.level)
        // a period that starts next week counts from its own start, not from today
        assertEquals(6, PhaseDaysRules.scheduledDays(java.time.LocalDate.of(2026, 11, 29), java.time.LocalDate.of(2026, 12, 4), PhaseDaysRules.SUN_TO_FRI))
    }
}
