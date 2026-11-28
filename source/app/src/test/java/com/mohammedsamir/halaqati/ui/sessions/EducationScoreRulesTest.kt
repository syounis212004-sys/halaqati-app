package com.mohammedsamir.halaqati.ui.sessions

import org.junit.Assert.*
import org.junit.Test

class EducationScoreRulesTest {
    @Test fun missingIsNotAZeroOrDefaultScore() {
        assertEquals("غير مقيّم", EducationScoreRules.label(null))
        assertFalse(EducationScoreRules.valid("general",0))
        assertTrue(EducationScoreRules.valid("general",5))
        assertFalse(EducationScoreRules.valid("hifz",4))
    }
    @Test fun bulkOnlyAppliesToPresentUnratedStudents() {
        val states=mapOf("a" to "present", "b" to "late", "c" to "absent", "d" to "present")
        val scores=mapOf("d" to mapOf("general" to 4))
        assertEquals(setOf("a","b"), EducationScoreRules.bulkCandidates(states,scores))
    }
}
