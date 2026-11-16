package com.mohammedsamir.halaqati.ui.dashboard

/** Shared follow-up grouping so Dashboard and the full follow-up screen never disagree. */
object FollowUpRules {
    data class Group(val title: String, val items: List<DashboardAttentionItem>)

    fun group(
        delayed: List<DashboardAttentionItem>,
        quality: List<DashboardAttentionItem>,
        absence: List<DashboardAttentionItem>,
        notHeard: List<DashboardAttentionItem>,
    ): List<Group> {
        val sources = listOf(
            "متأخرون عن الخطة" to delayed,
            "جودة التسميع" to quality,
            "غياب متكرر / اليوم" to absence,
            "لم يسمّع" to notHeard,
        )
        val counts = sources.flatMap { it.second }.groupingBy { it.studentId }.eachCount()
        val urgent = sources.flatMap { it.second }
            .filter { (counts[it.studentId] ?: 0) >= 2 }
            .distinctBy { it.studentId }
        val used = urgent.mapTo(linkedSetOf()) { it.studentId }
        return buildList {
            if (urgent.isNotEmpty()) add(Group("متابعة عاجلة", urgent))
            sources.forEach { (title, rows) ->
                val unique = rows.filter { it.studentId !in used }.distinctBy { it.studentId }
                if (unique.isNotEmpty()) {
                    add(Group(title, unique))
                    used += unique.map { it.studentId }
                }
            }
        }
    }
}
