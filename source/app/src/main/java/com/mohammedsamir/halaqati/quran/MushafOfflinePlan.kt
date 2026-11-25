package com.mohammedsamir.halaqati.quran

/** Which printed pages a juz covers. (The mushaf pages are inside the app; nothing is downloaded.) */
object MushafOfflinePlan {
    /** Pages of one juz; the page a juz starts in the middle of is included in both juz. */
    fun juzPages(juz: Int): IntRange {
        require(juz in 1..QuranMetadata.JUZ_COUNT)
        val start = QuranMetadata.juzStart(juz).let { QuranMetadata.pageFor(it.surah, it.ayah) }
        val end = if (juz == QuranMetadata.JUZ_COUNT) QuranMetadata.PAGE_COUNT
        else QuranMetadata.juzStart(juz + 1).let { QuranMetadata.pageFor(it.surah, it.ayah) }
        return start..end
    }

    fun pagesFor(juzSet: Set<Int>): List<Int> = juzSet.flatMap { juzPages(it) }.distinct().sorted()

}
