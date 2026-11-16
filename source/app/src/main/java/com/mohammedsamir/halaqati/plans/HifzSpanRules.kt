package com.mohammedsamir.halaqati.plans

import com.mohammedsamir.halaqati.quran.QuranLocation

/**
 * RC27.8: ONE definition of "pages heard" for new hifz, shared by the plan screen, ranking, statistics,
 * reports and the student profile: the continuous stretch from the FIRST ayah recited in the period to the
 * LAST ayah reached along the route (15 lines per page). Recitations without ayah data are added by their
 * own pages_count. Callers pass only entries of the period being shown (current month by default).
 */
object HifzSpanRules {
    const val DEFAULT_ROUTE = "reverse_surahs_forward_ayahs"

    data class Entry(
        val startSurah: Int?, val startAyah: Int?, val endSurah: Int?, val endAyah: Int?,
        val pagesCount: Double = 0.0,
    ) {
        val hasAyahs: Boolean get() = startSurah != null && startAyah != null && endSurah != null && endAyah != null
    }

    /** Pages for [entries]; null counter or counting failure falls back to the plain pages_count sum. */
    fun pages(
        entries: List<Entry>,
        route: String = DEFAULT_ROUTE,
        counter: ((QuranLocation, QuranLocation, String) -> Int?)? = SmartPlanEngine.spanLineCounter,
    ): Double {
        val ayahLevel = entries.filter { it.hasAyahs }
        val loose = entries.filter { !it.hasAyahs }.sumOf { it.pagesCount.coerceAtLeast(0.0) }
        val sum = entries.sumOf { it.pagesCount.coerceAtLeast(0.0) }
        if (ayahLevel.isEmpty()) return loose
        val c = counter ?: return sum
        fun loc(s: Int?, a: Int?) = QuranLocation(s!!, a!!)
        val base = ayahLevel.map { loc(it.startSurah, it.startAyah) }
            .minByOrNull { PlanRangeRebaseRules.position(it, route) } ?: return sum
        val end = ayahLevel.map { loc(it.endSurah, it.endAyah) }
            .maxByOrNull { PlanRangeRebaseRules.position(it, route) } ?: return sum
        val lines = c(base, end, route) ?: return sum
        return lines / 15.0 + loose
    }
}
