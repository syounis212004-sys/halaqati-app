package com.mohammedsamir.halaqati.ui.dashboard

import java.time.LocalDate

/**
 * RC10.1 single source of truth for whether today's dashboard is an active halaqa day.
 *
 * Daily counters must never be derived from "active students - today's recitations" until a
 * real session has started. This prevents off-days and not-yet-started days from marking the
 * whole halaqa as "لم يسمّع" or absent.
 */
object DailyOperationStateEngine {
    enum class Activity { HIFZ, SARD, REVIEW, TATHBIT, TEST, OTHER }

    enum class Mode {
        OFF_DAY,
        NO_SESSION_YET,
        HIFZ_DAY,
        SARD_DAY,
        REVIEW_DAY,
        TATHBIT_DAY,
        MIXED_DAY,
        OTHER_ACTIVITY_DAY,
    }

    data class ScheduledActivity(
        val studentId: String,
        val activity: Activity,
    )

    data class SessionSignal(
        val type: String,
        val status: String,
    )

    data class State(
        val mode: Mode,
        val activeActivities: Set<Activity>,
        val expectedStudentIds: Set<String>,
        val sessionStarted: Boolean,
        val countDailyMetrics: Boolean,
        val title: String,
        val subtitle: String,
    )


    fun isScheduledDay(
        date: LocalDate,
        start: LocalDate,
        end: LocalDate,
        scheduleWeekdays: Set<Int>,
        catchUpWeekday: Int?,
        pauseRanges: List<Pair<LocalDate, LocalDate>> = emptyList(),
        intensiveRange: Pair<LocalDate, LocalDate>? = null,
        intensiveWeekdays: Set<Int> = emptySet(),
        intensiveEnabled: Boolean = false,
    ): Boolean {
        if (date !in start..end) return false
        if (pauseRanges.any { (from, to) -> date in from..to }) return false
        val weekday = date.dayOfWeek.value % 7
        if (weekday == catchUpWeekday) return false
        val intensive = intensiveEnabled && intensiveRange?.let { (from, to) -> date in from..to } == true
        return if (intensive) weekday in intensiveWeekdays else weekday in scheduleWeekdays
    }

    fun activityForSessionType(type: String): Activity = when (type.lowercase()) {
        "tasmee", "hifz", "new_hifz" -> Activity.HIFZ
        "sard" -> Activity.SARD
        "review" -> Activity.REVIEW
        "tathbit" -> Activity.TATHBIT
        "test" -> Activity.TEST
        else -> Activity.OTHER
    }

    fun evaluate(
        activeStudentIds: Set<String>,
        scheduled: List<ScheduledActivity>,
        sessions: List<SessionSignal>,
    ): State {
        val eligibleSessions = sessions.filter { it.status.lowercase() != "cancelled" }
        val startedSessions = eligibleSessions.filter { it.status.lowercase() !in setOf("planned", "draft") }
        val scheduledActivities = scheduled.map { it.activity }.toSet()
        val startedActivities = startedSessions.map { activityForSessionType(it.type) }.toSet()
        val activities = if (scheduledActivities.isNotEmpty()) scheduledActivities else startedActivities
        val expected = if (scheduled.isNotEmpty()) scheduled.map { it.studentId }.filter { it in activeStudentIds }.toSet()
        else if (startedSessions.isNotEmpty()) activeStudentIds
        else emptySet()

        if (activities.isEmpty() && eligibleSessions.isEmpty()) {
            return State(
                mode = Mode.OFF_DAY,
                activeActivities = emptySet(),
                expectedStudentIds = emptySet(),
                sessionStarted = false,
                countDailyMetrics = false,
                title = "اليوم إجازة",
                subtitle = "لا توجد جلسة حفظ أو سرد أو مراجعة مقررة اليوم.",
            )
        }

        if (startedSessions.isEmpty()) {
            return State(
                mode = Mode.NO_SESSION_YET,
                activeActivities = activities,
                expectedStudentIds = expected,
                sessionStarted = false,
                countDailyMetrics = false,
                title = "جلسة اليوم لم تبدأ بعد",
                subtitle = activitySubtitle(activities, waiting = true),
            )
        }

        val mode = modeFor(activities.ifEmpty { startedActivities })
        return State(
            mode = mode,
            activeActivities = activities.ifEmpty { startedActivities },
            expectedStudentIds = expected,
            sessionStarted = true,
            countDailyMetrics = true,
            title = modeTitle(mode),
            subtitle = activitySubtitle(activities.ifEmpty { startedActivities }, waiting = false),
        )
    }

    private fun modeFor(activities: Set<Activity>): Mode = when {
        activities.size > 1 -> Mode.MIXED_DAY
        Activity.HIFZ in activities -> Mode.HIFZ_DAY
        Activity.SARD in activities -> Mode.SARD_DAY
        Activity.TATHBIT in activities -> Mode.TATHBIT_DAY
        Activity.REVIEW in activities -> Mode.REVIEW_DAY
        activities.isNotEmpty() -> Mode.OTHER_ACTIVITY_DAY
        else -> Mode.OFF_DAY
    }

    private fun modeTitle(mode: Mode): String = when (mode) {
        Mode.OFF_DAY -> "اليوم إجازة"
        Mode.NO_SESSION_YET -> "جلسة اليوم لم تبدأ بعد"
        Mode.HIFZ_DAY -> "يوم حفظ"
        Mode.SARD_DAY -> "يوم سرد"
        Mode.REVIEW_DAY -> "يوم مراجعة"
        Mode.TATHBIT_DAY -> "يوم تثبيت"
        Mode.MIXED_DAY -> "أنشطة اليوم"
        Mode.OTHER_ACTIVITY_DAY -> "نشاط اليوم"
    }

    private fun activitySubtitle(activities: Set<Activity>, waiting: Boolean): String {
        val names = activities.mapNotNull {
            when (it) {
                Activity.HIFZ -> "الحفظ"
                Activity.SARD -> "السرد"
                Activity.REVIEW -> "المراجعة"
                Activity.TATHBIT -> "التثبيت"
                Activity.TEST -> "الاختبار"
                Activity.OTHER -> null
            }
        }.distinct()
        val joined = when (names.size) {
            0 -> "النشاط المقرر"
            1 -> names.first()
            else -> names.joinToString(" و")
        }
        return if (waiting) "$joined مقرر اليوم، وتبدأ العدادات بعد فتح الجلسة."
        else "$joined هو النشاط الفعلي لليوم."
    }
}
