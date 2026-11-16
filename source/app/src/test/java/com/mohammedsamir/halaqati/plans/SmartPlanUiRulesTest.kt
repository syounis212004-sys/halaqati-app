package com.mohammedsamir.halaqati.plans

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartPlanUiRulesTest {
    @Test fun completeEarlyUsesCelebrationToneAndSurplusCopy() {
        val visual = SmartPlanUiRules.visualFor(
            SmartPlanCoreStateEngine.StatusCode.COMPLETE_EARLY,
            surplusPages = 2.5,
        )
        assertEquals(SmartPlanUiRules.Tone.SUCCESS, visual.tone)
        assertEquals("أتم الخطة مبكرًا", visual.label)
        assertEquals("+2.5 ص إضافية", visual.supportingText)
    }

    @Test fun behindUsesWarningToneWithoutNegativeCopy() {
        val visual = SmartPlanUiRules.visualFor(
            SmartPlanCoreStateEngine.StatusCode.BEHIND,
            surplusPages = 0.0,
        )
        assertEquals(SmartPlanUiRules.Tone.WARNING, visual.tone)
        assertEquals("يحتاج تعويض", visual.label)
        assertEquals("", visual.supportingText)
    }
    @Test fun performanceBandUsesRc19TrafficLightClassificationAgainstRequiredToDate() {
        assertEquals("متقدم", SmartPlanUiRules.performanceBand(115.0).label)
        assertEquals("على المسار", SmartPlanUiRules.performanceBand(95.0).label)
        assertEquals("يحتاج انتباه", SmartPlanUiRules.performanceBand(80.0).label)
        assertEquals("يحتاج متابعة", SmartPlanUiRules.performanceBand(60.0).label)
        assertEquals(100.0, SmartPlanUiRules.schedulePerformancePercent(3.0, 0.0), 0.001)
    }

}
