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
}
