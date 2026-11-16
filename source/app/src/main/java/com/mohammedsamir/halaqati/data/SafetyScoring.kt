package com.mohammedsamir.halaqati.data

import kotlin.math.floor

/** Pure Kotlin implementation of the approved Halaqati V6 scoring rules. */
object SafetyScoring {
    private fun jsRound(value: Double): Double = floor(value + 0.5)

    /**
     * weighted mistakes = mistakes + 0.5 * prompts.
     * 15 lines are assumed per full Mushaf page when no QCF line selection is present.
     * safety = 100 - 20 * normalized weighted mistakes.
     */
    fun calculate(pages: Double, mistakes: Int, prompts: Int, heardLines: Double? = null): Double {
        val p = pages.coerceAtLeast(0.0)
        val units = penaltyUnits(mistakes, prompts)
        val lines = heardLines?.coerceAtLeast(0.0) ?: (p * 15.0)
        if (lines <= 0.0) return 0.0
        val normalized = units * 15.0 / lines
        val safety = (100.0 - normalized * 20.0).coerceIn(0.0, 100.0)
        return jsRound(safety * 10.0) / 10.0
    }

    fun inferredEvaluation(safety: Double, hasContent: Boolean = true): String = when {
        !hasContent -> "not_rated"
        safety >= 90.0 -> "excellent"
        safety >= 80.0 -> "very_good"
        safety >= 70.0 -> "good"
        else -> "repeat"
    }

    fun penaltyUnits(mistakes: Int, prompts: Int): Double =
        mistakes.coerceAtLeast(0) + prompts.coerceAtLeast(0) * 0.5

    fun points(pages: Double, mistakes: Int, prompts: Int): Double {
        val p = pages.coerceAtLeast(0.0)
        if (p <= 0.0) return 0.0
        val value = (p - penaltyUnits(mistakes, prompts) * 0.25).coerceAtLeast(0.0)
        return jsRound(value * 100.0) / 100.0
    }
}
