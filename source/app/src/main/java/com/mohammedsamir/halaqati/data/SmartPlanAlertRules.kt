package com.mohammedsamir.halaqati.data

import com.mohammedsamir.halaqati.quran.QuranLocation
import com.mohammedsamir.halaqati.quran.QuranMetadata

/** Pure release-critical rules for smart-plan guardian alerts. */
object SmartPlanAlertRules {
    fun shouldSendDelay(consecutiveMissed: Int, delayAlertDays: Int): Boolean =
        delayAlertDays > 0 && consecutiveMissed >= delayAlertDays

    fun shouldSendHomePrep(localHour: Int, enabled: Boolean, startPage: Int?): Boolean =
        enabled && localHour >= 17 && startPage != null

    fun preferredPlanType(studentTrack: String): String =
        if (StudentTrackRules.canonicalStored(studentTrack) == StudentTrackRules.TATHBIT) "tathbit" else "hifz"

    /**
     * RC24.2: guardian/student alerts describe the requirement by ayah range, never by pages.
     * [exactStart]/[exactEnd] come from the plan's line keys (partial pages); otherwise the range of the
     * assignment's pages is converted to its first/last ayah. Smart plans no longer have milestone tests.
     */
    fun assignmentLabel(
        kind: String,
        startPage: Int?,
        endPage: Int?,
        exactStart: QuranLocation? = null,
        exactEnd: QuranLocation? = null,
    ): String {
        val range = when {
            exactStart != null && exactEnd != null -> ayahRange(exactStart, exactEnd)
            startPage == null -> "المقرر المحدد في الخطة"
            else -> {
                val lo = minOf(startPage, endPage ?: startPage).coerceIn(1, QuranMetadata.PAGE_COUNT)
                val hi = maxOf(startPage, endPage ?: startPage).coerceIn(1, QuranMetadata.PAGE_COUNT)
                ayahRange(QuranMetadata.pageStart(lo), QuranMetadata.pageEnd(hi))
            }
        }
        return when (kind) {
            "sard" -> "سرد $range"
            "tathbit" -> "تثبيت/إعادة $range"
            "review", "review_near" -> "مراجعة قريبة $range"
            "review_far" -> "مراجعة بعيدة $range"
            else -> "حفظ $range"
        }
    }

    fun ayahRange(from: QuranLocation, to: QuranLocation): String =
        if (from.surah == to.surah) {
            "سورة ${QuranMetadata.surahName(from.surah)} من الآية ${from.ayah} إلى الآية ${to.ayah}"
        } else {
            "من سورة ${QuranMetadata.surahName(from.surah)} آية ${from.ayah} إلى سورة ${QuranMetadata.surahName(to.surah)} آية ${to.ayah}"
        }
}
