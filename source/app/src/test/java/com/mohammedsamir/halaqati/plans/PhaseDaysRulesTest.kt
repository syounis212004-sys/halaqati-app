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
}
