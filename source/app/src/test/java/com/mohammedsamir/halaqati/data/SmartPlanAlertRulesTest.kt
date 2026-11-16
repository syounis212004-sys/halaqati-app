package com.mohammedsamir.halaqati.data

import com.mohammedsamir.halaqati.quran.QuranLocation
import org.junit.Assert.*
import org.junit.Test

class SmartPlanAlertRulesTest {
    @Test fun delayThresholdIsInclusive() {
        assertFalse(SmartPlanAlertRules.shouldSendDelay(2, 3))
        assertTrue(SmartPlanAlertRules.shouldSendDelay(3, 3))
    }

    @Test fun homePrepNeedsEveningEnabledAndRange() {
        assertFalse(SmartPlanAlertRules.shouldSendHomePrep(16, true, 20))
        assertFalse(SmartPlanAlertRules.shouldSendHomePrep(18, false, 20))
        assertFalse(SmartPlanAlertRules.shouldSendHomePrep(18, true, null))
        assertTrue(SmartPlanAlertRules.shouldSendHomePrep(17, true, 20))
    }

    @Test fun studentTrackSelectsOnlyTwoPlanFamilies() {
        assertEquals("hifz", SmartPlanAlertRules.preferredPlanType("general_hifz"))
        assertEquals("hifz", SmartPlanAlertRules.preferredPlanType("sard"))
        assertEquals("tathbit", SmartPlanAlertRules.preferredPlanType("tathbit"))
    }

    @Test fun labelsAssignmentsInArabic() {
        val label = SmartPlanAlertRules.assignmentLabel("hifz", 20, 22)
        assertTrue(label.startsWith("حفظ ") && label.contains("سورة") && label.contains("آية"))
        assertFalse(label.contains("ص "))
        // RC24.2: no milestone/test wording; unknown kinds fall back to memorization
        assertFalse(SmartPlanAlertRules.assignmentLabel("test", 41, 41).contains("اختبار"))
        assertEquals(
            "حفظ سورة البقرة من الآية 255 إلى الآية 282",
            SmartPlanAlertRules.assignmentLabel("hifz", 42, 48, QuranLocation(2, 255), QuranLocation(2, 282)),
        )
        assertEquals(
            "سرد من سورة الفاتحة آية 1 إلى سورة البقرة آية 5",
            SmartPlanAlertRules.assignmentLabel("sard", 1, 2, QuranLocation(1, 1), QuranLocation(2, 5)),
        )
    }
}
