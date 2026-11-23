package com.mohammedsamir.halaqati.plans

/**
 * RC27 pure rules for two home cards:
 *  - repeated absence (consecutive absent sessions, newest first), with a ready guardian message;
 *  - halaqa comparison for a month (attendance, pages, quality).
 */
object OrgInsightRules {
    data class Attendance(val studentId: String, val date: String, val status: String, val sessionCancelled: Boolean = false)
    data class Streak(val studentId: String, val count: Int, val lastAbsentDate: String)

    /**
     * Consecutive "absent" from the most recent session backwards. "excused" neither counts nor breaks the
     * streak; "present" (or late) breaks it; cancelled sessions are ignored.
     */
    fun absenceStreaks(records: List<Attendance>, minStreak: Int = 3): List<Streak> =
        records.filter { !it.sessionCancelled }
            .groupBy { it.studentId }
            .mapNotNull { (id, rows) ->
                var count = 0
                var last: String? = null
                for (r in rows.sortedByDescending { it.date }) {
                    when (r.status) {
                        "absent" -> { count++; if (last == null) last = r.date }
                        "excused" -> Unit
                        else -> break
                    }
                }
                if (count >= minStreak && last != null) Streak(id, count, last) else null
            }
            .sortedWith(compareByDescending<Streak> { it.count }.thenByDescending { it.lastAbsentDate })

    fun guardianMessage(studentName: String, count: Int, halaqaName: String? = null): String =
        "السلام عليكم ورحمة الله وبركاته، نودّ الاطمئنان على الطالب $studentName، فقد غاب عن $count جلسات متتالية" +
            (if (!halaqaName.isNullOrBlank()) " في $halaqaName" else "") +
            ". نرجو إفادتنا بالسبب وحرصكم على انتظامه. جزاكم الله خيرًا."

    data class Inactive(val studentId: String, val daysSince: Int?, val lastDate: String?)

