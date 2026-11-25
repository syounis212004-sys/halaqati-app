package com.mohammedsamir.halaqati.quran

import org.junit.Assert.assertEquals
import org.junit.Test

class MushafJuzPagesTest {
    @Test fun juzPagesCoverTheWholeMushaf() {
        assertEquals(1, MushafOfflinePlan.juzPages(1).first)
        assertEquals(604, MushafOfflinePlan.juzPages(30).last)
        assertEquals(604, MushafOfflinePlan.pagesFor((1..30).toSet()).size)
    }
}
