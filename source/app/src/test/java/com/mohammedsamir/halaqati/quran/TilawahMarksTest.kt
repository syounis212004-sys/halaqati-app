package com.mohammedsamir.halaqati.quran

import org.junit.Assert.*
import org.junit.Test

class TilawahMarksTest {
    @Test fun independentCategoriesAndRepeats() {
        val word = "2:181:5"
        val first = TilawahMarks.add(emptyList(), listOf(word), 27, TilawahCategory.WORD)
        val both = TilawahMarks.add(first, listOf(word), 27, TilawahCategory.GHUNNAH)
        val repeated = TilawahMarks.add(both, listOf(word, word), 27, TilawahCategory.WORD)
        assertEquals(2, repeated.size)
        assertEquals(2, TilawahMarks.count(repeated, TilawahCategory.WORD))
        assertEquals(3, TilawahMarks.total(1, repeated, TilawahCategory.WORD))
        assertEquals(1, TilawahMarks.count(repeated, TilawahCategory.GHUNNAH))
        val corrected = TilawahMarks.correct(repeated, repeated.first().markId)
        assertTrue(corrected.first().corrected)
        assertEquals(2, TilawahMarks.count(corrected, TilawahCategory.WORD))
        assertEquals(1, TilawahMarks.remove(corrected, corrected.first().markId).size)
        assertTrue(TilawahMarks.eraseWords(repeated, listOf(word)).isEmpty())
        assertEquals(1, first.size)
    }

    @Test fun rejectsInvalidLocation() {
        assertTrue(runCatching { TilawahMarks.add(emptyList(), listOf("invalid"), 1, TilawahCategory.LETTER) }.isFailure)
        assertTrue(runCatching { TilawahMarks.add(emptyList(), listOf("2:1:1"), 605, TilawahCategory.LETTER) }.isFailure)
    }
}
