package com.mohammedsamir.halaqati

import com.mohammedsamir.halaqati.data.SafetyScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyScoringTest {
    @Test fun approvedV6Boundaries() {
        assertEquals(100.0, SafetyScoring.calculate(1.0, 0, 0), 0.001)
        assertEquals(90.0, SafetyScoring.calculate(1.0, 0, 1), 0.001)
        assertEquals("excellent", SafetyScoring.inferredEvaluation(90.0))
        assertEquals(80.0, SafetyScoring.calculate(1.0, 0, 2), 0.001)
        assertEquals("very_good", SafetyScoring.inferredEvaluation(80.0))
        assertEquals(70.0, SafetyScoring.calculate(1.0, 1, 1), 0.001)
        assertEquals("good", SafetyScoring.inferredEvaluation(70.0))
        assertEquals("repeat", SafetyScoring.inferredEvaluation(SafetyScoring.calculate(1.0, 2, 0)))
    }

    @Test fun shortSegmentsNormalizeByActualLines() {
        val seven = SafetyScoring.calculate(7.0 / 15.0, 0, 1, heardLines = 7.0)
        val eight = SafetyScoring.calculate(8.0 / 15.0, 0, 1, heardLines = 8.0)
        assertTrue(kotlin.math.abs(seven - 78.6) < 0.11)
        assertTrue(kotlin.math.abs(eight - 81.3) < 0.11)
        assertEquals("good", SafetyScoring.inferredEvaluation(seven))
        assertEquals("very_good", SafetyScoring.inferredEvaluation(eight))
    }

    @Test fun pointsMatchLegacyJsRounding() {
        assertEquals(0.75, SafetyScoring.points(1.0, 0, 2), 0.001)
        assertEquals(0.75, SafetyScoring.points(1.0, 1, 0), 0.001)
        assertEquals(0.63, SafetyScoring.points(1.0, 1, 1), 0.001)
        assertEquals(1.63, SafetyScoring.points(2.0, 1, 1), 0.001)
    }

    @Test fun noContentIsNotRated() {
        assertEquals("not_rated", SafetyScoring.inferredEvaluation(0.0, hasContent = false))
    }
}
