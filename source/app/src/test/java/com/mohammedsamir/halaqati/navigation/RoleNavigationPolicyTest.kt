package com.mohammedsamir.halaqati.navigation

import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.navigation.AppDestination
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

    @Test fun mushafIsReachableFromTheMenuForEveryReadingRole() {
        for (role in listOf(UserRole.ADMIN, UserRole.TEACHER, UserRole.SUPERVISOR, UserRole.GUARDIAN)) {
            org.junit.Assert.assertTrue("$role can open the mushaf", RoleNavigationPolicy.canOpen(role, AppDestination.Mushaf))
            org.junit.Assert.assertTrue("$role sees it in the more menu", RoleNavigationPolicy.moreSections(role).any { AppDestination.Mushaf in it.items })
        }
        org.junit.Assert.assertEquals(AppDestination.Mushaf, AppDestination.fromRoute("mushaf"))
    }

    @Test fun contestsCanBeSwitchedOffEverywhereWithOneFlag() {
        com.mohammedsamir.halaqati.data.ContestsFeature.enabled = false
        try {
            for (role in UserRole.values()) {
                assertFalse("$role cannot open contests while the flag is off", RoleNavigationPolicy.canOpen(role, AppDestination.Contests))
                assertFalse(RoleNavigationPolicy.drawerItems(role).any { it.destination == AppDestination.Contests })
                assertFalse(RoleNavigationPolicy.moreSections(role).any { AppDestination.Contests in it.items })
            }
        } finally { com.mohammedsamir.halaqati.data.ContestsFeature.enabled = true }
    }

    @Test fun contestsAreOpenToDonorAdminAndEveryHalaqaRelatedRole() {
        assertTrue(com.mohammedsamir.halaqati.data.ContestsFeature.enabled)
        contestsOpenBody()
    }

    private fun contestsOpenBody() {
        for (role in listOf(UserRole.OBSERVER, UserRole.ADMIN, UserRole.TEACHER, UserRole.SUPERVISOR, UserRole.GUARDIAN, UserRole.STUDENT)) {
            assertTrue("$role can open contests", RoleNavigationPolicy.canOpen(role, AppDestination.Contests))
            assertTrue("$role sees contests in the drawer", RoleNavigationPolicy.drawerItems(role).any { it.destination == AppDestination.Contests })
            assertTrue("$role sees contests in the more menu or bottom bar", RoleNavigationPolicy.moreSections(role).any { AppDestination.Contests in it.items } || AppDestination.Contests in RoleNavigationPolicy.bottom(role))
        }
        assertFalse(RoleNavigationPolicy.canOpen(UserRole.UNKNOWN, AppDestination.Contests))
        assertEquals(AppDestination.Contests, AppDestination.fromRoute("contests"))
        assertEquals("المسابقات", AppDestination.Contests.label)
    }

    @Test fun theContributionPageIsForGuardiansTeachersSupervisorsAndAdminOnly() {
        for (role in listOf(UserRole.GUARDIAN, UserRole.TEACHER, UserRole.SUPERVISOR, UserRole.ADMIN)) {
            assertTrue("$role can open the contribution page", RoleNavigationPolicy.canOpen(role, AppDestination.Contribute))
            assertTrue("$role sees it in the drawer", RoleNavigationPolicy.drawerItems(role).any { it.destination == AppDestination.Contribute })
        }
        for (role in listOf(UserRole.OBSERVER, UserRole.STUDENT, UserRole.UNKNOWN)) {
            assertFalse("$role has no contribution page", RoleNavigationPolicy.canOpen(role, AppDestination.Contribute))
        }
        assertEquals(AppDestination.Contribute, AppDestination.fromRoute("contribute"))
    }

    @Test fun adminHasOneAccountMenuButOldApprovalRouteStillWorks() {
        val role = UserRole.ADMIN
        assertTrue(RoleNavigationPolicy.canOpen(role, AppDestination.Approvals))
        assertTrue(RoleNavigationPolicy.drawerItems(role).any { it.destination == AppDestination.Users })
        assertFalse(RoleNavigationPolicy.drawerItems(role).any { it.destination == AppDestination.Approvals })
        assertFalse(RoleNavigationPolicy.moreSections(role).any { AppDestination.Approvals in it.items })
        for (staff in listOf(UserRole.SUPERVISOR, UserRole.TEACHER)) {
            assertTrue(RoleNavigationPolicy.canOpen(staff, AppDestination.Approvals))
            assertFalse(RoleNavigationPolicy.canOpen(staff, AppDestination.Users))
            assertTrue(RoleNavigationPolicy.drawerItems(staff).any { it.destination == AppDestination.Approvals })
        }
    }
}
