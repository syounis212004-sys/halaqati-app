package com.mohammedsamir.halaqati.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranRangeEngineTest {
    private val layout = object : QuranLineLayout {
        override val source = "test"
        override val lineModel = 15
        override fun firstLine(globalAyah: Int) = when (globalAyah) {
            QuranMetadata.globalAyah(1, 1) -> 2
            QuranMetadata.globalAyah(1, 2) -> 3
            QuranMetadata.globalAyah(1, 3) -> 3
            else -> 1
        }
        override fun lastLine(globalAyah: Int) = when (globalAyah) {
            QuranMetadata.globalAyah(1, 1) -> 2
            QuranMetadata.globalAyah(1, 2) -> 4
            QuranMetadata.globalAyah(1, 3) -> 5
            else -> 1
        }
    }
    private val engine = QuranRangeEngine(layout)

    @Test fun partialAyahRangeCountsUniqueQcfLines() {
        val result = engine.calculate(
            QuranRangeRequest("tathbit", "sard", QuranSelection.Ayahs(QuranLocation(1, 1), QuranLocation(1, 3)))
        )
        assertEquals(4.0 / 15.0, result.exactPages, 0.0000001)
        assertEquals(3, result.ayahCount)
    }

    @Test fun generalHifzMovesDownSurahNumbersButUpInsideEachSurah() {
        val result = engine.calculate(
            QuranRangeRequest("general_hifz", "new_hifz", QuranSelection.Ayahs(QuranLocation(42, 27), QuranLocation(41, 18)))
        )
        assertEquals(QuranResolvedTraversal.REVERSE_SURAH_ORDER, result.resolvedTraversal)
        assertEquals(QuranLocation(42, 27), result.orderedSegments[0].start)
        assertEquals(QuranLocation(42, 53), result.orderedSegments[0].end)
        assertEquals(QuranLocation(41, 1), result.orderedSegments[1].start)
        assertEquals(QuranLocation(41, 18), result.orderedSegments[1].end)
        assertTrue(result.orderedSegments.all { it.ayahDirection == QuranAyahDirection.ASCENDING })
    }

    @Test fun sardDescendingInsideOneSurahPreservesUserOrder() {
        val result = engine.calculate(
            QuranRangeRequest("tathbit", "sard", QuranSelection.Ayahs(QuranLocation(42, 53), QuranLocation(42, 27)))
        )
        assertEquals(QuranResolvedTraversal.REVERSE_WITHIN_SURAH, result.resolvedTraversal)
        assertEquals(QuranLocation(42, 53), result.start)
        assertEquals(QuranLocation(42, 27), result.end)
    }

    @Test fun generalHifzJuzThirtyStartsFromAnNas() {
        val result = engine.calculate(
            QuranRangeRequest("general_hifz", "new_hifz", QuranSelection.Juz(listOf(30)))
        )
        assertEquals(114, result.orderedSegments.first().start.surah)
        assertEquals(78, result.orderedSegments.last().start.surah)
        assertTrue(result.orderedSegments.all { it.ayahDirection == QuranAyahDirection.ASCENDING })
    }

    @Test fun generalHifzDirectPagesPreserveExplicitForwardOrder() {
        val result = engine.calculate(
            QuranRangeRequest("general_hifz", "new_hifz", QuranSelection.Pages(486, 488))
        )
        assertEquals(QuranResolvedTraversal.PAGE_ORDER_FORWARD, result.resolvedTraversal)
        assertEquals(3.0, result.exactPages, 0.0)
        assertEquals(486, result.startPage)
        assertEquals(488, result.endPage)
    }

    @Test fun directPagesPreserveDescendingInputOutsideGeneralHifz() {
        val result = engine.calculate(
            QuranRangeRequest("tathbit", "review_near", QuranSelection.Pages(550, 549))
        )
        assertEquals(QuranResolvedTraversal.PAGE_ORDER_REVERSE, result.resolvedTraversal)
        assertEquals(550, result.startPage)
        assertEquals(549, result.endPage)
        assertEquals(2.0, result.exactPages, 0.0)
        assertEquals(30, result.qcfLineKeys.size)
    }
    @Test fun sardPreviewPagesRespectsReverseSurahOrderAndAscendingAyahs() {
        val result = engine.previewPagesFrom(
            start = QuranLocation(114, 6),
            targetPages = 2.0 / 15.0,
            track = "general_hifz",
            activityType = "sard",
            direction = QuranTraversalDirection.REVERSE_SURAH_ORDER,
        )
        assertEquals(QuranLocation(114, 6), result.start)
        assertEquals(QuranLocation(111, 1), result.end)
        assertEquals(QuranResolvedTraversal.REVERSE_SURAH_ORDER, result.resolvedTraversal)
        assertTrue(result.orderedSegments.all { it.ayahDirection == QuranAyahDirection.ASCENDING })
    }

}
