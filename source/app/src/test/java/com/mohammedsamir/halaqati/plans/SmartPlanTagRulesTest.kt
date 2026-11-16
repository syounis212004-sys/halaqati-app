package com.mohammedsamir.halaqati.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartPlanTagRulesTest {
    @Test fun jsonNullLikeIdsBecomeNull() {
        assertNull(SmartPlanTagRules.normalizeId(null))
        assertNull(SmartPlanTagRules.normalizeId(""))
        assertNull(SmartPlanTagRules.normalizeId(" null "))
        assertNull(SmartPlanTagRules.normalizeId("undefined"))
        assertEquals("plan-1", SmartPlanTagRules.normalizeId(" plan-1 "))
    }
}
