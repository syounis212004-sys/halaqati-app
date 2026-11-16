package com.mohammedsamir.halaqati.plans

/**
 * RC26.2 pure rules for the "previous months" list on the Plans screen.
 *
 * Rolling a month updates the student's plan row in place and stores the finished month in
 * smart_plan_month_archives. The history list therefore has to read BOTH the plan rows that still
 * start in an earlier month and the archived month snapshots, otherwise earlier months look short.
 */
object PlanHistoryRules {
    data class Entry(
        val key: String,
        val month: String,       // yyyy-MM-dd of the month (any day)
        val studentId: String,
        val halaqaId: String?,
        val title: String,
        val status: String,      // Arabic label shown on the row
    )

    data class PlanInput(val id: String, val studentId: String, val halaqaId: String?, val startDate: String?, val title: String, val status: String)
    data class ArchiveInput(val id: String, val sourcePlanId: String?, val studentId: String, val halaqaId: String?, val periodMonth: String?, val title: String, val target: Double, val done: Double)

    fun statusLabel(status: String): String = when (status) {
        "active" -> "فعّالة"
        "completed" -> "مكتملة"
        "paused" -> "موقوفة"
        "archived" -> "مؤرشفة"
        "draft" -> "مسودة"
        else -> status
    }

    fun archiveLabel(target: Double, done: Double): String =
        if (target > 0.001 && done + 0.001 >= target) "مكتملة" else "منتهية"

    private fun monthPrefix(date: String?): String? = date?.trim()?.takeIf { it.length >= 7 }?.substring(0, 7)

    /**
     * @param currentMonth "yyyy-MM" of today; only strictly earlier months are returned.
     * A snapshot is dropped when the same plan row still sits in that very month (no double count).
     */
    fun merge(plans: List<PlanInput>, archives: List<ArchiveInput>, currentMonth: String, halaqaId: String?): List<Entry> {
        val out = ArrayList<Entry>()
        val livePlanMonth = HashSet<String>()
        plans.forEach { plan ->
            val m = monthPrefix(plan.startDate) ?: return@forEach
            if (m >= currentMonth) return@forEach
            if (halaqaId != null && plan.halaqaId != halaqaId) return@forEach
            livePlanMonth += plan.id + "|" + m
            out += Entry("plan-" + plan.id, plan.startDate!!, plan.studentId, plan.halaqaId, plan.title.ifBlank { "خطة" }, statusLabel(plan.status))
        }
        val seen = HashSet<String>()
        archives.forEach { a ->
            val m = monthPrefix(a.periodMonth) ?: return@forEach
            if (m >= currentMonth) return@forEach
            if (halaqaId != null && a.halaqaId != halaqaId) return@forEach
            if (a.sourcePlanId != null && (a.sourcePlanId + "|" + m) in livePlanMonth) return@forEach
            if (!seen.add(a.id)) return@forEach
            out += Entry("archive-" + a.id, a.periodMonth!!, a.studentId, a.halaqaId, a.title.ifBlank { "خطة" }, archiveLabel(a.target, a.done))
        }
        return out
    }
}
