package com.mohammedsamir.halaqati.quran

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranMetadataTest {
    @Test fun metadataShapeMatchesMadinahHafs() {
        assertEquals(114, QuranMetadata.surahNames.size)
        assertEquals(6236, QuranMetadata.ayahCounts.sum())
        assertEquals(606, QuranMetadata.pageStarts.size)
        assertEquals(32, QuranMetadata.juzStarts.size)
        assertEquals(242, QuranMetadata.rubStarts.size)
        assertEquals(1, QuranMetadata.pageStarts[1])
        assertEquals(6237, QuranMetadata.pageStarts[605])
    }

    @Test fun knownBoundariesResolveCorrectly() {
        assertEquals(1, QuranMetadata.pageFor(1, 1))
        assertEquals(2, QuranMetadata.pageFor(2, 1))
        assertEquals(22, QuranMetadata.pageFor(2, 142))
        assertEquals(30, QuranMetadata.juzFor(78, 1))
        assertEquals(112, QuranMetadata.pageStart(604).surah)
        assertEquals(114, QuranMetadata.pageEnd(604).surah)
    }

    @Test fun globalLocationsRoundTripWithoutRangeNormalization() {
        val global = QuranMetadata.globalAyah(42, 27)
        assertEquals(QuranLocation(42, 27), QuranMetadata.location(global))
    }

}
