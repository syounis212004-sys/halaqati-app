package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentInsightEngineTest {
    @Test fun separatesPerformanceLevelFromPlanDelay() {
        val state = StudentInsightEngine.build(
            requiredToDate = 40.0, completed = 37.0, averageSafety = 94.0,
            mistakes = 2, prompts = 1, present = 15, absent = 0, excused = 0, late = 0,
        )
        assertEquals(StudentInsightEngine.PerformanceLevel.VERY_GOOD, state.performanceLevel)
        assertTrue(state.followUpReasons.any { it.kind == StudentInsightEngine.FollowUpKind.PLAN_DELAY })
    }

    @Test fun weakQualityCanRequireFollowUpEvenWithGoodQuantity() {
        val state = StudentInsightEngine.build(
            requiredToDate = 40.0, completed = 40.0, averageSafety = 70.0,
            mistakes = 15, prompts = 5, present = 15, absent = 0, excused = 0, late = 0,
        )
        assertEquals(StudentInsightEngine.PerformanceLevel.NEEDS_FOLLOW_UP, state.performanceLevel)
        assertTrue(state.followUpReasons.any { it.kind == StudentInsightEngine.FollowUpKind.QUALITY })
    }
}
