package com.mohammedsamir.halaqati.quran

import org.junit.Assert.assertEquals
import org.junit.Test

class RecitationParityRulesCountTest {
    @Test
    fun aDistinctLineCountReplacesTheStoredPages() {
        assertEquals(1.0, RecitationParityRules.effectivePagesFromCount(0.4, 15), 1e-9)
        assertEquals(0.5, RecitationParityRules.effectivePagesFromCount(9.0, 7).let { Math.round(it * 2) / 2.0 }, 1e-9)
    }

    @Test
    fun noLinesMeansTheStoredPagesNeverNegative() {
        assertEquals(2.5, RecitationParityRules.effectivePagesFromCount(2.5, 0), 1e-9)
        assertEquals(0.0, RecitationParityRules.effectivePagesFromCount(-3.0, 0), 1e-9)
    }

    @Test
    fun theCountFormMatchesTheListFormForTheSameLines() {
        val keys = listOf("1:1", "1:2", "1:2", "1:3", "")
        val distinctNonBlank = keys.filter { it.isNotBlank() }.distinct().size
        assertEquals(RecitationParityRules.effectivePages(0.0, keys.filter { it.isNotBlank() }), RecitationParityRules.effectivePagesFromCount(0.0, distinctNonBlank), 1e-9)
    }
}
