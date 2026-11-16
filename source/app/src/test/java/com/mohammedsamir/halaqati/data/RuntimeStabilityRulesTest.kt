package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStabilityRulesTest {
    @Test fun cachedProfileCanOpenAuthenticatedShellImmediately() {
        assertTrue(RuntimeStabilityRules.canOpenFromCachedProfile(true, true))
        assertFalse(RuntimeStabilityRules.canOpenFromCachedProfile(true, false))
    }

    @Test fun milestoneApprovalKeyIsStableAndMilestoneSpecific() {
        val first = RuntimeStabilityRules.milestoneEventKey("plan", "student", "juz:27")
        val second = RuntimeStabilityRules.milestoneEventKey("plan", "student", "juz:27")
        val other = RuntimeStabilityRules.milestoneEventKey("plan", "student", "juz:28")
        assertEquals(first, second)
        assertNotEquals(first, other)
    }
}
