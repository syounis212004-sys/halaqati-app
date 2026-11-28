package com.mohammedsamir.halaqati.data

import org.junit.Assert.*
import org.junit.Test

class StudentEnrollmentRulesTest {
    private val scopes = listOf(
        StudentEnrollmentRules.Scope("regular", false, true, true),
        StudentEnrollmentRules.Scope("course", true, true, true),
        StudentEnrollmentRules.Scope("closed", true, false, true),
        StudentEnrollmentRules.Scope("private", true, true, false),
    )

    @Test fun requestedScopeIsNotSilentlyReplaced() {
        assertEquals("course", StudentEnrollmentRules.initialScope("course", scopes))
        assertNull(StudentEnrollmentRules.initialScope("missing", listOf(scopes.first())))
        assertNull(StudentEnrollmentRules.initialScope("closed", scopes))
        assertNull(StudentEnrollmentRules.initialScope(null, scopes))
        assertEquals("regular", StudentEnrollmentRules.initialScope(null, listOf(scopes.first())))
    }

    @Test fun onlyActiveSecondaryProgramsAreOffered() {
        assertEquals(listOf("course"), StudentEnrollmentRules.additionalScopes("regular", scopes).map { it.id })
        assertTrue(StudentEnrollmentRules.additionalScopes("course", scopes).isEmpty())
    }

    @Test fun secondaryContextCannotEditPrimaryRecord() {
        assertFalse(StudentEnrollmentRules.canEditPrimary("regular", "course", setOf("regular", "course")))
        assertFalse(StudentEnrollmentRules.canEditPrimary("regular", "regular", setOf("course")))
        assertTrue(StudentEnrollmentRules.canEditPrimary("regular", "regular", setOf("regular", "course")))
    }

    @Test fun detailsNeverMixMembershipScopes() {
        assertEquals("course", StudentEnrollmentRules.detailScope("regular", "course", setOf("course"), scopes, true))
        assertEquals("regular", StudentEnrollmentRules.detailScope("regular", null, setOf("course"), scopes, false))
        assertNull(StudentEnrollmentRules.detailScope("regular", "private", setOf("course"), scopes, true))
        val twoPrograms = scopes + StudentEnrollmentRules.Scope("other", true, true, true)
        assertNull(StudentEnrollmentRules.detailScope("regular", null, setOf("course","other"), twoPrograms, true))
    }
}
