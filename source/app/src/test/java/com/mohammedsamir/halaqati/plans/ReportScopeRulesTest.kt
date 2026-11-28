package com.mohammedsamir.halaqati.plans

import org.junit.Assert.*
import org.junit.Test

class ReportScopeRulesTest {
    private val h = "00000000-0000-0000-0000-000000000001"
    private val p = "00000000-0000-0000-0000-000000000002"
    private val types = mapOf(h to "regular", p to "tilawah")
    @Test fun sameStudentCanParticipateWithoutMixingSessionScopes() {
        val regular = ReportScopeRules.ids(WorkMode.REGULAR, "all", types)
        val program = ReportScopeRules.ids(WorkMode.PROGRAM, "all", types)
        assertEquals(setOf(h),regular)
        assertEquals(setOf(p),program)
        assertTrue(ReportScopeRules.contains(h,listOf(p),regular))
        assertTrue(ReportScopeRules.contains(h,listOf(p),program))
        assertEquals("&sessions.halaqa_id=in.($h)",ReportScopeRules.sessionFilter("all",regular))
        assertEquals("&sessions.halaqa_id=in.($p)",ReportScopeRules.sessionFilter("all",program))
        assertNotEquals(ReportScopeRules.cachePart(regular),ReportScopeRules.cachePart(program))
    }
    @Test fun unknownOrMismatchedScopeFailsClosed() {
        assertTrue(ReportScopeRules.ids(WorkMode.PROGRAM,h,types).isEmpty())
        assertTrue(ReportScopeRules.ids(WorkMode.REGULAR,"all",emptyMap()).isEmpty())
        assertTrue(runCatching { ReportScopeRules.sessionFilter("all",emptySet()) }.isFailure)
        assertTrue(runCatching { ReportScopeRules.sessionFilter(h,setOf(p)) }.isFailure)
    }
}
