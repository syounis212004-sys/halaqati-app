package com.mohammedsamir.halaqati.data

import org.junit.Assert.*
import org.junit.Test

class StudentTrackRulesTest {
    @Test fun contractHasExactlyTwoTracks() {
        assertEquals(setOf("general_hifz", "tathbit"), StudentTrackRules.allowed)
    }

    @Test fun legacySardCanonicalizesToGeneralHifz() {
        assertEquals("general_hifz", StudentTrackRules.canonicalStored("sard"))
        assertEquals("general_hifz", StudentTrackRules.parseInput("سرد"))
        assertEquals("tathbit", StudentTrackRules.parseInput("تثبيت"))
        assertNull(StudentTrackRules.parseInput("مسار ثالث"))
    }

    @Test fun generalFilterIncludesLegacySardRows() {
        assertEquals("track_code=in.(general_hifz,sard)", StudentTrackRules.postgrestFilter("track_code", "general_hifz"))
        assertEquals("track_code=eq.tathbit", StudentTrackRules.postgrestFilter("track_code", "tathbit"))
    }
}
