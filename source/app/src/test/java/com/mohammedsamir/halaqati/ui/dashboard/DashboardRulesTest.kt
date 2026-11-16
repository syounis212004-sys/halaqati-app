package com.mohammedsamir.halaqati.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardRulesTest {
    @Test fun hiddenCountUsesAllRowsAfterPreviewLimit() {
        assertEquals(39, DashboardRules.hiddenCount(total = 41, preview = 2))
    }

    @Test fun recitationRangeDisplaysLowToHigh() {
        assertEquals("القصص 20–29", DashboardRules.displayRange("القصص", 29, 20))
    }
    @Test fun absenceBucketCountsOnlyAbsentOrExcused() {
        assertEquals(true, DashboardRules.isAbsentStatus("absent"))
        assertEquals(true, DashboardRules.isAbsentStatus("excused"))
        assertEquals(false, DashboardRules.isAbsentStatus("late"))
        assertEquals(false, DashboardRules.isAbsentStatus("present"))
    }

}
