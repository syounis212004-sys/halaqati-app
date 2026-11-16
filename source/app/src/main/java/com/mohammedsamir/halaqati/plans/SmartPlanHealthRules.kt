package com.mohammedsamir.halaqati.plans

import kotlin.math.max

/**
 * RC19 progress-health contract.
 *
 * Health is measured against what the student was expected to finish by today,
 * not against the whole monthly goal. This keeps early-month cards fair.
 */
object SmartPlanHealthRules {
    const val BLUE = "blue"
    const val GREEN = "green"
    const val YELLOW = "yellow"
    const val RED = "red"
    const val GRAY = "gray"

    data class Band(
        val code: String,
        val label: String,
        val ratio: Double,
        val lagPercent: Double,
    )

    fun fromProgress(completed: Double, requiredByToday: Double, paused: Boolean = false): Band {
        if (paused) return Band(GRAY, "الخطة معلقة", 1.0, 0.0)
        if (requiredByToday <= 0.001) return Band(GREEN, "على المسار", 1.0, 0.0)
        return fromRatio(completed.coerceAtLeast(0.0) / requiredByToday)
    }

    fun fromRatio(rawRatio: Double): Band {
        val ratio = rawRatio.coerceAtLeast(0.0)
        val lag = max(0.0, (1.0 - ratio) * 100.0)
        return when {
            ratio > 1.10 -> Band(BLUE, "متقدم", ratio, 0.0)
            ratio >= 0.90 -> Band(GREEN, "على المسار", ratio, lag)
            ratio >= 0.75 -> Band(YELLOW, "يحتاج انتباه", ratio, lag)
            else -> Band(RED, "يحتاج متابعة", ratio, lag)
        }
    }

    fun trendLabel(code: String): String = when (code) {
        BLUE -> "↑ متقدم"
        GREEN -> "→ ثابت"
        YELLOW -> "↓ متأخر قليلًا"
        RED -> "↓ متأخر كثيرًا"
        else -> "— معلّق"
    }

    fun shouldNotifyGuardian(redScheduledDayStreak: Int): Boolean = redScheduledDayStreak >= 2
}
