package com.mohammedsamir.halaqati.plans

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
}
