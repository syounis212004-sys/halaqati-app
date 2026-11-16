package com.mohammedsamir.halaqati.data

import java.time.LocalDate

object StudentInsightEngine {
    enum class PerformanceLevel(val label: String) {
        EXCELLENT("ممتاز"), VERY_GOOD("جيد جدًا"), GOOD("جيد"), NEEDS_FOLLOW_UP("يحتاج متابعة")
    }

    enum class FollowUpKind { PLAN_DELAY, QUALITY, ATTENDANCE, CONSISTENCY }

    data class FollowUpReason(val kind: FollowUpKind, val text: String, val priority: Int)

    data class DayProgress(
        val date: LocalDate,
        val activity: String,
        val required: Double?,
        val completed: Double,
        val statusLabel: String,
    )

    data class State(
        val performanceLevel: PerformanceLevel,
        val achievementRatio: Double,
        val followUpReasons: List<FollowUpReason>,
        val averageSafety: Double?,
        val mistakes: Int,
        val prompts: Int,
        val present: Int,
        val absent: Int,
        val excused: Int,
        val late: Int,
    )

    fun build(
        requiredToDate: Double,
        completed: Double,
        averageSafety: Double?,
        mistakes: Int,
        prompts: Int,
        present: Int,
        absent: Int,
        excused: Int,
        late: Int,
        missedActivityDays: Int = 0,
    ): State {
        val ratio = if (requiredToDate <= 0.01) 1.0 else (completed / requiredToDate).coerceAtLeast(0.0)
        val quality = averageSafety ?: 100.0
        val performance = when {
            ratio >= 1.0 && quality >= 90.0 -> PerformanceLevel.EXCELLENT
            ratio >= 0.90 && quality >= 82.0 -> PerformanceLevel.VERY_GOOD
            ratio >= 0.75 && quality >= 72.0 -> PerformanceLevel.GOOD
            else -> PerformanceLevel.NEEDS_FOLLOW_UP
        }
        val reasons = buildList {
            val behind = (requiredToDate - completed).coerceAtLeast(0.0)
            if (behind >= 0.5) add(FollowUpReason(FollowUpKind.PLAN_DELAY, "متأخر عن المطلوب حتى اليوم ${format(behind)} صفحة", if (behind >= 7.0) 100 else 70))
            if (averageSafety != null && averageSafety < 78.0) add(FollowUpReason(FollowUpKind.QUALITY, "جودة التسميع ${format(averageSafety)}% وتحتاج متابعة", 80))
            if (absent + excused >= 2) add(FollowUpReason(FollowUpKind.ATTENDANCE, "لديه ${absent + excused} أيام غياب هذا الشهر", 75))
            if (missedActivityDays >= 2) add(FollowUpReason(FollowUpKind.CONSISTENCY, "لم ينجز نشاطه في $missedActivityDays أيام مقررة", 85))
        }.sortedByDescending { it.priority }
        return State(performance, ratio, reasons, averageSafety, mistakes, prompts, present, absent, excused, late)
    }

    private fun format(value: Double): String = if (kotlin.math.abs(value - value.toInt()) < 0.005) value.toInt().toString() else "%.1f".format(java.util.Locale.US, value)
}
