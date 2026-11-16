package com.mohammedsamir.halaqati.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** RC27.45: the birth date a guardian types is the second factor for linking a child. */
class NormalizeBirthDateTest {
    @Test fun acceptsIsoDate() {
        assertEquals("2015-03-27", normalizeBirthDate("2015-03-27"))
    }

    @Test fun acceptsArabicDigitsAndSlashes() {
        assertEquals("2015-03-27", normalizeBirthDate("٢٠١٥/٠٣/٢٧"))
    }

    @Test fun trimsSpaces() {
        assertEquals("2015-03-27", normalizeBirthDate("  2015-03-27 "))
    }

    @Test fun rejectsGarbageAndImpossibleDates() {
        assertNull(normalizeBirthDate("abc"))
        assertNull(normalizeBirthDate("2015-13-40"))
        assertNull(normalizeBirthDate(""))
    }
}
