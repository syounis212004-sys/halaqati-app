package com.mohammedsamir.halaqati.data

import com.mohammedsamir.halaqati.data.FullRefreshPolicy.Step as S
import com.mohammedsamir.halaqati.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullRefreshPolicyTest {
    @Test
    fun staffRolesPullTheStaffData() {
        for (role in listOf(UserRole.ADMIN, UserRole.SUPERVISOR)) {
            val steps = FullRefreshPolicy.stepsFor(role)
            assertTrue(role.name, steps.containsAll(listOf(S.STUDENTS, S.SESSIONS, S.PLANS, S.RANKING, S.REPORTS, S.SUPERVISOR_VISITS, S.ACCOUNTS, S.GUARDIAN_ACCOUNTS)))
        }
    }

    @Test
    fun aTeacherDoesNotPullAccountDirectories() {
        val steps = FullRefreshPolicy.stepsFor(UserRole.TEACHER)
        assertTrue(steps.containsAll(listOf(S.STUDENTS, S.SESSIONS, S.PLANS, S.RANKING, S.REPORTS)))
        assertFalse(S.ACCOUNTS in steps); assertFalse(S.GUARDIAN_ACCOUNTS in steps)
    }

    @Test
    fun guardiansAndDonorsSkipStaffOnlyPulls() {
        for (role in listOf(UserRole.GUARDIAN, UserRole.OBSERVER, UserRole.STUDENT, UserRole.UNKNOWN)) {
            val steps = FullRefreshPolicy.stepsFor(role)
            for (staffOnly in listOf(S.RANKING, S.REPORTS, S.SUPERVISOR_VISITS, S.ACCOUNTS, S.GUARDIAN_ACCOUNTS, S.SUPERVISION)) assertFalse("$role $staffOnly", staffOnly in steps)
            assertTrue(role.name, S.ANNOUNCEMENTS in steps)
        }
    }

    @Test
    fun aRoundIsASuccessOnlyWhenEveryCriticalStepWorked() {
        val ok = mapOf(S.STUDENTS to true, S.SESSIONS to true, S.PLANS to true, S.RANKING to false)
        assertTrue(FullRefreshPolicy.isSuccess(ok))
        assertFalse(FullRefreshPolicy.isSuccess(ok + (S.SESSIONS to false)))
        assertFalse(FullRefreshPolicy.isSuccess(emptyMap()))
    }

    @Test
    fun afterFailureTheNextAttemptIsFiveMinutesAwayNotThirty() {
        val gap = 30 * 60_000L; val now = 1_000_000_000L
        assertEquals(now, FullRefreshPolicy.lastStampToStore(now, true, gap))
        val failed = FullRefreshPolicy.lastStampToStore(now, false, gap)
        assertEquals(now + 5 * 60_000L, failed + gap) // stamp + gap = now + retry delay
    }

    @Test
    fun failureMessageNamesTheFailedStepsAndSuccessHasNone() {
        assertEquals(null, FullRefreshPolicy.errorText(mapOf(S.STUDENTS to true)))
        assertEquals("PLANS,RANKING", FullRefreshPolicy.errorText(mapOf(S.STUDENTS to true, S.PLANS to false, S.RANKING to false)))
    }
}
