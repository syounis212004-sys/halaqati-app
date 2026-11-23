package com.mohammedsamir.halaqati.data

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * V7: the circle's rule, the same on web, Android and the server (compute_recitation_scoring).
 * A mistake is a phrase (counted once however long); a prompt is one word, every two harakat are one prompt.
 * The most allowed on one page is ONE mistake and TWO prompts (a started page has the full allowance, more pages allow
 * proportionally more). safety = 100 - (20 * mistakes + 5 * prompts) / pages. Going over the allowance is always "repeat":
 * the percentage is then capped at 69.9 so it never contradicts the grade.
 */
object SafetyScoring {
    private fun isOver(pages: Double, mistakes: Int, prompts: Int, lines: Double): Boolean {
        val allowance = max(1.0, ceil(lines / 15.0 - 1e-9))
        return mistakes.coerceAtLeast(0) > allowance || prompts.coerceAtLeast(0) > 2.0 * allowance
    }

    /** 15 lines are assumed per full Mushaf page when no QCF line selection is present. */
    fun calculate(pages: Double, mistakes: Int, prompts: Int, heardLines: Double? = null): Double {
        val p = pages.coerceAtLeast(0.0)
        val lines = heardLines?.coerceAtLeast(0.0) ?: (p * 15.0)
        if (p <= 0.0 || lines <= 0.0) return 0.0
        val raw = 100.0 - 15.0 * (20.0 * mistakes.coerceAtLeast(0) + 5.0 * prompts.coerceAtLeast(0)) / lines
        val capped = if (isOver(p, mistakes, prompts, lines)) minOf(raw, 69.9) else raw
        return floor(capped.coerceIn(0.0, 100.0) * 10.0 + 0.5 + 1e-9) / 10.0
    }

    fun inferredEvaluation(safety: Double, hasContent: Boolean = true): String = when {
        !hasContent -> "not_rated"
        safety >= 90.0 -> "excellent"
        safety >= 80.0 -> "very_good"
        safety >= 70.0 -> "good"
        else -> "repeat"
    }

    /** A penalty unit is one mistake or four prompts. */
    fun penaltyUnits(mistakes: Int, prompts: Int): Double =
        mistakes.coerceAtLeast(0) + prompts.coerceAtLeast(0) * 0.25

    fun points(pages: Double, mistakes: Int, prompts: Int): Double {
        val p = pages.coerceAtLeast(0.0)
        if (p <= 0.0) return 0.0
        val value = (p - penaltyUnits(mistakes, prompts) * 0.25).coerceAtLeast(0.0)
        return floor(value * 100.0 + 0.5 + 1e-9) / 100.0
    }
}
