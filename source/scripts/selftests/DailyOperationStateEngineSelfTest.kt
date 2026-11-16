import com.mohammedsamir.halaqati.ui.dashboard.DailyOperationStateEngine

private fun check(value: Boolean, message: String) {
    if (!value) error(message)
}

fun main() {
    val off = DailyOperationStateEngine.evaluate(
        activeStudentIds = setOf("a", "b"),
        scheduled = emptyList(),
        sessions = emptyList(),
    )
    check(off.mode == DailyOperationStateEngine.Mode.OFF_DAY, "No schedule/session must be OFF_DAY")
    check(!off.countDailyMetrics, "OFF_DAY must not count daily metrics")
    check(off.expectedStudentIds.isEmpty(), "OFF_DAY must have no expected students")

    val waiting = DailyOperationStateEngine.evaluate(
        activeStudentIds = setOf("a", "b"),
        scheduled = listOf(
            DailyOperationStateEngine.ScheduledActivity("a", DailyOperationStateEngine.Activity.HIFZ),
            DailyOperationStateEngine.ScheduledActivity("b", DailyOperationStateEngine.Activity.HIFZ),
        ),
        sessions = emptyList(),
    )
    check(waiting.mode == DailyOperationStateEngine.Mode.NO_SESSION_YET, "Scheduled day without session must wait")
    check(!waiting.countDailyMetrics, "NO_SESSION_YET must not mark students not-heard")
    check(waiting.expectedStudentIds == setOf("a", "b"), "Scheduled students must be retained")

    val sard = DailyOperationStateEngine.evaluate(
        activeStudentIds = setOf("a", "b", "c"),
        scheduled = listOf(
            DailyOperationStateEngine.ScheduledActivity("a", DailyOperationStateEngine.Activity.SARD),
            DailyOperationStateEngine.ScheduledActivity("b", DailyOperationStateEngine.Activity.SARD),
        ),
        sessions = listOf(DailyOperationStateEngine.SessionSignal("sard", "open")),
    )
    check(sard.mode == DailyOperationStateEngine.Mode.SARD_DAY, "Open sard session must be SARD_DAY")
    check(sard.countDailyMetrics, "Open session must enable daily metrics")
    check(sard.expectedStudentIds == setOf("a", "b"), "Only scheduled students should be expected")

    val manual = DailyOperationStateEngine.evaluate(
        activeStudentIds = setOf("a", "b"),
        scheduled = emptyList(),
        sessions = listOf(DailyOperationStateEngine.SessionSignal("tasmee", "open")),
    )
    check(manual.mode == DailyOperationStateEngine.Mode.HIFZ_DAY, "Manual open tasmee session must count as hifz day")
    check(manual.expectedStudentIds == setOf("a", "b"), "Manual session falls back to active roster")

    val plannedOnly = DailyOperationStateEngine.evaluate(
        activeStudentIds = setOf("a"),
        scheduled = listOf(DailyOperationStateEngine.ScheduledActivity("a", DailyOperationStateEngine.Activity.HIFZ)),
        sessions = listOf(DailyOperationStateEngine.SessionSignal("tasmee", "planned")),
    )
    check(plannedOnly.mode == DailyOperationStateEngine.Mode.NO_SESSION_YET, "Planned session is not started")
    check(!plannedOnly.countDailyMetrics, "Planned session must not count daily metrics")

    val mixed = DailyOperationStateEngine.evaluate(
        activeStudentIds = setOf("a", "b"),
        scheduled = listOf(
            DailyOperationStateEngine.ScheduledActivity("a", DailyOperationStateEngine.Activity.HIFZ),
            DailyOperationStateEngine.ScheduledActivity("b", DailyOperationStateEngine.Activity.SARD),
        ),
        sessions = listOf(
            DailyOperationStateEngine.SessionSignal("tasmee", "open"),
            DailyOperationStateEngine.SessionSignal("sard", "open"),
        ),
    )
    check(mixed.mode == DailyOperationStateEngine.Mode.MIXED_DAY, "Mixed scheduled activities must be MIXED_DAY")


    val saturday = java.time.LocalDate.of(2026, 9, 26)
    check(
        !DailyOperationStateEngine.isScheduledDay(
            date = saturday,
            start = java.time.LocalDate.of(2026, 9, 1),
            end = java.time.LocalDate.of(2026, 9, 30),
            scheduleWeekdays = setOf(0, 1, 2, 3, 4),
            catchUpWeekday = null,
        ),
        "Saturday 2026-09-26 must be off when weekday 6 is not scheduled",
    )
    val friday = java.time.LocalDate.of(2026, 9, 25)
    check(
        DailyOperationStateEngine.isScheduledDay(
            date = friday,
            start = java.time.LocalDate.of(2026, 9, 1),
            end = java.time.LocalDate.of(2026, 9, 30),
            scheduleWeekdays = setOf(5),
            catchUpWeekday = null,
        ),
        "Friday 2026-09-25 must be active when weekday 5 is scheduled",
    )

    println("DailyOperationStateEngineSelfTest: OK")
}
