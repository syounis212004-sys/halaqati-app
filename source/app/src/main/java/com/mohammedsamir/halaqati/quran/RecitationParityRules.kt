package com.mohammedsamir.halaqati.quran

/**
 * Small pure rules shared by RC8.2 recitation cards, rankings and Smart Plan context.
 * They mirror the v2.44 behaviour: QCF4 line keys are authoritative, and rankings
 * never mix program tracks or activity tracks.
 */
object RecitationParityRules {
    private val reviewActivities = setOf("review_near", "review_far", "review")

    fun effectivePages(storedPages: Double, qcfLineKeys: List<String>): Double =
        if (qcfLineKeys.isNotEmpty()) qcfLineKeys.distinct().size.toDouble() / 15.0
        else storedPages.coerceAtLeast(0.0)

    /** Same rule as [effectivePages] when only the number of distinct non-blank line keys is known (the ranking source RPC returns the count, not the keys). */
    fun effectivePagesFromCount(storedPages: Double, distinctLineCount: Int): Double =
        if (distinctLineCount > 0) distinctLineCount.toDouble() / 15.0 else storedPages.coerceAtLeast(0.0)

    fun rankingTrackForActivity(activityType: String): String = when (activityType) {
        "new_hifz", "hifz" -> "hifz"
        in reviewActivities -> "review"
        "tathbit" -> "review" // tathbit students keep their own ranking bucket (program track tathbit)
        "sard" -> "sard"
        else -> "other"
    }

    fun activityMatchesRanking(activityType: String, rankingTrack: String): Boolean =
        rankingTrackForActivity(activityType) == rankingTrack

    fun canonicalProgramTrack(track: String?): String =
        if (track == "tathbit") "tathbit" else "general_hifz"

    fun quranRangeLabel(
        startSurah: Int?,
        startAyah: Int?,
        endSurah: Int?,
        endAyah: Int?,
        startPage: Int?,
        endPage: Int?,
    ): String {
        val exactStart = if (startSurah != null && startAyah != null) QuranLocation(startSurah, startAyah) else null
        val exactEnd = if (endSurah != null && endAyah != null) QuranLocation(endSurah, endAyah) else null
        val pageStart = startPage?.takeIf { it in 1..QuranMetadata.PAGE_COUNT }?.let(QuranMetadata::pageStart)
        val pageEnd = (endPage ?: startPage)?.takeIf { it in 1..QuranMetadata.PAGE_COUNT }?.let(QuranMetadata::pageEnd)
        val from = exactStart ?: pageStart
        val to = exactEnd ?: pageEnd ?: from
        if (from != null && to != null) {
            return if (from.surah == to.surah) {
                "سورة ${QuranMetadata.surahName(from.surah)} · من الآية ${from.ayah} إلى الآية ${to.ayah}"
            } else {
                "من سورة ${QuranMetadata.surahName(from.surah)} آية ${from.ayah} إلى سورة ${QuranMetadata.surahName(to.surah)} آية ${to.ayah}"
            }
        }
        return "نطاق قرآني محفوظ"
    }

    fun pageRangeQuranLabel(startPage: Int?, endPage: Int?): String =
        quranRangeLabel(null, null, null, null, startPage, endPage)
}
