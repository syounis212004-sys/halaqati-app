package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyScoringTest {
    private fun ev(pages: Double, m: Int, p: Int, lines: Double? = null): String =
        SafetyScoring.inferredEvaluation(SafetyScoring.calculate(pages, m, p, lines))

    @Test fun onePageBoundaries() {
        assertEquals(100.0, SafetyScoring.calculate(1.0, 0, 0), 0.0)
        assertEquals(95.0, SafetyScoring.calculate(1.0, 0, 1), 0.0)
        assertEquals(90.0, SafetyScoring.calculate(1.0, 0, 2), 0.0)
        assertEquals("excellent", ev(1.0, 0, 2))
        assertEquals(80.0, SafetyScoring.calculate(1.0, 1, 0), 0.0)
        assertEquals("very_good", ev(1.0, 1, 0))
        assertEquals(75.0, SafetyScoring.calculate(1.0, 1, 1), 0.0)
        assertEquals(70.0, SafetyScoring.calculate(1.0, 1, 2), 0.0)
        assertEquals("good", ev(1.0, 1, 2))
    }

    @Test fun overTheAllowanceIsAlwaysRepeat() {
        assertEquals("repeat", ev(1.0, 1, 3))
        assertEquals("repeat", ev(1.0, 2, 0))
        assertEquals("repeat", ev(1.0, 0, 3))
        assertTrue(SafetyScoring.calculate(1.0, 0, 3) < 70.0)
    }

    @Test fun allowanceGrowsWithPages() {
        assertEquals(85.0, SafetyScoring.calculate(2.0, 1, 2), 0.0)
        assertEquals("good", ev(2.0, 2, 4))
        assertEquals("repeat", ev(2.0, 3, 0))
        assertEquals("repeat", ev(2.0, 0, 5))
    }

    @Test fun shortSegmentsUseTheirLineCount() {
        assertEquals(89.3, SafetyScoring.calculate(7.0 / 15.0, 0, 1, 7.0), 0.05)
        assertEquals(90.6, SafetyScoring.calculate(8.0 / 15.0, 0, 1, 8.0), 0.05)
        assertEquals(0.0, SafetyScoring.calculate(0.0, 0, 0), 0.0)
    }

    @Test fun pointsKeepTheirShape() {
        assertEquals(0.88, SafetyScoring.points(1.0, 0, 2), 0.0001)
        assertEquals(0.75, SafetyScoring.points(1.0, 1, 0), 0.0001)
        assertEquals(0.63, SafetyScoring.points(1.0, 1, 2), 0.0001)
    }
}
