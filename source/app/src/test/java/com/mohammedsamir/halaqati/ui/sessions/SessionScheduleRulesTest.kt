package com.mohammedsamir.halaqati.ui.sessions

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SessionScheduleRulesTest {
    @Test fun saturdayUsesExplicitWorkDaysNotLegacyHardcode() {
        val saturday = LocalDate.of(2026,11,28)
        assertFalse(SessionScheduleRules.allowsAutomatic(saturday, false, null, null))
        assertTrue(SessionScheduleRules.allowsAutomatic(saturday, false, setOf(6), true))
        assertFalse(SessionScheduleRules.allowsAutomatic(saturday, true, setOf(6), false))
        assertTrue(SessionScheduleRules.allowsAutomatic(saturday, true, null, true))
        assertFalse(SessionScheduleRules.allowsAutomatic(saturday, true, null, null))
    }
    @Test fun explicitEmptyDaysNeverSilentlyBecomesEveryDay() {
        assertFalse(SessionScheduleRules.allowsAutomatic(LocalDate.of(2026,11,29), false, emptySet(), true))
        assertEquals(setOf(1,2,3,4,5,7), SessionScheduleRules.days(false, null))
        assertEquals((1..7).toSet(), SessionScheduleRules.days(true, null))
    }
    @Test fun sessionQueueMatchingDoesNotConfuseOtherSessions() {
        val id="session-abc"
        assertTrue(SessionScheduleRules.pendingFor(id,"attendance:session-abc:student",null,null))
        assertTrue(SessionScheduleRules.pendingFor(id,"session-delete:session-abc",null,null))
        assertTrue(SessionScheduleRules.pendingFor(id,null,"/rest/v1/sessions?id=eq.session-abc",null))
        assertTrue(SessionScheduleRules.pendingFor(id,null,null,"session-abc"))
        assertFalse(SessionScheduleRules.pendingFor(id,"attendance:session-abcd:student",null,null))
    }
}
