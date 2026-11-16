package com.mohammedsamir.halaqati.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlanHealthRulesTest {
    @Test fun classifiesAgainstRequiredToDate() {
        assertEquals("blue", SmartPlanHealthRules.fromProgress(12.0, 10.0).code)
        assertEquals("green", SmartPlanHealthRules.fromProgress(9.5, 10.0).code)
        assertEquals("yellow", SmartPlanHealthRules.fromProgress(8.0, 10.0).code)
        assertEquals("red", SmartPlanHealthRules.fromProgress(7.4, 10.0).code)
    }

    @Test fun redMeansMoreThanTwentyFivePercentBehind() {
        assertEquals("yellow", SmartPlanHealthRules.fromRatio(.75).code)
        assertEquals("red", SmartPlanHealthRules.fromRatio(.749).code)
    }

    @Test fun guardianNotificationRequiresTwoRedScheduledDays() {
        assertFalse(SmartPlanHealthRules.shouldNotifyGuardian(1))
        assertTrue(SmartPlanHealthRules.shouldNotifyGuardian(2))
    }
}
