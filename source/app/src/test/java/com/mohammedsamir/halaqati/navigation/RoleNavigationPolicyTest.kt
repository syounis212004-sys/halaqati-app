package com.mohammedsamir.halaqati.navigation

import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.navigation.RoleNavigationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleNavigationPolicyTest {
    @Test
    fun guardianBottomBarMatchesRc17RoleExperience() {
        assertEquals(
            listOf("home", "guardian_profile", "guardian_achievements", "community", "more"),
            RoleNavigationPolicy.bottom(UserRole.GUARDIAN).map { it.route },
        )
    }

    @Test
    fun staffBottomBarMatchesGoldenMaster() {
        assertEquals(
            listOf("home", "sessions", "add", "community", "more"),
            RoleNavigationPolicy.bottom(UserRole.TEACHER).map { it.route },
        )
    }

    @Test
    fun guardianCompetitionAndAchievementsStayDistinct() {
        val routes = RoleNavigationPolicy.destinations(UserRole.GUARDIAN).map { it.route }
        assertTrue("guardian_competition" in routes)
        assertTrue("guardian_achievements" in routes)
        assertFalse("users" in routes)
    }

    @Test
    fun guardianReportsAndPlansRemainAccessibleOutsideBottomBar() {
        val allowed = RoleNavigationPolicy.destinations(UserRole.GUARDIAN).map { it.route }.toSet()
        val bottom = RoleNavigationPolicy.bottom(UserRole.GUARDIAN).map { it.route }.toSet()
        val more = RoleNavigationPolicy.moreSections(UserRole.GUARDIAN)
            .flatMap { it.items }
            .map { it.route }
            .toSet()
        val drawer = RoleNavigationPolicy.drawerItems(UserRole.GUARDIAN)
            .map { it.destination.route }
            .toSet()

        assertTrue("plans" in allowed)
        assertTrue("reports" in allowed)
        assertFalse("plans" in bottom)
        assertFalse("reports" in bottom)
        assertTrue("plans" in drawer)
        assertTrue("reports" in drawer)
        assertTrue("reports" in more)
    }

    @Test
    fun observerIsReadOnlyAndCannotReachStaffPages() {
        val routes = RoleNavigationPolicy.destinations(UserRole.OBSERVER).map { it.route }.toSet()
        assertTrue("home" in routes)
        listOf("students", "sessions", "plans", "users", "approvals", "halaqas", "importexport", "observer_requests").forEach {
            assertFalse(it + " must be closed to the observer", it in routes)
        }
        assertEquals(listOf("home", "donor_awards", "donor_support", "donor_impact", "more"), RoleNavigationPolicy.bottom(UserRole.OBSERVER).map { it.route })
        assertTrue("donor_stats" in routes)
        assertFalse("support" in routes)
        assertFalse("awards" in routes)
        assertTrue("observer_requests" in RoleNavigationPolicy.destinations(UserRole.ADMIN).map { it.route })
        assertFalse("observer_requests" in RoleNavigationPolicy.destinations(UserRole.SUPERVISOR).map { it.route })
        assertEquals(UserRole.OBSERVER, UserRole.from("observer"))
    }

    @Test
    fun supervisorHasHisOwnFollowUpPagesAndNoTeacherTools() {
        val routes = RoleNavigationPolicy.destinations(UserRole.SUPERVISOR).map { it.route }.toSet()
        listOf("plans", "certificates", "quick_memorization", "importexport", "users").forEach {
            assertFalse(it + " must be closed to the supervisor", it in routes)
        }
        listOf("supervision", "visits", "reports", "insights", "rankings", "halaqas").forEach {
            assertTrue(it + " must be open to the supervisor", it in routes)
        }
        assertEquals(listOf("home", "halaqas", "add", "supervision", "more"), RoleNavigationPolicy.bottom(UserRole.SUPERVISOR).map { it.route })
        val teacher = RoleNavigationPolicy.destinations(UserRole.TEACHER).map { it.route }.toSet()
        assertFalse("visits" in teacher)
        assertFalse("supervision" in teacher)
        assertTrue("plans" in teacher)
        assertTrue("visits" in RoleNavigationPolicy.destinations(UserRole.ADMIN).map { it.route })
    }
}
