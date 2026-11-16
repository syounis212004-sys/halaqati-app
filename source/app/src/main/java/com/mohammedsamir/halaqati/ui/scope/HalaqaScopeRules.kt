package com.mohammedsamir.halaqati.ui.scope

/**
 * RC25: pure rules for "work inside one halaqa, month by month".
 * No Android types here so it is covered by the Kotlin self-tests.
 */
data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {
    private val order: Int get() = year * 12 + month
    override fun compareTo(other: MonthKey): Int = order.compareTo(other.order)

    val isUndated: Boolean get() = year == 0

    fun previous(): MonthKey = if (month <= 1) MonthKey(year - 1, 12) else MonthKey(year, month - 1)

    fun label(): String =
        if (isUndated) "بدون تاريخ" else "${ARABIC_MONTHS[(month - 1).coerceIn(0, 11)]} $year"

    companion object {
        val UNDATED = MonthKey(0, 0)
        private val ARABIC_MONTHS = listOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
        )

        /** Accepts "2026-10-12" or "2026-10-12T..." and returns null for anything else. */
        fun parse(iso: String?): MonthKey? {
            val text = iso?.trim().orEmpty()
            if (text.length < 7 || text[4] != '-') return null
            val year = text.substring(0, 4).toIntOrNull() ?: return null
            val month = text.substring(5, 7).toIntOrNull() ?: return null
            if (month !in 1..12 || year < 1900) return null
            return MonthKey(year, month)
        }
    }
}

data class MonthGroup<T>(val key: MonthKey, val items: List<T>)

object MonthGrouping {
    /** Newest month first. Items keep the order they came in. Unparsable dates land in one "undated" group at the end. */
    fun <T> group(items: List<T>, dateOf: (T) -> String?): List<MonthGroup<T>> {
        val buckets = LinkedHashMap<MonthKey, MutableList<T>>()
        items.forEach { item ->
            val key = MonthKey.parse(dateOf(item)) ?: MonthKey.UNDATED
            buckets.getOrPut(key) { mutableListOf() }.add(item)
        }
        return buckets.entries
            .sortedByDescending { it.key }
            .let { sorted ->
                val undated = sorted.filter { it.key.isUndated }
                sorted.filterNot { it.key.isUndated } + undated
            }
            .map { MonthGroup(it.key, it.value) }
    }

    /** This month and the previous one are open. If neither exists, the newest group is open. */
    fun defaultExpanded(keys: List<MonthKey>, today: MonthKey): Set<MonthKey> {
        val open = keys.filter { it == today || it == today.previous() }.toSet()
        if (open.isNotEmpty()) return open
        val newest = keys.filterNot { it.isUndated }.maxOrNull() ?: return emptySet()
        return setOf(newest)
    }
}

data class HalaqaScope(
    val selectedId: String?,
    /** Admin/supervisor with several halaqas and nothing chosen yet: show the halaqa overview first. */
    val showOverview: Boolean,
    /** Only people who can see more than one halaqa get the switcher. */
    val showSwitcher: Boolean,
)

object HalaqaScopeRules {
    fun resolve(role: String, visibleHalaqaIds: List<String>, savedId: String?): HalaqaScope {
        val ids = visibleHalaqaIds.filter { it.isNotBlank() }.distinct()
        return when {
            ids.isEmpty() -> HalaqaScope(null, false, false)
            ids.size == 1 -> HalaqaScope(ids.first(), false, false)
            savedId != null && savedId in ids -> HalaqaScope(savedId, false, true)
            role.equals("admin", true) || role.equals("supervisor", true) -> HalaqaScope(null, true, true)
            else -> HalaqaScope(ids.first(), false, true)
        }
    }
}

data class OverviewSession(val halaqaId: String, val date: String)
data class OverviewStudent(val halaqaId: String?, val active: Boolean)
data class HalaqaOverviewRow(
    val halaqaId: String,
    val studentCount: Int,
    val hasTodaySession: Boolean,
    val lastSessionDate: String?,
    val sessionCount: Int,
)

object HalaqaOverview {
    fun build(
        halaqaIds: List<String>,
        sessions: List<OverviewSession>,
        students: List<OverviewStudent>,
        today: String,
    ): List<HalaqaOverviewRow> = halaqaIds.map { id ->
        val mine = sessions.filter { it.halaqaId == id }
        HalaqaOverviewRow(
            halaqaId = id,
            studentCount = students.count { it.active && it.halaqaId == id },
            hasTodaySession = mine.any { it.date.startsWith(today) },
            lastSessionDate = mine.map { it.date }.maxOrNull(),
            sessionCount = mine.size,
        )
    }
}
