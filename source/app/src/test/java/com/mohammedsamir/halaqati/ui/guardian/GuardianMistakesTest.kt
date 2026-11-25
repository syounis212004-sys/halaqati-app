package com.mohammedsamir.halaqati.ui.guardian

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** A guardian keeps seeing his child's mistakes and prompts for the latest recitation day. */
class GuardianMistakesTest {
    private fun json(mistakes: Int, prompts: Int) = JSONObject(
        """{"student":{"full_name":"طالب","track":"hifz"},"today":{},"month":{},
           "last_day":{"date":"2026-11-24","is_today":true,"mistakes":$mistakes,"prompts":$prompts,"quality":94.5}}""",
    )

    @Test fun childMistakesAndPromptsOfTheLastDayAreParsed() {
        val o = GuardianOverview.parse(json(2, 1))
        assertNotNull(o?.lastDay)
        assertEquals(2, o!!.lastDay!!.mistakes); assertEquals(1, o.lastDay!!.prompts); assertEquals(94.5, o.lastDay!!.quality!!, 0.001)
    }

    @Test fun missingLastDayIsNotAnError() {
        val o = GuardianOverview.parse(JSONObject("""{"student":{"full_name":"طالب"},"today":{},"month":{}}"""))
        assertNotNull(o); assertEquals(null, o!!.lastDay)
    }
}
