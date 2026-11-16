package com.mohammedsamir.halaqati.exams

import org.json.JSONObject

/**
 * RC65: pure rules of the exams feature (scoring, pass/fail, grade band, label). No Android, so the numbers can be unit tested.
 * Defaults: pass mark 80; deductions of 2 per mistake, 1 per hesitation, 0.5 per prompt; bands 95 / 90 / 85.
 */
object ExamRules {
    data class Settings(
        val passMark: Double = 80.0,
        val excellent: Double = 95.0,
        val veryGood: Double = 90.0,
        val good: Double = 85.0,
        val mistakeWeight: Double = 2.0,
        val hesitationWeight: Double = 1.0,
        val promptWeight: Double = 0.5,
    )

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0

    fun settingsFrom(json: JSONObject?): Settings {
        if (json == null) return Settings()
        fun num(key: String, fallback: Double): Double = if (json.has(key) && !json.isNull(key)) json.optDouble(key, fallback) else fallback
        val d = Settings()
        return Settings(
            passMark = num("pass_mark", d.passMark), excellent = num("excellent", d.excellent), veryGood = num("very_good", d.veryGood), good = num("good", d.good),
            mistakeWeight = num("mistake_weight", d.mistakeWeight), hesitationWeight = num("hesitation_weight", d.hesitationWeight), promptWeight = num("prompt_weight", d.promptWeight),
        )
    }

    /** 100 minus the weighted deductions, never below 0. */
    fun deductionScore(mistakes: Int, hesitations: Int, prompts: Int, s: Settings): Double =
        round1((100.0 - mistakes * s.mistakeWeight - hesitations * s.hesitationWeight - prompts * s.promptWeight).coerceIn(0.0, 100.0))

    /** parts = (score, max) per question; the result is a percentage. */
    fun questionsScore(parts: List<Pair<Double, Double>>): Double {
        val max = parts.sumOf { it.second }
        if (max <= 0.0) return 0.0
        val got = parts.sumOf { it.first.coerceIn(0.0, it.second) }
        return round1((got / max * 100.0).coerceIn(0.0, 100.0))
    }

    fun passed(score: Double, s: Settings): Boolean = score + 1e-9 >= s.passMark

    fun gradeAr(score: Double, s: Settings): String = when {
        score + 1e-9 < s.passMark -> "لم يجتز"
        score >= s.excellent -> "ممتاز"
        score >= s.veryGood -> "جيد جدًا"
        score >= s.good -> "جيد"
        else -> "مقبول"
    }

    fun labelFor(kind: String, from: Int?, to: Int?, custom: String): String = when (kind) {
        "juz" -> "الجزء ${from ?: 1}"
        "juz_range" -> "الأجزاء ${minOf(from ?: 1, to ?: from ?: 1)}–${maxOf(from ?: 1, to ?: from ?: 1)}"
        else -> custom.trim().ifBlank { "نطاق مخصص" }
    }

    fun modeAr(mode: String): String = when (mode) {
        "hifz" -> "حفظ"
        "tajweed" -> "تجويد"
        "hifz_tajweed" -> "حفظ وتجويد"
        "sard" -> "سرد"
        else -> mode
    }
}
