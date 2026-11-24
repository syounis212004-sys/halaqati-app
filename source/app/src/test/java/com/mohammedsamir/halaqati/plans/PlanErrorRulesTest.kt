package com.mohammedsamir.halaqati.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanErrorRulesTest {
    @Test fun overlapErrorIsExplainedInArabic() {
        val m = PlanErrorRules.friendly("RC11.2: smart plan phases may not overlap")
        assertTrue(m.contains("تتداخل"))
        assertTrue(!m.contains("RC11"))
    }

    @Test fun datesOrderErrorsAreExplained() {
        assertTrue(PlanErrorRules.friendly("RC11.3: Sard must start after Hifz for plan 1").contains("بعد"))
        assertTrue(PlanErrorRules.friendly("RC11.3: invalid Hifz date range for plan 1").contains("الحفظ"))
        assertTrue(PlanErrorRules.friendly("RC11.3: invalid Sard date range for plan 1").contains("السرد"))
    }

    @Test fun anythingElseIsKeptAsIs() {
        assertEquals("خطأ آخر", PlanErrorRules.friendly("خطأ آخر"))
        assertEquals("تعذر تنفيذ العملية", PlanErrorRules.friendly(""))
    }
}
