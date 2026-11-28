package com.mohammedsamir.halaqati.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilawahScoreV2Test {
    private fun score(errors: Int = 0, reviewed: Boolean = true, adherence: Int? = null, rating: Int = 5) =
        TilawahScoreV2.score(rating, rating, rating, adherence, reviewed, errors)

    @Test fun fullCreditAndMissingCorrection() {
        assertEquals(100.0, score(), 0.0001)
        assertEquals(100.0, score(adherence = 5), 0.0001)
        assertEquals(77.5, score(adherence = 0), 0.0001)
        assertTrue(score() > score(adherence = 0))
    }

    @Test fun penaltiesAndBounds() {
        assertEquals(92.0, score(errors = 1), 0.0001)
        assertEquals(90.0, score(reviewed = false), 0.0001)
        assertEquals(82.0, score(errors = 1, reviewed = false), 0.0001)
        assertEquals(0.0, score(errors = 100), 0.0001)
        assertEquals(0.0, score(rating = 0, reviewed = false), 0.0001)
    }

    @Test fun changesAreMonotonic() {
        for (a in 0..5) for (m in 0..10) {
            assertTrue(score(errors = m + 1, adherence = a) <= score(errors = m, adherence = a))
            assertTrue(score(errors = m, adherence = a, reviewed = false) <= score(errors = m, adherence = a))
            if (a < 5) assertTrue(score(adherence = a + 1, errors = m) >= score(adherence = a, errors = m))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeCounts() { score(errors = -1) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeRating() { score(adherence = 6) }
}
