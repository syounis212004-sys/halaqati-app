package com.mohammedsamir.halaqati.ui.dashboard

import kotlin.math.max
import kotlin.math.min

object DashboardRules {
    fun isAbsentStatus(status: String): Boolean = status.lowercase() in setOf("absent", "excused")

    fun hiddenCount(total: Int, preview: Int): Int = max(0, total - max(0, preview))

    fun displayRange(surah: String, first: Int, second: Int): String {
        val start = min(first, second)
        val end = max(first, second)
        return if (start == end) "$surah $start" else "$surah $start–$end"
    }
}
