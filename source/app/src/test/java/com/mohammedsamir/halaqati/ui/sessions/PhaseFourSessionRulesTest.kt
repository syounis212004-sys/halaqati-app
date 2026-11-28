package com.mohammedsamir.halaqati.ui.sessions
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PhaseFourSessionRulesTest {
    private val today = LocalDate.of(2026,11,28)
    @Test fun dateAndExplicitFinishDetermineDisplayStatus() {
        assertEquals("منتهية", SessionWorkflowRules.displayStatus("open","2026-11-27",false,today))
        assertEquals("جارية", SessionWorkflowRules.displayStatus("open","2026-11-28",false,today))
        assertEquals("قادمة", SessionWorkflowRules.displayStatus("open","2026-11-29",false,today))
        assertEquals("منتهية", SessionWorkflowRules.displayStatus("closed","2026-11-28",false,today))
        assertEquals("مؤرشفة", SessionWorkflowRules.displayStatus("open","2026-11-27",true,today))
        assertEquals("ملغاة", SessionWorkflowRules.displayStatus("cancelled","2026-11-27",false,today))
    }
    @Test fun completedRecordsRemainEditable() {
        assertTrue(SessionWorkflowRules.canRecord("closed",false,"2026-11-28",today))
        assertTrue(SessionWorkflowRules.canRecord("open",false,"2026-11-27",today))
        assertFalse(SessionWorkflowRules.canRecord("cancelled",false,"2026-11-27",today))
        assertFalse(SessionWorkflowRules.canRecord("open",true,"2026-11-27",today))
        assertFalse(SessionWorkflowRules.canRecord("open",false,"2026-11-29",today))
    }
    @Test fun educationNeverUsesMemorizationToolsEvenInRegularCircle() {
        assertFalse(SessionWorkflowRules.usesRecitationTools("lesson"))
        assertTrue(SessionWorkflowRules.usesRecitationTools("tasmee"))
        assertTrue(SessionWorkflowRules.usesRecitationTools("tilawah"))
        assertFalse(SessionWorkflowRules.usesRecitationTools("other"))
    }
}
