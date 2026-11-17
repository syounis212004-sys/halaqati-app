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

class QuickSaveActivityTest {
    /** The audit finding: a general-hifz student on a retry ("tathbit" assignment) could not be saved from the quick button. */
    @org.junit.Test fun retryAssignmentOfAHifzStudentIsSavedAsTheSessionActivity() {
        org.junit.Assert.assertEquals("new_hifz", TrackActivityPolicy.quickSaveActivity("general_hifz", "tathbit", "hifz"))
        org.junit.Assert.assertEquals("sard", TrackActivityPolicy.quickSaveActivity("general_hifz", "tathbit", "sard"))
        org.junit.Assert.assertEquals("review_near", TrackActivityPolicy.quickSaveActivity("general_hifz", "tathbit", "review"))
    }

    @org.junit.Test fun ordinaryAssignmentsKeepTheirActivity() {
        org.junit.Assert.assertEquals("new_hifz", TrackActivityPolicy.quickSaveActivity("general_hifz", "hifz", "hifz"))
        org.junit.Assert.assertEquals("sard", TrackActivityPolicy.quickSaveActivity("general_hifz", "sard", "hifz"))
        org.junit.Assert.assertEquals("review_near", TrackActivityPolicy.quickSaveActivity("general_hifz", "review", "hifz"))
        org.junit.Assert.assertEquals("test", TrackActivityPolicy.quickSaveActivity("general_hifz", "test", "hifz"))
    }

    @org.junit.Test fun tathbitTrackStudentsStayOnTathbit() {
        org.junit.Assert.assertEquals("tathbit", TrackActivityPolicy.quickSaveActivity("tathbit", "tathbit", "hifz"))
        org.junit.Assert.assertEquals("tathbit", TrackActivityPolicy.quickSaveActivity("tathbit", "review", "hifz"))
        // A hifz/sard assignment is not recordable for this track: fall back to tathbit instead of failing.
        org.junit.Assert.assertEquals("tathbit", TrackActivityPolicy.quickSaveActivity("tathbit", "sard", "sard"))
    }
}
