package com.mohammedsamir.halaqati.plans

import com.mohammedsamir.halaqati.quran.QuranLocation
import java.time.LocalDate

/**
 * RC26 pure rules: detect a monthly hifz plan whose stored Quran range no longer matches where the
 * student really is, and decide the corrected start. A plan rolled into a new month keeps last
 * month's absolute range; a student who passes that end (or started elsewhere) got no daily duty and
 * the engine stopped counting pages outside the old range.
 */
object PlanRangeRebaseRules {
    /** ROUTE_COMPLETE: the student reached the last location of the hifz route; there is no range left to rebase. */
    enum class Status { OK, STALE_END, START_MISMATCH, NO_ANCHOR, ROUTE_COMPLETE }

    data class Diagnosis(
        val status: Status,
        val latestEnd: QuranLocation?,
        val expectedStart: QuranLocation?,
    )

    private fun isReverse(route: String) = route == "reverse_surahs_forward_ayahs"

    /**
     * smart_plans_check2 requires range_end_page >= range_start_page. A reverse-route range (An-Nas toward
     * Al-Baqarah) naturally starts on a higher page than it ends, so it was rejected with SQLSTATE 23514
     * and the edit stayed blocked in the Outbox. Always store the pair ascending.
     */
    fun normalizePages(start: Int?, end: Int?): Pair<Int?, Int?> =
        if (start != null && end != null && start > end) end to start else start to end

    /** Monotonic position along the route (bigger = further along). */
    fun position(location: QuranLocation, route: String): Long {
        val order = if (isReverse(route)) 200 - location.surah else location.surah
        return order * 1000L + location.ayah
    }

    private fun isPassing(evaluation: String?): Boolean =
        evaluation.orEmpty().lowercase() !in setOf("repeat", "weak", "إعادة", "ضعيف")

    private fun hifzEnds(recitations: List<SmartPlanEngine.Recitation>): List<SmartPlanEngine.Recitation> =
        recitations.filter {
            SmartPlanActivityRules.activityMatches("hifz", it.activityType) &&
                it.endSurah != null && it.endAyah != null && isPassing(it.evaluation)
        }

    private fun latest(list: List<SmartPlanEngine.Recitation>): SmartPlanEngine.Recitation? =
        list.maxWithOrNull(compareBy<SmartPlanEngine.Recitation> { it.date }.thenBy { it.createdAt })

    fun latestEnd(recitations: List<SmartPlanEngine.Recitation>): QuranLocation? =
        latest(hifzEnds(recitations))?.let { QuranLocation(it.endSurah!!, it.endAyah!!) }

    /** First location of the month: right after the last accepted hifz before the plan month began. */
    fun expectedStart(recitations: List<SmartPlanEngine.Recitation>, planStart: LocalDate, route: String): QuranLocation? {
        val ends = hifzEnds(recitations)
        latest(ends.filter { it.date < planStart })?.let { prev ->
            return SmartPlanActivityRules.nextLocation(QuranLocation(prev.endSurah!!, prev.endAyah!!), route)
        }
        val first = ends.filter { it.date >= planStart }
            .minWithOrNull(compareBy<SmartPlanEngine.Recitation> { it.date }.thenBy { it.createdAt })
        return first?.takeIf { it.startSurah != null && it.startAyah != null }
            ?.let { QuranLocation(it.startSurah!!, it.startAyah!!) }
    }

    fun diagnose(
        recitations: List<SmartPlanEngine.Recitation>,
        planStart: LocalDate,
        route: String,
        rangeStart: QuranLocation?,
        rangeEnd: QuranLocation?,
        terminal: QuranLocation? = null,
    ): Diagnosis {
        val latestEnd = latestEnd(recitations)
        val expected = expectedStart(recitations, planStart, route)
        // Finished the whole route (e.g. An-Nas toward Al-Baqarah, ending at 2:286): a new range cannot exist.
        if (terminal != null && latestEnd != null && position(latestEnd, route) >= position(terminal, route)) {
            return Diagnosis(Status.ROUTE_COMPLETE, latestEnd, expected)
        }
        if (terminal != null && expected != null && position(expected, route) > position(terminal, route)) {
            return Diagnosis(Status.ROUTE_COMPLETE, latestEnd, expected)
        }
        if (rangeEnd != null && latestEnd != null && position(latestEnd, route) >= position(rangeEnd, route)) {
            return Diagnosis(if (expected == null) Status.NO_ANCHOR else Status.STALE_END, latestEnd, expected)
        }
        if (expected != null && rangeStart != null && expected != rangeStart) {
            return Diagnosis(Status.START_MISMATCH, latestEnd, expected)
        }
        return Diagnosis(Status.OK, latestEnd, expected)
    }

    /**
     * Number of lines the corrected range must hold: the month's [wanted] lines PLUS everything the student
     * already memorised since the range start, so a student who is ahead is not reported as "beyond the end"
     * again right after the range is rebased.
     */
    fun rebasedLineCount(wanted: Int, alreadyCovered: Int): Int = wanted.coerceAtLeast(1) + alreadyCovered.coerceAtLeast(0)

    /**
     * RC27.6: a student plan whose stored Quran range holds far fewer lines than its own monthly target
     * (e.g. one line for a 45-page target after a start-only pick) can never produce a daily duty.
     * Only an UNDERSIZED range is expanded; a range at or above half the target is never touched.
     * [lineCount] is the number of stored line keys, or the page span x 15 when only pages are stored.
     */
    fun isUndersized(lineCount: Int?, targetPages: Double): Boolean {
        if (lineCount == null || targetPages <= 0.0) return false
        val wanted = kotlin.math.ceil(targetPages * 15.0).toInt()
        return lineCount < (wanted / 2).coerceAtLeast(1)
    }

    /** Why the smart daily hifz duty is empty, in plain Arabic; never a bare "no amount required". */
    fun emptyHifzDutyMessage(latestEnd: QuranLocation?, rangeEnd: QuranLocation?, route: String, terminal: QuranLocation? = null): String = when {
        latestEnd != null && terminal != null && position(latestEnd, route) >= position(terminal, route) ->
            "أتمّ الطالب مسار الحفظ كاملًا ووصل إلى نهايته. أنشئ له خطة مراجعة أو تثبيت أو غيّر مساره."
        latestEnd == null -> "لا يوجد تسميع حفظ سابق لتحديد نقطة البداية. سجّل أول تسميع للطالب ليظهر المقرر."
        else -> "لم يُحسب مقدار الحفظ لهذا اليوم. راجع هدف الخطة وأيام الدوام."
    }
}
