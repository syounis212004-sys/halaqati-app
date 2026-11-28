package com.mohammedsamir.halaqati.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionWorkflowRulesTest {
    @Test fun mapsAssignmentKindsToSessionTypes() {
        assertEquals("sard", SessionWorkflowRules.sessionTypeForAssignment("sard"))
        assertEquals("tasmee", SessionWorkflowRules.sessionTypeForAssignment("tathbit"))
        assertEquals("tasmee", SessionWorkflowRules.sessionTypeForAssignment("review"))
        assertEquals("test", SessionWorkflowRules.sessionTypeForAssignment("test"))
        assertEquals("tasmee", SessionWorkflowRules.sessionTypeForAssignment("hifz"))
    }

    @Test fun supportedManualTypesMatchGoldenMaster() {
        assertTrue(SessionWorkflowRules.manualSessionTypes.containsAll(listOf("tasmee","sard","test","tajweed","lesson","other")))
        assertFalse("review" in SessionWorkflowRules.manualSessionTypes)
        assertTrue(SessionWorkflowRules.usesRecitationTools("review"))
    }

    @Test fun programsOfferIndependentActivities() {
        assertEquals(listOf("tilawah", "lesson", "test", "other"), SessionWorkflowRules.manualTypesForScope("course"))
        assertEquals("tasmee", SessionWorkflowRules.manualTypesForScope("regular").first())
        assertFalse("tajweed" in SessionWorkflowRules.manualTypesForScope("tilawah"))
    }

    @Test fun sessionTypesDoNotBorrowOtherActivities() {
        assertEquals(setOf("sard"), SessionWorkflowRules.planKeysForSession("sard"))
        assertEquals(setOf("hifz", "review", "tathbit"), SessionWorkflowRules.planKeysForSession("tasmee"))
        assertTrue(SessionWorkflowRules.planKeysForSession("lesson").isEmpty())
        assertTrue(SessionWorkflowRules.matchesRecitation("sard", "sard"))
        assertFalse(SessionWorkflowRules.matchesRecitation("sard", "new_hifz"))
        assertFalse(SessionWorkflowRules.matchesRecitation("tasmee", "sard"))
        assertTrue(SessionWorkflowRules.matchesRecitation("review", "review_near"))
        assertFalse(SessionWorkflowRules.matchesRecitation("test", "tilawah"))
        assertEquals(listOf("sard"), SessionWorkflowRules.quickActivities("sard", false, false).map { it.first })
        assertEquals(listOf("new_hifz", "review_near"), SessionWorkflowRules.quickActivities("tasmee", false, false).map { it.first })
        assertEquals(listOf("tathbit"), SessionWorkflowRules.quickActivities("tasmee", false, true).map { it.first })
        assertEquals(listOf("test"), SessionWorkflowRules.quickActivities("test", true, false).map { it.first })
        assertTrue(SessionWorkflowRules.quickActivities("lesson", true, false).isEmpty())
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


    @Test fun excusedIsNotUnexcusedAbsence() {
        val states = listOf(SessionWorkflowRules.StudentState.ABSENT, SessionWorkflowRules.StudentState.EXCUSED, SessionWorkflowRules.StudentState.UNMARKED)
        val result = SessionWorkflowRules.summary(states)
        assertEquals(1, result.absent)
        assertEquals(1, result.excused)
        assertEquals(1, result.unmarked)
        assertFalse(SessionWorkflowRules.matchesFilter(SessionWorkflowRules.StudentState.EXCUSED, "absent"))
        assertTrue(SessionWorkflowRules.matchesFilter(SessionWorkflowRules.StudentState.EXCUSED, "excused"))
    }

    @Test fun statusTransitionNeverCancelsOrSilentlyReopens() {
        assertTrue(SessionWorkflowRules.canTransition("open","closed"))
        assertTrue(SessionWorkflowRules.canTransition("closed","open"))
        assertTrue(SessionWorkflowRules.canTransition("planned","open"))
        assertFalse(SessionWorkflowRules.canTransition("open","cancelled"))
        assertFalse(SessionWorkflowRules.canTransition("closed","planned"))
        assertFalse(SessionWorkflowRules.canTransition("cancelled","open"))
    }
}
