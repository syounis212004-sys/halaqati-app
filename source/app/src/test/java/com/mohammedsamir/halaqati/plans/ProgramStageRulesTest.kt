package com.mohammedsamir.halaqati.plans

import org.junit.Assert.*
import org.junit.Test

class ProgramStageRulesTest {
    @Test fun stagesBelongToRecitationFamilyRatherThanDeliveryKind() {
        assertTrue(ProgramKindRules.isRecitationFamily("tilawah", "tilawah"))
        assertTrue(ProgramKindRules.isRecitationFamily("course", "tajweed_practical"))
        assertTrue(ProgramKindRules.isRecitationFamily("course", "noorania"))
        assertFalse(ProgramKindRules.isRecitationFamily("training", "teacher_training"))
        assertFalse(ProgramKindRules.isRecitationFamily("regular", "hifz"))
        assertEquals("تأهيلية", ProgramKindRules.stageLabel("qualifying"))
        assertEquals("تهيئة السند", ProgramKindRules.stageLabel("sanad_preparation"))
        assertNull(ProgramKindRules.validStage("regular", "hifz", "qualifying"))
        assertNull(ProgramKindRules.validStage("tilawah", "tilawah", "unknown"))
        assertEquals("qualifying", ProgramKindRules.validStage("course", "tilawah", "qualifying"))
    }
}