    /**
     * RC27.26: active students with no recitation for [days] days or more (or never in the window).
     * [lastRecitationDate] maps studentId -> newest recitation date (yyyy-MM-dd); students absent from the map
     * have none inside the fetched window. Students enrolled less than [days] days ago are skipped.
     */
    fun inactiveStudents(
        activeIds: Collection<String>,
        lastRecitationDate: Map<String, String>,
        today: java.time.LocalDate,
        days: Int = 14,
        enrollmentDate: Map<String, String> = emptyMap(),
    ): List<Inactive> = activeIds.mapNotNull { id ->
        val enrolled = enrollmentDate[id]?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        if (enrolled != null && java.time.temporal.ChronoUnit.DAYS.between(enrolled, today) < days) return@mapNotNull null
        val last = lastRecitationDate[id]?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        val since = last?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today).toInt() }
        if (last == null || (since != null && since >= days)) Inactive(id, since, last?.toString()) else null
    }.sortedWith(compareByDescending<Inactive> { it.daysSince ?: Int.MAX_VALUE })

    fun inactiveGuardianMessage(studentName: String, days: Int?): String =
        "السلام عليكم ورحمة الله وبركاته، نودّ الاطمئنان على الطالب $studentName، " +
            (if (days != null) "فلم يُسمِّع منذ $days يومًا" else "فلم نسجّل له تسميعًا منذ مدة") +
            ". نرجو التواصل معنا لنعينه على العودة لانتظامه. جزاكم الله خيرًا."

    /** RC27.31: period presets of the comparison card (several options, not only "this month"). */
    val COMPARE_PERIODS = listOf(
        "week" to "هذا الأسبوع",
        "month" to "هذا الشهر",
        "prev" to "الشهر الماضي",
        "quarter" to "آخر 3 أشهر",
        "year" to "آخر سنة",
    )

    fun comparePeriodRange(kind: String, today: java.time.LocalDate): Pair<java.time.LocalDate, java.time.LocalDate> = when (kind) {
        "week" -> today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SATURDAY)) to today
        "prev" -> today.minusMonths(1).withDayOfMonth(1).let { it to it.withDayOfMonth(it.lengthOfMonth()) }
        "quarter" -> today.minusMonths(3) to today
        "year" -> today.minusYears(1) to today
        else -> today.withDayOfMonth(1) to today
    }

    /** One recitation/attendance row mapped to the group it counts for (halaqa or category). */
    data class GroupedSession(val groupId: String, val sessionKey: String)

    fun distinctSessionsByGroup(rows: List<GroupedSession>): Map<String, Int> =
        rows.distinct().groupBy { it.groupId }.mapValues { it.value.size }

    /** RC27.32: one ranking rule for the comparison card and the seasons: groups with sessions first, then attendance, quality, pages/student. */
    fun rank(stats: List<HalaqaStats>): List<HalaqaStats> = stats.sortedWith(
        compareByDescending<HalaqaStats> { it.sessions > 0 }
            .thenByDescending { it.attendancePercent ?: -1 }
            .thenByDescending { it.qualityPercent ?: -1 }
            .thenByDescending { it.pagesPerStudent },
    )

    val SEASON_DURATIONS = listOf(14 to "أسبوعان", 30 to "شهر", 91 to "3 أشهر", 182 to "6 أشهر", 365 to "سنة")

    data class HalaqaSession(val halaqaId: String, val date: String, val cancelled: Boolean)
    data class HalaqaAttendance(val halaqaId: String, val status: String)
    /** [safety] is the recitation's stored safety percentage (errors + prompts, scoring V7); null when the row has none. */
    data class HalaqaRecitation(val halaqaId: String, val activityType: String, val pages: Double, val evaluation: String, val safety: Double? = null)
    data class HalaqaStats(
        val halaqaId: String,
        val students: Int,
        val sessions: Int,
        val attendancePercent: Int?,   // present / (present + absent); excused excluded
        val hifzPages: Double,
        val reviewPages: Double,
        val pagesPerStudent: Double,
        val qualityPercent: Int?,      // RC29: average recitation safety (errors/prompts), not "share that is not weak"
    )

    /**
     * RC29: the old rule counted every recitation that was not "repeat"/"weak" as good, so a group whose recitations
     * were all "good" with several prompts still showed 100%. Quality is now the average safety percentage of the
     * recitations (the same figure the student and guardian reports use). Rows saved without a stored safety value
     * fall back to the middle of their evaluation's range.
     */
    private val EVALUATION_SAFETY = mapOf(
        "excellent" to 95.0, "ممتاز" to 95.0,
        "very_good" to 85.0, "جيد جدا" to 85.0, "جيد جدًا" to 85.0,
        "good" to 75.0, "جيد" to 75.0,
        "acceptable" to 65.0, "مقبول" to 65.0,
        "weak" to 50.0, "ضعيف" to 50.0,
        "repeat" to 40.0, "إعادة" to 40.0,
    )

    fun recitationSafety(r: HalaqaRecitation): Double? =
        if (RecitationQualityRules.isPlaceholder(r.evaluation, r.safety, r.pages)) null else r.safety?.takeIf { it in 0.0..100.0 } ?: EVALUATION_SAFETY[r.evaluation.trim().lowercase()]

    fun qualityPercent(recitations: List<HalaqaRecitation>): Int? {
        val values = recitations.mapNotNull(::recitationSafety)
        return if (values.isEmpty()) null else Math.round(values.average()).toInt()
    }
    private val REVIEW = setOf("review_near", "review_far", "tathbit", "sard")

    fun compare(
        halaqaIds: List<String>,
        activeStudentsByHalaqa: Map<String, Int>,
        sessions: List<HalaqaSession>,
        attendance: List<HalaqaAttendance>,
        recitations: List<HalaqaRecitation>,
    ): List<HalaqaStats> = halaqaIds.map { id ->
        val att = attendance.filter { it.halaqaId == id }
        val present = att.count { it.status == "present" || it.status == "late" }
        val absent = att.count { it.status == "absent" }
        val rec = recitations.filter { it.halaqaId == id }
        val hifz = rec.filter { it.activityType == "new_hifz" }.sumOf { it.pages }
        val review = rec.filter { it.activityType in REVIEW }.sumOf { it.pages }
        val students = activeStudentsByHalaqa[id] ?: 0
        HalaqaStats(
            halaqaId = id,
            students = students,
            sessions = sessions.count { it.halaqaId == id && !it.cancelled },
            attendancePercent = if (present + absent > 0) Math.round(present * 100.0 / (present + absent)).toInt() else null,
            hifzPages = hifz,
            reviewPages = review,
            pagesPerStudent = if (students > 0) (hifz + review) / students else 0.0,
            qualityPercent = qualityPercent(rec),
        )
    }.sortedByDescending { it.pagesPerStudent }
}
