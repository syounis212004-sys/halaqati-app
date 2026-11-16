package com.mohammedsamir.halaqati.plans

import com.mohammedsamir.halaqati.quran.QuranLocation

/**
 * RC27.5: a student's new hifz should begin where the previous one ended. A jump over whole surahs
 * (e.g. 71:13 then 55:1) usually means a session's data was lost or a recitation was typed wrong.
 * The rule only informs; it never blocks a save.
 */
object RecitationGapRules {
    data class Gap(val skippedSurahs: Int, val from: QuranLocation, val to: QuranLocation)

    private fun order(surah: Int, route: String) = if (route == "reverse_surahs_forward_ayahs") 200 - surah else surah

    /** Whole surahs strictly between the previous end and the new start, along the route. Null when contiguous or going backwards. */
    fun gap(previousEnd: QuranLocation?, newStart: QuranLocation?, route: String): Gap? {
        if (previousEnd == null || newStart == null) return null
        val between = order(newStart.surah, route) - order(previousEnd.surah, route) - 1
        return if (between >= 1) Gap(between, previousEnd, newStart) else null
    }

    fun message(gap: Gap): String =
        "ملاحظة: بين آخر حفظ (${gap.from.surah}:${gap.from.ayah}) وهذا التسميع (${gap.to.surah}:${gap.to.ayah}) " +
            "${gap.skippedSurahs} سورة لم تُسجَّل. تأكد أنه لم تضع جلسة."
}
