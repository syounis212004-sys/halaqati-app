package com.mohammedsamir.halaqati.plans

import java.time.LocalDate

/**
 * Sard days for the bulk Hifz -> Sard sheet (same rule as the web, see phase-days.js).
 * The sheet starts from the days the students really have and only overwrites them when the manager changes the
 * common days or picks days for one student.
 */
object PhaseDaysRules {
    private fun clean(days: Collection<Int>?): List<Int> = days.orEmpty().filter { it in 0..6 }.distinct().sorted()

    /** The most common non-empty set of the students' own Sard days; null when nobody has one. */
    fun commonDays(lists: List<Collection<Int>?>): List<Int>? =
        lists.map { clean(it) }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key

    /** [custom]: days picked for this student in the sheet; [existing]: the student's current phase days. */
    fun resolve(custom: Collection<Int>?, common: Collection<Int>, initial: Collection<Int>, existing: Collection<Int>?): List<Int> {
        val c = clean(custom)
        if (c.isNotEmpty()) return c
        val e = clean(existing)
        if (e.isNotEmpty() && clean(common) == clean(initial)) return e
        return clean(common)
    }

    data class SardPace(val expected: Double, val behind: Double, val ahead: Double, val health: String)

    /**
     * Pace of a Sard phase against its own schedule (same as the web's sardPace): pages due so far = target x scheduled days already
     * passed / scheduled days, with the plan engine's bands (ratio > 1.10 ahead, >= 0.90 on track, >= 0.75 needs attention, else follow-up).
     */
    fun sardPace(target: Double?, completed: Double, scheduledDays: Int, remainingDays: Int): SardPace {
        val t = target ?: 0.0
        val elapsed = maxOf(0, scheduledDays - remainingDays)
        val expected = if (t > 0.0 && scheduledDays > 0) Math.round(t * elapsed / scheduledDays * 100.0) / 100.0 else 0.0
        if (!(expected > 0.001)) return SardPace(0.0, 0.0, 0.0, "green")
        val ratio = completed / expected
        val behind = maxOf(0.0, Math.round((expected - completed) * 100.0) / 100.0)
        val ahead = maxOf(0.0, Math.round((completed - expected) * 100.0) / 100.0)
        val health = when { ratio > 1.10 -> "blue"; ratio >= 0.90 -> "green"; ratio >= 0.75 -> "yellow"; else -> "red" }
        return SardPace(expected, if (ratio in 0.90..1.10) 0.0 else behind, if (ratio > 1.10) ahead else 0.0, health)
    }

    /** Day numbers follow the app: 0 = Sunday ... 5 = Friday, 6 = Saturday. */
    val SUN_TO_FRI = listOf(0, 1, 2, 3, 4, 5)
    val SUN_TO_THU = listOf(0, 1, 2, 3, 4)
    val FRIDAY_ONLY = listOf(5)

    fun isScheduled(date: LocalDate, days: Collection<Int>): Boolean = (date.dayOfWeek.value % 7) in days

    /** Scheduled days between [from] and [end] (both included). */
    fun scheduledDays(from: LocalDate, end: LocalDate, days: Collection<Int>): Int {
        if (from > end) return 0
        val set = clean(days).toSet()
        var n = 0
        var d = from
        while (d <= end) { if ((d.dayOfWeek.value % 7) in set) n++; d = d.plusDays(1) }
        return n
    }

    /** level: ok | warn | error. [perDay] is the pages a student must recite on each remaining scheduled day. */
    data class DaysCheck(val daysLeft: Int, val perDay: Double?, val level: String, val message: String)

    /**
     * What the chosen days mean for the period that is still ahead (today onwards). A single scheduled day, or a very heavy daily
     * share, is the usual mistake (e.g. "Friday only" for a 25-page Sard that ends in a week), so it is said out loud before saving.
     */
    fun check(today: LocalDate, start: LocalDate, end: LocalDate, days: Collection<Int>, targetPages: Double?, heavyPerDay: Double = 8.0): DaysCheck {
        if (clean(days).isEmpty()) return DaysCheck(0, null, "error", "اختر يومًا واحدًا على الأقل.")
        val left = scheduledDays(maxOf(today, start), end, days)
        val per = if (targetPages != null && targetPages > 0.0 && left > 0) targetPages / left else null
        fun pages(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
        return when {
            left == 0 -> DaysCheck(0, null, "error", "لا يوجد أي يوم سرد متبقٍ في هذه الفترة بهذه الأيام.")
            left == 1 && targetPages != null && targetPages > 0.0 ->
                DaysCheck(1, per, "warn", "يوم سرد واحد فقط متبقٍ: كل الهدف (${pages(targetPages)} ص) يقع على هذا اليوم.")
            per != null && per > heavyPerDay ->
                DaysCheck(left, per, "warn", "$left أيام سرد متبقية ≈ ${pages(per)} ص لكل يوم، وهذا ثقيل. أضف أيامًا أو مدّد الفترة.")
            per != null -> DaysCheck(left, per, "ok", "$left أيام سرد متبقية ≈ ${pages(per)} ص لكل يوم.")
            else -> DaysCheck(left, null, "ok", "$left أيام سرد متبقية.")
        }
    }
}
