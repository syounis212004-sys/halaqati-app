package com.mohammedsamir.halaqati.plans

/** Reports scope sessions, not just the student's primary enrollment. Null is legacy unscoped; empty is no authorized scope. */
object ReportScopeRules {
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    fun ids(kind: WorkMode, selected: String, types: Map<String, String?>): Set<String> =
        types.filter { (id, type) ->
            uuid.matches(id) && (selected == "all" || id == selected) &&
                (kind == WorkMode.MIXED || WorkModeRules.isProgramType(type) == (kind == WorkMode.PROGRAM))
        }.keys.toSortedSet()

    fun sessionFilter(halaqaId: String, scopedIds: Set<String>?): String {
        if (scopedIds == null) return if (halaqaId == "all") "" else "&sessions.halaqa_id=eq.$halaqaId"
        require(scopedIds.isNotEmpty() && scopedIds.all(uuid::matches)) { "نطاق التقرير غير متاح" }
        require(halaqaId == "all" || halaqaId in scopedIds) { "نطاق التقرير غير متاح" }
        return "&sessions.halaqa_id=in.(${scopedIds.sorted().joinToString(",")})"
    }

    fun contains(primary: String?, memberships: Collection<String>, ids: Set<String>): Boolean =
        primary in ids || memberships.any { it in ids }

    fun cachePart(ids: Set<String>?): String = ids?.sorted()?.joinToString(",") ?: "legacy"
}
