package com.mohammedsamir.halaqati.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionWorkflowRulesTest {
    @Test fun mapsAssignmentKindsToLegacySessionTypes() {
        assertEquals("sard", SessionWorkflowRules.sessionTypeForAssignment("sard"))
        assertEquals("review", SessionWorkflowRules.sessionTypeForAssignment("tathbit"))
        assertEquals("review", SessionWorkflowRules.sessionTypeForAssignment("review"))
        assertEquals("test", SessionWorkflowRules.sessionTypeForAssignment("test"))
        assertEquals("tasmee", SessionWorkflowRules.sessionTypeForAssignment("hifz"))
    }

    @Test fun supportedManualTypesMatchGoldenMaster() {
        assertTrue(SessionWorkflowRules.manualSessionTypes.containsAll(listOf("tasmee","sard","review","test","tajweed","lesson","other")))
    }

    @Test fun programsOfferIndependentActivities() {
        assertEquals(listOf("tilawah", "lesson", "test", "other"), SessionWorkflowRules.manualTypesForScope("course"))
        assertEquals("tasmee", SessionWorkflowRules.manualTypesForScope("regular").first())
        assertFalse("tajweed" in SessionWorkflowRules.manualTypesForScope("tilawah"))
    }

    @Test fun explicitNotRecitedIsDifferentFromUnmarked() {
        assertEquals(
            SessionWorkflowRules.StudentState.UNMARKED,
            SessionWorkflowRules.studentState(null, null, hasRecitations = false),
        )
        assertEquals(
            SessionWorkflowRules.StudentState.NOT_RECITED,
            SessionWorkflowRules.studentState("present", SessionWorkflowRules.NOT_RECITED_MARKER, hasRecitations = false),
        )
    }

    @Test fun missingAttendanceRemainsUnmarkedEvenIfARecitationExists() {
        assertEquals(
            SessionWorkflowRules.StudentState.UNMARKED,
            SessionWorkflowRules.studentState(null, null, hasRecitations = true),
        )
    }

    @Test fun recitationWinsOnceAttendanceWasRecorded() {
        assertEquals(
            SessionWorkflowRules.StudentState.RECITED,
            SessionWorkflowRules.studentState("present", SessionWorkflowRules.NOT_RECITED_MARKER, hasRecitations = true),
        )
    }

    @Test fun filtersAndPriorityPutUnfinishedStudentsFirst() {
        assertTrue(SessionWorkflowRules.matchesFilter(SessionWorkflowRules.StudentState.NOT_RECITED, "not_recited"))
        assertTrue(SessionWorkflowRules.matchesFilter(SessionWorkflowRules.StudentState.UNMARKED, "unmarked"))
        assertTrue(SessionWorkflowRules.priority(SessionWorkflowRules.StudentState.UNMARKED) < SessionWorkflowRules.priority(SessionWorkflowRules.StudentState.RECITED))
    }
    @Test fun sessionSummarySeparatesUnmarkedFromNotRecited() {
        val states = listOf(
            SessionWorkflowRules.StudentState.UNMARKED,
            SessionWorkflowRules.StudentState.NOT_RECITED,
            SessionWorkflowRules.StudentState.RECITED,
            SessionWorkflowRules.StudentState.PRESENT,
            SessionWorkflowRules.StudentState.ABSENT,
        )
        val summary = SessionWorkflowRules.summary(states)
        assertEquals(1, summary.unmarked)
        assertEquals(1, summary.notRecited)
        assertEquals(1, summary.recited)
        assertEquals(3, summary.present)
        assertEquals(1, summary.absent)
    }

    @Test fun arabicSearchNormalizesAlefAndExtraSpaces() {
        assertTrue(SessionWorkflowRules.matchesSearch("أحمد  عبد الكريم", "احمد عبد"))
        assertTrue(SessionWorkflowRules.matchesSearch("إياد محمد", "اياد"))
        assertFalse(SessionWorkflowRules.matchesSearch("محمد سمير", "احمد"))
    }

    @Test fun closedSessionIsStillTheUniqueDailySession() {
        assertTrue(SessionWorkflowRules.isReusableDailySessionStatus("open"))
        assertTrue(SessionWorkflowRules.isReusableDailySessionStatus("planned"))
        assertTrue(SessionWorkflowRules.isReusableDailySessionStatus("closed"))
        assertFalse(SessionWorkflowRules.isReusableDailySessionStatus("cancelled"))
    }

    @Test fun oneSessionIdentityIgnoresTitle() {
        assertEquals(
            "halaqa-1|2026-10-08|tasmee",
            SessionWorkflowRules.dailySessionKey("halaqa-1", "2026-10-08", "tasmee"),
        )
    }

}
