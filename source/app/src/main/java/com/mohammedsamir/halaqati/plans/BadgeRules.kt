package com.mohammedsamir.halaqati.plans

import java.time.LocalDate

/**
 * RC52 badges. Computed from existing attendance and recitation rows (no new tables).
 * Levels: 1 bronze, 2 silver, 3 gold. Three scopes, all deliberately hard to earn:
 *  - "day"    : running streaks and the last 30 days (long streaks only);
 *  - "month"  : calendar-month badges — every condition of a tier must hold together;
 *  - "season" : three calendar months in a row — the weakest of the three months decides the tier.
 */
object BadgeRules {
    data class Attendance(val date: String, val status: String, val cancelled: Boolean = false)
    data class Recitation(val date: String, val pages: Double, val evaluation: String, val cancelled: Boolean = false)
    data class Badge(val key: String, val title: String, val detail: String, val level: Int, val scope: String = "day")
    data class Next(val title: String, val remaining: Int, val unit: String)
    data class Result(val earned: List<Badge>, val next: Next?)

    private val POOR = setOf("repeat", "weak", "إعادة", "ضعيف")
    private val STRONG = setOf("excellent", "very_good", "ممتاز", "جيد جدا", "جيد جدًا")
    private val EXCELLENT = setOf("excellent", "ممتاز")

    val STREAK_STEPS = listOf(10, 20, 40)
    val QUALITY_STEPS = listOf(8, 15, 30)
    val PAGES_STEPS = listOf(45.0, 90.0, 150.0)
    val MONTH_PAGES_STEPS = listOf(25.0, 40.0, 60.0)

    /** Consecutive present/late sessions from the newest backwards; excused is neutral, absent breaks. */
    fun attendanceStreak(rows: List<Attendance>): Int {
        var n = 0
        for (r in rows.filter { !it.cancelled }.sortedByDescending { it.date }) {
            when (r.status) {
                "present", "late" -> n++
                "excused" -> Unit
                else -> break
            }
        }
        return n
    }

    /** Consecutive recent recitations rated very good or better. */
    fun qualityStreak(rows: List<Recitation>): Int {
        var n = 0
        for (r in rows.filter { !it.cancelled }.sortedByDescending { it.date }) {
            if (r.evaluation.lowercase() in STRONG) n++ else break
        }
        return n
    }

    private fun level(value: Double, steps: List<Double>): Int = steps.count { value >= it }

    /** Levels (0..3) of the three monthly badges for one "yyyy-MM". */
    data class MonthLevels(val consistency: Int, val quality: Int, val pages: Int, val pageTotal: Double) {
        val weakest: Int get() = minOf(consistency, quality, pages)
    }

    fun monthLevels(attendance: List<Attendance>, recitations: List<Recitation>, ym: String): MonthLevels {
        val a = attendance.filter { !it.cancelled && it.date.startsWith(ym) && it.status != "excused" }
        val good = a.count { it.status == "present" || it.status == "late" }
        val consistency = when {
            a.size < 8 -> 0
            good == a.size && a.size >= 12 -> 3
            good * 100 >= a.size * 95 -> 2
            good * 100 >= a.size * 90 -> 1
            else -> 0
        }
        val rec = recitations.filter { !it.cancelled && it.date.startsWith(ym) }
        val rated = rec.filter { it.evaluation.isNotBlank() && it.evaluation.lowercase() != "not_rated" }
        val poor = rated.count { it.evaluation.lowercase() in POOR }
        val excellent = rated.count { it.evaluation.lowercase() in EXCELLENT }
        val share = if (rated.size >= 10) excellent.toDouble() / rated.size else -1.0
        val quality = when {
            share >= 0.85 && poor == 0 -> 3
            share >= 0.70 && poor == 0 -> 2
            share >= 0.50 && poor <= 1 -> 1
            else -> 0
        }
        val total = rec.sumOf { it.pages }
        return MonthLevels(consistency, quality, level(total, MONTH_PAGES_STEPS), total)
    }

    fun compute(attendance: List<Attendance>, recitations: List<Recitation>, today: LocalDate = LocalDate.now()): Result {
        val earned = ArrayList<Badge>()
        val streak = attendanceStreak(attendance)
        val sl = level(streak.toDouble(), STREAK_STEPS.map { it.toDouble() })
        if (sl > 0) earned += Badge("streak", "انتظام", "$streak جلسة متتالية دون غياب", sl)
        val quality = qualityStreak(recitations)
        val ql = level(quality.toDouble(), QUALITY_STEPS.map { it.toDouble() })
        if (ql > 0) earned += Badge("quality", "إتقان", "$quality تسميعًا متتاليًا بتقدير جيد جدًا فأعلى", ql)
        val from = today.minusDays(30).toString()
        val pages = recitations.filter { !it.cancelled && it.date >= from }.sumOf { it.pages }
        val pl = level(pages, PAGES_STEPS)
        if (pl > 0) earned += Badge("pages", "اجتهاد", "${Math.round(pages)} صفحة خلال 30 يومًا", pl)

        // Monthly badges for the current month (they can still rise until the month ends).
        val ym = java.time.YearMonth.from(today)
        val cur = monthLevels(attendance, recitations, ym.toString())
        if (cur.consistency > 0) earned += Badge("m-consistency", "وسام الانتظام الشهري", "حضور ${ym.monthValue}/${ym.year} دون تغيّب يُذكر", cur.consistency, "month")
        if (cur.quality > 0) earned += Badge("m-quality", "وسام الإتقان الشهري", "أغلب تسميعات هذا الشهر بتقدير ممتاز", cur.quality, "month")
        if (cur.pages > 0) earned += Badge("m-pages", "وسام الحفظ الشهري", "${Math.round(cur.pageTotal)} صفحة في الشهر", cur.pages, "month")

        // Season = the last three calendar months. Every month must hold all three monthly badges.
        val months = (0..2).map { ym.minusMonths(it.toLong()).toString() }
        val seasonLevels = months.map { monthLevels(attendance, recitations, it).weakest }
        val seasonLevel = seasonLevels.min()
        if (seasonLevel > 0) {
            earned += Badge("season", "وسام الموسم", "ثلاثة أشهر متتالية بأوسمة الانتظام والإتقان والحفظ معًا", seasonLevel, "season")
        }

        // Closest next goal: this month's pages first (most concrete), else the streak step ahead.
        val nextPages = MONTH_PAGES_STEPS.firstOrNull { it > cur.pageTotal }
        val next = nextPages?.let { Next("وسام الحفظ الشهري", Math.ceil(it - cur.pageTotal).toInt(), "صفحة هذا الشهر") }
            ?: STREAK_STEPS.firstOrNull { it > streak }?.let { Next("شارة الانتظام", it - streak, "جلسة متتالية") }
        return Result(earned.sortedWith(compareByDescending<Badge> { it.level }.thenBy { it.scope }), next)
    }
}
